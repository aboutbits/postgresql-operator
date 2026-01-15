package it.aboutbits.postgresql.crd.database;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.quarkus.test.junit.QuarkusTest;
import it.aboutbits.postgresql._support.testdata.persisted.Given;
import it.aboutbits.postgresql.core.CRPhase;
import it.aboutbits.postgresql.core.CRStatus;
import it.aboutbits.postgresql.core.PostgreSQLContextFactory;
import it.aboutbits.postgresql.crd.clusterconnection.ClusterConnection;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

@NullMarked
@QuarkusTest
@RequiredArgsConstructor
class DatabaseReconcilerTest {
    private final Given given;

    private final DatabaseService databaseService;
    private final PostgreSQLContextFactory postgreSQLContextFactory;

    private final KubernetesClient kubernetesClient;

    @BeforeEach
    void cleanUp() {
        kubernetesClient.resources(Database.class)
                .withTimeout(5, TimeUnit.SECONDS)
                .delete();

        kubernetesClient.resources(ClusterConnection.class)
                .withTimeout(5, TimeUnit.SECONDS)
                .delete();
    }

    @Test
    @DisplayName("When a Database is created, it should be reconciled to READY")
    void createDatabase_andStatusReady() {
        // given
        var clusterConnection = given.one()
                .clusterConnection()
                .withName("test-connection-database")
                .returnFirst();

        var now = OffsetDateTime.now(ZoneOffset.UTC);
        var dbName = "test-db";

        // when
        var database = given.one()
                .database()
                .withName(dbName)
                .withClusterConnectionName(clusterConnection.getMetadata().getName())
                .returnFirst();

        // then
        var expectedStatus = new CRStatus()
                .setName(dbName)
                .setPhase(CRPhase.READY)
                .setObservedGeneration(1L);

        assertThatDatabaseHasExpectedStatus(
                database,
                expectedStatus,
                now
        );

        var dsl = postgreSQLContextFactory.getDSLContext(clusterConnection);

        assertThat(databaseService.databaseExists(dsl, database.getSpec())).isTrue();
    }

    private Database applyDatabase(
            Database database,
            Predicate<Database> condition
    ) {
        var namespace = kubernetesClient.getNamespace();

        database.getMetadata().setManagedFields(null);
        database.getMetadata().setResourceVersion(null);

        var applied = kubernetesClient.resources(Database.class)
                .inNamespace(namespace)
                .resource(database)
                .serverSideApply();

        var generation = applied.getMetadata().getGeneration();

        //noinspection ConstantConditions
        return kubernetesClient.resources(Database.class)
                .inNamespace(namespace)
                .withName(applied.getMetadata().getName())
                .waitUntilCondition(
                        d -> database.getStatus() != null && d.getStatus().getObservedGeneration() >= generation,
                        10,
                        TimeUnit.SECONDS
                );
    }

    private void assertThatDatabaseHasExpectedStatus(
            Database database,
            CRStatus expectedStatus,
            OffsetDateTime now
    ) {
        assertThat(database)
                .isNotNull()
                .extracting(Database::getStatus)
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
