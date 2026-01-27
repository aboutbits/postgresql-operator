package it.aboutbits.postgresql._support.testdata.persisted.creator;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import it.aboutbits.postgresql._support.testdata.base.TestDataCreator;
import it.aboutbits.postgresql._support.testdata.persisted.Given;
import it.aboutbits.postgresql.core.ClusterReference;
import it.aboutbits.postgresql.core.Privilege;
import it.aboutbits.postgresql.crd.defaultprivilege.DefaultPrivilege;
import it.aboutbits.postgresql.crd.defaultprivilege.DefaultPrivilegeObjectType;
import it.aboutbits.postgresql.crd.defaultprivilege.DefaultPrivilegeSpec;
import lombok.AccessLevel;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static it.aboutbits.postgresql.core.ReclaimPolicy.DELETE;

@NullMarked
@Setter
@Accessors(fluent = true, chain = true)
public class DefaultPrivilegeCreate extends TestDataCreator<DefaultPrivilege> {
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
    private String withOwner;

    @Nullable
    private String withSchema;

    private DefaultPrivilegeObjectType withObjectType = DefaultPrivilegeObjectType.SCHEMA;

    @Setter(AccessLevel.NONE)
    private List<Privilege> withPrivileges = new ArrayList<>();

    public DefaultPrivilegeCreate(
            int numberOfItems,
            Given given,
            KubernetesClient kubernetesClient
    ) {
        super(numberOfItems);
        this.given = given;
        this.kubernetesClient = kubernetesClient;
    }

    @SuppressWarnings("unused")
    public DefaultPrivilegeCreate withPrivileges(List<Privilege> privileges) {
        this.withPrivileges = privileges;
        return this;
    }

    @SuppressWarnings("unused")
    public DefaultPrivilegeCreate withPrivileges(Privilege... privileges) {
        this.withPrivileges = List.of(privileges);
        return this;
    }

    @SuppressWarnings("unused")
    public DefaultPrivilegeCreate withoutNamespace() {
        withoutNamespace = true;
        return this;
    }

    @Override
    protected DefaultPrivilege create(int index) {
        var namespace = getNamespace();
        var name = getName();

        var item = new DefaultPrivilege();

        item.setMetadata(new ObjectMetaBuilder()
                .withName(name)
                .withNamespace(namespace)
                .build()
        );

        var clusterRef = new ClusterReference();
        clusterRef.setName(getClusterConnectionName());
        clusterRef.setNamespace(withClusterConnectionNamespace);

        var spec = new DefaultPrivilegeSpec();

        spec.setClusterRef(clusterRef);
        spec.setDatabase(getDatabase());
        spec.setRole(getRole());
        spec.setOwner(getOwner());

        spec.setObjectType(withObjectType);

        if (withObjectType != DefaultPrivilegeObjectType.SCHEMA
                || withSchema != null
        ) {
            spec.setSchema(getSchema());
        }

        spec.setPrivileges(withPrivileges);

        item.setSpec(spec);

        kubernetesClient.resources(DefaultPrivilege.class)
                .inNamespace(namespace)
                .resource(item)
                .serverSideApply();

        //noinspection ConstantConditions
        return kubernetesClient.resources(DefaultPrivilege.class)
                .inNamespace(namespace)
                .withName(name)
                .waitUntilCondition(
                        defaultPrivilege -> defaultPrivilege != null && defaultPrivilege.getStatus() != null,
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

        withName = randomKubernetesNameSuffix("test-default-privilege");

        return withName;
    }

    private String getClusterConnectionName() {
        if (withClusterConnectionName != null) {
            return withClusterConnectionName;
        }

        var clusterConnection = given.one()
                .clusterConnection()
                .withName("conn-%s".formatted(getName()))
                .returnFirst();

        withClusterConnectionNamespace = clusterConnection.getMetadata().getNamespace();

        return clusterConnection.getMetadata().getName();
    }

    private String getDatabase() {
        if (withDatabase != null) {
            return withDatabase;
        }

        var item = given.one()
                .database()
                .withClusterConnectionName(getClusterConnectionName())
                .withClusterConnectionNamespace(withClusterConnectionNamespace)
                .withReclaimPolicy(DELETE)
                .returnFirst();

        withDatabase = item.getSpec().getName();

        return withDatabase;
    }

    private String getRole() {
        if (withRole != null) {
            return withRole;
        }

        var item = given.one()
                .role()
                .withClusterConnectionName(getClusterConnectionName())
                .withClusterConnectionNamespace(withClusterConnectionNamespace)
                .returnFirst();

        withRole = item.getSpec().getName();

        return withRole;
    }

    private String getOwner() {
        if (withOwner != null) {
            return withOwner;
        }

        var item = given.one()
                .role()
                .withClusterConnectionName(getClusterConnectionName())
                .withClusterConnectionNamespace(withClusterConnectionNamespace)
                .returnFirst();

        withOwner = item.getSpec().getName();

        return withOwner;
    }

    private String getSchema() {
        if (withSchema != null) {
            return withSchema;
        }

        var clusterConnectionDb = given.one()
                .clusterConnection()
                .withName(getClusterConnectionName() + "-db")
                .withNamespace(withClusterConnectionNamespace)
                .withDatabase(getDatabase())
                .returnFirst();

        var item = given.one()
                .schema()
                .withClusterConnectionName(clusterConnectionDb.getMetadata().getName())
                .withClusterConnectionNamespace(clusterConnectionDb.getMetadata().getNamespace())
                .withReclaimPolicy(DELETE)
                .returnFirst();

        withSchema = item.getSpec().getName();

        return withSchema;
    }
}
