package it.aboutbits.postgresql.crd.role;

import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.config.informer.InformerEventSourceConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Cleaner;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.DeleteControl;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.SecondaryToPrimaryMapper;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;
import io.quarkiverse.operatorsdk.annotations.AdditionalRBACRules;
import io.quarkiverse.operatorsdk.annotations.RBACRule;
import it.aboutbits.postgresql.core.BaseReconciler;
import it.aboutbits.postgresql.core.CRPhase;
import it.aboutbits.postgresql.core.KubernetesService;
import it.aboutbits.postgresql.core.PasswordFingerprintService;
import it.aboutbits.postgresql.core.PostgreSQLAuthenticationService;
import it.aboutbits.postgresql.core.PostgreSQLContextFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@AdditionalRBACRules({
        @RBACRule(
                apiGroups = {""},
                resources = {"secrets"},
                // "create" is needed once for the password fingerprint key Secret in the operator namespace
                verbs = {"get", "list", "watch", "create"}
        )
})
@RequiredArgsConstructor
@NullMarked
public class RoleReconciler
        extends BaseReconciler<Role, RoleStatus>
        implements Reconciler<Role>, Cleaner<Role> {
    private final RoleService roleService;
    private final KubernetesService kubernetesService;
    private final PostgreSQLAuthenticationService postgreSQLAuthenticationService;
    private final PasswordFingerprintService passwordFingerprintService;

    private final KubernetesClient kubernetesClient;
    private final PostgreSQLContextFactory contextFactory;

    @Override
    public UpdateControl<Role> reconcile(
            Role resource,
            Context<Role> context
    ) {
        var spec = resource.getSpec();
        var status = initializeStatus(resource);

        var namespace = resource.getMetadata().getNamespace();
        var name = resource.getMetadata().getName();

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
                    .setMessage("The specified ClusterConnection does not exist or is not ready yet [resource=%s/%s]".formatted(
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

        String password;
        if (passwordSecretRef != null) {
            password = kubernetesService.getSecretRefCredentials(
                    kubernetesClient,
                    passwordSecretRef,
                    namespace
            ).password();
        } else {
            password = null;
        }

        UpdateControl<Role> updateControl;

        try (var dsl = contextFactory.getDSLContext(clusterConnection)) {
            var passwordEncryption = spec.getPasswordEncryption();

            // The password hash in pg_authid is readable by superusers only.
            // Managed PostgreSQL services of cloud providers do not grant that,
            // so the operator tracks a keyed fingerprint of the applied password in the status.
            var expectedFingerprint = password != null
                    ? passwordFingerprintService.fingerprint(password, passwordEncryption)
                    : null;
            var serverPassword = password != null
                    ? postgreSQLAuthenticationService.toServerPassword(password, passwordEncryption)
                    : null;

            // Run everything in a single transaction
            updateControl = dsl.transactionResult(
                    cfg -> reconcileInTransaction(
                            cfg.dsl(),
                            resource,
                            status,
                            expectedFingerprint,
                            serverPassword
                    )
            );

            // Record the fingerprint only after the commit. A failed commit must not leave
            // the fingerprint of a password that PostgreSQL never stored.
            status.setPasswordFingerprint(expectedFingerprint);
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
            Role resource,
            Context<Role> context
    ) {
        var spec = resource.getSpec();
        var status = initializeStatus(resource);

        var namespace = resource.getMetadata().getNamespace();
        var name = resource.getMetadata().getName();

        log.info(
                "Deleting Role [resource={}/{}, spec.name={}, status.phase={}]",
                namespace,
                name,
                spec.getName(),
                status.getPhase()
        );

        if (status.getPhase() != CRPhase.DELETING) {
            status.setPhase(CRPhase.DELETING)
                    .setMessage("Role deletion in progress");

            context.getClient().resource(resource).patchStatus();

            return DeleteControl.noFinalizerRemoval()
                    .rescheduleAfter(100, TimeUnit.MILLISECONDS);
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
            roleService.dropRole(dsl, spec);

            return DeleteControl.defaultDelete();
        } catch (Exception e) {
            log.error(
                    "Failed to delete Role [resource=%s/%s, spec.name=%s, status.phase=%s]".formatted(
                            namespace,
                            name,
                            spec.getName(),
                            status.getPhase()
                    ),
                    e
            );

            status.setMessage("Deletion failed: %s".formatted(e.getMessage()));

            context.getClient().resource(resource).patchStatus();

            return DeleteControl.noFinalizerRemoval()
                    .rescheduleAfter(60, TimeUnit.SECONDS);
        }
    }

    /**
     * Watches for {@code Secret} changes to trigger reconciliation for dependent {@code Role} resources.
     */
    @Override
    public List<EventSource<?, Role>> prepareEventSources(EventSourceContext<Role> context) {
        // 1. Define the Mapper
        // We define how to find the Primary Resource (Role) when a Secret changes
        // Filter Roles that reference this specific Secret
        SecondaryToPrimaryMapper<Secret> secretToRoleMapper = (Secret secret) -> context.getPrimaryCache()
                .list()
                .filter(role -> isReferencedBy(role, secret))
                .map(ResourceID::fromResource)
                .collect(Collectors.toSet());

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
    protected RoleStatus newStatus() {
        return new RoleStatus();
    }

    /// @param expectedFingerprint the fingerprint of the Secret password, or `null` for a `NOLOGIN` role;
    ///                            compared against the fingerprint in the status, and never written here
    /// @param serverPassword      the password literal to send to PostgreSQL, or `null` for a `NOLOGIN` role
    private UpdateControl<Role> reconcileInTransaction(
            DSLContext tx,
            Role resource,
            RoleStatus status,
            @Nullable String expectedFingerprint,
            @Nullable String serverPassword
    ) {
        var namespace = resource.getMetadata().getNamespace();
        var name = resource.getMetadata().getName();

        var spec = resource.getSpec();
        var expectedFlags = spec.getFlags();

        var loginExpected = serverPassword != null;

        // Create and return the role if it doesn't exist yet
        if (!roleService.roleExists(tx, spec)) {
            log.info(
                    "Creating Role [resource={}/{}]",
                    namespace,
                    name
            );

            roleService.createRole(
                    tx,
                    spec,
                    serverPassword
            );

            status.setPhase(CRPhase.READY)
                    .setMessage(null);

            return UpdateControl.patchStatus(resource);
        }

        var currentCanLogin = roleService.roleCanLogin(tx, spec);
        var roleLoginMatches = loginExpected == currentCanLogin;
        var currentFlags = roleService.fetchCurrentFlags(tx, spec);
        var flagsMatch = expectedFlags.equals(currentFlags);
        var commentMatches = roleService.roleCommentMatches(tx, spec);
        var passwordMatches = Objects.equals(expectedFingerprint, status.getPasswordFingerprint());

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

        if (!roleLoginMatches || changePassword || !flagsMatch) {
            roleService.alterRole(
                    tx,
                    spec,
                    currentFlags,
                    currentCanLogin,
                    changePassword,
                    serverPassword
            );
        }

        if (!flagsMatch) {
            log.info(
                    "Updating Role membership [resource={}/{}]",
                    namespace,
                    name
            );

            roleService.reconcileRoleMembership(
                    tx,
                    spec,
                    expectedFlags,
                    currentFlags
            );
        }

        if (!commentMatches) {
            roleService.updateComment(
                    tx,
                    spec
            );
        }

        status.setPhase(CRPhase.READY)
                .setMessage(null);

        return UpdateControl.patchStatus(resource);
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

        var ref = Objects.requireNonNull(spec.getPasswordSecretRef());
        var refName = ref.getName();
        var refNamespace = getResourceNamespaceOrOwn(role, ref.getNamespace());

        return refName.equals(secret.getMetadata().getName())
                && refNamespace.equals(secret.getMetadata().getNamespace());
    }
}
