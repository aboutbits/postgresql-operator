package it.aboutbits.postgresql.crd.grant;

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
import org.jooq.impl.SQLDataType;
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
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static it.aboutbits.postgresql.core.Privilege.CONNECT;
import static it.aboutbits.postgresql.core.Privilege.CREATE;
import static it.aboutbits.postgresql.core.Privilege.MAINTAIN;
import static it.aboutbits.postgresql.core.Privilege.SELECT;
import static it.aboutbits.postgresql.core.Privilege.USAGE;
import static it.aboutbits.postgresql.core.ReclaimPolicy.DELETE;
import static it.aboutbits.postgresql.crd.grant.GrantObjectType.DATABASE;
import static it.aboutbits.postgresql.crd.grant.GrantObjectType.SCHEMA;
import static it.aboutbits.postgresql.crd.grant.GrantObjectType.SEQUENCE;
import static it.aboutbits.postgresql.crd.grant.GrantObjectType.TABLE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.jooq.impl.DSL.quotedName;

@NullMarked
@QuarkusTest
@RequiredArgsConstructor
class GrantReconcilerTest {
    private final Given given;

    private final GrantService grantService;
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
                                .grant()
                                .withDatabase(blankOrEmptyString)
                                .withObjectType(SCHEMA)
                                .withPrivileges(USAGE)
                                .apply()
                ).withMessageContaining("The Grant database must not be empty.");
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
                                .grant()
                                .withRole(blankOrEmptyString)
                                .withObjectType(SCHEMA)
                                .withPrivileges(USAGE)
                                .apply()
                ).withMessageContaining("The Grant role must not be empty.");
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
                                .grant()
                                .withSchema(blankOrEmptyString)
                                .withObjectType(SCHEMA)
                                .withPrivileges(USAGE)
                                .apply()
                ).withMessageContaining("The Grant schema must not be empty.");
            }

            @Test
            @DisplayName("Should fail when the privileges are an empty List (CEL rule)")
            void failWhenPrivilegesAreAnEmptyList() {
                // then
                assertThatExceptionOfType(KubernetesClientException.class).isThrownBy(() ->
                        // given / when
                        given.one()
                                .grant()
                                .withObjectType(SCHEMA)
                                .withPrivileges(List.of())
                                .apply()
                ).withMessageContaining("The Grant privileges must not be empty.");
            }
        }

        @Nested
        class ImmutableFields {
            @Test
            @DisplayName("Should fail when the database changes (CEL rule)")
            void failWhenDatabaseChanges() {
                // given
                var item = given.one()
                        .grant()
                        .withObjectType(SCHEMA)
                        .withPrivileges(USAGE)
                        .returnFirst();

                // then
                assertThatExceptionOfType(KubernetesClientException.class).isThrownBy(() -> {
                    // when
                    item.getSpec().setDatabase("new-database");

                    kubernetesClient.resources(Grant.class)
                            .inNamespace(item.getMetadata().getNamespace())
                            .withName(item.getMetadata().getName())
                            .patch(item);
                }).withMessageContaining("The Grant database is immutable.");
            }

            @Test
            @DisplayName("Should fail when the role changes (CEL rule)")
            void failWhenRoleChanges() {
                // given
                var item = given.one()
                        .grant()
                        .withObjectType(SCHEMA)
                        .withPrivileges(USAGE)
                        .returnFirst();

                // then
                assertThatExceptionOfType(KubernetesClientException.class).isThrownBy(() -> {
                    // when
                    item.getSpec().setRole("new-role");

                    kubernetesClient.resources(Grant.class)
                            .inNamespace(item.getMetadata().getNamespace())
                            .withName(item.getMetadata().getName())
                            .patch(item);
                }).withMessageContaining("The Grant role is immutable.");
            }

            @Test
            @DisplayName("Should fail when the schema changes (CEL rule)")
            void failWhenSchemaChanges() {
                // given
                var item = given.one()
                        .grant()
                        .withObjectType(SCHEMA)
                        .withPrivileges(USAGE)
                        .returnFirst();

                // then
                assertThatExceptionOfType(KubernetesClientException.class).isThrownBy(() -> {
                    // when
                    item.getSpec().setSchema("new-schema");

                    kubernetesClient.resources(Grant.class)
                            .inNamespace(item.getMetadata().getNamespace())
                            .withName(item.getMetadata().getName())
                            .patch(item);
                }).withMessageContaining("The Grant schema is immutable.");
            }

            @Test
            @DisplayName("Should fail when the objectType changes (CEL rule)")
            void failWhenObjectTypeChanges() {
                // given
                var item = given.one()
                        .grant()
                        .withObjectType(SCHEMA)
                        .withPrivileges(USAGE)
                        .returnFirst();

                // then
                assertThatExceptionOfType(KubernetesClientException.class).isThrownBy(() -> {
                    // when
                    item.getSpec().setObjectType(SEQUENCE);

                    kubernetesClient.resources(Grant.class)
                            .inNamespace(item.getMetadata().getNamespace())
                            .withName(item.getMetadata().getName())
                            .patch(item);
                }).withMessageContaining("The Grant objectType is immutable.");
            }
        }

        @Nested
        class RelatedFields {
            @Test
            @DisplayName("Should fail when objectType is DATABASE but schema is set (CEL rule)")
            void failWhenDatabaseHasSchema() {
                // then
                assertThatExceptionOfType(KubernetesClientException.class).isThrownBy(() ->
                        // given / when
                        given.one()
                                .grant()
                                .withSchema("some-schema")
                                .withObjectType(DATABASE)
                                .withPrivileges(CONNECT)
                                .returnFirst()
                ).withMessageContaining("The Grant schema must be not set if objectType is 'database'");
            }

            @Test
            @DisplayName("Should fail when objectType is DATABASE but objects has items (CEL rule)")
            void failWhenDatabaseHasObjects() {
                // then
                assertThatExceptionOfType(KubernetesClientException.class).isThrownBy(() ->
                        // given / when
                        given.one()
                                .grant()
                                .withObjectType(DATABASE)
                                .withObjects("some_object", "other_object")
                                .withPrivileges(CONNECT)
                                .returnFirst()
                ).withMessageContaining("The Grant objects must be not set if objectType is 'database' or 'schema', for all other objectType's a list is required.");
            }

            @Test
            @DisplayName("Should fail when objectType is SCHEMA but objects has items (CEL rule)")
            void failWhenSchemaHasObjects() {
                // then
                assertThatExceptionOfType(KubernetesClientException.class).isThrownBy(() ->
                        // given / when
                        given.one()
                                .grant()
                                .withObjectType(SCHEMA)
                                .withObjects("some_object", "other_object")
                                .withPrivileges(USAGE)
                                .returnFirst()
                ).withMessageContaining("The Grant objects must be not set if objectType is 'database' or 'schema', for all other objectType's a list is required.");
            }

            @Test
            @DisplayName("Should reconcile to ERROR when privileges are invalid for objectType")
            void errorWhenInvalidPrivileges() {
                // given / when
                var grant = given.one()
                        .grant()
                        .withObjectType(SCHEMA)
                        // SELECT is not allowed for SCHEMA
                        .withPrivileges(SELECT)
                        .returnFirst();

                // then
                assertThat(grant)
                        .isNotNull()
                        .extracting(Grant::getStatus)
                        .satisfies(status -> {
                            assertThat(status.getPhase()).isEqualTo(CRPhase.ERROR);
                            assertThat(status.getMessage()).startsWith("Grant contains invalid privileges for the specified objectType");
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

                var tableName = "test_table";
                createTable(
                        clusterConnectionDb,
                        database.getSpec().getName(),
                        schema.getSpec().getName(),
                        tableName
                );

                // when
                var grant = given.one()
                        .grant()
                        .withClusterConnectionName(clusterConnectionDb.getMetadata().getName())
                        .withDatabase(database.getSpec().getName())
                        .withSchema(schema.getSpec().getName())
                        .withRole(role.getSpec().getName())
                        .withObjectType(TABLE)
                        .withObjects(tableName)
                        .withPrivileges(MAINTAIN)
                        .returnFirst();

                // then
                assertThat(grant)
                        .isNotNull()
                        .extracting(Grant::getStatus)
                        .satisfies(status -> {
                            assertThat(status.getPhase()).isEqualTo(CRPhase.ERROR);
                            assertThat(status.getMessage())
                                    .startsWith("The following privileges require a newer PostgreSQL version (current:")
                                    .contains("{MAINTAIN=17}");
                        });

                // cleanup
                deleteTable(
                        clusterConnectionDb,
                        database.getSpec().getName(),
                        schema.getSpec().getName(),
                        tableName
                );
            }
        }
    }

    @Nested
    class DatabaseTests {
        @Test
        @DisplayName("Should grant and revoke privileges on database")
        void grantOnDatabase() {
            // given
            var now = OffsetDateTime.now(ZoneOffset.UTC);

            var clusterConnection = given.one()
                    .clusterConnection()
                    .returnFirst();

            var role = given.one()
                    .role()
                    .withClusterConnectionName(clusterConnection.getMetadata().getName())
                    .returnFirst();

            var database = given.one()
                    .database()
                    .withClusterConnectionName(clusterConnection.getMetadata().getName())
                    .withReclaimPolicy(DELETE)
                    .returnFirst();

            // when
            var grant = given.one()
                    .grant()
                    .withClusterConnectionName(clusterConnection.getMetadata().getName())
                    .withDatabase(database.getSpec().getName())
                    .withRole(role.getSpec().getName())
                    .withObjectType(DATABASE)
                    .withPrivileges(CONNECT)
                    .returnFirst();

            // then
            var expectedStatus = new CRStatus()
                    .setName("")
                    .setPhase(CRPhase.READY)
                    .setObservedGeneration(1L);

            assertThatGrantHasStatus(
                    grant,
                    expectedStatus,
                    now
            );

            assertThatPrivileges(
                    clusterConnection,
                    grant,
                    grant.getSpec().getDatabase(),
                    Set.of(CONNECT)
            );

            // given: grant all privileges change
            var spec = grant.getSpec();
            var expectedPrivileges = DATABASE.privileges();
            var initialGeneration = grant.getStatus().getObservedGeneration();

            spec.setPrivileges(expectedPrivileges);

            // when
            applyGrant(
                    grant,
                    g -> g.getStatus().getObservedGeneration() >= initialGeneration + 1
            );

            // then
            assertThatPrivileges(
                    clusterConnection,
                    grant,
                    grant.getSpec().getDatabase(),
                    Set.copyOf(expectedPrivileges)
            );

            // given: grant privileges change
            spec.setPrivileges(List.of(CONNECT));

            // when
            applyGrant(
                    grant,
                    g -> g.getStatus().getObservedGeneration() >= initialGeneration + 2
            );

            // then
            assertThatPrivileges(
                    clusterConnection,
                    grant,
                    grant.getSpec().getDatabase(),
                    Set.of(CONNECT)
            );

            // when: grant CR instance delete
            kubernetesClient.resources(Grant.class)
                    .resource(grant)
                    .withTimeout(5, TimeUnit.SECONDS)
                    .delete();

            // then
            assertThatNoPrivileges(
                    clusterConnection,
                    grant,
                    grant.getSpec().getDatabase()
            );
        }
    }

    @Nested
    class SchemaTests {
        @Test
        @DisplayName("Should grant and revoke privileges on schema")
        void grantOnSchema() {
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
            var grant = given.one()
                    .grant()
                    .withClusterConnectionName(clusterConnectionDb.getMetadata().getName())
                    .withDatabase(database.getSpec().getName())
                    .withSchema(schema.getSpec().getName())
                    .withRole(role.getSpec().getName())
                    .withObjectType(SCHEMA)
                    .withPrivileges(USAGE)
                    .returnFirst();

            // then
            var schemaName = Objects.requireNonNull(grant.getSpec().getSchema());

            var expectedStatus = new CRStatus()
                    .setName("")
                    .setPhase(CRPhase.READY)
                    .setObservedGeneration(1L);

            assertThatGrantHasStatus(
                    grant,
                    expectedStatus,
                    now
            );

            assertThatPrivileges(
                    clusterConnectionDb,
                    grant,
                    Objects.requireNonNull(grant.getSpec().getSchema()),
                    Set.of(USAGE)
            );

            // given: grant all privileges change
            var spec = grant.getSpec();
            var expectedPrivileges = SCHEMA.privileges();
            var initialGeneration = grant.getStatus().getObservedGeneration();

            spec.setPrivileges(expectedPrivileges);

            // when
            applyGrant(
                    grant,
                    g -> g.getStatus().getObservedGeneration() >= initialGeneration + 1
            );

            // then
            assertThatPrivileges(
                    clusterConnectionDb,
                    grant,
                    schemaName,
                    Set.copyOf(expectedPrivileges)
            );

            // given: grant privileges change
            spec.setPrivileges(List.of(CREATE));

            // when
            applyGrant(
                    grant,
                    g -> g.getStatus().getObservedGeneration() >= initialGeneration + 2
            );

            // then
            assertThatPrivileges(
                    clusterConnectionDb,
                    grant,
                    schemaName,
                    Set.of(CREATE)
            );

            // when: grant CR instance delete
            kubernetesClient.resources(Grant.class)
                    .resource(grant)
                    .withTimeout(5, TimeUnit.SECONDS)
                    .delete();

            // then
            assertThatNoPrivileges(
                    clusterConnectionDb,
                    grant,
                    schemaName
            );
        }
    }

    @Nested
    class TableTests {
        @ParameterizedTest
        @MethodSource("provideAllSupportedPrivileges")
        @DisplayName("Should grant and revoke privileges on table")
        void grantOnTable(
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
                    .withReclaimPolicy(DELETE)
                    .returnFirst();

            var role = given.one()
                    .role()
                    .withClusterConnectionName(clusterConnectionMain.getMetadata().getName())
                    .returnFirst();

            var tableName = "test_table";
            createTable(
                    clusterConnectionDb,
                    database.getSpec().getName(),
                    schema.getSpec().getName(),
                    tableName
            );

            // when
            var grant = given.one()
                    .grant()
                    .withClusterConnectionName(clusterConnectionDb.getMetadata().getName())
                    .withDatabase(database.getSpec().getName())
                    .withSchema(schema.getSpec().getName())
                    .withRole(role.getSpec().getName())
                    .withObjectType(TABLE)
                    .withObjects(tableName)
                    .withPrivileges(SELECT)
                    .returnFirst();

            // then
            var expectedStatus = new CRStatus()
                    .setName("")
                    .setPhase(CRPhase.READY)
                    .setObservedGeneration(1L);

            assertThatGrantHasStatus(
                    grant,
                    expectedStatus,
                    now
            );

            assertThatPrivileges(
                    clusterConnectionDb,
                    grant,
                    tableName,
                    Set.of(SELECT)
            );

            // given: grant all privileges change
            var spec = grant.getSpec();
            var expectedPrivileges = allSupportedPrivileges;
            var initialGeneration = grant.getStatus().getObservedGeneration();

            spec.setPrivileges(expectedPrivileges);

            // when
            applyGrant(
                    grant,
                    g -> g.getStatus().getObservedGeneration() >= initialGeneration + 1
            );

            // then
            assertThatPrivileges(
                    clusterConnectionDb,
                    grant,
                    tableName,
                    Set.copyOf(expectedPrivileges)
            );

            // given: grant privileges change
            spec.setPrivileges(List.of(SELECT));

            // when
            applyGrant(
                    grant,
                    g -> g.getStatus().getObservedGeneration() >= initialGeneration + 2
            );

            // then
            assertThatPrivileges(
                    clusterConnectionDb,
                    grant,
                    tableName,
                    Set.of(SELECT)
            );

            // when: grant CR instance delete
            kubernetesClient.resources(Grant.class)
                    .resource(grant)
                    .withTimeout(5, TimeUnit.SECONDS)
                    .delete();

            // then
            assertThatNoPrivileges(
                    clusterConnectionDb,
                    grant,
                    tableName
            );

            // cleanup
            deleteTable(
                    clusterConnectionDb,
                    database.getSpec().getName(),
                    schema.getSpec().getName(),
                    tableName
            );
        }

        @SuppressWarnings("checkstyle:MethodLength")
        @ParameterizedTest
        @MethodSource("provideAllSupportedPrivileges")
        @DisplayName("Should grant and revoke privileges on all tables")
        void grantOnAllTables(
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
                    .withReclaimPolicy(DELETE)
                    .returnFirst();

            var role = given.one()
                    .role()
                    .withClusterConnectionName(clusterConnectionMain.getMetadata().getName())
                    .returnFirst();

            var tableName1 = "test_table_1";
            var tableName2 = "test_table_2";
            createTable(
                    clusterConnectionDb,
                    database.getSpec().getName(),
                    schema.getSpec().getName(),
                    tableName1
            );
            createTable(
                    clusterConnectionDb,
                    database.getSpec().getName(),
                    schema.getSpec().getName(),
                    tableName2
            );

            // when
            var grant = given.one()
                    .grant()
                    .withClusterConnectionName(clusterConnectionDb.getMetadata().getName())
                    .withDatabase(database.getSpec().getName())
                    .withSchema(schema.getSpec().getName())
                    .withRole(role.getSpec().getName())
                    .withObjectType(TABLE)
                    .withObjects(List.of())
                    .withPrivileges(SELECT)
                    .returnFirst();

            // then
            var expectedStatus = new CRStatus()
                    .setName("")
                    .setPhase(CRPhase.READY)
                    .setObservedGeneration(1L);

            assertThatGrantHasStatus(
                    grant,
                    expectedStatus,
                    now
            );

            assertThatPrivileges(
                    clusterConnectionDb,
                    grant,
                    tableName1,
                    Set.of(SELECT)
            );
            assertThatPrivileges(
                    clusterConnectionDb,
                    grant,
                    tableName2,
                    Set.of(SELECT)
            );

            // given: grant all privileges change
            var spec = grant.getSpec();
            var expectedPrivileges = allSupportedPrivileges;
            var initialGeneration = grant.getStatus().getObservedGeneration();

            spec.setPrivileges(allSupportedPrivileges);

            // when
            applyGrant(
                    grant,
                    g -> g.getStatus().getObservedGeneration() >= initialGeneration + 1
            );

            // then
            assertThatPrivileges(
                    clusterConnectionDb,
                    grant,
                    tableName1,
                    Set.copyOf(expectedPrivileges)
            );
            assertThatPrivileges(
                    clusterConnectionDb,
                    grant,
                    tableName2,
                    Set.copyOf(expectedPrivileges)
            );

            // given: grant privileges change
            spec.setPrivileges(List.of(SELECT));

            // when
            applyGrant(
                    grant,
                    g -> g.getStatus().getObservedGeneration() >= initialGeneration + 2
            );

            // then
            assertThatPrivileges(
                    clusterConnectionDb,
                    grant,
                    tableName1,
                    Set.of(SELECT)
            );
            assertThatPrivileges(
                    clusterConnectionDb,
                    grant,
                    tableName2,
                    Set.of(SELECT)
            );

            // when: grant CR instance delete
            kubernetesClient.resources(Grant.class)
                    .resource(grant)
                    .withTimeout(5, TimeUnit.SECONDS)
                    .delete();

            // then
            assertThatNoPrivileges(
                    clusterConnectionDb,
                    grant,
                    tableName1
            );
            assertThatNoPrivileges(
                    clusterConnectionDb,
                    grant,
                    tableName2
            );

            // cleanup
            deleteTable(
                    clusterConnectionDb,
                    database.getSpec().getName(),
                    schema.getSpec().getName(),
                    tableName1
            );
            deleteTable(
                    clusterConnectionDb,
                    database.getSpec().getName(),
                    schema.getSpec().getName(),
                    tableName2
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
        @DisplayName("Should grant and revoke privileges on sequence")
        void grantOnSequence() {
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
                    .withReclaimPolicy(DELETE)
                    .returnFirst();

            var role = given.one()
                    .role()
                    .withClusterConnectionName(clusterConnectionMain.getMetadata().getName())
                    .returnFirst();

            var tableName = "test_sequence_table";
            createTableWithSerial(
                    clusterConnectionDb,
                    database.getSpec().getName(),
                    schema.getSpec().getName(),
                    tableName
            );
            var sequenceName = tableName + "_id_seq";

            // when
            var grant = given.one()
                    .grant()
                    .withClusterConnectionName(clusterConnectionDb.getMetadata().getName())
                    .withDatabase(database.getSpec().getName())
                    .withSchema(schema.getSpec().getName())
                    .withRole(role.getSpec().getName())
                    .withObjectType(SEQUENCE)
                    .withObjects(sequenceName)
                    .withPrivileges(USAGE)
                    .returnFirst();

            // then
            var expectedStatus = new CRStatus()
                    .setName("")
                    .setPhase(CRPhase.READY)
                    .setObservedGeneration(1L);

            assertThatGrantHasStatus(
                    grant,
                    expectedStatus,
                    now
            );

            assertThatPrivileges(
                    clusterConnectionDb,
                    grant,
                    sequenceName,
                    Set.of(USAGE)
            );

            // given: grant all privileges change
            var spec = grant.getSpec();
            var expectedPrivileges = SEQUENCE.privileges();
            var initialGeneration = grant.getStatus().getObservedGeneration();

            spec.setPrivileges(expectedPrivileges);

            // when
            applyGrant(
                    grant,
                    g -> g.getStatus().getObservedGeneration() >= initialGeneration + 1
            );

            // then
            assertThatPrivileges(
                    clusterConnectionDb,
                    grant,
                    sequenceName,
                    Set.copyOf(expectedPrivileges)
            );

            // given: grant privileges change
            spec.setPrivileges(List.of(SELECT));

            // when
            applyGrant(
                    grant,
                    g -> g.getStatus().getObservedGeneration() >= initialGeneration + 2
            );

            // then
            assertThatPrivileges(
                    clusterConnectionDb,
                    grant,
                    sequenceName,
                    Set.of(SELECT)
            );

            // when: grant CR instance delete
            kubernetesClient.resources(Grant.class)
                    .resource(grant)
                    .withTimeout(5, TimeUnit.SECONDS)
                    .delete();

            // then
            assertThatNoPrivileges(
                    clusterConnectionDb,
                    grant,
                    sequenceName
            );

            // cleanup
            deleteTable(
                    clusterConnectionDb,
                    database.getSpec().getName(),
                    schema.getSpec().getName(),
                    tableName
            );
        }

        @SuppressWarnings("checkstyle:MethodLength")
        @Test
        @DisplayName("Should grant and revoke privileges on all sequences")
        void grantOnAllSequences() {
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
                    .withReclaimPolicy(DELETE)
                    .returnFirst();

            var role = given.one()
                    .role()
                    .withClusterConnectionName(clusterConnectionMain.getMetadata().getName())
                    .returnFirst();

            var tableName1 = "test_sequence_table_1";
            var tableName2 = "test_sequence_table_2";
            createTableWithSerial(
                    clusterConnectionDb,
                    database.getSpec().getName(),
                    schema.getSpec().getName(),
                    tableName1
            );
            createTableWithSerial(
                    clusterConnectionDb,
                    database.getSpec().getName(),
                    schema.getSpec().getName(),
                    tableName2
            );
            var sequenceName1 = tableName1 + "_id_seq";
            var sequenceName2 = tableName2 + "_id_seq";

            // when
            var grant = given.one()
                    .grant()
                    .withClusterConnectionName(clusterConnectionDb.getMetadata().getName())
                    .withDatabase(database.getSpec().getName())
                    .withSchema(schema.getSpec().getName())
                    .withRole(role.getSpec().getName())
                    .withObjectType(SEQUENCE)
                    .withObjects(List.of())
                    .withPrivileges(USAGE)
                    .returnFirst();

            // then
            var expectedStatus = new CRStatus()
                    .setName("")
                    .setPhase(CRPhase.READY)
                    .setObservedGeneration(1L);

            assertThatGrantHasStatus(
                    grant,
                    expectedStatus,
                    now
            );

            assertThatPrivileges(
                    clusterConnectionDb,
                    grant,
                    sequenceName1,
                    Set.of(USAGE)
            );
            assertThatPrivileges(
                    clusterConnectionDb,
                    grant,
                    sequenceName2,
                    Set.of(USAGE)
            );

            // given: grant all privileges change
            var spec = grant.getSpec();
            var expectedPrivileges = SEQUENCE.privileges();
            var initialGeneration = grant.getStatus().getObservedGeneration();

            spec.setPrivileges(expectedPrivileges);

            // when
            applyGrant(
                    grant,
                    g -> g.getStatus().getObservedGeneration() >= initialGeneration + 1
            );

            // then
            assertThatPrivileges(
                    clusterConnectionDb,
                    grant,
                    sequenceName1,
                    Set.copyOf(expectedPrivileges)
            );
            assertThatPrivileges(
                    clusterConnectionDb,
                    grant,
                    sequenceName2,
                    Set.copyOf(expectedPrivileges)
            );

            // given: grant privileges change
            spec.setPrivileges(List.of(SELECT));

            // when
            applyGrant(
                    grant,
                    g -> g.getStatus().getObservedGeneration() >= initialGeneration + 2
            );

            // then
            assertThatPrivileges(
                    clusterConnectionDb,
                    grant,
                    sequenceName1,
                    Set.of(SELECT)
            );
            assertThatPrivileges(
                    clusterConnectionDb,
                    grant,
                    sequenceName2,
                    Set.of(SELECT)
            );

            // when: grant CR instance delete
            kubernetesClient.resources(Grant.class)
                    .resource(grant)
                    .withTimeout(5, TimeUnit.SECONDS)
                    .delete();

            // then
            assertThatNoPrivileges(
                    clusterConnectionDb,
                    grant,
                    sequenceName1
            );
            assertThatNoPrivileges(
                    clusterConnectionDb,
                    grant,
                    sequenceName2
            );

            // cleanup
            deleteTable(
                    clusterConnectionDb,
                    database.getSpec().getName(),
                    schema.getSpec().getName(),
                    tableName1
            );
            deleteTable(
                    clusterConnectionDb,
                    database.getSpec().getName(),
                    schema.getSpec().getName(),
                    tableName2
            );
        }
    }

    private Grant applyGrant(
            Grant grant,
            Predicate<Grant> condition
    ) {
        var namespace = kubernetesClient.getNamespace();

        grant.getMetadata().setManagedFields(null);
        grant.getMetadata().setResourceVersion(null);

        var applied = kubernetesClient.resources(Grant.class)
                .inNamespace(namespace)
                .resource(grant)
                .serverSideApply();

        return kubernetesClient.resources(Grant.class)
                .inNamespace(namespace)
                .withName(applied.getMetadata().getName())
                .waitUntilCondition(
                        condition,
                        5,
                        TimeUnit.SECONDS
                );
    }

    private void assertThatGrantHasStatus(
            Grant grant,
            CRStatus expectedStatus,
            OffsetDateTime now
    ) {
        assertThat(grant)
                .isNotNull()
                .extracting(Grant::getStatus)
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

    private void createTable(
            ClusterConnection clusterConnection,
            String databaseName,
            String schemaName,
            String tableName
    ) {
        try (var dsl = postgreSQLContextFactory.getDSLContext(clusterConnection, databaseName)) {
            dsl.createTable(quotedName(schemaName, tableName))
                    .column("id", SQLDataType.INTEGER)
                    .execute();
        }
    }

    private void createTableWithSerial(
            ClusterConnection clusterConnection,
            String databaseName,
            String schemaName,
            String tableName
    ) {
        try (var dsl = postgreSQLContextFactory.getDSLContext(clusterConnection, databaseName)) {
            dsl.createTable(quotedName(schemaName, tableName))
                    .column("id", SQLDataType.BIGINT.identity(true))
                    .execute();
        }
    }

    private void deleteTable(
            ClusterConnection clusterConnection,
            String databaseName,
            String schemaName,
            String tableName
    ) {
        try (var dsl = postgreSQLContextFactory.getDSLContext(clusterConnection, databaseName)) {
            dsl.dropTable(quotedName(schemaName, tableName)).execute();
        }
    }

    private void assertThatPrivileges(
            ClusterConnection clusterConnection,
            Grant grant,
            String objectName,
            Set<Privilege> expectedPrivileges
    ) {
        var databaseName = grant.getSpec().getDatabase();

        try (var dsl = postgreSQLContextFactory.getDSLContext(clusterConnection, databaseName)) {
            var privileges = grantService.determineCurrentObjectPrivileges(dsl, grant.getSpec());

            assertThat(privileges).containsEntry(
                    objectName,
                    expectedPrivileges
            );
        }
    }

    private void assertThatNoPrivileges(
            ClusterConnection clusterConnection,
            Grant grant,
            String objectName
    ) {
        var databaseName = grant.getSpec().getDatabase();

        try (var dsl = postgreSQLContextFactory.getDSLContext(clusterConnection, databaseName)) {
            var privileges = grantService.determineCurrentObjectPrivileges(dsl, grant.getSpec());

            assertThat(privileges).doesNotContainKey(objectName);
        }
    }
}
