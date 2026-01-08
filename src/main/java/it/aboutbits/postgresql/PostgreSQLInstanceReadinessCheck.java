package it.aboutbits.postgresql;

import io.fabric8.kubernetes.client.KubernetesClient;
import it.aboutbits.postgresql.core.PostgreSQLContextFactory;
import it.aboutbits.postgresql.crd.connection.ClusterConnection;
import lombok.RequiredArgsConstructor;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;
import org.eclipse.microprofile.health.Readiness;
import org.jspecify.annotations.NullMarked;

/**
 * MicroProfile readiness health check that verifies connectivity to all
 * configured PostgreSQL instances. Each instance is probed with a lightweight
 * operation, and the aggregated status is exposed.
 */
@NullMarked
@Readiness
@RequiredArgsConstructor
public class PostgreSQLInstanceReadinessCheck implements HealthCheck {
    private final PostgreSQLContextFactory postgreSQLContextFactory;

    private final KubernetesClient kubernetesClient;

    @Override
    public HealthCheckResponse call() {
        var builder = HealthCheckResponse.builder().name("PostgreSQL Instances");

        var connections = kubernetesClient.resources(ClusterConnection.class).list().getItems();

        boolean allUp = connections.stream()
                .allMatch(connection -> checkInstance(
                        connection,
                        builder
                ));

        return builder.status(allUp).build();
    }

    private boolean checkInstance(
            ClusterConnection clusterConnection,
            HealthCheckResponseBuilder builder
    ) {
        var name = clusterConnection.getMetadata().getName();

        try (var dsl = postgreSQLContextFactory.getDSLContext(clusterConnection)) {
            var version = dsl.fetchSingle("select version()").into(String.class);

            builder.withData(
                    name,
                    "UP (%s)".formatted(version)
            );

            return true;
        } catch (Exception _) {
            builder.withData(
                    name,
                    "DOWN"
            );

            return false;
        }
    }
}
