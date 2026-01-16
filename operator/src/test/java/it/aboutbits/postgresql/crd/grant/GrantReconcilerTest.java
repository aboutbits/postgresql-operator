package it.aboutbits.postgresql.crd.grant;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.quarkus.test.junit.QuarkusTest;
import it.aboutbits.postgresql._support.testdata.persisted.Given;
import it.aboutbits.postgresql._support.valuesource.BlankSource;
import it.aboutbits.postgresql.core.CRPhase;
import it.aboutbits.postgresql.core.CRStatus;
import it.aboutbits.postgresql.core.PostgreSQLContextFactory;
import it.aboutbits.postgresql.crd.clusterconnection.ClusterConnection;
import it.aboutbits.postgresql.crd.database.Database;
import it.aboutbits.postgresql.crd.role.Role;
import it.aboutbits.postgresql.crd.schema.Schema;
import lombok.RequiredArgsConstructor;
import org.jooq.impl.SQLDataType;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static it.aboutbits.postgresql.crd.grant.GrantObjectType.DATABASE;
import static it.aboutbits.postgresql.crd.grant.GrantObjectType.SCHEMA;
import static it.aboutbits.postgresql.crd.grant.GrantObjectType.SEQUENCE;
import static it.aboutbits.postgresql.crd.grant.GrantObjectType.TABLE;
import static it.aboutbits.postgresql.crd.grant.GrantPrivilege.CONNECT;
import static it.aboutbits.postgresql.crd.grant.GrantPrivilege.SELECT;
import static it.aboutbits.postgresql.crd.grant.GrantPrivilege.USAGE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
        deleteResources(Grant.class);
        deleteResources(Schema.class);
        deleteResources(Database.class);
        deleteResources(Role.class);
        deleteResources(ClusterConnection.class);

        // Create the default connection "test-cluster-connection" used by GrantCreate defaults
        given.one().clusterConnection()
                .withName("test-cluster-connection")
                .returnFirst();
    }

    private <T extends HasMetadata> void deleteResources(Class<T> resourceClass) {
        kubernetesClient.resources(resourceClass)
                .withTimeout(5, TimeUnit.SECONDS)
                .delete();
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
                assertThatThrownBy(() ->
                        // given / when
                        given.one()
                                .grant()
                                .withDatabase(blankOrEmptyString)
                                .withObjectType(SCHEMA)
                                .withPrivileges(USAGE)
                                .apply()
                ).isInstanceOf(KubernetesClientException.class)
                        .hasMessageContaining("The Grant database must not be empty.");
            }

            @ParameterizedTest
            @BlankSource
            @DisplayName("Should fail when the role is a blank or empty String (CEL rule)")
            void failWhenRoleIsBlankOrEmptyString(
                    String blankOrEmptyString
            ) {
                // then
                assertThatThrownBy(() ->
                        // given / when
                        given.one()
                                .grant()
                                .withRole(blankOrEmptyString)
                                .withObjectType(SCHEMA)
                                .withPrivileges(USAGE)
                                .apply()
                ).isInstanceOf(KubernetesClientException.class)
                        .hasMessageContaining("The Grant role must not be empty.");
            }

            @ParameterizedTest
            @BlankSource
            @DisplayName("Should fail when the schema is a blank or empty String (CEL rule)")
            void failWhenSchemaIsBlankOrEmptyString(
                    String blankOrEmptyString
            ) {
                // then
                assertThatThrownBy(() ->
                        // given / when
                        given.one()
                                .grant()
                                .withSchema(blankOrEmptyString)
                                .withObjectType(SCHEMA)
                                .withPrivileges(USAGE)
                                .apply()
                ).isInstanceOf(KubernetesClientException.class)
                        .hasMessageContaining("The Grant schema must not be empty.");
            }

            @Test
            @DisplayName("Should fail when the privileges are an empty List (CEL rule)")
            void failWhenPrivilegesAreAnEmptyList(
            ) {
                // then
                assertThatThrownBy(() ->
                        // given / when
                        given.one()
                                .grant()
                                .withObjectType(SCHEMA)
                                .withPrivileges(List.of())
                                .apply()
                ).isInstanceOf(KubernetesClientException.class)
                        .hasMessageContaining("The Grant privileges must not be empty.");
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
                assertThatThrownBy(() -> {
                    // when
                    item.getSpec().setDatabase("new-database");

                    kubernetesClient.resources(Grant.class)
                            .inNamespace(item.getMetadata().getNamespace())
                            .withName(item.getMetadata().getName())
                            .patch(item);
                }).isInstanceOf(KubernetesClientException.class)
                        .hasMessageContaining("The Grant database is immutable.");
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
                assertThatThrownBy(() -> {
                    // when
                    item.getSpec().setRole("new-role");

                    kubernetesClient.resources(Grant.class)
                            .inNamespace(item.getMetadata().getNamespace())
                            .withName(item.getMetadata().getName())
                            .patch(item);
                }).isInstanceOf(KubernetesClientException.class)
                        .hasMessageContaining("The Grant role is immutable.");
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
                assertThatThrownBy(() -> {
                    // when
                    item.getSpec().setSchema("new-schema");

                    kubernetesClient.resources(Grant.class)
                            .inNamespace(item.getMetadata().getNamespace())
                            .withName(item.getMetadata().getName())
                            .patch(item);
                }).isInstanceOf(KubernetesClientException.class)
                        .hasMessageContaining("The Grant schema is immutable.");
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
                assertThatThrownBy(() -> {
                    // when
                    item.getSpec().setObjectType(SEQUENCE);

                    kubernetesClient.resources(Grant.class)
                            .inNamespace(item.getMetadata().getNamespace())
                            .withName(item.getMetadata().getName())
                            .patch(item);
                }).isInstanceOf(KubernetesClientException.class)
                        .hasMessageContaining("The Grant objectType is immutable.");
            }
        }

        @Nested
        class RelatedFields {
            @Test
            @DisplayName("Should fail when objectType is DATABASE but schema is set (CEL rule)")
            void failWhenDatabaseHasSchema() {
                // then
                assertThatThrownBy(() ->
                        // given / when
                        given.one()
                                .grant()
                                .withSchema("some-schema")
                                .withObjectType(DATABASE)
                                .withPrivileges(CONNECT)
                                .returnFirst()
                ).isInstanceOf(KubernetesClientException.class)
                        .hasMessageContaining("The Grant schema must be not set if objectType is 'database'");
            }

            @Test
            @DisplayName("Should fail when objectType is DATABASE but objects has items (CEL rule)")
            void failWhenDatabaseHasObjects() {
                // then
                assertThatThrownBy(() ->
                        // given / when
                        given.one()
                                .grant()
                                .withObjectType(DATABASE)
                                .withObjects("some_object", "other_object")
                                .withPrivileges(CONNECT)
                                .returnFirst()
                ).isInstanceOf(KubernetesClientException.class)
                        .hasMessageContaining("The Grant objects must be not set if objectType is 'database' or 'schema', for all other objectType's a list is required.");
            }

            @Test
            @DisplayName("Should fail when objectType is SCHEMA but objects has items (CEL rule)")
            void failWhenSchemaHasObjects() {
                // then
                assertThatThrownBy(() ->
                        // given / when
                        given.one()
                                .grant()
                                .withObjectType(SCHEMA)
                                .withObjects("some_object", "other_object")
                                .withPrivileges(USAGE)
                                .returnFirst()
                ).isInstanceOf(KubernetesClientException.class)
                        .hasMessageContaining("The Grant objects must be not set if objectType is 'database' or 'schema', for all other objectType's a list is required.");
            }

            @Test
            @DisplayName("Should reconcile to ERROR when privileges are invalid for objectType")
            void errorWhenInvalidPrivileges() {
                // given
                var now = OffsetDateTime.now(ZoneOffset.UTC);

                // when
                var grant = given.one()
                        .grant()
                        .withObjectType(SCHEMA)
                        // SELECT is not allowed for SCHEMA
                        .withPrivileges(SELECT)
                        .returnFirst();

                var expectedStatus = new CRStatus()
                        .setName("")
                        .setPhase(CRPhase.ERROR)
                        .setMessage("Grant contains invalid privileges for the specified objectType")
                        .setObservedGeneration(1L);

                // then
                assertThatGrantHasStatus(
                        grant,
                        expectedStatus,
                        now
                );
            }
        }
    }

    @Nested
    class DatabaseTests {
        @Test
        @DisplayName("Should grant rights on database")
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
        }
    }

    @Nested
    class SchemaTests {
        @Test
        @DisplayName("Should grant rights on schema")
        void grantOnSchema() {
            // given
            var now = OffsetDateTime.now(ZoneOffset.UTC);

            var clusterConnectionMain = given.one()
                    .clusterConnection()
                    .returnFirst();

            var database = given.one()
                    .database()
                    .withClusterConnectionName(clusterConnectionMain.getMetadata().getName())
                    .returnFirst();

            // To create a Schema in the new database, we need a ClusterConnection pointing to it
            var clusterConnectionDb = given.one()
                    .clusterConnection()
                    .withDatabase(database.getSpec().getName())
                    .returnFirst();

            var schema = given.one()
                    .schema()
                    .withClusterConnectionName(clusterConnectionDb.getMetadata().getName())
                    .returnFirst();

            var role = given.one()
                    .role()
                    .withClusterConnectionName(clusterConnectionMain.getMetadata().getName())
                    .returnFirst();

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
        }
    }

    @Nested
    class TableTests {
        @Test
        @DisplayName("Should grant rights on table")
        void grantOnTable() {
            // given
            var now = OffsetDateTime.now(ZoneOffset.UTC);

            var clusterConnectionMain = given.one()
                    .clusterConnection()
                    .returnFirst();
            var database = given.one()
                    .database()
                    .withClusterConnectionName(clusterConnectionMain.getMetadata().getName())
                    .returnFirst();

            var clusterConnectionDb = given.one()
                    .clusterConnection()
                    .withDatabase(database.getSpec().getName())
                    .returnFirst();

            var schema = given.one()
                    .schema()
                    .withClusterConnectionName(clusterConnectionDb.getMetadata().getName())
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
        }

        @Test
        @DisplayName("Should grant rights on all tables")
        void grantOnAllTables() {
            // given
            var now = OffsetDateTime.now(ZoneOffset.UTC);

            var clusterConnectionMain = given.one()
                    .clusterConnection()
                    .returnFirst();
            var database = given.one()
                    .database()
                    .withClusterConnectionName(clusterConnectionMain.getMetadata().getName())
                    .returnFirst();

            var clusterConnectionDb = given.one()
                    .clusterConnection()
                    .withDatabase(database.getSpec().getName())
                    .returnFirst();

            var schema = given.one()
                    .schema()
                    .withClusterConnectionName(clusterConnectionDb.getMetadata().getName())
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
        }
    }

    @Nested
    class SequenceTests {
        @Test
        @DisplayName("Should grant rights on sequence")
        void grantOnSequence() {
            // given
            var now = OffsetDateTime.now(ZoneOffset.UTC);

            var clusterConnectionMain = given.one()
                    .clusterConnection()
                    .returnFirst();
            var database = given.one()
                    .database()
                    .withClusterConnectionName(clusterConnectionMain.getMetadata().getName())
                    .returnFirst();

            var clusterConnectionDb = given.one()
                    .clusterConnection()
                    .withDatabase(database.getSpec().getName())
                    .returnFirst();

            var schema = given.one()
                    .schema()
                    .withClusterConnectionName(clusterConnectionDb.getMetadata().getName())
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
        }

        @Test
        @DisplayName("Should grant rights on all sequences")
        void grantOnAllSequences() {
            // given
            var now = OffsetDateTime.now(ZoneOffset.UTC);

            var clusterConnectionMain = given.one()
                    .clusterConnection()
                    .returnFirst();

            var database = given.one()
                    .database()
                    .withClusterConnectionName(clusterConnectionMain.getMetadata().getName())
                    .returnFirst();

            var clusterConnectionDb = given.one()
                    .clusterConnection()
                    .withDatabase(database.getSpec().getName())
                    .returnFirst();

            var schema = given.one()
                    .schema()
                    .withClusterConnectionName(clusterConnectionDb.getMetadata().getName())
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
        }
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

    private void assertThatPrivileges(
            ClusterConnection clusterConnection,
            Grant grant,
            String objectName,
            Set<GrantPrivilege> expectedPrivileges
    ) {
        var databaseName = grant.getSpec().getDatabase();

        try (var dsl = postgreSQLContextFactory.getDSLContext(clusterConnection, databaseName)) {
            var privileges = grantService.determineCurrentObjectPrivileges(dsl, grant.getSpec());

            assertThat(privileges)
                    .containsKey(objectName)
                    .containsEntry(objectName, expectedPrivileges);
        }
    }
}
