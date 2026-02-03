package it.aboutbits.postgresql.crd.schema;

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
class SchemaReconcilerTest {
    private final Given given;

    private final SchemaService schemaService;
    private final PostgreSQLContextFactory postgreSQLContextFactory;

    private final KubernetesClient kubernetesClient;

    @BeforeEach
    void resetEnvironment() {
        TestUtil.resetEnvironment(kubernetesClient);
    }

    @Test
    @DisplayName("When a Schema is created, it should be reconciled to READY")
    void createSchema_andStatusReady() {
        // given
        var clusterConnectionMain = given.one()
                .clusterConnection()
                .withName("test-connection-schema")
                .returnFirst();

        var database = given.one()
                .database()
                .withClusterConnectionName(clusterConnectionMain.getMetadata().getName())
                .returnFirst();

        // Creating a second ClusterConnection CR is only required for the tests
        var clusterConnectionDb = given.one()
                .clusterConnection()
                .withName("test-connection-schema-db")
                .withDatabase(database.getSpec().getName())
                .returnFirst();

        var now = OffsetDateTime.now(ZoneOffset.UTC);
        var schemaName = "test-schema";

        // when
        var schema = given.one()
                .schema()
                .withClusterConnectionName(clusterConnectionDb.getMetadata().getName())
                .withDatabase(database.getSpec().getName())
                .withName(schemaName)
                .withReclaimPolicy(DELETE)
                .returnFirst();

        // then
        var expectedStatus = new CRStatus()
                .setName(schemaName)
                .setPhase(CRPhase.READY)
                .setObservedGeneration(1L);

        assertThatSchemaHasExpectedStatus(
                schema,
                expectedStatus,
                now
        );

        var dsl = postgreSQLContextFactory.getDSLContext(clusterConnectionDb);

        assertThat(schemaService.schemaExists(dsl, schema.getSpec())).isTrue();
    }

    private void assertThatSchemaHasExpectedStatus(
            Schema schema,
            CRStatus expectedStatus,
            OffsetDateTime now
    ) {
        assertThat(schema)
                .isNotNull()
                .extracting(Schema::getStatus)
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
