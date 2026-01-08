package it.aboutbits.postgresql.crd.role;

import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.config.informer.InformerEventSourceConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.SecondaryToPrimaryMapper;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;
import it.aboutbits.postgresql.core.BaseReconciler;
import it.aboutbits.postgresql.core.CRPhase;
import it.aboutbits.postgresql.core.CRStatus;
import it.aboutbits.postgresql.core.KubernetesUtil;
import it.aboutbits.postgresql.core.PostgreSQLAuthenticationUtil;
import it.aboutbits.postgresql.core.PostgreSQLContextFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;

import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
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

        var name = resource.getMetadata().getName();
        var namespace = resource.getMetadata().getNamespace();

        log.info(
                "Reconciling Role [resource={}/{}, status.phase={}]",
                namespace,
                name,
                status.getPhase()
        );

        var clusterRef = spec.getClusterRef();
        var expectedFlags = spec.getFlags();

        var clusterConnectionOptional = getReferencedClusterConnection(
                kubernetesClient,
                resource,
                clusterRef
        );

        if (clusterConnectionOptional.isEmpty()) {
            status.setPhase(CRPhase.PENDING)
                    .setMessage("The specified ClusterConnection does not exist or is not ready yet [clusterRef=%s/%s]".formatted(
                            getResourceNamespaceOrOwn(resource, clusterRef.getNamespace()),
                            clusterRef.getName()
                    ));

            return UpdateControl.patchStatus(resource)
                    .rescheduleAfter(60, TimeUnit.SECONDS);
        }

        var clusterConnection = clusterConnectionOptional.get();

        // We need to case-insensitive sort the roles, as PostgreSQL will lowercase anything without quotes
        expectedFlags.getRole().sort(String.CASE_INSENSITIVE_ORDER);
        expectedFlags.getInRole().sort(String.CASE_INSENSITIVE_ORDER);

        var passwordSecretRef = spec.getPasswordSecretRef();
        var loginExpected = passwordSecretRef != null;

        String password;
        if (loginExpected) {
            password = KubernetesUtil.getSecretRefCredentials(
                    kubernetesClient,
                    passwordSecretRef,
                    namespace
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

                // Create and return the role if it doesn't exist yet
                if (!RoleUtil.roleExists(tx, spec)) {
                    log.info(
                            "Creating Role [resource={}/{}]",
                            namespace,
                            name
                    );

                    RoleUtil.createRole(
                            tx,
                            spec,
                            password
                    );

                    status.setPhase(CRPhase.READY)
                            .setMessage(null);

                    return UpdateControl.patchStatus(resource);
                }

                // When there is NOLOGIN, we set no password
                var passwordMatches = true;
                var roleLoginMatches = RoleUtil.roleLoginMatches(tx, spec);
                var currentFlags = RoleUtil.fetchCurrentFlags(tx, spec);
                var flagsMatch = expectedFlags.equals(currentFlags);
                var commentMatches = RoleUtil.roleCommentMatches(tx, spec);

                if (loginExpected) {
                    passwordMatches = PostgreSQLAuthenticationUtil.passwordMatches(
                            tx,
                            spec,
                            password
                    );
                }

                if (roleLoginMatches && passwordMatches && flagsMatch && commentMatches) {
                    log.info(
                            "Role up-to-date [resource={}/{}]",
                            namespace,
                            name
                    );

                    return UpdateControl.noUpdate();
                }

                var changePassword = loginExpected && !passwordMatches;

                log.info(
                        "Updating Role [resource={}/{}]",
                        namespace,
                        name
                );

                if (!roleLoginMatches || !passwordMatches || !flagsMatch) {
                    RoleUtil.alterRole(
                            tx,
                            spec,
                            changePassword,
                            password
                    );
                }

                if (!flagsMatch) {
                    log.info(
                            "Updating Role membership [resource={}/{}]",
                            namespace,
                            name
                    );

                    RoleUtil.reconcileRoleMembership(
                            tx,
                            spec,
                            expectedFlags,
                            currentFlags
                    );
                }

                if (!commentMatches) {
                    RoleUtil.updateComment(
                            tx,
                            spec
                    );
                }

                status.setPhase(CRPhase.READY)
                        .setMessage(null);

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

    /**
     * Watches for {@code Secret} changes to trigger reconciliation for dependent {@code Role} resources.
     */
    @Override
    public List<EventSource<?, Role>> prepareEventSources(EventSourceContext<Role> context) {
        // 1. Define the Mapper
        // We define how to find the Primary Resource (Role) when a Secret changes
        SecondaryToPrimaryMapper<Secret> secretToRoleMapper = (Secret secret) -> {
            // 2. Filter Roles that reference this specific Secret
            return context.getPrimaryCache()
                    .list()
                    .filter(role -> isReferencedBy(role, secret))
                    .map(ResourceID::fromResource)
                    .collect(Collectors.toSet());
        };

        // 2. Build the Event Source Configuration which binds the InformerConfig + Mapper
        var eventSourceConfig = InformerEventSourceConfiguration.from(Secret.class, Role.class)
                .withSecondaryToPrimaryMapper(secretToRoleMapper)
                // or .withWatchAllNamespaces() if we want to have the secret in another namespace than the Role CR instance
                .withNamespacesInheritedFromController()
                .build();

        // 3. Create the Event Source
        // This will watch for Secret changes and run the mapper
        var secretEventSource = new InformerEventSource<>(
                eventSourceConfig,
                context
        );

        return List.of(secretEventSource);
    }

    @Override
    protected @NonNull CRStatus newStatus() {
        return new CRStatus();
    }

    /**
     * Checks if the given Role's spec.passwordSecretRef points to the changed Secret.
     */
    private boolean isReferencedBy(
            Role role,
            Secret secret
    ) {
        var spec = role.getSpec();

        if (spec.getPasswordSecretRef() == null) {
            return false;
        }

        var ref = spec.getPasswordSecretRef();
        var refName = ref.getName();
        var refNamespace = getResourceNamespaceOrOwn(role, ref.getNamespace());

        return refName.equals(secret.getMetadata().getName()) &&
                refNamespace.equals(secret.getMetadata().getNamespace());
    }
}
