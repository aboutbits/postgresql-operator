package it.aboutbits.postgresql.crd.role;

import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.quarkus.test.junit.QuarkusTest;
import it.aboutbits.postgresql._support.PostgreSQLPasswordVerifier;
import it.aboutbits.postgresql._support.testdata.base.TestUtil;
import it.aboutbits.postgresql._support.testdata.persisted.Given;
import it.aboutbits.postgresql.core.CRPhase;
import it.aboutbits.postgresql.core.PostgreSQLContextFactory;
import it.aboutbits.postgresql.crd.clusterconnection.ClusterConnection;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import static it.aboutbits.postgresql.core.KubernetesService.SECRET_DATA_BASIC_AUTH_PASSWORD_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.jooq.impl.DSL.role;

/// Runs the Role reconciler with an admin role that is not a superuser.
///
/// The admin role has only `LOGIN`, `CREATEDB`, and `CREATEROLE`, like the master user of managed PostgreSQL services
/// of cloud providers, such as AWS RDS, Google Cloud SQL, or Azure Database for PostgreSQL.
/// Such a role cannot read `pg_authid` or `pg_shadow` and cannot name `SUPERUSER`, `REPLICATION`, or `BYPASSRLS` in `ALTER ROLE`.
@QuarkusTest
@RequiredArgsConstructor
@NullMarked
class RoleReconcilerNonSuperuserTest {
    private static final String ADMIN_ROLE = "test_non_superuser_admin";
    private static final String ADMIN_PASSWORD = "test-non-superuser-admin-password";

    private final Given given;

    private final RoleService roleService;
    private final PostgreSQLContextFactory postgreSQLContextFactory;

    private final KubernetesClient kubernetesClient;

    @BeforeEach
    void resetEnvironment() {
        TestUtil.resetEnvironment(kubernetesClient);
    }

    @Test
    @DisplayName("When the admin is not a superuser, a Role (LOGIN) should still be created, updated, and dropped")
    void nonSuperuserAdmin_createsUpdatesAndDropsLoginRole() {
        // given
        var rootConnection = givenRootClusterConnection("test-connection-root-login");
        var rootDsl = postgreSQLContextFactory.getDSLContext(rootConnection);
        var adminConnection = givenNonSuperuserClusterConnection(rootDsl, "test-connection-non-superuser-login");
        var adminDsl = postgreSQLContextFactory.getDSLContext(adminConnection);

        var roleName = "test-non-superuser-role-login";
        var initialPassword = "initial-password";
        var newPassword = "new-password";

        var secretRef = given.one()
                .secretRef()
                .withPassword(initialPassword)
                .returnFirst();

        // when: create
        var role = given.one()
                .role()
                .withName(roleName)
                .withClusterConnectionName(adminConnection.getMetadata().getName())
                .withPasswordSecretRef(secretRef)
                .withComment("created by a non-superuser admin")
                .returnFirst();

        // then: the reads through pg_roles work as non-superuser
        assertThat(role.getStatus().getPhase()).isEqualTo(CRPhase.READY);
        assertThat(role.getStatus().getPasswordFingerprint()).isNotBlank();
        assertThat(roleService.roleExists(adminDsl, role.getSpec())).isTrue();
        assertThat(roleService.roleLoginMatches(adminDsl, role.getSpec())).isTrue();
        assertThat(roleService.fetchCurrentRoleComment(adminDsl, roleName)).isEqualTo("created by a non-superuser admin");
        assertThat(PostgreSQLPasswordVerifier.passwordMatches(
                rootDsl,
                roleName,
                initialPassword
        )).isTrue();

        // when: update flags, comment, and connection limit
        var spec = role.getSpec();
        spec.setComment("updated comment");
        spec.getFlags().setCreatedb(true);
        spec.getFlags().setConnectionLimit(7);

        var updated = applyRole(role);

        // then
        assertThat(updated.getStatus().getPhase()).isEqualTo(CRPhase.READY);

        var currentFlags = roleService.fetchCurrentFlags(adminDsl, spec);
        assertThat(currentFlags.isCreatedb()).isTrue();
        assertThat(currentFlags.getConnectionLimit()).isEqualTo(7);
        assertThat(roleService.fetchCurrentRoleComment(adminDsl, roleName)).isEqualTo("updated comment");

        // when: rotate the password in the Secret
        var secret = kubernetesClient.secrets()
                .inNamespace(kubernetesClient.getNamespace())
                .withName(secretRef.getName())
                .require();
        secret.getMetadata().setManagedFields(null);
        secret = new SecretBuilder(secret)
                .addToStringData(SECRET_DATA_BASIC_AUTH_PASSWORD_KEY, newPassword)
                .build();

        kubernetesClient.secrets()
                .inNamespace(kubernetesClient.getNamespace())
                .resource(secret)
                .serverSideApply();

        // then
        await().atMost(5, TimeUnit.SECONDS)
                .pollInterval(100, TimeUnit.MILLISECONDS)
                .until(() -> PostgreSQLPasswordVerifier.passwordMatches(
                        rootDsl,
                        roleName,
                        newPassword
                ));

        // when: drop
        kubernetesClient.resources(Role.class)
                .inNamespace(role.getMetadata().getNamespace())
                .withName(role.getMetadata().getName())
                .withTimeout(5, TimeUnit.SECONDS)
                .delete();

        // then
        await().atMost(5, TimeUnit.SECONDS)
                .pollInterval(100, TimeUnit.MILLISECONDS)
                .until(() -> !roleService.roleExists(rootDsl, role.getSpec()));
    }

    @Test
    @DisplayName("When the admin is not a superuser, login and membership of an existing Role should be updated")
    void nonSuperuserAdmin_updatesLoginAndMembership() {
        // given
        var rootConnection = givenRootClusterConnection("test-connection-root-membership");
        var rootDsl = postgreSQLContextFactory.getDSLContext(rootConnection);
        var adminConnection = givenNonSuperuserClusterConnection(rootDsl, "test-connection-non-superuser-membership");
        var adminDsl = postgreSQLContextFactory.getDSLContext(adminConnection);

        // The admin creates the parent role, so it holds ADMIN OPTION on it (required since PostgreSQL 16)
        var parentRole = "test_non_superuser_parent";
        adminDsl.execute("drop role if exists {0}", role(parentRole));
        adminDsl.execute("create role {0}", role(parentRole));

        var roleName = "test-non-superuser-role-membership";

        // when: create a NOLOGIN role
        var role = given.one()
                .role()
                .withName(roleName)
                .withClusterConnectionName(adminConnection.getMetadata().getName())
                .returnFirst();

        var spec = role.getSpec();

        // then
        assertThat(role.getStatus().getPhase()).isEqualTo(CRPhase.READY);
        assertThat(roleService.roleCanLogin(adminDsl, spec)).isFalse();

        // when: turn it into a LOGIN role and add it to the parent role
        var password = "member-password";
        spec.setPasswordSecretRef(given.one()
                .secretRef()
                .withPassword(password)
                .returnFirst()
        );
        spec.getFlags().setInRole(List.of(parentRole));

        var updated = applyRole(role);

        // then
        assertThat(updated.getStatus().getPhase()).isEqualTo(CRPhase.READY);
        assertThat(roleService.roleCanLogin(adminDsl, spec)).isTrue();
        assertThat(roleService.fetchCurrentFlags(adminDsl, spec).getInRole()).containsExactly(parentRole);
        assertThat(PostgreSQLPasswordVerifier.passwordMatches(
                rootDsl,
                roleName,
                password
        )).isTrue();

        // when: back to NOLOGIN without membership
        spec.setPasswordSecretRef(null);
        spec.getFlags().setInRole(List.of());

        updated = applyRole(role);

        // then
        assertThat(updated.getStatus().getPhase()).isEqualTo(CRPhase.READY);
        assertThat(updated.getStatus().getPasswordFingerprint()).isNull();
        assertThat(roleService.roleCanLogin(adminDsl, spec)).isFalse();
        assertThat(roleService.fetchCurrentFlags(adminDsl, spec).getInRole()).isEmpty();

        // cleanup
        adminDsl.execute("drop role if exists {0}", role(parentRole));
    }

    @ParameterizedTest(name = "flag {0}")
    @MethodSource("provideSuperuserOnlyFlags")
    @DisplayName("When the admin is not a superuser and the Role asks for a superuser-only flag, the status should be ERROR")
    void nonSuperuserAdmin_superuserOnlyFlag_setsError(
            String flagName,
            BiConsumer<RoleSpec.Flags, Boolean> flagSetter
    ) {
        // given
        var rootConnection = givenRootClusterConnection("test-connection-root-%s-flag".formatted(flagName));
        var rootDsl = postgreSQLContextFactory.getDSLContext(rootConnection);
        var adminConnection = givenNonSuperuserClusterConnection(rootDsl, "test-connection-non-superuser-%s-flag".formatted(flagName));

        var roleName = "test-non-superuser-role-%s-flag".formatted(flagName);

        var flags = new RoleSpec.Flags();
        flagSetter.accept(flags, true);

        // when
        var role = given.one()
                .role()
                .withName(roleName)
                .withClusterConnectionName(adminConnection.getMetadata().getName())
                .withFlags(flags)
                .returnFirst();

        // then
        var failed = kubernetesClient.resources(Role.class)
                .inNamespace(role.getMetadata().getNamespace())
                .withName(role.getMetadata().getName())
                .waitUntilCondition(
                        r -> r.getStatus() != null && r.getStatus().getPhase() == CRPhase.ERROR,
                        5,
                        TimeUnit.SECONDS
                );

        assertThat(failed.getStatus().getMessage())
                .containsAnyOf("permission denied", "must be superuser");
        assertThat(roleService.roleExists(rootDsl, role.getSpec())).isFalse();
    }

    @Test
    @DisplayName("When the admin is not a superuser and passwordEncryption is 'server', the server should hash the password")
    void nonSuperuserAdmin_serverPasswordEncryption_letsTheServerHash() {
        // given
        var rootConnection = givenRootClusterConnection("test-connection-root-server-encryption");
        var rootDsl = postgreSQLContextFactory.getDSLContext(rootConnection);
        var adminConnection = givenNonSuperuserClusterConnection(rootDsl, "test-connection-non-superuser-server-encryption");

        var roleName = "test-non-superuser-role-server-encryption";
        var password = "server-side-password";

        var secretRef = given.one()
                .secretRef()
                .withPassword(password)
                .returnFirst();

        // when
        var role = given.one()
                .role()
                .withName(roleName)
                .withClusterConnectionName(adminConnection.getMetadata().getName())
                .withPasswordSecretRef(secretRef)
                .withPasswordEncryption(PasswordEncryption.SERVER)
                .returnFirst();

        // then
        assertThat(role.getStatus().getPhase()).isEqualTo(CRPhase.READY);
        assertThat(PostgreSQLPasswordVerifier.passwordMatches(
                rootDsl,
                roleName,
                password
        )).isTrue();
    }

    @Test
    @DisplayName("When the admin is not a superuser and the Secret contains a SCRAM-SHA-256 verifier, it should be stored verbatim")
    void nonSuperuserAdmin_preHashedPassword_isStoredVerbatim() {
        // given
        var rootConnection = givenRootClusterConnection("test-connection-root-prehashed");
        var rootDsl = postgreSQLContextFactory.getDSLContext(rootConnection);
        var adminConnection = givenNonSuperuserClusterConnection(rootDsl, "test-connection-non-superuser-prehashed");

        var roleName = "test-non-superuser-role-prehashed";

        // Verifier for the password "abc", generated by PostgreSQL
        var verifier = "SCRAM-SHA-256$4096:gxUQWxfrRYegSTNiHXFT+g==$lxMC2yO9Lx9gm2dgNPo/1Qar+pjAvxCP2VN4yPWYnzE=:0QzcS9VJHJszBq4vSce3n4M6NZmyWa1GWdkJDi8hRNc=";

        var secretRef = given.one()
                .secretRef()
                .withPassword(verifier)
                .returnFirst();

        // when
        var role = given.one()
                .role()
                .withName(roleName)
                .withClusterConnectionName(adminConnection.getMetadata().getName())
                .withPasswordSecretRef(secretRef)
                .returnFirst();

        // then
        assertThat(role.getStatus().getPhase()).isEqualTo(CRPhase.READY);
        assertThat(PostgreSQLPasswordVerifier.storedVerifier(rootDsl, roleName)).isEqualTo(verifier);
        assertThat(PostgreSQLPasswordVerifier.passwordMatches(
                rootDsl,
                roleName,
                "abc"
        )).isTrue();
    }

    private static Stream<Arguments> provideSuperuserOnlyFlags() {
        return Stream.of(
                Arguments.of("superuser", (BiConsumer<RoleSpec.Flags, Boolean>) RoleSpec.Flags::setSuperuser),
                Arguments.of("replication", (BiConsumer<RoleSpec.Flags, Boolean>) RoleSpec.Flags::setReplication),
                Arguments.of("bypassrls", (BiConsumer<RoleSpec.Flags, Boolean>) RoleSpec.Flags::setBypassrls)
        );
    }

    private ClusterConnection givenRootClusterConnection(String name) {
        return given.one()
                .clusterConnection()
                .withName(name)
                .returnFirst();
    }

    /// Creates the non-superuser admin role in PostgreSQL (if missing) and a ClusterConnection that uses it.
    private ClusterConnection givenNonSuperuserClusterConnection(
            DSLContext rootDsl,
            String name
    ) {
        rootDsl.execute(
                """
                do $$
                begin
                    if not exists (select from pg_catalog.pg_roles where rolname = '%s') then
                        create role %s with login nosuperuser createdb createrole noreplication nobypassrls password '%s';
                    end if;
                end
                $$
                """.formatted(ADMIN_ROLE, ADMIN_ROLE, ADMIN_PASSWORD)
        );

        var adminSecretRef = given.one()
                .secretRef()
                .withUsername(ADMIN_ROLE)
                .withPassword(ADMIN_PASSWORD)
                .returnFirst();

        var clusterConnection = given.one()
                .clusterConnection()
                .withName(name)
                .withAdminSecretRef(adminSecretRef)
                .returnFirst();

        return kubernetesClient.resources(ClusterConnection.class)
                .inNamespace(clusterConnection.getMetadata().getNamespace())
                .withName(clusterConnection.getMetadata().getName())
                .waitUntilCondition(
                        c -> c.getStatus() != null && c.getStatus().getPhase() == CRPhase.READY,
                        5,
                        TimeUnit.SECONDS
                );
    }

    private Role applyRole(Role role) {
        var namespace = kubernetesClient.getNamespace();

        role.getMetadata().setManagedFields(null);
        role.getMetadata().setResourceVersion(null);

        var applied = kubernetesClient.resources(Role.class)
                .inNamespace(namespace)
                .resource(role)
                .serverSideApply();

        var generation = applied.getMetadata().getGeneration();

        //noinspection ConstantConditions
        return kubernetesClient.resources(Role.class)
                .inNamespace(namespace)
                .withName(applied.getMetadata().getName())
                .waitUntilCondition(
                        r -> r.getStatus() != null && r.getStatus().getObservedGeneration() >= generation,
                        5,
                        TimeUnit.SECONDS
                );
    }
}
