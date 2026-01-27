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

import static it.aboutbits.postgresql.core.ReclaimPolicy.DELETE;
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
