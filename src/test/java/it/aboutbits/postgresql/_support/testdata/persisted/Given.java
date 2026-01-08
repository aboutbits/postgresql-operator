package it.aboutbits.postgresql._support.testdata.persisted;

import io.fabric8.kubernetes.client.KubernetesClient;
import it.aboutbits.postgresql._support.testdata.persisted.creator.ClusterConnectionCreate;
import it.aboutbits.postgresql._support.testdata.persisted.creator.SecretRefCreate;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jspecify.annotations.NullMarked;

import java.net.URI;

@NullMarked
@ApplicationScoped
@RequiredArgsConstructor
public class Given {
    private final KubernetesClient kubernetesClient;

    @SuppressWarnings("NullAway.Init")
    @ConfigProperty(name = "quarkus.datasource.devservices.username")
    String username;

    @SuppressWarnings("NullAway.Init")
    @ConfigProperty(name = "quarkus.datasource.devservices.password")
    String password;

    @SuppressWarnings("NullAway.Init")
    @ConfigProperty(name = "quarkus.datasource.jdbc.url")
    String jdbcUrl;

    DBConnectionDetails dbConnectionDetails() {
        return new DBConnectionDetails(
                parsePortFromJdbcUrl(jdbcUrl),
                username,
                password
        );
    }

    public One one() {
        return new One(this);
    }

    public Many many(int numberOfItems) {
        return new Many(numberOfItems, this);
    }

    public class One extends Item {
        One(Given given) {
            super(1, given);
        }
    }

    public class Many extends Item {
        Many(int numberOfItems, Given given) {
            super(numberOfItems, given);
        }
    }

    @RequiredArgsConstructor(access = AccessLevel.PACKAGE)
    public abstract class Item {
        private final int numberOfItems;
        private final Given given;

        @SuppressWarnings("unused")
        public Item describedAs(String description) {
            return this;
        }

        public SecretRefCreate secretRef() {
            return new SecretRefCreate(
                    numberOfItems,
                    kubernetesClient
            );
        }

        public ClusterConnectionCreate clusterConnection() {
            return new ClusterConnectionCreate(
                    numberOfItems,
                    given,
                    kubernetesClient,
                    dbConnectionDetails()
            );
        }
    }

    public record DBConnectionDetails(
            int port,
            String username,
            String password
    ) {
    }

    private int parsePortFromJdbcUrl(String url) {
        // Typical format: jdbc:postgresql://localhost:5432/db
        // We strip "jdbc:" so URI.create can handle the "postgresql://..." part
        return URI.create(
                url.replace("jdbc:", "")
        ).getPort();
    }
}
