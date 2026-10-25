package it.aboutbits.postgresql.crd.database;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.quarkus.test.junit.QuarkusTest;
import it.aboutbits.postgresql._support.testdata.base.TestUtil;
import it.aboutbits.postgresql._support.testdata.persisted.Given;
import it.aboutbits.postgresql.core.CRPhase;
import it.aboutbits.postgresql.core.CRStatus;
import it.aboutbits.postgresql.core.PostgreSQLContextFactory;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.TimeUnit;

import static it.aboutbits.postgresql.core.ReclaimPolicy.DELETE;
import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@RequiredArgsConstructor
@NullMarked
class DatabaseReconcilerTest {
    private final Given given;

    private final DatabaseService databaseService;
    private final PostgreSQLContextFactory postgreSQLContextFactory;

    private final KubernetesClient kubernetesClient;

    @BeforeEach
    void resetEnvironment() {
        TestUtil.resetEnvironment(kubernetesClient);
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
                .withReclaimPolicy(DELETE)
                .returnFirst();

        // then
        var expectedStatus = new CRStatus()
                .setName(dbName)
                .updatePhase(CRPhase.READY)
                .setObservedGeneration(1L);

        assertThatDatabaseHasExpectedStatus(
                database,
                expectedStatus,
                now
        );

        var dsl = postgreSQLContextFactory.getDSLContext(clusterConnection);

        assertThat(databaseService.databaseExists(dsl, database.getSpec())).isTrue();
    }

    @Test
    @DisplayName(
            "When the ClusterConnection of an up-to-date Database is back, the Database should be reconciled to READY"
    )
    void upToDateDatabase_afterPendingClusterConnection_setsReady() {
        // given: a reconciled Database
        var clusterConnection = given.one()
                .clusterConnection()
                .withName("test-connection-database-pending-to-ready")
                .returnFirst();

        var database = given.one()
                .database()
                .withClusterConnectionName(clusterConnection.getMetadata().getName())
                .withReclaimPolicy(DELETE)
                .returnFirst();

        var clusterRef = database.getSpec().getClusterRef();

        // and: the ClusterConnection is not available for a while
        clusterRef.setName("non-existing-cc");

        var pendingDatabase = applyDatabase(database);

        assertThat(pendingDatabase.getStatus().getPhase()).isEqualTo(CRPhase.PENDING);

        // when: the ClusterConnection is available again
        clusterRef.setName(clusterConnection.getMetadata().getName());

        var updatedDatabase = applyDatabase(database);

        // then
        assertThat(updatedDatabase.getStatus().getPhase()).isEqualTo(CRPhase.READY);
        assertThat(updatedDatabase.getStatus().getMessage()).isNull();
    }

    @Test
    @DisplayName("When a Database without owner is reconciled again, it should keep its owner and stay READY")
    void databaseWithoutOwner_reconciledAgain_keepsOwner() {
        // given: a reconciled Database without owner
        var clusterConnection = given.one()
                .clusterConnection()
                .withName("test-connection-database-without-owner")
                .returnFirst();

        var database = given.one()
                .database()
                .withClusterConnectionName(clusterConnection.getMetadata().getName())
                .returnFirst();

        var dsl = postgreSQLContextFactory.getDSLContext(clusterConnection);

        var initialOwner = databaseService.fetchDatabaseOwner(dsl, database.getSpec());

        // when: a spec change that needs no SQL statement triggers the next reconcile
        database.getSpec().setReclaimPolicy(DELETE);

        var updatedDatabase = applyDatabase(database);

        // then
        assertThat(updatedDatabase.getStatus().getPhase()).isEqualTo(CRPhase.READY);
        assertThat(updatedDatabase.getStatus().getMessage()).isNull();
        assertThat(databaseService.fetchDatabaseOwner(dsl, database.getSpec())).isEqualTo(initialOwner);
    }

    private Database applyDatabase(Database database) {
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
                        d -> d.getStatus() != null && d.getStatus().getObservedGeneration() >= generation,
                        5,
                        TimeUnit.SECONDS
                );
    }

    private static void assertThatDatabaseHasExpectedStatus(
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
