package it.aboutbits.postgresql.crd.schema;

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
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.TimeUnit;

import static it.aboutbits.postgresql.core.ReclaimPolicy.DELETE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

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
        kubernetesClient.resources(Schema.class).delete();

        await().atMost(5, TimeUnit.SECONDS)
                .pollInterval(100, TimeUnit.MILLISECONDS)
                .until(() ->
                        kubernetesClient.resources(Schema.class).list().getItems().isEmpty()
                );

        kubernetesClient.resources(ClusterConnection.class).delete();

        await().atMost(5, TimeUnit.SECONDS)
                .pollInterval(100, TimeUnit.MILLISECONDS)
                .until(() ->
                        kubernetesClient.resources(ClusterConnection.class).list().getItems().isEmpty()
                );
    }

    @Disabled("TODO Fix me when running the whole test suite")
    @Test
    @DisplayName("When a Schema is created, it should be reconciled to READY")
    void createSchema_andStatusReady() {
        // given
        var clusterConnection = given.one()
                .clusterConnection()
                .withName("test-connection-schema")
                .returnFirst();

        var now = OffsetDateTime.now(ZoneOffset.UTC);
        var schemaName = "test-schema";

        // when
        var schema = given.one()
                .schema()
                .withName(schemaName)
                .withClusterConnectionName(clusterConnection.getMetadata().getName())
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

        var dsl = postgreSQLContextFactory.getDSLContext(clusterConnection);

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
