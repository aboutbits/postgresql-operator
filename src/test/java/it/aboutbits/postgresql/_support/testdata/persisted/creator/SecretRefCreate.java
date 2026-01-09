package it.aboutbits.postgresql._support.testdata.persisted.creator;

import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import it.aboutbits.postgresql._support.testdata.base.TestDataCreator;
import it.aboutbits.postgresql.core.SecretRef;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import static it.aboutbits.postgresql.core.KubernetesService.SECRET_DATA_BASIC_AUTH_PASSWORD_KEY;
import static it.aboutbits.postgresql.core.KubernetesService.SECRET_DATA_BASIC_AUTH_USERNAME_KEY;
import static it.aboutbits.postgresql.core.KubernetesService.SECRET_TYPE_BASIC_AUTH;

@NullMarked
public class SecretRefCreate extends TestDataCreator<SecretRef> {
    private final KubernetesClient kubernetesClient;

    @Nullable
    private String withNamespace;
    private boolean withoutNamespace = false;

    @Nullable
    private String withName;

    @Nullable
    private String withUsername;
    private boolean withoutUsername = false;

    @Nullable
    private String withPassword;
    private boolean withoutPassword = false;

    public SecretRefCreate(
            int numberOfItems,
            KubernetesClient kubernetesClient
    ) {
        super(numberOfItems);
        this.kubernetesClient = kubernetesClient;
    }

    @SuppressWarnings("unused")
    public SecretRefCreate withNamespace(String namespace) {
        withNamespace = namespace;
        return this;
    }

    @SuppressWarnings("unused")
    public SecretRefCreate withoutNamespace() {
        withoutNamespace = true;
        return this;
    }

    @SuppressWarnings("unused")
    public SecretRefCreate withName(String name) {
        withName = name;
        return this;
    }

    @SuppressWarnings("unused")
    public SecretRefCreate withUsername(String username) {
        withUsername = username;
        return this;
    }

    @SuppressWarnings("unused")
    public SecretRefCreate withoutUsername() {
        withoutUsername = true;
        return this;
    }

    @SuppressWarnings("unused")
    public SecretRefCreate withPassword(String password) {
        withPassword = password;
        return this;
    }

    @SuppressWarnings("unused")
    public SecretRefCreate withoutPassword() {
        withoutPassword = true;
        return this;
    }

    @Override
    protected SecretRef create(int index) {
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

        var secretRef = new SecretRef();
        secretRef.setName(name);
        secretRef.setNamespace(namespace);

        return secretRef;
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

        return randomKubernetesNameSuffix("test-secret");
    }

    @Nullable
    private String getUsername() {
        if (withoutUsername) {
            return null;
        }

        if (withUsername != null) {
            return withUsername;
        }

        return FAKER.credentials().username();
    }

    @Nullable
    private String getPassword() {
        if (withoutPassword) {
            return null;
        }

        if (withPassword != null) {
            return withPassword;
        }

        return FAKER.credentials().username();
    }
}
