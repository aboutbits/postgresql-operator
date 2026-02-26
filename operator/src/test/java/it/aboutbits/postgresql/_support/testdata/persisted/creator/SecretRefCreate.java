package it.aboutbits.postgresql._support.testdata.persisted.creator;

import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import it.aboutbits.postgresql._support.testdata.base.TestDataCreator;
import it.aboutbits.postgresql.core.ResourceRef;
import lombok.AccessLevel;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import static it.aboutbits.postgresql.core.KubernetesService.SECRET_DATA_BASIC_AUTH_PASSWORD_KEY;
import static it.aboutbits.postgresql.core.KubernetesService.SECRET_DATA_BASIC_AUTH_USERNAME_KEY;
import static it.aboutbits.postgresql.core.KubernetesService.SECRET_TYPE_BASIC_AUTH;

@Setter
@Accessors(fluent = true, chain = true)
@NullMarked
public class SecretRefCreate extends TestDataCreator<ResourceRef> {
    private final KubernetesClient kubernetesClient;

    private @Nullable String withNamespace;

    @Setter(AccessLevel.NONE)
    private boolean withoutNamespace = false;

    private @Nullable String withName;

    private @Nullable String withUsername;

    @Setter(AccessLevel.NONE)
    private boolean withoutUsername = false;

    private @Nullable String withPassword;

    @Setter(AccessLevel.NONE)
    private boolean withoutPassword = false;

    public SecretRefCreate(
            int numberOfItems,
            KubernetesClient kubernetesClient
    ) {
        super(numberOfItems);
        this.kubernetesClient = kubernetesClient;
    }

    @SuppressWarnings("unused")
    public SecretRefCreate withoutNamespace() {
        withoutNamespace = true;
        return this;
    }

    @SuppressWarnings("unused")
    public SecretRefCreate withoutUsername() {
        withoutUsername = true;
        return this;
    }

    @SuppressWarnings("unused")
    public SecretRefCreate withoutPassword() {
        withoutPassword = true;
        return this;
    }

    @Override
    protected ResourceRef create(int index) {
        var namespace = getNamespace();
        var name = getName();

        var secret = new SecretBuilder()
                .withNewMetadata()
                .withNamespace(namespace)
                .withName(name)
                .endMetadata()
                .withType(SECRET_TYPE_BASIC_AUTH)
                .addToStringData(SECRET_DATA_BASIC_AUTH_USERNAME_KEY, getUsername())
                .addToStringData(SECRET_DATA_BASIC_AUTH_PASSWORD_KEY, getPassword())
                .build();

        kubernetesClient.secrets()
                .inNamespace(namespace)
                .resource(secret)
                .serverSideApply();

        var secretRef = new ResourceRef();
        secretRef.setName(name);
        secretRef.setNamespace(namespace);

        return secretRef;
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

        withName = randomKubernetesNameSuffix("test-secret");

        return withName;
    }

    private @Nullable String getUsername() {
        if (withoutUsername) {
            return null;
        }

        if (withUsername != null) {
            return withUsername;
        }

        withUsername = FAKER.credentials().username();

        return withUsername;
    }

    private @Nullable String getPassword() {
        if (withoutPassword) {
            return null;
        }

        if (withPassword != null) {
            return withPassword;
        }

        withPassword = FAKER.credentials().password(8, 16, true, true);

        return withPassword;
    }
}
