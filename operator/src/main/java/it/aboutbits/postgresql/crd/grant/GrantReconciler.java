package it.aboutbits.postgresql.crd.grant;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.Cleaner;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.DeleteControl;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import it.aboutbits.postgresql.core.BaseReconciler;
import it.aboutbits.postgresql.core.CRPhase;
import it.aboutbits.postgresql.core.CRStatus;
import it.aboutbits.postgresql.core.PostgreSQLContextFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.jooq.impl.DSL.quotedName;

@NullMarked
@Slf4j
@RequiredArgsConstructor
public class GrantReconciler
        extends BaseReconciler<Grant, CRStatus>
        implements Reconciler<Grant>, Cleaner<Grant> {
    private final GrantService grantService;

    private final KubernetesClient kubernetesClient;
    private final PostgreSQLContextFactory contextFactory;

    @Override
    public UpdateControl<Grant> reconcile(
            Grant resource,
            Context<Grant> context
    ) {
        var spec = resource.getSpec();
        var status = initializeStatus(resource);

        var name = resource.getMetadata().getName();
        var namespace = resource.getMetadata().getNamespace();

        log.info(
                "Reconciling Grant [resource={}/{}, status.phase={}]",
                namespace,
                name,
                status.getPhase()
        );

        var clusterRef = spec.getClusterRef();

        var clusterConnectionOptional = getReferencedClusterConnection(
                kubernetesClient,
                resource,
                clusterRef
        );

        var objectType = spec.getObjectType();
        var grantPrivileges = new HashSet<>(spec.getPrivileges());
        var allowedPrivilegesForObjectType = objectType.privilegesSet();

        if (!allowedPrivilegesForObjectType.containsAll(grantPrivileges)) {
            var invalid = new HashSet<>(grantPrivileges);

            invalid.removeAll(allowedPrivilegesForObjectType);

            status.setPhase(CRPhase.ERROR)
                    .setMessage("Grant contains invalid privileges for the specified objectType. [resource=%s/%s, objectType=%s, invalidPrivileges=%s, allowedPrivilegesForObjectType=%s]".formatted(
                            getResourceNamespaceOrOwn(resource, clusterRef.getNamespace()),
                            clusterRef.getName(),
                            objectType,
                            invalid,
                            objectType.privileges()
                    ));

            return UpdateControl.patchStatus(resource);
        }

        if (clusterConnectionOptional.isEmpty()) {
            status.setPhase(CRPhase.PENDING)
                    .setMessage("The specified ClusterConnection does not exist or is not ready yet [resource=%s/%s]".formatted(
                            getResourceNamespaceOrOwn(resource, clusterRef.getNamespace()),
                            clusterRef.getName()
                    ));

            return UpdateControl.patchStatus(resource)
                    .rescheduleAfter(60, TimeUnit.SECONDS);
        }

        var database = spec.getDatabase();
        var clusterConnection = clusterConnectionOptional.get();

        UpdateControl<Grant> updateControl;

        try (var dsl = contextFactory.getDSLContext(clusterConnection, database)) {
            // Run everything in a single transaction
            updateControl = dsl.transactionResult(
                    cfg -> reconcileInTransaction(
                            cfg.dsl(),
                            resource,
                            status
                    )
            );
        } catch (Exception e) {
            return handleError(
                    resource,
                    status,
                    e
            );
        }

        return updateControl;
    }

    @Override
    public DeleteControl cleanup(
            Grant resource,
            Context<Grant> context
    ) throws Exception {
        var spec = resource.getSpec();
        var status = initializeStatus(resource);

        var name = resource.getMetadata().getName();
        var namespace = resource.getMetadata().getNamespace();

        log.info(
                "Deleting Grant [resource={}/{}, status.phase={}]",
                namespace,
                name,
                status.getPhase()
        );

        if (status.getPhase() != CRPhase.DELETING) {
            status.setPhase(CRPhase.DELETING)
                    .setMessage("Grant deletion in progress");
        }

        var clusterRef = spec.getClusterRef();

        var clusterConnectionOptional = getReferencedClusterConnection(
                kubernetesClient,
                resource,
                clusterRef
        );

        if (clusterConnectionOptional.isEmpty()) {
            status.setMessage("The specified ClusterConnection no longer exists or is not ready yet [resource=%s/%s]".formatted(
                    getResourceNamespaceOrOwn(resource, clusterRef.getNamespace()),
                    clusterRef.getName()
            ));

            return DeleteControl.noFinalizerRemoval()
                    .rescheduleAfter(60, TimeUnit.SECONDS);
        }

        var clusterConnection = clusterConnectionOptional.get();

        try (var dsl = contextFactory.getDSLContext(clusterConnection)) {
            dsl.transaction(cfg -> {
                var tx = cfg.dsl();

                var currentObjectPrivileges = grantService.determineCurrentObjectPrivileges(tx, spec);

                for (var objectPrivileges : currentObjectPrivileges.entrySet()) {
                    var object = objectPrivileges.getKey();
                    var privileges = objectPrivileges.getValue();

                    grantService.revoke(
                            tx,
                            spec,
                            object,
                            privileges
                    );
                }
            });

            return DeleteControl.defaultDelete();
        } catch (Exception e) {
            log.error(
                    "Failed to delete Grant [resource={}/{}, status.phase={}]",
                    namespace,
                    name,
                    status.getPhase()
            );

            status.setMessage("Deletion failed: %s".formatted(e.getMessage()));

            return DeleteControl.noFinalizerRemoval()
                    .rescheduleAfter(60, TimeUnit.SECONDS);
        }
    }

    @Override
    protected CRStatus newStatus() {
        return new CRStatus();
    }

    @SuppressWarnings("java:S3776")
    private UpdateControl<Grant> reconcileInTransaction(
            DSLContext tx,
            Grant resource,
            CRStatus status
    ) {
        var name = resource.getMetadata().getName();
        var namespace = resource.getMetadata().getNamespace();

        var spec = resource.getSpec();

        var schema = spec.getSchema();
        var objectType = spec.getObjectType();

        var expectedObjects = new HashSet<>(
                Objects.requireNonNullElse(
                        spec.getObjects(),
                        Collections.emptySet()
                )
        );
        var expectedPrivileges = new HashSet<>(spec.getPrivileges());

        var isAllMode = expectedObjects.isEmpty();

        var currentObjectPrivileges = grantService.determineCurrentObjectPrivileges(tx, spec);
        var ownershipMap = grantService.determineObjectExistenceAndOwnership(tx, spec);

        // Classify objects in a single pass
        var missingObjects = new ArrayList<String>();
        var ownedObjects = new ArrayList<String>();
        var processObjects = new ArrayList<String>();

        ownershipMap.forEach((object, isOwned) -> {
            var qualifiedObject = tx.render(quotedName(schema, object));

            if (isOwned == null) {
                missingObjects.add(qualifiedObject);
            } else if (isOwned) {
                ownedObjects.add(qualifiedObject);
            } else {
                processObjects.add(object);
            }
        });

        if (!missingObjects.isEmpty()) {
            status.setPhase(CRPhase.ERROR)
                    .setMessage("Did not grant or revoke any privileges as the listed %s objects do not exist [resource=%s/%s]%n%s".formatted(
                            objectType,
                            getResourceNamespaceOrOwn(resource, namespace),
                            name,
                            String.join("\n  • ", missingObjects)
                    ));

            return UpdateControl.patchStatus(resource)
                    .rescheduleAfter(60, TimeUnit.SECONDS);
        }

        // 1. Reconcile objects explicitly listed in the Spec (processObjects).
        // We know these are NOT owned (filtered above) and ARE in the spec.
        for (var object : processObjects) {
            var currentPrivileges = currentObjectPrivileges.getOrDefault(object, Set.of());

            // Calculate Revokes: Current - Expected
            var privilegesToRevoke = new HashSet<>(currentPrivileges);
            privilegesToRevoke.removeAll(expectedPrivileges);

            if (!privilegesToRevoke.isEmpty()) {
                grantService.revoke(
                        tx,
                        spec,
                        object,
                        privilegesToRevoke
                );
            }

            // If we are not in the "ALL" mode, e.g. objects is an empty List, do explicit grants
            // We need to exclude objectType's DATABASE and SCHEMA as the CRD doesn't allow to specify objects there
            if (!isAllMode
                    || objectType == GrantObjectType.DATABASE
                    || objectType == GrantObjectType.SCHEMA
            ) {
                // Calculate Grants: Expected - Current
                var privilegesToGrant = new HashSet<>(expectedPrivileges);
                privilegesToGrant.removeAll(currentPrivileges);

                if (!privilegesToGrant.isEmpty()) {
                    grantService.grant(
                            tx,
                            spec,
                            object,
                            privilegesToGrant
                    );
                }
            }
        }

        // 2. Bulk grant ("ALL" mode only)
        if (isAllMode) {
            grantService.grantOnAll(
                    tx,
                    spec,
                    expectedPrivileges
            );
        }

        // 2. Revoke orphaned object privileges (Objects with privileges but not in Spec)
        // We iterate current privileges and skip those we just processed.
        // Any object currently having privileges but not listed in 'expectedObjects' is an orphan.
        // Objects in 'expectedObjects' were either processed in Step 1 or skipped as 'owned'.
        for (var entry : currentObjectPrivileges.entrySet()) {
            var object = entry.getKey();

            // If the role owns the object, we can skip it:
            // - In "Explicit" mode: ownershipMap contains exactly the spec objects.
            // - In "ALL" mode: ownershipMap contains ALL objects.
            // Therefore, if it's in ownershipMap, it is NOT an orphan so we should not revoke anything
            if (ownershipMap.containsKey(object)) {
                continue;
            }

            var privilegesToRevoke = entry.getValue();
            if (!privilegesToRevoke.isEmpty()) {
                grantService.revoke(
                        tx,
                        spec,
                        object,
                        privilegesToRevoke
                );
            }
        }

        String message = null;
        if (!ownedObjects.isEmpty()) {
            message = "The role is the owner of the listed %s objects and thus we did not need to grant or revoke any privileges from them. [resource=%s/%s]%n%s".formatted(
                    objectType,
                    getResourceNamespaceOrOwn(resource, namespace),
                    name,
                    String.join("\n  • ", ownedObjects)
            );
        }

        status.setPhase(CRPhase.READY)
                .setMessage(message);

        return UpdateControl.patchStatus(resource);
    }
}
