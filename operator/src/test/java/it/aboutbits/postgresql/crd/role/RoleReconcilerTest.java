package it.aboutbits.postgresql.crd.role;

import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.quarkus.test.junit.QuarkusTest;
import it.aboutbits.postgresql._support.PostgreSQLPasswordVerifier;
import it.aboutbits.postgresql._support.testdata.base.TestUtil;
import it.aboutbits.postgresql._support.testdata.persisted.Given;
import it.aboutbits.postgresql.core.CRPhase;
import it.aboutbits.postgresql.core.CRStatus;
import it.aboutbits.postgresql.core.PostgreSQLContextFactory;
import it.aboutbits.postgresql.core.ResourceRef;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static it.aboutbits.postgresql.core.KubernetesService.SECRET_DATA_BASIC_AUTH_PASSWORD_KEY;
import static it.aboutbits.postgresql.core.infrastructure.persistence.Tables.PG_ROLES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.jooq.impl.DSL.inline;
import static org.jooq.impl.DSL.query;
import static org.jooq.impl.DSL.role;
import static org.jooq.impl.DSL.val;

@QuarkusTest
@RequiredArgsConstructor
@NullMarked
class RoleReconcilerTest {
    private final Given given;

    private final RoleService roleService;
    private final PostgreSQLContextFactory postgreSQLContextFactory;

    private final KubernetesClient kubernetesClient;

    @BeforeEach
    void resetEnvironment() {
        TestUtil.resetEnvironment(kubernetesClient);
    }

    @Test
    @DisplayName("When a Role (LOGIN) is created, it should be reconciled to READY and present in pg_roles")
    void createRole_withLogin_andStatusReady() {
        // given
        var clusterConnection = given.one()
                .clusterConnection()
                .withName("test-connection-role-login")
                .returnFirst();

        var now = OffsetDateTime.now(ZoneOffset.UTC);
        var roleName = "test-role-login";

        // when
        var role = given.one()
                .role()
                .withName(roleName)
                .withClusterConnectionName(clusterConnection.getMetadata().getName())
                .withPasswordSecretRef(clusterConnection.getSpec().getAdminSecretRef())
                .returnFirst();

        // then: assert READY
        var expectedStatus = new RoleStatus()
                .setName(roleName)
                .setPhase(CRPhase.READY)
                .setObservedGeneration(1L);

        assertThatRoleHasExpectedStatus(
                role,
                expectedStatus,
                now
        );

        var dsl = postgreSQLContextFactory.getDSLContext(clusterConnection);

        assertThat(roleService.roleExists(dsl, role.getSpec())).isTrue();
        assertThat(roleService.roleLoginMatches(dsl, role.getSpec())).isTrue();
        assertThat(role.getStatus().getPasswordFingerprint()).isNotBlank();
    }

    @Test
    @DisplayName("When a Role (NOLOGIN) is created, it should be reconciled to READY and present with NOLOGIN")
    void createRole_withoutLogin_andStatusReady() {
        // given
        var clusterConnection = given.one()
                .clusterConnection()
                .withName("test-connection-role-nologin")
                .returnFirst();

        var now = OffsetDateTime.now(ZoneOffset.UTC);
        var roleName = "test-role-nologin";

        // when
        var role = given.one()
                .role()
                .withName(roleName)
                .withClusterConnectionName(clusterConnection.getMetadata().getName())
                .returnFirst();

        var expectedStatus = new RoleStatus()
                .setName(roleName)
                .setPhase(CRPhase.READY)
                .setObservedGeneration(1L);

        assertThatRoleHasExpectedStatus(
                role,
                expectedStatus,
                now
        );

        var dsl = postgreSQLContextFactory.getDSLContext(clusterConnection);

        assertThat(roleService.roleExists(dsl, role.getSpec())).isTrue();
        assertThat(roleService.roleLoginMatches(dsl, role.getSpec())).isTrue();
        assertThat(role.getStatus().getPasswordFingerprint()).isNull();
    }

    @Test
    @DisplayName("When a Role login state is changed, it should be updated correctly in pg_roles")
    void toggleRoleLogin_updatesCorrectly() {
        // given
        var clusterConnection = given.one()
                .clusterConnection()
                .withName("test-connection-role-toggle-login")
                .returnFirst();

        var now = OffsetDateTime.now(ZoneOffset.UTC);
        var roleName = "test-role-toggle-login";

        // when
        var role = given.one()
                .role()
                .withName(roleName)
                .withClusterConnectionName(clusterConnection.getMetadata().getName())
                .returnFirst();

        var spec = role.getSpec();

        // then
        assertThatRoleHasExpectedStatus(
                role,
                new RoleStatus()
                        .setName(roleName)
                        .setPhase(CRPhase.READY)
                        .setObservedGeneration(1L),
                now
        );

        var dsl = postgreSQLContextFactory.getDSLContext(clusterConnection);

        assertThat(
                roleService.roleExists(dsl, role.getSpec())
        ).isTrue();

        assertThat(
                getRoleFlagValue(dsl, roleName, PG_ROLES.ROLCANLOGIN)
        ).isFalse();

        // 2. Add a passwordSecretRef to make it a login role
        spec.setPasswordSecretRef(clusterConnection.getSpec().getAdminSecretRef());

        // when
        applyRole(
                role,
                r -> r.getStatus().getObservedGeneration() == 2L
        );

        // then
        assertThat(getRoleFlagValue(dsl, roleName, PG_ROLES.ROLCANLOGIN)).isTrue();

        // 3. Remove passwordSecretRef again
        spec.setPasswordSecretRef(null);

        // when
        applyRole(
                role,
                r -> r.getStatus().getObservedGeneration() == 3L
        );

        // then
        assertThat(getRoleFlagValue(dsl, roleName, PG_ROLES.ROLCANLOGIN)).isFalse();
    }

    @Test
    @DisplayName("When a NOLOGIN Role still holds a password, the next update should clear it")
    void noLoginRole_withLeftoverPassword_clearsPassword() {
        // given: a reconciled NOLOGIN role
        var clusterConnection = given.one()
                .clusterConnection()
                .withName("test-connection-role-leftover-password")
                .returnFirst();

        var roleName = "test-role-leftover-password";

        var role = given.one()
                .role()
                .withName(roleName)
                .withClusterConnectionName(clusterConnection.getMetadata().getName())
                .returnFirst();

        var dsl = postgreSQLContextFactory.getDSLContext(clusterConnection);

        assertThat(getRoleFlagValue(dsl, roleName, PG_ROLES.ROLCANLOGIN)).isFalse();

        // and: somebody sets a password directly in PostgreSQL.
        // The role keeps NOLOGIN, so the login state still matches the spec.
        // `pg_roles` masks `rolpassword`, so the operator cannot see that password.
        dsl.execute(query(
                "alter role {0} with password {1}",
                role(roleName),
                val("leftover-password")
        ));

        assertThat(PostgreSQLPasswordVerifier.storedVerifier(dsl, roleName)).isNotNull();

        // when: an unrelated flag changes, so the operator alters the role
        role.getSpec().getFlags().setCreatedb(true);

        applyRole(role);

        // then: the operator cleared the leftover password
        assertThat(getRoleFlagValue(dsl, roleName, PG_ROLES.ROLCREATEDB)).isTrue();
        assertThat(PostgreSQLPasswordVerifier.storedVerifier(dsl, roleName)).isNull();
    }

    @Test
    @DisplayName("When a Role references a missing ClusterConnection, status should be PENDING with a helpful message")
    void createRole_withMissingClusterConnection_setsPending() {
        // given
        var roleName = "test-role-missing-cc";
        var missingClusterName = "non-existing-cc";

        var now = OffsetDateTime.now(ZoneOffset.UTC);

        var dummySecretRef = new ResourceRef();
        dummySecretRef.setName("dummy");

        // when
        var role = given.one()
                .role()
                .withName(roleName)
                .withClusterConnectionName(missingClusterName)
                .withClusterConnectionNamespace(kubernetesClient.getNamespace())
                .withPasswordSecretRef(dummySecretRef)
                .returnFirst();

        // then
        assertThat(role).isNotNull();
        assertThat(role.getStatus()).isNotNull();

        assertThat(role.getStatus().getPhase()).isEqualTo(CRPhase.PENDING);
        assertThat(role.getStatus().getMessage()).startsWith(
                "The specified ClusterConnection does not exist"
        );
        assertThat(role.getStatus().getLastProbeTime()).isAfter(
                now
        );
        assertThat(role.getStatus().getLastPhaseTransitionTime()).isNull();

        // We have to manually clean up this as the RoleController#cleanup will always fail as the ClusterConnection does not exist
        kubernetesClient.resource(role).delete();
        role.getMetadata().setFinalizers(null);
        role.getMetadata().setResourceVersion(null);
        kubernetesClient.resource(role).patch();
    }

    @Test
    @DisplayName(
            "When a Role (LOGIN) references a secret and that secret changes, it should trigger a re-reconciliation"
    )
    void secretChange_triggersReconciliation() {
        // given
        var clusterConnection = given.one()
                .clusterConnection()
                .withName("test-connection-role-secret-change")
                .returnFirst();

        var roleName = "test-role-secret-change";

        var initialPassword = "initial-password";
        var newPassword = "new-password";

        var secretRef = given.one()
                .secretRef()
                .withPassword(initialPassword)
                .returnFirst();

        var secret = kubernetesClient.secrets()
                .inNamespace(kubernetesClient.getNamespace())
                .withName(secretRef.getName())
                .require();

        // when: create Role
        var role = given.one()
                .role()
                .withName(roleName)
                .withClusterConnectionName(clusterConnection.getMetadata().getName())
                .withPasswordSecretRef(secretRef)
                .returnFirst();

        var dsl = postgreSQLContextFactory.getDSLContext(clusterConnection);

        // then: password should match the initial one
        // Wait for password to match because reconciliation might take a bit
        await().atMost(5, TimeUnit.SECONDS)
                .pollInterval(100, TimeUnit.MILLISECONDS)
                .until(() -> PostgreSQLPasswordVerifier.passwordMatches(
                        dsl,
                        role.getSpec().getName(),
                        initialPassword
                ));

        // when: update secret
        secret.getMetadata().setManagedFields(null);
        secret = new SecretBuilder(secret)
                .addToStringData(SECRET_DATA_BASIC_AUTH_PASSWORD_KEY, newPassword)
                .build();

        kubernetesClient.secrets()
                .inNamespace(kubernetesClient.getNamespace())
                .resource(secret)
                .serverSideApply();

        // then: password should eventually match the new one
        await().atMost(5, TimeUnit.SECONDS)
                .pollInterval(100, TimeUnit.MILLISECONDS)
                .until(() -> PostgreSQLPasswordVerifier.passwordMatches(
                        dsl,
                        role.getSpec().getName(),
                        newPassword
                ));
    }

    @Test
    @DisplayName(
            "When a Role (LOGIN) changes its secret reference, it should trigger a re-reconciliation"
    )
    void secretRefChange_triggersReconciliation() {
        // given
        var clusterConnection = given.one()
                .clusterConnection()
                .withName("test-connection-role-secret-ref-change")
                .returnFirst();

        var roleName = "test-role-secret-ref-change";

        var initialPassword = "initial-password";
        var newPassword = "new-password";

        var initialSecretRef = given.one()
                .secretRef()
                .withPassword(initialPassword)
                .returnFirst();

        var newSecretRef = given.one()
                .secretRef()
                .withPassword(newPassword)
                .returnFirst();

        // when: create Role
        var role = given.one()
                .role()
                .withName(roleName)
                .withClusterConnectionName(clusterConnection.getMetadata().getName())
                .withPasswordSecretRef(initialSecretRef)
                .returnFirst();

        var spec = role.getSpec();

        var dsl = postgreSQLContextFactory.getDSLContext(clusterConnection);

        // then: password should match the initial one
        await().atMost(5, TimeUnit.SECONDS)
                .pollInterval(100, TimeUnit.MILLISECONDS)
                .until(() -> PostgreSQLPasswordVerifier.passwordMatches(
                        dsl,
                        role.getSpec().getName(),
                        initialPassword
                ));

        // when: update secret reference in the Role
        spec.setPasswordSecretRef(newSecretRef);

        var updatedRole = applyRole(role);

        // then: password should eventually match the new one
        await().atMost(5, TimeUnit.SECONDS)
                .pollInterval(100, TimeUnit.MILLISECONDS)
                .until(() -> PostgreSQLPasswordVerifier.passwordMatches(
                        dsl,
                        updatedRole.getSpec().getName(),
                        newPassword
                ));
    }

    @Test
    @DisplayName("When an existing Role has no password fingerprint in its status, the Secret password should be applied once")
    void missingPasswordFingerprint_appliesSecretPasswordOnce() {
        // given: a Role created by the operator
        var clusterConnection = given.one()
                .clusterConnection()
                .withName("test-connection-role-fingerprint-upgrade")
                .returnFirst();

        var roleName = "test-role-fingerprint-upgrade";
        var password = "secret-password";

        var secretRef = given.one()
                .secretRef()
                .withPassword(password)
                .returnFirst();

        var role = given.one()
                .role()
                .withName(roleName)
                .withClusterConnectionName(clusterConnection.getMetadata().getName())
                .withPasswordSecretRef(secretRef)
                .returnFirst();

        var dsl = postgreSQLContextFactory.getDSLContext(clusterConnection);

        var initialFingerprint = role.getStatus().getPasswordFingerprint();
        assertThat(initialFingerprint).isNotBlank();
        assertThat(PostgreSQLPasswordVerifier.passwordMatches(
                dsl,
                roleName,
                password
        )).isTrue();

        // given: the password in PostgreSQL differs from the Secret, and the status has no fingerprint,
        // like a Role that was reconciled by a version of the operator without fingerprints
        dsl.execute("alter role {0} with password {1}", role(roleName), inline("out-of-band-password"));
        assertThat(PostgreSQLPasswordVerifier.passwordMatches(
                dsl,
                roleName,
                password
        )).isFalse();

        kubernetesClient.resources(Role.class)
                .inNamespace(role.getMetadata().getNamespace())
                .withName(role.getMetadata().getName())
                .editStatus(current -> {
                    current.getStatus().setPasswordFingerprint(null);
                    return current;
                });

        // when: a spec change triggers the next reconcile
        role.getSpec().setComment("triggers a reconcile");

        var updatedRole = applyRole(role);

        // then: the Secret password is applied once and the fingerprint is set again
        assertThat(updatedRole.getStatus().getPhase()).isEqualTo(CRPhase.READY);
        assertThat(updatedRole.getStatus().getPasswordFingerprint()).isEqualTo(initialFingerprint);
        assertThat(PostgreSQLPasswordVerifier.passwordMatches(
                dsl,
                roleName,
                password
        )).isTrue();

        // when: the next reconcile finds a matching fingerprint and leaves the password alone
        var verifierAfterUpgrade = PostgreSQLPasswordVerifier.storedVerifier(dsl, roleName);

        updatedRole.getSpec().setComment("triggers another reconcile");

        updatedRole = applyRole(updatedRole);

        // then
        assertThat(updatedRole.getStatus().getPhase()).isEqualTo(CRPhase.READY);
        assertThat(updatedRole.getStatus().getPasswordFingerprint()).isEqualTo(initialFingerprint);
        assertThat(PostgreSQLPasswordVerifier.storedVerifier(dsl, roleName)).isEqualTo(verifierAfterUpgrade);
    }

    @Test
    @DisplayName("When the Secret already contains a SCRAM-SHA-256 verifier, it should be stored verbatim")
    void preHashedPassword_isStoredVerbatim() {
        // given
        var clusterConnection = given.one()
                .clusterConnection()
                .withName("test-connection-role-prehashed")
                .returnFirst();

        var roleName = "test-role-prehashed";

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
                .withClusterConnectionName(clusterConnection.getMetadata().getName())
                .withPasswordSecretRef(secretRef)
                .returnFirst();

        var dsl = postgreSQLContextFactory.getDSLContext(clusterConnection);

        // then
        assertThat(role.getStatus().getPhase()).isEqualTo(CRPhase.READY);
        assertThat(PostgreSQLPasswordVerifier.storedVerifier(dsl, roleName)).isEqualTo(verifier);
        assertThat(PostgreSQLPasswordVerifier.passwordMatches(
                dsl,
                roleName,
                "abc"
        )).isTrue();
    }

    @Test
    @DisplayName("When passwordEncryption is 'server', the server should hash the password")
    void serverPasswordEncryption_letsTheServerHash() {
        // given
        var clusterConnection = given.one()
                .clusterConnection()
                .withName("test-connection-role-server-encryption")
                .returnFirst();

        var roleName = "test-role-server-encryption";
        var password = "server-side-password";

        var secretRef = given.one()
                .secretRef()
                .withPassword(password)
                .returnFirst();

        // when
        var role = given.one()
                .role()
                .withName(roleName)
                .withClusterConnectionName(clusterConnection.getMetadata().getName())
                .withPasswordSecretRef(secretRef)
                .withPasswordEncryption(PasswordEncryption.SERVER)
                .returnFirst();

        var dsl = postgreSQLContextFactory.getDSLContext(clusterConnection);

        // then
        assertThat(role.getStatus().getPhase()).isEqualTo(CRPhase.READY);
        assertThat(PostgreSQLPasswordVerifier.passwordMatches(
                dsl,
                roleName,
                password
        )).isTrue();

        // when: switch to operator-side SCRAM, the password is re-applied with a fresh verifier
        var previousVerifier = PostgreSQLPasswordVerifier.storedVerifier(dsl, roleName);
        var previousFingerprint = role.getStatus().getPasswordFingerprint();

        role.getSpec().setPasswordEncryption(PasswordEncryption.SCRAM_SHA_256);

        var updatedRole = applyRole(role);

        // then
        assertThat(updatedRole.getStatus().getPhase()).isEqualTo(CRPhase.READY);
        assertThat(updatedRole.getStatus().getPasswordFingerprint()).isNotEqualTo(previousFingerprint);
        assertThat(PostgreSQLPasswordVerifier.storedVerifier(dsl, roleName)).isNotEqualTo(previousVerifier);
        assertThat(PostgreSQLPasswordVerifier.passwordMatches(
                dsl,
                roleName,
                password
        )).isTrue();
    }

    @Test
    @DisplayName("When the comment is changed, it should be updated in the database")
    void comment_updatesCorrectly() {
        // given
        var clusterConnection = given.one()
                .clusterConnection()
                .withName("test-role-comment")
                .returnFirst();

        var dsl = postgreSQLContextFactory.getDSLContext(clusterConnection);

        var roleName = "test-role-comment";

        var role = given.one()
                .role()
                .withName(roleName)
                .withClusterConnectionName(clusterConnection.getMetadata().getName())
                .returnFirst();

        var spec = role.getSpec();

        // 1. Set a comment
        var comment = "This is a test comment";
        spec.setComment(comment);

        // when
        var reconciled = applyRole(role);
        var initialGeneration = reconciled.getStatus().getObservedGeneration();

        // then
        assertThat(
                roleService.fetchCurrentRoleComment(dsl, roleName)
        ).isEqualTo(comment);

        // 2. Change comment
        var newComment = "Updated comment";
        spec.setComment(newComment);

        // when
        applyRole(
                role,
                r -> r.getStatus().getObservedGeneration() == initialGeneration + 1
        );

        // then
        assertThat(
                roleService.fetchCurrentRoleComment(dsl, roleName)
        ).isEqualTo(newComment);

        // 3. Remove comment
        spec.setComment(null);

        // when
        applyRole(
                role,
                r -> r.getStatus().getObservedGeneration() == initialGeneration + 2
        );

        // then
        assertThat(
                roleService.fetchCurrentRoleComment(dsl, roleName)
        ).isNull();
    }

    @ParameterizedTest
    @MethodSource("provideBooleanFlags")
    @DisplayName("When a boolean Role flag is toggled, it should be updated in the database")
    void roleFlag_togglesCorrectly(
            Field<Boolean> field,
            BiConsumer<RoleSpec.Flags, Boolean> setter
    ) {
        // given
        var clusterConnection = given.one()
                .clusterConnection()
                .withName("test-role-flags")
                .returnFirst();

        var dsl = postgreSQLContextFactory.getDSLContext(clusterConnection);

        var roleName = "test-role-" + field.getName();

        var role = given.one()
                .role()
                .withName(roleName)
                .withClusterConnectionName(clusterConnection.getMetadata().getName())
                .returnFirst();

        var spec = role.getSpec();

        // 1. Enable flag (true)
        setter.accept(
                spec.getFlags(),
                true
        );

        // when
        var reconciled = applyRole(role);
        var initialGeneration = reconciled.getStatus().getObservedGeneration();

        // then
        assertThat(
                getRoleFlagValue(
                        dsl,
                        roleName,
                        field
                )
        ).isTrue();

        // 2. Disable flag (false)
        setter.accept(
                spec.getFlags(),
                false
        );

        // when
        applyRole(
                role,
                r -> r.getStatus().getObservedGeneration() == initialGeneration + 1
        );

        // then
        assertThat(
                getRoleFlagValue(
                        dsl,
                        roleName,
                        field
                )
        ).isFalse();
    }

    @Test
    @DisplayName("When the CONNECTION LIMIT is changed, it should be updated in the database")
    void connectionLimit_updatesCorrectly() {
        // given
        var clusterConnection = given.one()
                .clusterConnection()
                .withName("test-role-conn-limit")
                .returnFirst();

        var dsl = postgreSQLContextFactory.getDSLContext(clusterConnection);

        var roleName = "test-role-conn-limit";

        var role = given.one()
                .role()
                .withName(roleName)
                .withClusterConnectionName(clusterConnection.getMetadata().getName())
                .returnFirst();

        var spec = role.getSpec();

        // 1. Set a connection limit
        spec.getFlags().setConnectionLimit(10);

        // when
        var reconciled = applyRole(role);
        var initialGeneration = reconciled.getStatus().getObservedGeneration();

        // then
        assertThat(
                getRoleFlagValue(dsl, roleName, PG_ROLES.ROLCONNLIMIT)
        ).isEqualTo(10);

        // 2. Change connection limit
        spec.getFlags().setConnectionLimit(20);

        // when
        applyRole(
                role,
                r -> r.getStatus().getObservedGeneration() == initialGeneration + 1
        );

        // then
        assertThat(
                getRoleFlagValue(dsl, roleName, PG_ROLES.ROLCONNLIMIT)
        ).isEqualTo(20);

        // 3. Reset connection limit to -1
        spec.getFlags().setConnectionLimit(-1);

        // when
        applyRole(
                role,
                r -> r.getStatus().getObservedGeneration() == initialGeneration + 2
        );

        // then
        assertThat(
                getRoleFlagValue(dsl, roleName, PG_ROLES.ROLCONNLIMIT)
        ).isEqualTo(-1);
    }

    @Test
    @DisplayName("When the VALID UNTIL is changed, it should be updated in the database")
    void validUntil_updatesCorrectly() {
        // given
        var clusterConnection = given.one()
                .clusterConnection()
                .withName("test-role-valid-until")
                .returnFirst();

        var dsl = postgreSQLContextFactory.getDSLContext(clusterConnection);

        var roleName = "test-role-valid-until";

        var role = given.one()
                .role()
                .withName(roleName)
                .withClusterConnectionName(clusterConnection.getMetadata().getName())
                .returnFirst();

        var spec = role.getSpec();

        var expiry = OffsetDateTime.now(ZoneOffset.UTC)
                .plusDays(1)
                .truncatedTo(ChronoUnit.SECONDS);

        // 1. Set a valid until date
        spec.getFlags().setValidUntil(expiry);

        // when
        var reconciled = applyRole(role);
        var initialGeneration = reconciled.getStatus().getObservedGeneration();

        var currentFlags = roleService.fetchCurrentFlags(dsl, spec);

        // then
        assertThat(
                currentFlags.getValidUntil()
        ).isEqualTo(expiry);

        // 2. Change valid until date
        var newExpiry = expiry.plusDays(1);
        spec.getFlags().setValidUntil(newExpiry);

        // when
        applyRole(
                role,
                r -> r.getStatus().getObservedGeneration() == initialGeneration + 1
        );

        currentFlags = roleService.fetchCurrentFlags(dsl, spec);

        // then
        assertThat(
                currentFlags.getValidUntil()
        ).isEqualTo(newExpiry);

        // 3. Reset valid until to null (infinity)
        spec.getFlags().setValidUntil(null);

        // when
        applyRole(
                role,
                r -> r.getStatus().getObservedGeneration() == initialGeneration + 2
        );

        currentFlags = roleService.fetchCurrentFlags(dsl, spec);

        // then
        assertThat(
                currentFlags.getValidUntil()
        ).isNull();
    }

    @Test
    @DisplayName("When IN ROLE membership is changed, it should be updated in the database")
    void inRole_updatesCorrectly() {
        // given
        var clusterConnection = given.one()
                .clusterConnection()
                .withName("test-role-in-role")
                .returnFirst();

        var dsl = postgreSQLContextFactory.getDSLContext(clusterConnection);

        var parentRole1 = "parent_role_1";
        var parentRole2 = "parent_role_2";

        dsl.execute("create role {0}", role(parentRole1));
        dsl.execute("create role {0}", role(parentRole2));

        var roleName = "test-role-in-role";

        var role = given.one()
                .role()
                .withName(roleName)
                .withClusterConnectionName(clusterConnection.getMetadata().getName())
                .returnFirst();

        var spec = role.getSpec();

        // 1. Add a parent role
        spec.getFlags().setInRole(
                List.of(parentRole1)
        );

        // when
        var reconciled = applyRole(role);
        var initialGeneration = reconciled.getStatus().getObservedGeneration();

        // then
        assertThat(
                roleService.fetchCurrentFlags(dsl, spec).getInRole()
        ).containsExactly(parentRole1);

        // 2. Add another parent role and remove the first one
        spec.getFlags().setInRole(
                List.of(parentRole2)
        );

        // when
        applyRole(
                role,
                r -> r.getStatus().getObservedGeneration() == initialGeneration + 1
        );

        // then
        assertThat(
                roleService.fetchCurrentFlags(dsl, spec).getInRole()
        ).containsExactly(parentRole2);

        // 3. Remove all parent roles
        spec.getFlags().setInRole(
                List.of()
        );

        // when
        applyRole(
                role,
                r -> r.getStatus().getObservedGeneration() == initialGeneration + 2
        );

        // then
        assertThat(
                roleService.fetchCurrentFlags(dsl, spec).getInRole()
        ).isEmpty();

        // cleanup
        dsl.execute("drop role if exists {0}", role(parentRole1));
        dsl.execute("drop role if exists {0}", role(parentRole2));
    }

    @Test
    @DisplayName("When ROLE membership is changed, it should be updated in the database")
    void role_updatesCorrectly() {
        // given
        var clusterConnection = given.one()
                .clusterConnection()
                .withName("test-role-role")
                .returnFirst();

        var dsl = postgreSQLContextFactory.getDSLContext(clusterConnection);

        var memberRole1 = "member_role_1";
        var memberRole2 = "member_role_2";

        dsl.execute("create role {0}", role(memberRole1));
        dsl.execute("create role {0}", role(memberRole2));

        var roleName = "test-role-role";

        var role = given.one()
                .role()
                .withName(roleName)
                .withClusterConnectionName(clusterConnection.getMetadata().getName())
                .returnFirst();

        var spec = role.getSpec();

        // 1. Add a member role
        spec.getFlags().setRole(
                List.of(memberRole1)
        );

        // when
        var reconciled = applyRole(role);
        var initialGeneration = reconciled.getStatus().getObservedGeneration();

        // then
        assertThat(
                roleService.fetchCurrentFlags(dsl, spec).getRole()
        ).containsExactly(memberRole1);

        // 2. Add another member role and remove the first one
        spec.getFlags().setRole(
                List.of(memberRole2)
        );

        // when
        applyRole(
                role,
                r -> r.getStatus().getObservedGeneration() == initialGeneration + 1
        );

        // then
        assertThat(
                roleService.fetchCurrentFlags(dsl, spec).getRole()
        ).containsExactly(memberRole2);

        // 3. Remove all member roles
        spec.getFlags().setRole(
                List.of()
        );

        // when
        applyRole(
                role,
                r -> r.getStatus().getObservedGeneration() == initialGeneration + 2
        );

        // then
        assertThat(
                roleService.fetchCurrentFlags(dsl, spec).getRole()
        ).isEmpty();

        // cleanup
        dsl.execute("drop role if exists {0}", role(memberRole1));
        dsl.execute("drop role if exists {0}", role(memberRole2));
    }

    @Test
    @DisplayName("When multiple ROLE memberships are added, they should be sorted and updated correctly")
    void role_multipleMemberships_updatesCorrectly() {
        // given
        var clusterConnection = given.one()
                .clusterConnection()
                .withName("test-role-multiple")
                .returnFirst();

        var dsl = postgreSQLContextFactory.getDSLContext(clusterConnection);

        var roleA = "role_a";
        var roleB = "role_b";
        var roleC = "role_c";

        dsl.execute("create role {0}", role(roleA));
        dsl.execute("create role {0}", role(roleB));
        dsl.execute("create role {0}", role(roleC));

        var roleName = "test-role-multiple";

        var role = given.one()
                .role()
                .withName(roleName)
                .withClusterConnectionName(clusterConnection.getMetadata().getName())
                .returnFirst();

        var spec = role.getSpec();

        // Add multiple roles out of order
        spec.getFlags().setInRole(
                List.of(roleC, roleA, roleB)
        );

        // when
        applyRole(role);

        // then
        assertThat(
                roleService.fetchCurrentFlags(dsl, spec).getInRole()
        ).containsExactly(roleA, roleB, roleC);

        // cleanup
        dsl.execute("drop role if exists {0}", role(roleA));
        dsl.execute("drop role if exists {0}", role(roleB));
        dsl.execute("drop role if exists {0}", role(roleC));
    }

    @Test
    @DisplayName("When a Role is deleted, it should be dropped from the database")
    void deleteRole_removesFromDatabase() {
        // given
        var clusterConnection = given.one()
                .clusterConnection()
                .withName("test-connection-role-delete")
                .returnFirst();

        var roleName = "test-role-delete";

        var role = given.one()
                .role()
                .withName(roleName)
                .withClusterConnectionName(clusterConnection.getMetadata().getName())
                .returnFirst();

        var dsl = postgreSQLContextFactory.getDSLContext(clusterConnection);

        // Verify it exists initially
        assertThat(roleService.roleExists(dsl, role.getSpec())).isTrue();

        // when
        kubernetesClient.resources(Role.class)
                .inNamespace(role.getMetadata().getNamespace())
                .withName(role.getMetadata().getName())
                .withTimeout(5, TimeUnit.SECONDS)
                .delete();

        // then
        await().atMost(5, TimeUnit.SECONDS)
                .pollInterval(100, TimeUnit.MILLISECONDS)
                .until(() -> !roleService.roleExists(dsl, role.getSpec()));
    }

    private @Nullable <T> T getRoleFlagValue(
            DSLContext dsl,
            String roleName,
            Field<T> field
    ) {
        return dsl.select(field)
                .from(PG_ROLES)
                .where(PG_ROLES.ROLNAME.eq(roleName))
                .fetchSingle(field);
    }

    private static Stream<Arguments> provideBooleanFlags() {
        return Stream.of(
                Arguments.of(PG_ROLES.ROLSUPER, (BiConsumer<RoleSpec.Flags, Boolean>) RoleSpec.Flags::setSuperuser),
                Arguments.of(PG_ROLES.ROLCREATEDB, (BiConsumer<RoleSpec.Flags, Boolean>) RoleSpec.Flags::setCreatedb),
                Arguments.of(PG_ROLES.ROLCREATEROLE, (BiConsumer<RoleSpec.Flags, Boolean>) RoleSpec.Flags::setCreaterole),
                Arguments.of(PG_ROLES.ROLINHERIT, (BiConsumer<RoleSpec.Flags, Boolean>) RoleSpec.Flags::setInherit),
                Arguments.of(PG_ROLES.ROLREPLICATION, (BiConsumer<RoleSpec.Flags, Boolean>) RoleSpec.Flags::setReplication),
                Arguments.of(PG_ROLES.ROLBYPASSRLS, (BiConsumer<RoleSpec.Flags, Boolean>) RoleSpec.Flags::setBypassrls)
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

    private Role applyRole(
            Role role,
            Predicate<Role> condition
    ) {
        var namespace = kubernetesClient.getNamespace();

        role.getMetadata().setManagedFields(null);
        role.getMetadata().setResourceVersion(null);

        var applied = kubernetesClient.resources(Role.class)
                .inNamespace(namespace)
                .resource(role)
                .serverSideApply();

        return kubernetesClient.resources(Role.class)
                .inNamespace(namespace)
                .withName(applied.getMetadata().getName())
                .waitUntilCondition(
                        condition,
                        5,
                        TimeUnit.SECONDS
                );
    }

    private static void assertThatRoleHasExpectedStatus(
            Role role,
            CRStatus expectedStatus,
            OffsetDateTime now
    ) {
        assertThat(role)
                .isNotNull()
                .extracting(Role::getStatus)
                .satisfies(status -> {
                    assertThat(status.getLastProbeTime()).isAfter(
                            now
                    );
                    assertThat(status.getLastPhaseTransitionTime()).isAfter(
                            now
                    );
                })
                .usingRecursiveComparison()
                .ignoringFields("lastProbeTime", "lastPhaseTransitionTime", "passwordFingerprint")
                .isEqualTo(expectedStatus);
    }
}
