package it.aboutbits.postgresql._support.testdata.persisted.creator;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import it.aboutbits.postgresql._support.testdata.base.TestDataCreator;
import it.aboutbits.postgresql._support.testdata.persisted.Given;
import it.aboutbits.postgresql.core.SecretRef;
import it.aboutbits.postgresql.crd.clusterconnection.ClusterConnection;
import it.aboutbits.postgresql.crd.clusterconnection.ClusterConnectionSpec;
import lombok.AccessLevel;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@NullMarked
@Setter
@Accessors(fluent = true, chain = true)
public class ClusterConnectionCreate extends TestDataCreator<ClusterConnection> {
    private final Given given;

    private final KubernetesClient kubernetesClient;
    private final Given.DBConnectionDetails dbConnectionDetails;

    @Nullable
    private String withNamespace;
    @Setter(AccessLevel.NONE)
    private boolean withoutNamespace = false;

    @Nullable
    private String withName;

    @Nullable
    private String withHost;

    @Nullable
    private Integer withPort;

    @Nullable
    private String withDatabase;

    @Nullable
    private SecretRef withAdminSecretRef;

    @Nullable
    private String withApplicationName;

    public ClusterConnectionCreate(
            int numberOfItems,
            Given given,
            KubernetesClient kubernetesClient,
            Given.DBConnectionDetails dbConnectionDetails
    ) {
        super(numberOfItems);
        this.given = given;
        this.kubernetesClient = kubernetesClient;
        this.dbConnectionDetails = dbConnectionDetails;
    }

    @SuppressWarnings("unused")
    public ClusterConnectionCreate withoutNamespace() {
        this.withoutNamespace = true;
        return this;
    }

    @Override
    protected ClusterConnection create(int index) {
        // given
        var namespace = getNamespace();
        var name = getName();

        var item = new ClusterConnection();

        item.setMetadata(new ObjectMetaBuilder()
                .withName(name)
                .withNamespace(namespace)
                .build()
        );

        var spec = new ClusterConnectionSpec();
        spec.setHost(getHost());
        spec.setPort(getPort());
        spec.setDatabase(getDatabase());
        spec.setAdminSecretRef(getAdminSecretRef());
        spec.setParameters(getParameters());

        item.setSpec(spec);

        kubernetesClient.resources(ClusterConnection.class)
                .inNamespace(namespace)
                .resource(item)
                .serverSideApply();

        //noinspection ConstantConditions
        return kubernetesClient.resources(ClusterConnection.class)
                .inNamespace(namespace)
                .withName(name)
                .waitUntilCondition(
                        clusterConnection -> clusterConnection != null && clusterConnection.getStatus() != null,
                        5,
                        TimeUnit.SECONDS
                );
    }

    @Nullable
    private String getNamespace() {
        if (withoutNamespace) {
            return null;
        }

        if (withNamespace != null) {
            return withNamespace;
        }

        return kubernetesClient.getNamespace();
    }

    private String getName() {
        if (withName != null) {
            return withName;
        }

        return randomKubernetesNameSuffix("test-cluster-connection");
    }

    private String getHost() {
        if (withHost != null) {
            return withHost;
        }

        return "localhost";
    }

    private int getPort() {
        if (withPort != null) {
            return withPort;
        }

        return dbConnectionDetails.port();
    }

    private String getDatabase() {
        return Objects.requireNonNullElse(
                withDatabase,
                "postgres"
        );
    }

    private SecretRef getAdminSecretRef() {
        if (withAdminSecretRef != null) {
            return withAdminSecretRef;
        }

        return given.one()
                .secretRef()
                .withUsername(dbConnectionDetails.username())
                .withPassword(dbConnectionDetails.password())
                .returnFirst();
    }

    private Map<String, String> getParameters() {
        if (withApplicationName != null) {
            return Map.of("ApplicationName", withApplicationName);
        }

        return Map.of();
    }
}
