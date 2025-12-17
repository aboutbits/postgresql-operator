package it.aboutbits.postgresql.crd.role;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import it.aboutbits.postgresql.core.BaseReconciler;
import it.aboutbits.postgresql.core.CRPhase;
import it.aboutbits.postgresql.core.CRStatus;
import it.aboutbits.postgresql.core.KubernetesUtil;
import it.aboutbits.postgresql.core.PostgreSQLAuthenticationUtil;
import it.aboutbits.postgresql.core.PostgreSQLContextFactory;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;

import java.sql.SQLException;
import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
public class RoleReconciler
        extends BaseReconciler<Role, CRStatus>
        implements Reconciler<Role> {
    private final KubernetesClient kubernetesClient;
    private final PostgreSQLContextFactory contextFactory;

    @Override
    public UpdateControl<Role> reconcile(
            Role resource,
            Context<Role> context
    ) {
        var spec = resource.getSpec();
        var status = initializeStatus(resource);

        var clusterRef = spec.getClusterRef();
        var expectedFlags = spec.getFlags();

        var clusterConnectionOptional = getReferencedClusterConnection(
                kubernetesClient,
                clusterRef
        );

        if (clusterConnectionOptional.isEmpty()) {
            status.setPhase(CRPhase.PENDING)
                    .setMessage("The specified ClusterConnection does not exist or is not ready yet [clusterRef=%s/%s]".formatted(
                            getResourceNamespaceOrOwn(clusterRef.getNamespace()),
                            clusterRef.getName()
                    ));

            return UpdateControl.patchStatus(resource)
                    .rescheduleAfter(60, TimeUnit.SECONDS);
        }

        var clusterConnection = clusterConnectionOptional.get();

        // We need to case-insensitive sort the roles, as PostgreSQL will lowercase anything without quotes
        expectedFlags.getRole().sort(String.CASE_INSENSITIVE_ORDER);
        expectedFlags.getInRole().sort(String.CASE_INSENSITIVE_ORDER);

        var loginExpected = spec.getPasswordSecretRef() != null;

        String password;
        if (loginExpected) {
            password = KubernetesUtil.getSecretRefCredentials(
                    kubernetesClient,
                    clusterConnection
            ).password();
        } else {
            password = null;
        }

        UpdateControl<Role> updateControl;
        try {
            // Run everything in a single transaction
            updateControl = contextFactory.getDSLContext(
                    clusterConnection
            ).transactionResult(cfg -> {
                // Get the transactional DSL context
                var tx = cfg.dsl();

                // Create the role if it doesn't exist yet
                if (!RoleUtil.roleExists(tx, spec)) {
                    RoleUtil.createRole(
                            tx,
                            spec,
                            password
                    );

                    status.setPhase(CRPhase.READY);

                    return UpdateControl.patchStatus(resource);
                }

                // When there is NOLOGIN, we set no password
                var passwordMatches = true;
                var roleLoginMatches = RoleUtil.roleLoginMatches(tx, spec);
                var currentFlags = RoleUtil.fetchCurrentFlags(tx, spec);
                var flagsMatch = expectedFlags.equals(currentFlags);

                if (loginExpected) {
                    passwordMatches = PostgreSQLAuthenticationUtil.passwordMatches(
                            tx,
                            spec,
                            password
                    );
                }

                if (roleLoginMatches && passwordMatches && flagsMatch) {
                    return UpdateControl.noUpdate();
                }

                var changePassword = loginExpected && !passwordMatches;

                RoleUtil.alterRole(
                        tx,
                        spec,
                        changePassword,
                        password
                );

                RoleUtil.reconcileRoleMembership(
                        tx,
                        spec,
                        expectedFlags,
                        currentFlags
                );

                status.setPhase(CRPhase.READY);

                return UpdateControl.patchStatus(resource);
            });
        } catch (SQLException e) {
            return handleError(
                    resource,
                    status,
                    e
            );
        }

        return updateControl;
    }

    @Override
    protected @NonNull CRStatus newStatus() {
        return new CRStatus();
    }
}
