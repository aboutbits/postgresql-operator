package it.aboutbits.postgresql.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.client.KubernetesClient;
import it.aboutbits.postgresql.crd.clusterconnection.ClusterConnection;
import it.aboutbits.postgresql.crd.clusterconnection.ClusterConnectionSpec;
import jakarta.inject.Singleton;
import org.jspecify.annotations.NullMarked;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

@Singleton
@NullMarked
public final class KubernetesService {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static final String SECRET_TYPE_BASIC_AUTH = "kubernetes.io/basic-auth";
    public static final String SECRET_DATA_BASIC_AUTH_USERNAME_KEY = "username";
    public static final String SECRET_DATA_BASIC_AUTH_PASSWORD_KEY = "password";

    public Credentials getAdminCredentials(
            KubernetesClient kubernetesClient,
            ClusterConnection clusterConnection
    ) {
        var spec = clusterConnection.getSpec();
        if (spec.getAdminSecretRef() != null) {
            return getSecretRefCredentials(
                    kubernetesClient,
                    spec.getAdminSecretRef(),
                    clusterConnection.getMetadata().getNamespace()
            );
        } else if (spec.getAdminSecretFileRef() != null) {
            return getSecretFileRefCredentials(spec.getAdminSecretFileRef());
        }

        throw new IllegalStateException("Exactly one of 'adminSecretRef' or 'adminSecretFileRef' must be provided");
    }

    public Credentials getSecretFileRefCredentials(FileRef fileRef) {
        var path = Path.of(fileRef.getPath());

        if (!Files.exists(path)) {
            throw new IllegalStateException("Credential file not found [path=%s]".formatted(path));
        }

        try {
            var content = Files.readString(path);
            var json = OBJECT_MAPPER.readTree(content);

            var usernameNode = json.get(SECRET_DATA_BASIC_AUTH_USERNAME_KEY);
            var username = usernameNode != null && !usernameNode.isNull()
                    ? usernameNode.asText()
                    : null;
            if (username == null) {
                throw new IllegalStateException("Credential file is missing required field '%s' [path=%s]".formatted(
                        SECRET_DATA_BASIC_AUTH_USERNAME_KEY,
                        path
                ));
            }

            var passwordNode = json.get(SECRET_DATA_BASIC_AUTH_PASSWORD_KEY);
            if (passwordNode == null || passwordNode.isNull()) {
                throw new IllegalStateException("Credential file is missing required field '%s' [path=%s]".formatted(
                        SECRET_DATA_BASIC_AUTH_PASSWORD_KEY,
                        path
                ));
            }

            return new Credentials(username, passwordNode.asText());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read Credential file [path=%s]".formatted(path), e);
        }
    }

    public Credentials getSecretRefCredentials(
            KubernetesClient kubernetesClient,
            ResourceRef secretRef,
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
            throw new IllegalStateException("Secret reference not found [secret.namespace=%s, secret.name=%s]".formatted(
                    secretNamespace,
                    secretName
            ));
        }

        if (!secret.getType().equals(SECRET_TYPE_BASIC_AUTH)) {
            throw new IllegalArgumentException("The Secret reference is of the wrong type [secret.namespace=%s, secret.name=%s, expected.secret.type=%s, actual.secret.type=%s]".formatted(
                    secretNamespace,
                    secretName,
                    SECRET_TYPE_BASIC_AUTH,
                    secret.getType()
            ));
        }

        var data = secret.getData();
        if (data == null || data.isEmpty()) {
            throw new IllegalStateException("The Secret reference has no data set [secret.namespace=%s, secret.name=%s]".formatted(
                    secretNamespace,
                    secretName
            ));
        }

        var usernameBase64 = data.get(SECRET_DATA_BASIC_AUTH_USERNAME_KEY);
        var username = usernameBase64 == null
                ? null
                : new String(
                        Base64.getDecoder().decode(usernameBase64),
                        Charset.defaultCharset()
                );

        var passwordBase64 = data.get(SECRET_DATA_BASIC_AUTH_PASSWORD_KEY);
        if (passwordBase64 == null) {
            throw new IllegalStateException("The Secret reference is missing required data password [secret.namespace=%s, secret.name=%s]".formatted(
                    secretNamespace,
                    secretName
            ));
        }
        var password = new String(
                Base64.getDecoder().decode(passwordBase64),
                Charset.defaultCharset()
        );

        return new Credentials(
                username,
                password
        );
    }
}
