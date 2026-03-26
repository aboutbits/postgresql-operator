package it.aboutbits.postgresql._support.testdata.persisted.creator;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import it.aboutbits.postgresql._support.testdata.base.TestDataCreator;
import it.aboutbits.postgresql._support.testdata.persisted.Given;
import it.aboutbits.postgresql.core.ResourceRef;
import it.aboutbits.postgresql.crd.role.Role;
import it.aboutbits.postgresql.crd.role.RoleSpec;
import lombok.AccessLevel;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.TimeUnit;

@Setter
@Accessors(fluent = true, chain = true)
@NullMarked
public class RoleCreate extends TestDataCreator<Role> {
    private final Given given;

    private final KubernetesClient kubernetesClient;

    private @Nullable String withNamespace;

    @Setter(AccessLevel.NONE)
    private boolean withoutNamespace = false;

    private @Nullable String withName;

    private @Nullable String withComment;

    private @Nullable String withClusterConnectionName;

    private @Nullable String withClusterConnectionNamespace;

    private @Nullable ResourceRef withPasswordSecretRef;

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

        var clusterRef = new ResourceRef();
        clusterRef.setName(getClusterConnectionName());
        clusterRef.setNamespace(withClusterConnectionNamespace);

        var spec = new RoleSpec();

        spec.setClusterRef(clusterRef);
        spec.setName(name);
        spec.setComment(withComment);
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
                        role -> role != null && role.getStatus() != null,
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

        withName = randomKubernetesNameSuffix("test-role");

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
