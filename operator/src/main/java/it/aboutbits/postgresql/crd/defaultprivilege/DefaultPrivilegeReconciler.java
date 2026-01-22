package it.aboutbits.postgresql.crd.defaultprivilege;

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

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@NullMarked
@Slf4j
@RequiredArgsConstructor
public class DefaultPrivilegeReconciler
        extends BaseReconciler<DefaultPrivilege, CRStatus>
        implements Reconciler<DefaultPrivilege>, Cleaner<DefaultPrivilege> {
    private final DefaultPrivilegeService defaultPrivilegeService;

    private final KubernetesClient kubernetesClient;
    private final PostgreSQLContextFactory contextFactory;

    @Override
    public UpdateControl<DefaultPrivilege> reconcile(
            DefaultPrivilege resource,
            Context<DefaultPrivilege> context
    ) {
        var spec = resource.getSpec();
        var status = initializeStatus(resource);

        var name = resource.getMetadata().getName();
        var namespace = resource.getMetadata().getNamespace();

        log.info(
                "Reconciling DefaultPrivilege [resource={}/{}, status.phase={}]",
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
        var grantPrivileges = Set.copyOf(spec.getPrivileges());
        var allowedPrivilegesForObjectType = objectType.privilegesSet();

        if (!allowedPrivilegesForObjectType.containsAll(grantPrivileges)) {
            var invalid = new HashSet<>(grantPrivileges);

            invalid.removeAll(allowedPrivilegesForObjectType);

            status.setPhase(CRPhase.ERROR)
                    .setMessage("DefaultPrivilege contains invalid privileges for the specified objectType. [resource=%s/%s, objectType=%s, invalidPrivileges=%s, allowedPrivilegesForObjectType=%s]".formatted(
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

        UpdateControl<DefaultPrivilege> updateControl;

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
            DefaultPrivilege resource,
            Context<DefaultPrivilege> context
    ) {
        var spec = resource.getSpec();
        var status = initializeStatus(resource);

        var name = resource.getMetadata().getName();
        var namespace = resource.getMetadata().getNamespace();

        log.info(
                "Deleting DefaultPrivilege [resource={}/{}, status.phase={}]",
                namespace,
                name,
                status.getPhase()
        );

        if (status.getPhase() != CRPhase.DELETING) {
            status.setPhase(CRPhase.DELETING)
                    .setMessage("DefaultPrivilege deletion in progress");

            context.getClient().resource(resource).patchStatus();
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

            context.getClient().resource(resource).patchStatus();

            return DeleteControl.noFinalizerRemoval()
                    .rescheduleAfter(60, TimeUnit.SECONDS);
        }

        var clusterConnection = clusterConnectionOptional.get();

        try (var dsl = contextFactory.getDSLContext(clusterConnection)) {
            dsl.transaction(cfg -> {
                var tx = cfg.dsl();

                var currentDefaultPrivileges = defaultPrivilegeService.determineCurrentDefaultPrivileges(tx, spec);

                defaultPrivilegeService.revoke(
                        tx,
                        spec,
                        currentDefaultPrivileges
                );
            });

            return DeleteControl.defaultDelete();
        } catch (Exception e) {
            log.error(
                    "Failed to delete DefaultPrivilege [resource={}/{}, status.phase={}]",
                    namespace,
                    name,
                    status.getPhase()
            );

            status.setMessage("Deletion failed: %s".formatted(e.getMessage()));

            context.getClient().resource(resource).patchStatus();

            return DeleteControl.noFinalizerRemoval()
                    .rescheduleAfter(60, TimeUnit.SECONDS);
        }
    }

    @Override
    protected CRStatus newStatus() {
        return new CRStatus();
    }

    @SuppressWarnings("java:S3776")
    private UpdateControl<DefaultPrivilege> reconcileInTransaction(
            DSLContext tx,
            DefaultPrivilege resource,
            CRStatus status
    ) {
        var spec = resource.getSpec();

        var expectedPrivileges = Set.copyOf(spec.getPrivileges());
        var currentDefaultPrivileges = defaultPrivilegeService.determineCurrentDefaultPrivileges(tx, spec);

        // Calculate Revokes: Current - Expected
        var privilegesToRevoke = new HashSet<>(currentDefaultPrivileges);
        privilegesToRevoke.removeAll(expectedPrivileges);

        if (!privilegesToRevoke.isEmpty()) {
            defaultPrivilegeService.revoke(
                    tx,
                    spec,
                    privilegesToRevoke
            );
        }

        // Calculate Grants: Expected - Current
        var privilegesToGrant = new HashSet<>(expectedPrivileges);
        privilegesToGrant.removeAll(currentDefaultPrivileges);

        if (!privilegesToGrant.isEmpty()) {
            defaultPrivilegeService.grant(
                    tx,
                    spec,
                    privilegesToGrant
            );
        }

        status.setPhase(CRPhase.READY)
                .setMessage(null);

        return UpdateControl.patchStatus(resource);
    }
}
