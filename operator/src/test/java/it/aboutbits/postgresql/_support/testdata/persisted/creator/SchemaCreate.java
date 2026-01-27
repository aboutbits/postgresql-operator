package it.aboutbits.postgresql._support.testdata.persisted.creator;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import it.aboutbits.postgresql._support.testdata.base.TestDataCreator;
import it.aboutbits.postgresql._support.testdata.persisted.Given;
import it.aboutbits.postgresql.core.ClusterReference;
import it.aboutbits.postgresql.core.ReclaimPolicy;
import it.aboutbits.postgresql.crd.schema.Schema;
import it.aboutbits.postgresql.crd.schema.SchemaSpec;
import lombok.AccessLevel;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.TimeUnit;

@NullMarked
@Setter
@Accessors(fluent = true, chain = true)
public class SchemaCreate extends TestDataCreator<Schema> {
    private final Given given;

    private final KubernetesClient kubernetesClient;

    @Nullable
    private String withNamespace;
    @Setter(AccessLevel.NONE)
    private boolean withoutNamespace = false;

    @Nullable
    private String withName;

    @Nullable
    private String withClusterConnectionName;

    @Nullable
    private String withClusterConnectionNamespace;

    private ReclaimPolicy withReclaimPolicy = ReclaimPolicy.RETAIN;

    @Nullable
    private String withOwner;

    public SchemaCreate(
            int numberOfItems,
            Given given,
            KubernetesClient kubernetesClient
    ) {
        super(numberOfItems);
        this.given = given;
        this.kubernetesClient = kubernetesClient;
    }

    @SuppressWarnings("unused")
    public SchemaCreate withoutNamespace() {
        withoutNamespace = true;
        return this;
    }

    @Override
    protected Schema create(int index) {
        var namespace = getNamespace();
        var name = getName();

        var item = new Schema();

        item.setMetadata(new ObjectMetaBuilder()
                .withName(name)
                .withNamespace(namespace)
                .build()
        );

        var spec = new SchemaSpec();
        spec.setName(name);
        spec.setReclaimPolicy(withReclaimPolicy);
        spec.setOwner(withOwner);

        var clusterRef = new ClusterReference();
        clusterRef.setName(getClusterConnectionName());
        clusterRef.setNamespace(withClusterConnectionNamespace);
        spec.setClusterRef(clusterRef);

        item.setSpec(spec);

        kubernetesClient.resources(Schema.class)
                .inNamespace(namespace)
                .resource(item)
                .serverSideApply();

        //noinspection ConstantConditions
        return kubernetesClient.resources(Schema.class)
                .inNamespace(namespace)
                .withName(name)
                .waitUntilCondition(
                        db -> db != null && db.getStatus() != null,
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

        withNamespace = kubernetesClient.getNamespace();

        return withNamespace;
    }

    private String getName() {
        if (withName != null) {
            return withName;
        }

        withName = randomKubernetesNameSuffix("test-schema");

        return withName;
    }

    private String getClusterConnectionName() {
        if (withClusterConnectionName != null) {
            return withClusterConnectionName;
        }

        var clusterConnection = given.one()
                .clusterConnection()
                .withName("%s-conn".formatted(getName()))
                .returnFirst();

        withClusterConnectionNamespace = clusterConnection.getMetadata().getNamespace();

        return clusterConnection.getMetadata().getName();
    }
}
