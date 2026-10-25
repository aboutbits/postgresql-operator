package it.aboutbits.postgresql.core;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import it.aboutbits.postgresql.crd.clusterconnection.ClusterConnection;
import it.aboutbits.postgresql.crd.clusterconnection.ClusterConnectionSpec;
import it.aboutbits.postgresql.crd.database.Database;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@EnableKubernetesMockClient(crud = true)
@NullMarked
class BaseReconcilerTest {
    private static final String NAMESPACE = "test-namespace";
    private static final String CONNECTION_NAME = "test-connection";
    /// The generation that the mock server gives a new resource.
    private static final long GENERATION = 1L;

    @SuppressWarnings("NullAway.Init")
    KubernetesClient client;

    private final BaseReconciler<Database, CRStatus> reconciler = new BaseReconciler<>() {
        @Override
        protected CRStatus newStatus() {
            return new CRStatus();
        }
    };

    @BeforeEach
    void clearClusterConnections() {
        client.resources(ClusterConnection.class).inAnyNamespace().delete();
    }

    @Nested
    class GetReferencedClusterConnection {
        @Test
        @DisplayName("When the ClusterConnection does not exist, it should return empty")
        void missingClusterConnection_returnsEmpty() {
            // when
            var result = getReferencedClusterConnection();

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("When the ClusterConnection has no status yet, it should return empty")
        void clusterConnectionWithoutStatus_returnsEmpty() {
            // given
            createClusterConnection(null);

            // when
            var result = getReferencedClusterConnection();

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("When the ClusterConnection is not READY, it should return empty")
        void clusterConnectionNotReady_returnsEmpty() {
            // given
            createClusterConnection(
                    new CRStatus()
                            .updatePhase(CRPhase.ERROR)
                            .setObservedGeneration(GENERATION)
            );

            // when
            var result = getReferencedClusterConnection();

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("When the ClusterConnection is READY for an older generation, it should return empty")
        void clusterConnectionReadyForOlderGeneration_returnsEmpty() {
            // given: the spec changed, and the ClusterConnection has not been probed again yet
            createClusterConnection(
                    new CRStatus()
                            .updatePhase(CRPhase.READY)
                            .setObservedGeneration(GENERATION - 1)
            );

            // when
            var result = getReferencedClusterConnection();

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("When the ClusterConnection is READY for its current generation, it should return it")
        void clusterConnectionReady_returnsClusterConnection() {
            // given
            createClusterConnection(
                    new CRStatus()
                            .updatePhase(CRPhase.READY)
                            .setObservedGeneration(GENERATION)
            );

            // when
            var result = getReferencedClusterConnection();

            // then
            assertThat(result)
                    .get()
                    .extracting(clusterConnection -> clusterConnection.getMetadata().getName())
                    .isEqualTo(CONNECTION_NAME);
        }
    }

    private Optional<ClusterConnection> getReferencedClusterConnection() {
        var database = new Database();
        database.setMetadata(new ObjectMetaBuilder()
                .withName("test-database")
                .withNamespace(NAMESPACE)
                .build()
        );

        var clusterRef = new ResourceRef();
        clusterRef.setName(CONNECTION_NAME);

        return reconciler.getReferencedClusterConnection(
                client,
                database,
                clusterRef
        );
    }

    private void createClusterConnection(@Nullable CRStatus status) {
        var spec = new ClusterConnectionSpec();
        spec.setHost("localhost");
        spec.setPort(5432);
        spec.setDatabase("postgres");

        var clusterConnection = new ClusterConnection();
        clusterConnection.setMetadata(new ObjectMetaBuilder()
                .withName(CONNECTION_NAME)
                .withNamespace(NAMESPACE)
                .build()
        );
        clusterConnection.setSpec(spec);
        clusterConnection.setStatus(status);

        client.resource(clusterConnection).create();

        // The mock server sets the generation itself and must keep the status, or the tests above say nothing
        var stored = client.resources(ClusterConnection.class)
                .inNamespace(NAMESPACE)
                .withName(CONNECTION_NAME)
                .get();

        assertThat(stored.getMetadata().getGeneration()).isEqualTo(GENERATION);
        assertThat(stored.getStatus() == null).isEqualTo(status == null);
    }
}
