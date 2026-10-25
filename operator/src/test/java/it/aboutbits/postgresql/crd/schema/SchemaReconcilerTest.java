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
import java.util.concurrent.TimeUnit;

import static it.aboutbits.postgresql.core.ReclaimPolicy.DELETE;
import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@RequiredArgsConstructor
@NullMarked
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

    @Test
    @DisplayName("When the ClusterConnection of an up-to-date Schema is back, the Schema should be reconciled to READY")
    void upToDateSchema_afterPendingClusterConnection_setsReady() {
        // given: a reconciled Schema with an explicit owner, so that the next reconciles find it up to date
        var clusterConnectionMain = given.one()
                .clusterConnection()
                .withName("test-connection-schema-pending-to-ready")
                .returnFirst();

        var database = given.one()
                .database()
                .withClusterConnectionName(clusterConnectionMain.getMetadata().getName())
                .returnFirst();

        // Creating a second ClusterConnection CR is only required for the tests
        var clusterConnectionDb = given.one()
                .clusterConnection()
                .withName("test-connection-schema-pending-to-ready-db")
                .withDatabase(database.getSpec().getName())
                .returnFirst();

        var schema = given.one()
                .schema()
                .withClusterConnectionName(clusterConnectionDb.getMetadata().getName())
                .withDatabase(database.getSpec().getName())
                .withReclaimPolicy(DELETE)
                .returnFirst();

        var spec = schema.getSpec();
        var clusterRef = spec.getClusterRef();

        var dsl = postgreSQLContextFactory.getDSLContext(clusterConnectionDb);

        spec.setOwner(schemaService.fetchSchemaOwner(dsl, spec));

        // and: the ClusterConnection is not available for a while
        clusterRef.setName("non-existing-cc");

        var pendingSchema = applySchema(schema);

        assertThat(pendingSchema.getStatus().getPhase()).isEqualTo(CRPhase.PENDING);

        // when: the ClusterConnection is available again
        clusterRef.setName(clusterConnectionDb.getMetadata().getName());

        var updatedSchema = applySchema(schema);

        // then
        assertThat(updatedSchema.getStatus().getPhase()).isEqualTo(CRPhase.READY);
        assertThat(updatedSchema.getStatus().getMessage()).isNull();
    }

    private Schema applySchema(Schema schema) {
        var namespace = kubernetesClient.getNamespace();

        schema.getMetadata().setManagedFields(null);
        schema.getMetadata().setResourceVersion(null);

        var applied = kubernetesClient.resources(Schema.class)
                .inNamespace(namespace)
                .resource(schema)
                .serverSideApply();

        var generation = applied.getMetadata().getGeneration();

        //noinspection ConstantConditions
        return kubernetesClient.resources(Schema.class)
                .inNamespace(namespace)
                .withName(applied.getMetadata().getName())
                .waitUntilCondition(
                        s -> s.getStatus() != null && s.getStatus().getObservedGeneration() >= generation,
                        5,
                        TimeUnit.SECONDS
                );
    }

    private static void assertThatSchemaHasExpectedStatus(
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
