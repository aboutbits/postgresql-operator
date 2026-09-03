package it.aboutbits.postgresql._support.testdata.persisted.creator;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import it.aboutbits.postgresql._support.testdata.base.TestDataCreator;
import it.aboutbits.postgresql._support.testdata.persisted.Given;
import it.aboutbits.postgresql.core.FileRef;
import it.aboutbits.postgresql.core.ResourceRef;
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

@Setter
@Accessors(fluent = true, chain = true)
@NullMarked
public class ClusterConnectionCreate extends TestDataCreator<ClusterConnection> {
    private final Given given;

    private final KubernetesClient kubernetesClient;
    private final Given.DBConnectionDetails dbConnectionDetails;

    private @Nullable String withNamespace;

    @Setter(AccessLevel.NONE)
    private boolean withoutNamespace = false;

    private @Nullable String withName;

    private @Nullable String withHost;

    private @Nullable Integer withPort;

    private @Nullable String withDatabase;

    private @Nullable ResourceRef withAdminSecretRef;

    private @Nullable FileRef withAdminSecretFileRef;

    @Setter(AccessLevel.NONE)
    private boolean withoutAdminSecret = false;

    private @Nullable String withApplicationName;

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

    public ClusterConnectionCreate withAdminSecretFileRef(FileRef fileRef) {
        this.withAdminSecretFileRef = fileRef;
        return this;
    }

    public ClusterConnectionCreate withoutAdminSecret() {
        this.withoutAdminSecret = true;
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
        if (withAdminSecretFileRef != null) {
            spec.setAdminSecretFileRef(withAdminSecretFileRef);
        }
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

    private @Nullable String getNamespace() {
        if (withoutNamespace) {
            return null;
        }

        if (withNamespace != null) {
            return withNamespace;
        }

        withNamespace = kubernetesClient.getNamespace();

        return withNamespace;
    }

    private String getName() {
        if (withName != null) {
            return withName;
        }

        withName = randomKubernetesNameSuffix("test-cluster-connection");

        return withName;
    }

    private String getHost() {
        if (withHost != null) {
            return withHost;
        }

        withHost = "localhost";

        return withHost;
    }

    private int getPort() {
        if (withPort != null) {
            return withPort;
        }

        withPort = dbConnectionDetails.port();

        return withPort;
    }

    private String getDatabase() {
        withDatabase = Objects.requireNonNullElse(
                withDatabase,
                "postgres"
        );

        return withDatabase;
    }

    private @Nullable ResourceRef getAdminSecretRef() {
        if (withoutAdminSecret) {
            return null;
        }

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
