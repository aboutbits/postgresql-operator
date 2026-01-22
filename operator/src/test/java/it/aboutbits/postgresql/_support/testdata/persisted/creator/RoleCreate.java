package it.aboutbits.postgresql._support.testdata.persisted.creator;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import it.aboutbits.postgresql._support.testdata.base.TestDataCreator;
import it.aboutbits.postgresql._support.testdata.persisted.Given;
import it.aboutbits.postgresql.core.ClusterReference;
import it.aboutbits.postgresql.core.SecretRef;
import it.aboutbits.postgresql.crd.role.Role;
import it.aboutbits.postgresql.crd.role.RoleSpec;
import lombok.AccessLevel;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

@NullMarked
@Setter
@Accessors(fluent = true, chain = true)
public class RoleCreate extends TestDataCreator<Role> {
    private final Given given;

    private final KubernetesClient kubernetesClient;

    @Nullable
    private String withNamespace;
    @Setter(AccessLevel.NONE)
    private boolean withoutNamespace = false;

    @Nullable
    private String withName;

    @Nullable
    private String withComment;

    @Nullable
    private String withClusterConnectionName;

    @Nullable
    private String withClusterConnectionNamespace;

    @Nullable
    private SecretRef withPasswordSecretRef;

    private RoleSpec.@Nullable Flags withFlags;

    public RoleCreate(
            int numberOfItems,
            Given given,
            KubernetesClient kubernetesClient
    ) {
        super(numberOfItems);
        this.given = given;
        this.kubernetesClient = kubernetesClient;
    }

    @SuppressWarnings("unused")
    public RoleCreate withLogin(boolean login) {
        if (!login) {
            withPasswordSecretRef = null;
            return this;
        }

        if (withPasswordSecretRef != null) {
            return this;
        }

        withPasswordSecretRef = given.one()
                .secretRef()
                .returnFirst();

        return this;
    }

    @SuppressWarnings("unused")
    public RoleCreate withoutNamespace() {
        withoutNamespace = true;
        return this;
    }

    @Override
    protected Role create(int index) {
        var namespace = getNamespace();
        var name = getName();

        var item = new Role();

        item.setMetadata(new ObjectMetaBuilder()
                .withName(name)
                .withNamespace(namespace)
                .build()
        );

        var spec = new RoleSpec();
        spec.setName(name);
        spec.setComment(withComment);

        var clusterRef = new ClusterReference();
        clusterRef.setName(getClusterConnectionName());
        clusterRef.setNamespace(withClusterConnectionNamespace);
        spec.setClusterRef(clusterRef);

        spec.setPasswordSecretRef(withPasswordSecretRef);

        if (withFlags != null) {
            spec.setFlags(withFlags);
        }

        item.setSpec(spec);

        kubernetesClient.resources(Role.class)
                .inNamespace(namespace)
                .resource(item)
                .serverSideApply();

        //noinspection ConstantConditions
        return kubernetesClient.resources(Role.class)
                .inNamespace(namespace)
                .withName(name)
                .waitUntilCondition(
                        role -> role.getStatus() != null,
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

        return randomKubernetesNameSuffix("test-role");
    }

    private String getClusterConnectionName() {
        return Objects.requireNonNullElse(
                withClusterConnectionName,
                "test-cluster-connection"
        );
    }
}
