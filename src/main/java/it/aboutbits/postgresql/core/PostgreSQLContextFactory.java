package it.aboutbits.postgresql.core;

import io.fabric8.kubernetes.client.KubernetesClient;
import it.aboutbits.postgresql.crd.connection.ClusterConnection;
import jakarta.enterprise.context.ApplicationScoped;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jspecify.annotations.NullMarked;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

@NullMarked
@ApplicationScoped
public class PostgreSQLContextFactory {
    private static final String POSTGRESQL_AUTHENTICATION_USER_KEY = "user";
    private static final String POSTGRESQL_AUTHENTICATION_PASSWORD_KEY = "password";

    private final KubernetesClient kubernetesClient;

    public PostgreSQLContextFactory(KubernetesClient kubernetesClient) {
        this.kubernetesClient = kubernetesClient;
    }

    public DSLContext getDSLContext(ClusterConnection clusterConnection) throws SQLException {
        var credentials = KubernetesUtil.getSecretRefCredentials(
                kubernetesClient,
                clusterConnection
        );

        var spec = clusterConnection.getSpec();

        var jdbcUrl = "jdbc:postgresql://%s:%d/%s".formatted(
                spec.getHost(),
                spec.getPort(),
                spec.getMaintenanceDatabase()
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

        var connection = DriverManager.getConnection(
                jdbcUrl,
                properties
        );

        return DSL.using(
                connection,
                SQLDialect.POSTGRES
        );
    }
}
