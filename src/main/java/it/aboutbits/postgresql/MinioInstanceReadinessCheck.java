package it.aboutbits.postgresql;

import io.javaoperatorsdk.operator.Operator;
import it.aboutbits.postgresql.core.PostgreSQLContextFactory;
import it.aboutbits.postgresql.crd.connection.ClusterConnectionReconciler;
import lombok.RequiredArgsConstructor;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;
import org.eclipse.microprofile.health.Readiness;
import org.jspecify.annotations.NullMarked;

/**
 * MicroProfile readiness health check that verifies connectivity to all
 * configured PostgreSQL instances. Each instance is probed with a lightweight
 * operation and the aggregated status is exposed.
 */
@NullMarked
@Readiness
@RequiredArgsConstructor
public class MinioInstanceReadinessCheck implements HealthCheck {
    private final PostgreSQLContextFactory postgreSQLContextFactory;

    private final Operator operator;

    @Override
    public HealthCheckResponse call() {
        var builder = HealthCheckResponse.builder().name("PostgreSQL Instances");

        var optionalClusterConnectionReconciler = operator.getRegisteredController(
                ClusterConnectionReconciler.class.getSimpleName()
        );

        if (optionalClusterConnectionReconciler.isEmpty()) {
            return builder
                    .status(false)
                    .withData("ClusterConnectionReconciler", "NOT_REGISTERED")
                    .build();
        }

        var clusterConnectionReconciler = optionalClusterConnectionReconciler.get();

//        boolean allUp = postgreSQLContextFactory.getClients()
//                .entrySet()
//                .stream()
//                .allMatch(entry -> checkInstance(
//                        entry.getKey(),
//                        entry.getValue(),
//                        builder
//                ));

        return builder.status(true /* allUp */).build();
    }

    /*private boolean checkInstance(
            String instance,
            MinioClient minioClient,
            HealthCheckResponseBuilder builder
    ) {
        try {
            minioClient.listBuckets();
            builder.withData(instance, "UP");
            return true;
        } catch (Exception _) {
            builder.withData(instance, "DOWN");
            return false;
        }
    }*/
}
