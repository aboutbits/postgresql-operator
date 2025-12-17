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
        var spec = clusterConnection.getSpec();
        var metadata = clusterConnection.getMetadata();

        var passwordSecretRef = spec.getAdminSecretRef();

        var secretNamespace = passwordSecretRef.getNamespace() != null
                ? passwordSecretRef.getNamespace()
                : clusterConnection.getMetadata().getNamespace();
        var secretName = passwordSecretRef.getName();

        var secret = kubernetesClient.secrets()
                .inNamespace(secretNamespace)
                .withName(secretName)
                .get();

        if (secret == null) {
            throw new IllegalStateException("ClusterConnection SecretRef not found [clusterConnection.namespace=%s, clusterConnection.name=%s, secret.namespace=%s, secret.name=%s]".formatted(
                    metadata.getNamespace(),
                    metadata.getName(),
                    secretNamespace,
                    secretName
            ));
        }

        if (!secret.getType().equals(SECRET_TYPE_BASIC_AUTH)) {
            throw new IllegalArgumentException("The ClusterConnection SecretRef is of the wrong type [clusterConnection.namespace=%s, clusterConnection.name=%s, secret.namespace=%s, secret.name=%s, expected.secret.type=%s, actual.secret.type=%s]".formatted(
                    metadata.getNamespace(),
                    metadata.getName(),
                    secretNamespace,
                    secretName,
                    SECRET_TYPE_BASIC_AUTH,
                    secret.getType()
            ));
        }

        var data = secret.getData();
        if (data == null || data.isEmpty()) {
            throw new IllegalStateException("The ClusterConnection SecretRef has no data set [clusterConnection.namespace=%s, clusterConnection.name=%s, secret.namespace=%s, secret.name=%s]".formatted(
                    metadata.getNamespace(),
                    metadata.getName(),
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
            throw new IllegalStateException("The ClusterConnection SecretRef is missing required data password [clusterConnection.namespace=%s, clusterConnection.name=%s, secret.namespace=%s, secret.name=%s]".formatted(
                    metadata.getNamespace(),
                    metadata.getName(),
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
