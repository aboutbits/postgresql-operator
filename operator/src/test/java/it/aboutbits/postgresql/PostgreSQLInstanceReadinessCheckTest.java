package it.aboutbits.postgresql;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.quarkus.test.junit.QuarkusTest;
import it.aboutbits.postgresql._support.testdata.base.TestUtil;
import it.aboutbits.postgresql._support.testdata.persisted.Given;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@NullMarked
class PostgreSQLInstanceReadinessCheckTest {
    @Inject
    Given given;

    @Inject
    @Readiness
    PostgreSQLInstanceReadinessCheck readinessCheck;

    @Inject
    KubernetesClient kubernetesClient;

    @BeforeEach
    void resetEnvironment() {
        TestUtil.resetEnvironment(kubernetesClient);
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
                .apply();

        given.one()
                .clusterConnection()
                .withName("db-2")
                .withHost("localhost")
                .withPort(2345) // Wrong port
                .apply();

        given.one()
                .clusterConnection()
                .withName("db-3")
                .withHost("127.0.0.1")
                .withPort(2345) // Wrong port
                .apply();

        given.one()
                .clusterConnection()
                .withName("db-4")
                .withHost("::1")
                .withPort(2345) // Wrong port
                .apply();

        given.one()
                .clusterConnection()
                .withName("db-5")
                .withHost("0:0:0:0:0:0:0:1")
                .withPort(2345) // Wrong port
                .apply();

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

                    assertThat(data).containsAllEntriesOf(Map.of(
                            "db-2", "DOWN",
                            "db-3", "DOWN",
                            "db-4", "DOWN",
                            "db-5", "DOWN"
                    ));
                });
    }
}
