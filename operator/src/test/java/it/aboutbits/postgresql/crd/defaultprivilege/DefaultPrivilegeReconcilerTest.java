package it.aboutbits.postgresql.crd.defaultprivilege;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.quarkus.test.junit.QuarkusTest;
import it.aboutbits.postgresql._support.testdata.base.TestUtil;
import it.aboutbits.postgresql._support.testdata.persisted.Given;
import it.aboutbits.postgresql._support.valuesource.BlankSource;
import it.aboutbits.postgresql.core.CRPhase;
import it.aboutbits.postgresql.core.CRStatus;
import it.aboutbits.postgresql.core.PostgreSQLContextFactory;
import it.aboutbits.postgresql.core.Privilege;
import it.aboutbits.postgresql.crd.clusterconnection.ClusterConnection;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static it.aboutbits.postgresql.core.Privilege.CREATE;
import static it.aboutbits.postgresql.core.Privilege.MAINTAIN;
import static it.aboutbits.postgresql.core.Privilege.SELECT;
import static it.aboutbits.postgresql.core.Privilege.USAGE;
import static it.aboutbits.postgresql.core.ReclaimPolicy.DELETE;
import static it.aboutbits.postgresql.crd.defaultprivilege.DefaultPrivilegeObjectType.SCHEMA;
import static it.aboutbits.postgresql.crd.defaultprivilege.DefaultPrivilegeObjectType.SEQUENCE;
import static it.aboutbits.postgresql.crd.defaultprivilege.DefaultPrivilegeObjectType.TABLE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

@NullMarked
@QuarkusTest
@RequiredArgsConstructor
class DefaultPrivilegeReconcilerTest {
    private final Given given;

    private final DefaultPrivilegeService defaultPrivilegeService;
    private final PostgreSQLContextFactory postgreSQLContextFactory;

    private final KubernetesClient kubernetesClient;

    @BeforeEach
    void resetEnvironment() {
        TestUtil.resetEnvironment(kubernetesClient);
    }

    @Nested
    class CRDValidation {
        @Nested
        class FieldSize {
            @ParameterizedTest
            @BlankSource
            @DisplayName("Should fail when the database is a blank or empty String (CEL rule)")
            void failWhenDatabaseIsBlankOrEmptyString(
                    String blankOrEmptyString
            ) {
                // then
                assertThatExceptionOfType(KubernetesClientException.class).isThrownBy(() ->
                        // given / when
                        given.one()
                                .defaultPrivilege()
                                .withDatabase(blankOrEmptyString)
                                .withObjectType(SCHEMA)
                                .withPrivileges(USAGE)
                                .apply()
                ).withMessageContaining("The DefaultPrivilege database must not be empty.");
            }

            @ParameterizedTest
            @BlankSource
            @DisplayName("Should fail when the role is a blank or empty String (CEL rule)")
            void failWhenRoleIsBlankOrEmptyString(
                    String blankOrEmptyString
            ) {
                // then
                assertThatExceptionOfType(KubernetesClientException.class).isThrownBy(() ->
                        // given / when
                        given.one()
                                .defaultPrivilege()
                                .withRole(blankOrEmptyString)
                                .withObjectType(SCHEMA)
                                .withPrivileges(USAGE)
                                .apply()
                ).withMessageContaining("The DefaultPrivilege role must not be empty.");
            }

            @ParameterizedTest
            @BlankSource
            @DisplayName("Should fail when the owner is a blank or empty String (CEL rule)")
            void failWhenOwnerIsBlankOrEmptyString(
                    String blankOrEmptyString
            ) {
                // then
                assertThatExceptionOfType(KubernetesClientException.class).isThrownBy(() ->
                        // given / when
                        given.one()
                                .defaultPrivilege()
                                .withOwner(blankOrEmptyString)
                                .withObjectType(SCHEMA)
                                .withPrivileges(USAGE)
                                .apply()
                ).withMessageContaining("The DefaultPrivilege owner must not be empty.");
            }

            @ParameterizedTest
            @BlankSource
            @DisplayName("Should fail when the schema is a blank or empty String (CEL rule)")
            void failWhenSchemaIsBlankOrEmptyString(
                    String blankOrEmptyString
            ) {
                // then
                assertThatExceptionOfType(KubernetesClientException.class).isThrownBy(() ->
                        // given / when
                        given.one()
                                .defaultPrivilege()
                                .withSchema(blankOrEmptyString)
                                .withObjectType(SCHEMA)
                                .withPrivileges(USAGE)
                                .apply()
                ).withMessageContaining("The DefaultPrivilege schema must not be empty.");
            }

            @Test
            @DisplayName("Should fail when the privileges are an empty List (CEL rule)")
            void failWhenPrivilegesAreAnEmptyList() {
                // then
                assertThatExceptionOfType(KubernetesClientException.class).isThrownBy(() ->
                        // given / when
                        given.one()
                                .defaultPrivilege()
                                .withObjectType(SCHEMA)
                                .withPrivileges(List.of())
                                .apply()
                ).withMessageContaining("The DefaultPrivilege privileges must not be empty.");
            }
        }

        @Nested
        class ImmutableFields {
            @Test
            @DisplayName("Should fail when the database changes (CEL rule)")
            void failWhenDatabaseChanges() {
                // given
                var item = given.one()
                        .defaultPrivilege()
                        .withObjectType(SCHEMA)
                        .withPrivileges(USAGE)
                        .returnFirst();

                // then
                assertThatExceptionOfType(KubernetesClientException.class).isThrownBy(() -> {
                    // when
                    item.getSpec().setDatabase("new-database");

                    kubernetesClient.resources(DefaultPrivilege.class)
                            .inNamespace(item.getMetadata().getNamespace())
                            .withName(item.getMetadata().getName())
                            .patch(item);
                }).withMessageContaining("The DefaultPrivilege database is immutable.");
            }

            @Test
            @DisplayName("Should fail when the role changes (CEL rule)")
            void failWhenRoleChanges() {
                // given
                var item = given.one()
                        .defaultPrivilege()
                        .withObjectType(SCHEMA)
                        .withPrivileges(USAGE)
                        .returnFirst();

                // then
                assertThatExceptionOfType(KubernetesClientException.class).isThrownBy(() -> {
                    // when
                    item.getSpec().setRole("new-role");

                    kubernetesClient.resources(DefaultPrivilege.class)
                            .inNamespace(item.getMetadata().getNamespace())
                            .withName(item.getMetadata().getName())
                            .patch(item);
                }).withMessageContaining("The DefaultPrivilege role is immutable.");
            }

            @Test
            @DisplayName("Should fail when the owner changes (CEL rule)")
            void failWhenOwnerChanges() {
                // given
                var item = given.one()
                        .defaultPrivilege()
                        .withObjectType(SCHEMA)
                        .withPrivileges(USAGE)
                        .returnFirst();

                // then
                assertThatExceptionOfType(KubernetesClientException.class).isThrownBy(() -> {
                    // when
                    item.getSpec().setOwner("new-owner");

                    kubernetesClient.resources(DefaultPrivilege.class)
                            .inNamespace(item.getMetadata().getNamespace())
                            .withName(item.getMetadata().getName())
                            .patch(item);
                }).withMessageContaining("The DefaultPrivilege owner is immutable.");
            }

            @Test
            @DisplayName("Should fail when the schema changes (CEL rule)")
            void failWhenSchemaChanges() {
                // given
                var item = given.one()
                        .defaultPrivilege()
                        .withObjectType(TABLE)
                        .withPrivileges(USAGE)
                        .returnFirst();

                // then
                assertThatExceptionOfType(KubernetesClientException.class).isThrownBy(() -> {
                    // when
                    item.getSpec().setSchema("new-schema");

                    kubernetesClient.resources(DefaultPrivilege.class)
                            .inNamespace(item.getMetadata().getNamespace())
                            .withName(item.getMetadata().getName())
                            .patch(item);
                }).withMessageContaining("The DefaultPrivilege schema is immutable.");
            }

            @Test
            @DisplayName("Should fail when the objectType changes (CEL rule)")
            void failWhenObjectTypeChanges() {
                // given
                var item = given.one()
                        .defaultPrivilege()
                        .withObjectType(SCHEMA)
                        .withPrivileges(USAGE)
                        .returnFirst();

                // then
                assertThatExceptionOfType(KubernetesClientException.class).isThrownBy(() -> {
                    // when
                    item.getSpec().setObjectType(SEQUENCE);

                    kubernetesClient.resources(DefaultPrivilege.class)
                            .inNamespace(item.getMetadata().getNamespace())
                            .withName(item.getMetadata().getName())
                            .patch(item);
                }).withMessageContaining("The DefaultPrivilege objectType is immutable.");
            }
        }

        @Nested
        class RelatedFields {
            @Test
            @DisplayName("Should fail when objectType is SCHEMA but schema is set (CEL rule)")
            void failWhenDatabaseHasSchema() {
                // then
                assertThatExceptionOfType(KubernetesClientException.class).isThrownBy(() ->
                        // given / when
                        given.one()
                                .defaultPrivilege()
                                .withSchema("some-schema")
                                .withObjectType(SCHEMA)
                                .withPrivileges(USAGE)
                                .returnFirst()
                ).withMessageContaining("The DefaultPrivilege schema must be not set if objectType is 'schema'");
            }

            @Test
            @DisplayName("Should reconcile to ERROR when privileges are invalid for objectType")
            void errorWhenInvalidPrivileges() {
                // given / when
                var defaultPrivilege = given.one()
                        .defaultPrivilege()
                        .withObjectType(SCHEMA)
                        // SELECT is not allowed for SCHEMA
                        .withPrivileges(SELECT)
                        .returnFirst();

                // then
                assertThat(defaultPrivilege)
                        .isNotNull()
                        .extracting(DefaultPrivilege::getStatus)
                        .satisfies(status -> {
                            assertThat(status.getPhase()).isEqualTo(CRPhase.ERROR);
                            assertThat(status.getMessage()).startsWith("DefaultPrivilege contains invalid privileges for the specified objectType");
                        });
            }

            @Test
            @EnabledIfSystemProperty(
                    named = "quarkus.test.profile",
                    matches = "test-pg(15|16)",
                    disabledReason = "PostgreSQL 15 and 16 do not support the MAINTAIN privilege"
            )
            @DisplayName(
                    "Should reconcile to ERROR when the PostgreSQL version does not support the MAINTAIN table privilege"
            )
            void errorWhenUnsupportedMaintainTablePrivilege() {
                // given
                var clusterConnectionMain = given.one()
                        .clusterConnection()
                        .returnFirst();

                var database = given.one()
                        .database()
                        .withClusterConnectionName(clusterConnectionMain.getMetadata().getName())
                        .withReclaimPolicy(DELETE)
                        .returnFirst();

                var clusterConnectionDb = given.one()
                        .clusterConnection()
                        .withDatabase(database.getSpec().getName())
                        .returnFirst();

                var schema = given.one()
                        .schema()
                        .withClusterConnectionName(clusterConnectionDb.getMetadata().getName())
                        .withReclaimPolicy(DELETE)
                        .returnFirst();

                var role = given.one()
                        .role()
                        .withClusterConnectionName(clusterConnectionMain.getMetadata().getName())
                        .returnFirst();

                // when
                var defaultPrivilege = given.one()
                        .defaultPrivilege()
                        .withClusterConnectionName(clusterConnectionDb.getMetadata().getName())
                        .withDatabase(database.getSpec().getName())
                        .withSchema(schema.getSpec().getName())
                        .withRole(role.getSpec().getName())
                        .withObjectType(TABLE)
                        .withPrivileges(MAINTAIN)
                        .returnFirst();

                // then
                assertThat(defaultPrivilege)
                        .isNotNull()
                        .extracting(DefaultPrivilege::getStatus)
                        .satisfies(status -> {
                            assertThat(status.getPhase()).isEqualTo(CRPhase.ERROR);
                            assertThat(status.getMessage())
                                    .startsWith("The following privileges require a newer PostgreSQL version (current:")
                                    .contains("{MAINTAIN=17}");
                        });
            }
        }
    }

    @Nested
    class SchemaTests {
        @Test
        @DisplayName("Should grant and revoke default privileges on schema")
        void defaultPrivilegeOnSchema() {
            // given
            var now = OffsetDateTime.now(ZoneOffset.UTC);

            var clusterConnectionMain = given.one()
                    .clusterConnection()
                    .returnFirst();

            var database = given.one()
                    .database()
                    .withClusterConnectionName(clusterConnectionMain.getMetadata().getName())
                    .withReclaimPolicy(DELETE)
                    .returnFirst();

            // To create a Schema in the new database, we need a ClusterConnection pointing to it
            var clusterConnectionDb = given.one()
                    .clusterConnection()
                    .withDatabase(database.getSpec().getName())
                    .returnFirst();

            var role = given.one()
                    .role()
                    .withClusterConnectionName(clusterConnectionMain.getMetadata().getName())
                    .returnFirst();

            // when
            var defaultPrivilege = given.one()
                    .defaultPrivilege()
                    .withClusterConnectionName(clusterConnectionDb.getMetadata().getName())
                    .withDatabase(database.getSpec().getName())
                    .withRole(role.getSpec().getName())
                    .withObjectType(SCHEMA)
                    .withPrivileges(USAGE)
                    .returnFirst();

            // then
            var expectedStatus = new CRStatus()
                    .setName("")
                    .setPhase(CRPhase.READY)
                    .setObservedGeneration(1L);

            assertThatDefaultPrivilegeHasStatus(
                    defaultPrivilege,
                    expectedStatus,
                    now
            );

            assertThatDefaultPrivileges(
                    clusterConnectionDb,
                    defaultPrivilege,
                    Set.of(USAGE)
            );

            // given: grant all default privileges change
            var spec = defaultPrivilege.getSpec();
            var expectedPrivileges = SCHEMA.privileges();
            var initialGeneration = defaultPrivilege.getStatus().getObservedGeneration();

            spec.setPrivileges(expectedPrivileges);

            // when
            applyDefaultPrivilege(
                    defaultPrivilege,
                    g -> g.getStatus().getObservedGeneration() >= initialGeneration + 1
            );

            // then
            assertThatDefaultPrivileges(
                    clusterConnectionDb,
                    defaultPrivilege,
                    Set.copyOf(expectedPrivileges)
            );

            // given: DefaultPrivilege privileges change
            spec.setPrivileges(List.of(CREATE));

            // when
            applyDefaultPrivilege(
                    defaultPrivilege,
                    g -> g.getStatus().getObservedGeneration() >= initialGeneration + 2
            );

            // then
            assertThatDefaultPrivileges(
                    clusterConnectionDb,
                    defaultPrivilege,
                    Set.of(CREATE)
            );

            // when: DefaultPrivilege CR instance delete
            kubernetesClient.resources(DefaultPrivilege.class)
                    .resource(defaultPrivilege)
                    .withTimeout(5, TimeUnit.SECONDS)
                    .delete();

            // then
            assertThatNoDefaultPrivileges(
                    clusterConnectionDb,
                    defaultPrivilege
            );
        }
    }

    @Nested
    class TableTests {
        @ParameterizedTest
        @MethodSource("provideAllSupportedPrivileges")
        @DisplayName("Should grant and revoke default privileges on table")
        void defaultPrivilegeOnTable(
                List<Privilege> allSupportedPrivileges
        ) {
            // given
            var now = OffsetDateTime.now(ZoneOffset.UTC);

            var clusterConnectionMain = given.one()
                    .clusterConnection()
                    .returnFirst();

            var database = given.one()
                    .database()
                    .withClusterConnectionName(clusterConnectionMain.getMetadata().getName())
                    .withReclaimPolicy(DELETE)
                    .returnFirst();

            var clusterConnectionDb = given.one()
                    .clusterConnection()
                    .withDatabase(database.getSpec().getName())
                    .returnFirst();

            var schema = given.one()
                    .schema()
                    .withClusterConnectionName(clusterConnectionDb.getMetadata().getName())
                    .withDatabase(database.getSpec().getName())
                    .withReclaimPolicy(DELETE)
                    .returnFirst();

            var role = given.one()
                    .role()
                    .withClusterConnectionName(clusterConnectionMain.getMetadata().getName())
                    .returnFirst();

            // when
            var defaultPrivilege = given.one()
                    .defaultPrivilege()
                    .withClusterConnectionName(clusterConnectionDb.getMetadata().getName())
                    .withDatabase(database.getSpec().getName())
                    .withSchema(schema.getSpec().getName())
                    .withRole(role.getSpec().getName())
                    .withObjectType(TABLE)
                    .withPrivileges(SELECT)
                    .returnFirst();

            // then
            var expectedStatus = new CRStatus()
                    .setName("")
                    .setPhase(CRPhase.READY)
                    .setObservedGeneration(1L);

            assertThatDefaultPrivilegeHasStatus(
                    defaultPrivilege,
                    expectedStatus,
                    now
            );

            assertThatDefaultPrivileges(
                    clusterConnectionDb,
                    defaultPrivilege,
                    Set.of(SELECT)
            );

            // given: grant all default privileges change
            var spec = defaultPrivilege.getSpec();
            var expectedPrivileges = allSupportedPrivileges;
            var initialGeneration = defaultPrivilege.getStatus().getObservedGeneration();

            spec.setPrivileges(expectedPrivileges);

            // when
            applyDefaultPrivilege(
                    defaultPrivilege,
                    g -> g.getStatus().getObservedGeneration() >= initialGeneration + 1
            );

            // then
            assertThatDefaultPrivileges(
                    clusterConnectionDb,
                    defaultPrivilege,
                    Set.copyOf(expectedPrivileges)
            );

            // given: DefaultPrivilege privileges change
            spec.setPrivileges(List.of(SELECT));

            // when
            applyDefaultPrivilege(
                    defaultPrivilege,
                    g -> g.getStatus().getObservedGeneration() >= initialGeneration + 2
            );

            // then
            assertThatDefaultPrivileges(
                    clusterConnectionDb,
                    defaultPrivilege,
                    Set.of(SELECT)
            );

            // when: DefaultPrivilege CR instance delete
            kubernetesClient.resources(DefaultPrivilege.class)
                    .resource(defaultPrivilege)
                    .withTimeout(5, TimeUnit.SECONDS)
                    .delete();

            // then
            assertThatNoDefaultPrivileges(
                    clusterConnectionDb,
                    defaultPrivilege
            );
        }

        static Stream<List<Privilege>> provideAllSupportedPrivileges() {
            var profile = System.getProperty("quarkus.test.profile", "");
            var matcher = Pattern.compile("test-pg(\\d+)").matcher(profile);
            int version = matcher.find() ? Integer.parseInt(matcher.group(1)) : 0;

            return Stream.of(TABLE.privilegesSet().stream()
                    .filter(privilege -> privilege.minimumPostgresVersion() == null
                            || privilege.minimumPostgresVersion() <= version
                    )
                    .toList()
            );
        }
    }

    @Nested
    class SequenceTests {
        @Test
        @DisplayName("Should grant and revoke default privileges on sequence")
        void defaultPrivilegeOnSequence() {
            // given
            var now = OffsetDateTime.now(ZoneOffset.UTC);

            var clusterConnectionMain = given.one()
                    .clusterConnection()
                    .returnFirst();

            var database = given.one()
                    .database()
                    .withClusterConnectionName(clusterConnectionMain.getMetadata().getName())
                    .withReclaimPolicy(DELETE)
                    .returnFirst();

            var clusterConnectionDb = given.one()
                    .clusterConnection()
                    .withDatabase(database.getSpec().getName())
                    .returnFirst();

            var schema = given.one()
                    .schema()
                    .withClusterConnectionName(clusterConnectionDb.getMetadata().getName())
                    .withDatabase(database.getSpec().getName())
                    .withReclaimPolicy(DELETE)
                    .returnFirst();

            var role = given.one()
                    .role()
                    .withClusterConnectionName(clusterConnectionMain.getMetadata().getName())
                    .returnFirst();

            // when
            var defaultPrivilege = given.one()
                    .defaultPrivilege()
                    .withClusterConnectionName(clusterConnectionDb.getMetadata().getName())
                    .withDatabase(database.getSpec().getName())
                    .withSchema(schema.getSpec().getName())
                    .withRole(role.getSpec().getName())
                    .withObjectType(SEQUENCE)
                    .withPrivileges(USAGE)
                    .returnFirst();

            // then
            var expectedStatus = new CRStatus()
                    .setName("")
                    .setPhase(CRPhase.READY)
                    .setObservedGeneration(1L);

            assertThatDefaultPrivilegeHasStatus(
                    defaultPrivilege,
                    expectedStatus,
                    now
            );

            assertThatDefaultPrivileges(
                    clusterConnectionDb,
                    defaultPrivilege,
                    Set.of(USAGE)
            );

            // given: grant all default privileges change
            var spec = defaultPrivilege.getSpec();
            var expectedPrivileges = SEQUENCE.privileges();
            var initialGeneration = defaultPrivilege.getStatus().getObservedGeneration();

            spec.setPrivileges(expectedPrivileges);

            // when
            applyDefaultPrivilege(
                    defaultPrivilege,
                    g -> g.getStatus().getObservedGeneration() >= initialGeneration + 1
            );

            // then
            assertThatDefaultPrivileges(
                    clusterConnectionDb,
                    defaultPrivilege,
                    Set.copyOf(expectedPrivileges)
            );

            // given: DefaultPrivilege privileges change
            spec.setPrivileges(List.of(SELECT));

            // when
            applyDefaultPrivilege(
                    defaultPrivilege,
                    g -> g.getStatus().getObservedGeneration() >= initialGeneration + 2
            );

            // then
            assertThatDefaultPrivileges(
                    clusterConnectionDb,
                    defaultPrivilege,
                    Set.of(SELECT)
            );

            // when: DefaultPrivilege CR instance delete
            kubernetesClient.resources(DefaultPrivilege.class)
                    .resource(defaultPrivilege)
                    .withTimeout(5, TimeUnit.SECONDS)
                    .delete();

            // then
            assertThatNoDefaultPrivileges(
                    clusterConnectionDb,
                    defaultPrivilege
            );
        }
    }

    private DefaultPrivilege applyDefaultPrivilege(
            DefaultPrivilege defaultPrivilege,
            Predicate<DefaultPrivilege> condition
    ) {
        var namespace = kubernetesClient.getNamespace();

        defaultPrivilege.getMetadata().setManagedFields(null);
        defaultPrivilege.getMetadata().setResourceVersion(null);

        var applied = kubernetesClient.resources(DefaultPrivilege.class)
                .inNamespace(namespace)
                .resource(defaultPrivilege)
                .serverSideApply();

        return kubernetesClient.resources(DefaultPrivilege.class)
                .inNamespace(namespace)
                .withName(applied.getMetadata().getName())
                .waitUntilCondition(
                        condition,
                        5,
                        TimeUnit.SECONDS
                );
    }

    private void assertThatDefaultPrivilegeHasStatus(
            DefaultPrivilege defaultPrivilege,
            CRStatus expectedStatus,
            OffsetDateTime now
    ) {
        assertThat(defaultPrivilege)
                .isNotNull()
                .extracting(DefaultPrivilege::getStatus)
                .satisfies(status -> {
                    assertThat(status.getLastProbeTime()).isAfter(
                            now
                    );
                    assertThat(status.getLastPhaseTransitionTime()).isAfter(
                            now
                    );
                })
                .usingRecursiveComparison()
                .ignoringFields("message", "lastProbeTime", "lastPhaseTransitionTime")
                .isEqualTo(expectedStatus);
    }

    private void assertThatDefaultPrivileges(
            ClusterConnection clusterConnection,
            DefaultPrivilege defaultPrivilege,
            Set<Privilege> expectedPrivileges
    ) {
        var databaseName = defaultPrivilege.getSpec().getDatabase();

        try (var dsl = postgreSQLContextFactory.getDSLContext(clusterConnection, databaseName)) {
            var privileges = defaultPrivilegeService.determineCurrentDefaultPrivileges(dsl, defaultPrivilege.getSpec());

            assertThat(privileges).containsAll(expectedPrivileges);
        }
    }

    private void assertThatNoDefaultPrivileges(
            ClusterConnection clusterConnection,
            DefaultPrivilege defaultPrivilege
    ) {
        var databaseName = defaultPrivilege.getSpec().getDatabase();

        try (var dsl = postgreSQLContextFactory.getDSLContext(clusterConnection, databaseName)) {
            var privileges = defaultPrivilegeService.determineCurrentDefaultPrivileges(dsl, defaultPrivilege.getSpec());

            assertThat(privileges).isEmpty();
        }
    }
}
