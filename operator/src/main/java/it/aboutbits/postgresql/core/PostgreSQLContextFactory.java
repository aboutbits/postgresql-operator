package it.aboutbits.postgresql.core;

import io.fabric8.kubernetes.client.KubernetesClient;
import it.aboutbits.postgresql.crd.clusterconnection.ClusterConnection;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import org.jooq.CloseableDSLContext;
import org.jooq.impl.DSL;
import org.jspecify.annotations.NullMarked;

import java.util.Properties;

@NullMarked
@ApplicationScoped
@RequiredArgsConstructor
public class PostgreSQLContextFactory {
    private static final String POSTGRESQL_AUTHENTICATION_USER_KEY = "user";
    private static final String POSTGRESQL_AUTHENTICATION_PASSWORD_KEY = "password";

    private final KubernetesService kubernetesService;
    private final KubernetesClient kubernetesClient;

    /// Create a DSLContext with a JDBC connection to the PostgreSQL maintenance database.
    public CloseableDSLContext getDSLContext(ClusterConnection clusterConnection) {
        return getDSLContext(
                clusterConnection,
                clusterConnection.getSpec().getDatabase()
        );
    }

    /// Create a DSLContext with a JDBC connection to the specified database.
    public CloseableDSLContext getDSLContext(
            ClusterConnection clusterConnection,
            String database
    ) {
        var credentials = kubernetesService.getSecretRefCredentials(
                kubernetesClient,
                clusterConnection
        );

        var spec = clusterConnection.getSpec();

        var jdbcUrl = "jdbc:postgresql://%s:%d/%s".formatted(
                spec.getHost(),
                spec.getPort(),
                database
        );

        var properties = new Properties(2 + spec.getParameters().size());

        properties.setProperty(
                POSTGRESQL_AUTHENTICATION_USER_KEY,
                credentials.username()
        );
        properties.setProperty(
                POSTGRESQL_AUTHENTICATION_PASSWORD_KEY,
                credentials.password()
        );

        if (!spec.getParameters().isEmpty()) {
            properties.putAll(
                    spec.getParameters()
            );
        }

        return DSL.using(
                jdbcUrl,
                properties
        );
    }
}
