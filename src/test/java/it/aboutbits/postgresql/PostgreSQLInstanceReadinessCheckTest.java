package it.aboutbits.postgresql;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.quarkus.test.junit.QuarkusTest;
import it.aboutbits.postgresql._support.testdata.persisted.Given;
import it.aboutbits.postgresql.crd.connection.ClusterConnection;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

@NullMarked
@QuarkusTest
class PostgreSQLInstanceReadinessCheckTest {
    @Inject
    Given given;

    @Inject
    @Readiness
    PostgreSQLInstanceReadinessCheck readinessCheck;

    @Inject
    KubernetesClient kubernetesClient;

    @BeforeEach
    void cleanUp() {
        kubernetesClient.resources(ClusterConnection.class).delete();
    }

    @Test
    void call_whenAllConnectionsUp_shouldReturnUp() {
        given.one()
                .clusterConnection()
                .withName("test-db")
                .returnFirst();

        var response = readinessCheck.call();

        assertThat(response.getStatus()).isEqualTo(
                HealthCheckResponse.Status.UP
        );

        assertThat(response.getData())
                .isPresent()
                .get()
                .satisfies(data -> {
                    assertThat(data).containsKey("test-db");

                    var dbStatus = Objects.requireNonNull(
                            data.get("test-db")
                    );

                    assertThat(
                            dbStatus.toString()
                    ).startsWith("UP (PostgreSQL");
                });
    }

    @Test
    void call_whenSomeConnectionsDown_shouldReturnDown() {
        given.one()
                .clusterConnection()
                .withName("db-1")
                .returnFirst();

        given.one()
                .clusterConnection()
                .withName("db-2")
                .withHost("non-existent-host")
                .returnFirst();

        var response = readinessCheck.call();

        assertThat(response.getStatus()).isEqualTo(
                HealthCheckResponse.Status.DOWN
        );

        assertThat(response.getData())
                .isPresent()
                .get()
                .satisfies(data -> {
                    assertThat(data).containsKey("db-1");

                    var dbStatus = Objects.requireNonNull(
                            data.get("db-1")
                    );

                    assertThat(dbStatus.toString()).startsWith("UP (PostgreSQL");

                    assertThat(data).containsEntry("db-2", "DOWN");
                });
    }
}
