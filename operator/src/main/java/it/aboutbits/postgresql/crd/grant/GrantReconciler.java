package it.aboutbits.postgresql.crd.grant;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import it.aboutbits.postgresql.core.BaseReconciler;
import it.aboutbits.postgresql.core.CRPhase;
import it.aboutbits.postgresql.core.CRStatus;
import it.aboutbits.postgresql.core.PostgreSQLContextFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.Name;
import org.jooq.Privilege;
import org.jooq.Record3;
import org.jooq.Select;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.jooq.impl.DSL.grant;
import static org.jooq.impl.DSL.privilege;
import static org.jooq.impl.DSL.quotedName;
import static org.jooq.impl.DSL.role;
import static org.jooq.impl.DSL.select;
import static org.jooq.impl.DSL.val;

@NullMarked
@Slf4j
@RequiredArgsConstructor
public class GrantReconciler
        extends BaseReconciler<Grant, CRStatus>
        implements Reconciler<Grant> {
    private static final String OBJECT_FIELD_NAME = "object";
    private static final String PRIVILEGE_FIELD_NAME = "privilege";
    private static final String GRANTED_FIELD_NAME = "granted";

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
                    .setMessage("Grant contains invalid privileges for the specified object type [resource=%s/%s, objectType=%s, invalidPrivileges=%s, allowedPrivilegesForObjectType=%s]".formatted(
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

    private UpdateControl<Grant> reconcileInTransaction(
            DSLContext tx,
            Grant resource,
            CRStatus status
    ) {
        var name = resource.getMetadata().getName();
        var namespace = resource.getMetadata().getNamespace();

        var spec = resource.getSpec();

        var role = spec.getRole();
        var schema = spec.getSchema();
        var objectType = spec.getObjectType();
        var objects = spec.getObjects();
        var grantPrivileges = spec.getPrivileges();

        var checkPrivilegeFunction = objectType.checkPrivilegeFunction();

        var checks = new ArrayList<Select<Record3<String, String, Boolean>>>(
                objects.size() * grantPrivileges.size()
        );

        for (var object : objects) {
            var qualifiedObject = quotedName(schema, object);
            var renderedObject = tx.render(qualifiedObject);

            for (var grantPrivilege : grantPrivileges) {
                var privilege = grantPrivilege.name().toLowerCase(Locale.ROOT);

                var hasPrivilegeFunctionCall = checkPrivilegeFunction.apply(
                        role,
                        renderedObject,
                        privilege
                );

                // Select the object name and privilege string alongside the result
                // so we can map the answer back to the correct key.
                checks.add(
                        select(
                                val(object).as(OBJECT_FIELD_NAME),
                                val(privilege).as(PRIVILEGE_FIELD_NAME),
                                hasPrivilegeFunctionCall.as(GRANTED_FIELD_NAME)
                        )
                );
            }
        }

        // 2. Execute all checks in a single round-trip using UNION ALL
        if (checks.isEmpty()) {
            // TODO nothing to do
        }

        var batchQuery = checks.stream()
                .reduce(Select::unionAll)
                .orElseThrow();

        var objectPrivilegeIsGrantedMap = tx.fetch(batchQuery)
                .stream()
                .collect(Collectors.toUnmodifiableMap(
                        result -> {
                            var objectName = result.get(OBJECT_FIELD_NAME, String.class);
                            var privilegeName = result.get(PRIVILEGE_FIELD_NAME, String.class);

                            return new ObjectPrivilege(
                                    quotedName(schema, objectName),
                                    privilege(privilegeName)
                            );
                        },
                        result -> result.get(GRANTED_FIELD_NAME, Boolean.class)
                ));

        var grants = objectPrivilegeIsGrantedMap.entrySet()
                .stream()
                .map(entry -> {
                    var objectPrivilege = entry.getKey();
                    //var isGranted = entry.getValue();

                    log.info(
                            "Granting privilege [resource={}/{}, role={}, object={}, privilege={}]",
                            namespace,
                            name,
                            role,
                            objectPrivilege.qualifiedObject(),
                            objectPrivilege.privilege()
                    );

                    var privilege = objectPrivilege.privilege();
                    var qualifiedObject = objectPrivilege.qualifiedObject();

                    return grant(privilege)
                            .on(qualifiedObject)
                            .to(role(role));
                })
                .toList();

        tx.batch(grants).execute();

        status.setPhase(CRPhase.READY)
                .setMessage(null);

        return UpdateControl.patchStatus(resource);
    }

    @Override
    protected CRStatus newStatus() {
        return new CRStatus();
    }

    private record ObjectPrivilege(
            Name qualifiedObject,
            Privilege privilege
    ) {
    }
}
