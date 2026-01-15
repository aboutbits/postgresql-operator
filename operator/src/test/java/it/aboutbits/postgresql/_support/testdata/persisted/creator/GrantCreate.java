package it.aboutbits.postgresql._support.testdata.persisted.creator;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import it.aboutbits.postgresql._support.testdata.base.TestDataCreator;
import it.aboutbits.postgresql._support.testdata.persisted.Given;
import it.aboutbits.postgresql.core.ClusterReference;
import it.aboutbits.postgresql.crd.grant.Grant;
import it.aboutbits.postgresql.crd.grant.GrantObjectType;
import it.aboutbits.postgresql.crd.grant.GrantPrivilege;
import it.aboutbits.postgresql.crd.grant.GrantSpec;
import lombok.AccessLevel;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@NullMarked
@Setter
@Accessors(fluent = true, chain = true)
public class GrantCreate extends TestDataCreator<Grant> {
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

    @Nullable
    private String withDatabase;

    @Nullable
    private String withRole;

    @Nullable
    private String withSchema;

    private GrantObjectType withObjectType = GrantObjectType.DATABASE;

    @Nullable
    @Setter(AccessLevel.NONE)
    private List<String> withObjects = null;

    @Setter(AccessLevel.NONE)
    private List<GrantPrivilege> withPrivileges = new ArrayList<>();

    public GrantCreate(
            int numberOfItems,
            Given given,
            KubernetesClient kubernetesClient
    ) {
        super(numberOfItems);
        this.given = given;
        this.kubernetesClient = kubernetesClient;
    }

    @SuppressWarnings("unused")
    public GrantCreate withObjects(List<String> objects) {
        this.withObjects = objects;
        return this;
    }

    @SuppressWarnings("unused")
    public GrantCreate withObjects(String... objects) {
        this.withObjects = List.of(objects);
        return this;
    }

    @SuppressWarnings("unused")
    public GrantCreate withPrivileges(List<GrantPrivilege> privileges) {
        this.withPrivileges = privileges;
        return this;
    }

    @SuppressWarnings("unused")
    public GrantCreate withPrivileges(GrantPrivilege... privileges) {
        this.withPrivileges = List.of(privileges);
        return this;
    }

    @SuppressWarnings("unused")
    public GrantCreate withoutNamespace() {
        withoutNamespace = true;
        return this;
    }

    @Override
    protected Grant create(int index) {
        var namespace = getNamespace();
        var name = getName();

        var item = new Grant();

        item.setMetadata(new ObjectMetaBuilder()
                .withName(name)
                .withNamespace(namespace)
                .build()
        );

        var clusterRef = new ClusterReference();
        clusterRef.setName(getClusterConnectionName());
        clusterRef.setNamespace(withClusterConnectionNamespace);

        var spec = new GrantSpec();

        spec.setClusterRef(clusterRef);
        spec.setDatabase(getDatabase());
        spec.setRole(getRole());

        spec.setObjectType(withObjectType);
        spec.setObjects(withObjects);

        if (withObjectType != GrantObjectType.DATABASE
                || withSchema != null
        ) {
            spec.setSchema(getSchema());
        }

        if ((withObjectType != GrantObjectType.DATABASE && withObjectType == GrantObjectType.SCHEMA)
                || withObjects != null
        ) {
            spec.setObjects(withObjects);
        }

        spec.setPrivileges(withPrivileges);

        item.setSpec(spec);

        kubernetesClient.resources(Grant.class)
                .inNamespace(namespace)
                .resource(item)
                .serverSideApply();

        //noinspection ConstantConditions
        return kubernetesClient.resources(Grant.class)
                .inNamespace(namespace)
                .withName(name)
                .waitUntilCondition(
                        grant -> grant.getStatus() != null,
                        10,
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

        return randomKubernetesNameSuffix("test-grant");
    }

    private String getClusterConnectionName() {
        return Objects.requireNonNullElse(
                withClusterConnectionName,
                "test-cluster-connection"
        );
    }

    private String getDatabase() {
        if (withDatabase != null) {
            return withDatabase;
        }

        return given.one()
                .database()
                .returnFirst()
                .getSpec()
                .getName();
    }

    private String getRole() {
        if (withRole != null) {
            return withRole;
        }

        return given.one()
                .role()
                .returnFirst()
                .getSpec()
                .getName();
    }

    private String getSchema() {
        if (withSchema != null) {
            return withSchema;
        }

        return given.one()
                .schema()
                .returnFirst()
                .getSpec()
                .getName();
    }
}
