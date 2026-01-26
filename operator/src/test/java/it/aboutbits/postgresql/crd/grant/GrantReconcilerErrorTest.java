package it.aboutbits.postgresql.crd.grant;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.quarkus.test.junit.QuarkusTest;
import it.aboutbits.postgresql._support.testdata.persisted.Given;
import it.aboutbits.postgresql.core.CRPhase;
import it.aboutbits.postgresql.core.PostgreSQLContextFactory;
import it.aboutbits.postgresql.crd.clusterconnection.ClusterConnection;
import it.aboutbits.postgresql.crd.database.Database;
import it.aboutbits.postgresql.crd.role.Role;
import it.aboutbits.postgresql.crd.schema.Schema;
import lombok.RequiredArgsConstructor;
import org.jooq.impl.SQLDataType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.concurrent.TimeUnit;

import static it.aboutbits.postgresql.core.Privilege.MAINTAIN;
import static it.aboutbits.postgresql.crd.grant.GrantObjectType.TABLE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.jooq.impl.DSL.quotedName;

@QuarkusTest
@RequiredArgsConstructor
class GrantReconcilerErrorTest {
    private final Given given;

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

    @Test
    @EnabledIfSystemProperty(
            named = "quarkus.test.profile",
            matches = "test-pg(15|16)",
            disabledReason = "PostgreSQL 15 and 16 do not support the MAINTAIN privilege"
    )
    @DisplayName("Test unsupported MAINTAIN table privilege error")
    void testUnsupportedMaintainTablePrivilegeError() {
        // given
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
                    assertThat(status.getMessage()).startsWith("The following privileges require a newer PostgreSQL version (current: 16): {MAINTAIN=17}");
                });
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
}
