package it.aboutbits.postgresql.crd.role;

import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.quarkus.test.junit.QuarkusTest;
import it.aboutbits.postgresql._support.testdata.persisted.Given;
import it.aboutbits.postgresql.core.CRPhase;
import it.aboutbits.postgresql.core.CRStatus;
import it.aboutbits.postgresql.core.PostgreSQLAuthenticationService;
import it.aboutbits.postgresql.core.PostgreSQLContextFactory;
import it.aboutbits.postgresql.core.SecretRef;
import it.aboutbits.postgresql.crd.clusterconnection.ClusterConnection;
import it.aboutbits.postgresql.crd.database.Database;
import it.aboutbits.postgresql.crd.grant.Grant;
import it.aboutbits.postgresql.crd.schema.Schema;
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
import static it.aboutbits.postgresql.core.infrastructure.persistence.Tables.PG_AUTHID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.jooq.impl.DSL.role;

@NullMarked
@QuarkusTest
@RequiredArgsConstructor
class RoleReconcilerTest {
    private final Given given;

    private final RoleService roleService;
    private final PostgreSQLContextFactory postgreSQLContextFactory;
    private final PostgreSQLAuthenticationService postgreSQLAuthenticationService;

    private final KubernetesClient kubernetesClient;

    @BeforeEach
    void resetEnvironment() {
        kubernetesClient.resources(Grant.class).delete();

        await().atMost(5, TimeUnit.SECONDS)
                .pollInterval(100, TimeUnit.MILLISECONDS)
                .until(() ->
                        kubernetesClient.resources(Grant.class).list().getItems().isEmpty()
                );

        kubernetesClient.resources(Schema.class).delete();

        await().atMost(5, TimeUnit.SECONDS)
                .pollInterval(100, TimeUnit.MILLISECONDS)
                .until(() ->
                        kubernetesClient.resources(Schema.class).list().getItems().isEmpty()
                );

        kubernetesClient.resources(Role.class).delete();

        await().atMost(5, TimeUnit.SECONDS)
                .pollInterval(100, TimeUnit.MILLISECONDS)
                .until(() ->
                        kubernetesClient.resources(Role.class).list().getItems().isEmpty()
                );

        kubernetesClient.resources(Database.class).delete();

        await().atMost(5, TimeUnit.SECONDS)
                .pollInterval(100, TimeUnit.MILLISECONDS)
                .until(() ->
                        kubernetesClient.resources(Database.class).list().getItems().isEmpty()
                );

        kubernetesClient.resources(ClusterConnection.class).delete();

        await().atMost(5, TimeUnit.SECONDS)
                .pollInterval(100, TimeUnit.MILLISECONDS)
                .until(() ->
                        kubernetesClient.resources(ClusterConnection.class).list().getItems().isEmpty()
                );
    }

    @Test
    @DisplayName("When a Role (LOGIN) is created, it should be reconciled to READY and present in pg_authid")
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
        var expectedStatus = new CRStatus()
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

        var expectedStatus = new CRStatus()
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
    }

    @Test
    @DisplayName("When a Role login state is changed, it should be updated correctly in pg_authid")
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
                new CRStatus()
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
                getRoleFlagValue(dsl, roleName, PG_AUTHID.ROLCANLOGIN)
        ).isFalse();

        // 2. Add a passwordSecretRef to make it a login role
        spec.setPasswordSecretRef(clusterConnection.getSpec().getAdminSecretRef());

        // when
        applyRole(
                role,
                r -> r.getStatus().getObservedGeneration() == 2L
        );

        // then
        assertThat(getRoleFlagValue(dsl, roleName, PG_AUTHID.ROLCANLOGIN)).isTrue();

        // 3. Remove passwordSecretRef again
        spec.setPasswordSecretRef(null);

        // when
        applyRole(
                role,
                r -> r.getStatus().getObservedGeneration() == 3L
        );

        // then
        assertThat(getRoleFlagValue(dsl, roleName, PG_AUTHID.ROLCANLOGIN)).isFalse();
    }

    @Test
    @DisplayName("When a Role references a missing ClusterConnection, status should be PENDING with a helpful message")
    void createRole_withMissingClusterConnection_setsPending() {
        // given
        var roleName = "test-role-missing-cc";
        var missingClusterName = "non-existing-cc";

        var now = OffsetDateTime.now(ZoneOffset.UTC);

        var dummySecretRef = new SecretRef();
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

        // Cleanup manually
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
                .until(() -> postgreSQLAuthenticationService.passwordMatches(
                        dsl,
                        role.getSpec(),
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
                .until(() -> postgreSQLAuthenticationService.passwordMatches(
                        dsl,
                        role.getSpec(),
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
                .until(() -> postgreSQLAuthenticationService.passwordMatches(
                        dsl,
                        role.getSpec(),
                        initialPassword
                ));

        // when: update secret reference in the Role
        spec.setPasswordSecretRef(newSecretRef);

        var updatedRole = applyRole(role);

        // then: password should eventually match the new one
        await().atMost(5, TimeUnit.SECONDS)
                .pollInterval(100, TimeUnit.MILLISECONDS)
                .until(() -> postgreSQLAuthenticationService.passwordMatches(
                        dsl,
                        updatedRole.getSpec(),
                        newPassword
                ));
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
                getRoleFlagValue(dsl, roleName, PG_AUTHID.ROLCONNLIMIT)
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
                getRoleFlagValue(dsl, roleName, PG_AUTHID.ROLCONNLIMIT)
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
                getRoleFlagValue(dsl, roleName, PG_AUTHID.ROLCONNLIMIT)
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
                .from(PG_AUTHID)
                .where(PG_AUTHID.ROLNAME.eq(roleName))
                .fetchSingle(field);
    }

    private static Stream<Arguments> provideBooleanFlags() {
        return Stream.of(
                Arguments.of(PG_AUTHID.ROLSUPER, (BiConsumer<RoleSpec.Flags, Boolean>) RoleSpec.Flags::setSuperuser),
                Arguments.of(PG_AUTHID.ROLCREATEDB, (BiConsumer<RoleSpec.Flags, Boolean>) RoleSpec.Flags::setCreatedb),
                Arguments.of(PG_AUTHID.ROLCREATEROLE, (BiConsumer<RoleSpec.Flags, Boolean>) RoleSpec.Flags::setCreaterole),
                Arguments.of(PG_AUTHID.ROLINHERIT, (BiConsumer<RoleSpec.Flags, Boolean>) RoleSpec.Flags::setInherit),
                Arguments.of(PG_AUTHID.ROLREPLICATION, (BiConsumer<RoleSpec.Flags, Boolean>) RoleSpec.Flags::setReplication),
                Arguments.of(PG_AUTHID.ROLBYPASSRLS, (BiConsumer<RoleSpec.Flags, Boolean>) RoleSpec.Flags::setBypassrls)
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
                .ignoringFields("lastProbeTime", "lastPhaseTransitionTime")
                .isEqualTo(expectedStatus);
    }
}
