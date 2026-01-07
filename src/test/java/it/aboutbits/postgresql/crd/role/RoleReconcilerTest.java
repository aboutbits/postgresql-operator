package it.aboutbits.postgresql.crd.role;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.quarkus.test.junit.QuarkusTest;
import it.aboutbits.postgresql._support.testdata.persisted.Given;
import it.aboutbits.postgresql.core.CRPhase;
import it.aboutbits.postgresql.core.CRStatus;
import it.aboutbits.postgresql.core.ClusterReference;
import it.aboutbits.postgresql.core.PostgreSQLAuthenticationUtil;
import it.aboutbits.postgresql.core.PostgreSQLContextFactory;
import it.aboutbits.postgresql.core.SecretRef;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static it.aboutbits.postgresql.core.KubernetesUtil.SECRET_DATA_BASIC_AUTH_PASSWORD_KEY;
import static it.aboutbits.postgresql.core.infrastructure.persistence.Tables.PG_AUTHID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.awaitility.Awaitility.await;

@QuarkusTest
@RequiredArgsConstructor
class RoleReconcilerTest {
    private final Given given;

    private final PostgreSQLContextFactory postgreSQLContextFactory;
    private final KubernetesClient kubernetesClient;

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
        var role = buildRole(
                roleName,
                clusterConnection.getMetadata().getName(),
                /*login*/ true
        );

        var spec = role.getSpec();

        // when: create Role referencing the ClusterConnection and expecting LOGIN (passwordSecretRef non-null)
        spec.setPasswordSecretRef(clusterConnection.getSpec().getAdminSecretRef());

        // then: wait for status and assert READY
        var reconciled = applyRole(role);

        var expectedStatus = new CRStatus()
                .setName(roleName)
                .setPhase(CRPhase.READY)
                .setObservedGeneration(1L);

        assertThatRoleHasExpectedStatus(
                reconciled,
                expectedStatus,
                now
        );

        DSLContext dsl;
        try {
            dsl = postgreSQLContextFactory.getDSLContext(clusterConnection);
        } catch (Exception e) {
            throw new AssertionError("Failed to obtain DSLContext", e);
        }

        assertThat(RoleUtil.roleExists(dsl, reconciled.getSpec())).isTrue();
        assertThat(RoleUtil.roleLoginMatches(dsl, reconciled.getSpec())).isTrue();
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

        var role = buildRole(
                roleName,
                clusterConnection.getMetadata().getName(),
                /*login*/ false
        );

        // when
        var reconciled = applyRole(role);

        var expectedStatus = new CRStatus()
                .setName(roleName)
                .setPhase(CRPhase.READY)
                .setObservedGeneration(1L);

        assertThatRoleHasExpectedStatus(
                reconciled,
                expectedStatus,
                now
        );

        DSLContext dsl;
        try {
            dsl = postgreSQLContextFactory.getDSLContext(clusterConnection);
        } catch (Exception e) {
            throw new AssertionError("Failed to obtain DSLContext", e);
        }

        assertThat(RoleUtil.roleExists(dsl, reconciled.getSpec())).isTrue();
        assertThat(RoleUtil.roleLoginMatches(dsl, reconciled.getSpec())).isTrue();
    }

    @Test
    @DisplayName("When a Role references a missing ClusterConnection, status should be PENDING with a helpful message")
    void createRole_withMissingClusterConnection_setsPending() {
        // given
        var roleName = "test-role-missing-cc";
        var missingClusterName = "non-existing-cc";

        var now = OffsetDateTime.now(ZoneOffset.UTC);
        var role = buildRole(
                roleName,
                missingClusterName,
                /*login*/ true
        );
        role.getSpec().getClusterRef().setNamespace(kubernetesClient.getNamespace());

        // when
        var reconciled = applyRole(role);

        // then
        assertThat(reconciled).isNotNull();
        assertThat(reconciled.getStatus()).isNotNull();

        assertThat(reconciled.getStatus().getPhase()).isEqualTo(CRPhase.PENDING);
        assertThat(reconciled.getStatus().getMessage()).startsWith(
                "The specified ClusterConnection does not exist"
        );
        assertThat(reconciled.getStatus().getLastProbeTime()).isCloseTo(
                now,
                within(10, ChronoUnit.SECONDS)
        );
        assertThat(reconciled.getStatus().getLastPhaseTransitionTime()).isNull();
    }

    @Test
    @DisplayName(
            "When a Role (LOGIN) references a secret and that secret changes, it should trigger a re-reconciliation"
    )
    void secretChange_triggersReconciliation() throws SQLException {
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

        var role = buildRole(
                roleName,
                clusterConnection.getMetadata().getName(),
                /*login*/ true
        );

        role.getSpec().setPasswordSecretRef(secretRef);

        // when: create Role
        var reconciled = applyRole(role);

        var dsl = postgreSQLContextFactory.getDSLContext(clusterConnection);

        // then: password should match the initial one
        // Wait for password to match because reconciliation might take a bit
        await().atMost(10, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .until(() -> PostgreSQLAuthenticationUtil.passwordMatches(
                        dsl,
                        reconciled.getSpec(),
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
        await().atMost(10, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .until(() -> PostgreSQLAuthenticationUtil.passwordMatches(
                        dsl,
                        reconciled.getSpec(),
                        newPassword
                ));
    }

    @Test
    @DisplayName(
            "When a Role (LOGIN) changes its secret reference, it should trigger a re-reconciliation"
    )
    void secretRefChange_triggersReconciliation() throws SQLException {
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

        var role = buildRole(
                roleName,
                clusterConnection.getMetadata().getName(),
                /*login*/ true
        );

        role.getSpec().setPasswordSecretRef(initialSecretRef);

        // when: create Role
        var reconciled = applyRole(role);

        var dsl = postgreSQLContextFactory.getDSLContext(clusterConnection);

        // then: password should match the initial one
        var finalRole = reconciled;
        await().atMost(10, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .until(() -> PostgreSQLAuthenticationUtil.passwordMatches(
                        dsl,
                        finalRole.getSpec(),
                        initialPassword
                ));

        // when: update secret reference in the Role
        role.getSpec().setPasswordSecretRef(newSecretRef);

        reconciled = applyRole(role);

        // then: password should eventually match the new one
        var updatedRole = reconciled;
        await().atMost(10, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .until(() -> PostgreSQLAuthenticationUtil.passwordMatches(
                        dsl,
                        updatedRole.getSpec(),
                        newPassword
                ));
    }

    @ParameterizedTest
    @MethodSource("provideBooleanFlags")
    @DisplayName("When a boolean Role flag is toggled, it should be updated in the database")
    void roleFlag_togglesCorrectly(
            Field<Boolean> field,
            BiConsumer<RoleSpec.Flags, Boolean> setter
    ) throws SQLException {
        // given
        var clusterConnection = given.one()
                .clusterConnection()
                .withName("test-role-flags")
                .returnFirst();

        var dsl = postgreSQLContextFactory.getDSLContext(clusterConnection);

        var roleName = "test-role-" + field.getName();

        var role = buildRole(
                roleName,
                clusterConnection.getMetadata().getName(),
                /*login*/ false
        );

        // 1. Enable flag (true)
        setter.accept(
                role.getSpec().getFlags(),
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
                role.getSpec().getFlags(),
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
    void connectionLimit_updatesCorrectly() throws SQLException {
        // given
        var clusterConnection = given.one()
                .clusterConnection()
                .withName("test-role-conn-limit")
                .returnFirst();

        var dsl = postgreSQLContextFactory.getDSLContext(clusterConnection);

        var roleName = "test-role-conn-limit";

        var role = buildRole(
                roleName,
                clusterConnection.getMetadata().getName(),
                /*login*/ false
        );

        // 1. Set a connection limit
        role.getSpec().getFlags().setConnectionLimit(10);

        // when
        var reconciled = applyRole(role);
        var initialGeneration = reconciled.getStatus().getObservedGeneration();

        // then
        assertThat(
                getRoleFlagValue(dsl, roleName, PG_AUTHID.ROLCONNLIMIT)
        ).isEqualTo(10);

        // 2. Change connection limit
        role.getSpec().getFlags().setConnectionLimit(20);

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
        role.getSpec().getFlags().setConnectionLimit(-1);

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

    private <T> T getRoleFlagValue(
            DSLContext dsl,
            String roleName,
            Field<T> field
    ) {
        return dsl.select(field)
                .from(PG_AUTHID)
                .where(PG_AUTHID.ROLNAME.eq(roleName))
                .fetchSingleInto(field.getType());
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
        return applyRole(
                role,
                r -> r.getStatus() != null
        );
    }

    private Role applyRole(
            Role role,
            Predicate<Role> condition
    ) {
        var namespace = kubernetesClient.getNamespace();

        kubernetesClient.resources(Role.class)
                .inNamespace(namespace)
                .resource(role)
                .serverSideApply();

        return kubernetesClient.resources(Role.class)
                .inNamespace(namespace)
                .withName(role.getName())
                .waitUntilCondition(
                        condition,
                        10,
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
                    assertThat(status.getLastProbeTime()).isCloseTo(
                            now,
                            within(10, ChronoUnit.SECONDS)
                    );
                    assertThat(status.getLastPhaseTransitionTime()).isCloseTo(
                            now,
                            within(10, ChronoUnit.SECONDS)
                    );
                })
                .usingRecursiveComparison()
                .ignoringFields("lastProbeTime", "lastPhaseTransitionTime")
                .isEqualTo(expectedStatus);
    }

    private static Role buildRole(
            String roleName,
            String clusterConnectionName,
            boolean login
    ) {
        var role = new Role();

        var spec = new RoleSpec();
        spec.setName(roleName);
        spec.setClusterRef(new ClusterReference());
        spec.getClusterRef().setName(clusterConnectionName);

        if (login) {
            // any non-null SecretRef will signal LOGIN expectation
            var secretRef = new SecretRef();
            secretRef.setName("dummy");
            spec.setPasswordSecretRef(secretRef);
        }

        role.setSpec(spec);
        role.setMetadata(new ObjectMeta());
        role.getMetadata().setName(roleName);

        return role;
    }
}
