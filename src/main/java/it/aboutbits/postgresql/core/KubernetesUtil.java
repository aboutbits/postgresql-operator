package it.aboutbits.postgresql.core;

import io.fabric8.kubernetes.client.KubernetesClient;
import it.aboutbits.postgresql.crd.connection.ClusterConnection;
import jakarta.enterprise.context.RequestScoped;
import org.jspecify.annotations.NullMarked;

import java.util.Base64;

@NullMarked
@RequestScoped
public final class KubernetesUtil {
    public static final String SECRET_TYPE_BASIC_AUTH = "kubernetes.io/basic-auth";
    public static final String SECRET_DATA_BASIC_AUTH_USERNAME_KEY = "username";
    public static final String SECRET_DATA_BASIC_AUTH_PASSWORD_KEY = "password";

    public static Credentials getSecretRefCredentials(
            KubernetesClient kubernetesClient,
            ClusterConnection clusterConnection
    ) {
        return getSecretRefCredentials(
                kubernetesClient,
                clusterConnection.getSpec().getAdminSecretRef(),
                clusterConnection.getMetadata().getNamespace()
        );
    }

    public static Credentials getSecretRefCredentials(
            KubernetesClient kubernetesClient,
            SecretRef secretRef,
            String defaultNamespace
    ) {
        var secretNamespace = secretRef.getNamespace() != null
                ? secretRef.getNamespace()
                : defaultNamespace;

        var secretName = secretRef.getName();

        var secret = kubernetesClient.secrets()
                .inNamespace(secretNamespace)
                .withName(secretName)
                .get();

        if (secret == null) {
            throw new IllegalStateException("SecretRef not found [secret.namespace=%s, secret.name=%s]".formatted(
                    secretNamespace,
                    secretName
            ));
        }

        if (!secret.getType().equals(SECRET_TYPE_BASIC_AUTH)) {
            throw new IllegalArgumentException("The SecretRef is of the wrong type [secret.namespace=%s, secret.name=%s, expected.secret.type=%s, actual.secret.type=%s]".formatted(
                    secretNamespace,
                    secretName,
                    SECRET_TYPE_BASIC_AUTH,
                    secret.getType()
            ));
        }

        var data = secret.getData();
        if (data == null || data.isEmpty()) {
            throw new IllegalStateException("The SecretRef has no data set [secret.namespace=%s, secret.name=%s]".formatted(
                    secretNamespace,
                    secretName
            ));
        }

        var usernameBase64 = data.get(SECRET_DATA_BASIC_AUTH_USERNAME_KEY);
        var username = usernameBase64 == null
                ? null
                : new String(
                        Base64.getDecoder().decode(usernameBase64)
                );

        var passwordBase64 = data.get(SECRET_DATA_BASIC_AUTH_PASSWORD_KEY);
        if (passwordBase64 == null) {
            throw new IllegalStateException("The SecretRef is missing required data password [secret.namespace=%s, secret.name=%s]".formatted(
                    secretNamespace,
                    secretName
            ));
        }
        var password = new String(
                Base64.getDecoder().decode(passwordBase64)
        );

        return new Credentials(
                username,
                password
        );
    }
}
