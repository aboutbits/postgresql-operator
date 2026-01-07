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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;

import static it.aboutbits.postgresql.core.KubernetesUtil.SECRET_DATA_BASIC_AUTH_PASSWORD_KEY;
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

        spec.getClusterRef().setNamespace(kubernetesClient.getNamespace());

        // when: create Role referencing the ClusterConnection and expecting LOGIN (passwordSecretRef non-null)
        role.getSpec().setPasswordSecretRef(clusterConnection.getSpec().getAdminSecretRef());
        role = applyRole(role);

        // then: wait for status and assert READY
        var reconciled = waitForRoleStatus(role.getMetadata().getName());

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

        // when
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        var roleName = "test-role-nologin";
        var role = buildRole(
                roleName,
                clusterConnection.getMetadata().getName(),
                /*login*/ false
        );
        role.getSpec().getClusterRef().setNamespace(kubernetesClient.getNamespace());
        role = applyRole(role);

        var reconciled = waitForRoleStatus(role.getMetadata().getName());

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
        role = applyRole(role);

        // when
        var reconciled = waitForRoleStatus(role.getMetadata().getName());

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
    void secretChange_triggersReconciliation() throws Exception {
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
        role = applyRole(role);
        waitForRoleStatus(role.getMetadata().getName());

        var dsl = postgreSQLContextFactory.getDSLContext(clusterConnection);

        // then: password should match the initial one
        // Wait for password to match because reconciliation might take a bit
        var initialRole = role;
        await().atMost(10, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .until(() -> {
                    try {
                        return PostgreSQLAuthenticationUtil.passwordMatches(
                                dsl,
                                initialRole.getSpec(),
                                initialPassword
                        );
                    } catch (Exception e) {
                        return false;
                    }
                });

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
        var updatedRole = role;
        await().atMost(10, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .until(() -> {
                    try {
                        return PostgreSQLAuthenticationUtil.passwordMatches(
                                dsl,
                                updatedRole.getSpec(),
                                newPassword
                        );
                    } catch (Exception e) {
                        return false;
                    }
                });
    }

    private Role applyRole(Role role) {
        var namespace = kubernetesClient.getNamespace();

        return kubernetesClient.resources(Role.class)
                .inNamespace(namespace)
                .resource(role)
                .serverSideApply();
    }

    private Role waitForRoleStatus(String roleName) {
        var namespace = kubernetesClient.getNamespace();
        return kubernetesClient.resources(Role.class)
                .inNamespace(namespace)
                .withName(roleName)
                .waitUntilCondition(
                        r -> r.getStatus() != null,
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
