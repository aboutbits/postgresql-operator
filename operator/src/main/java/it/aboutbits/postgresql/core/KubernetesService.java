package it.aboutbits.postgresql.core;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.client.KubernetesClient;
import it.aboutbits.postgresql.crd.clusterconnection.ClusterConnection;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Base64;

@Singleton
@RequiredArgsConstructor
@NullMarked
public final class KubernetesService {
    private final ObjectMapper objectMapper;

    /// The JSON file may carry more keys than we need, for example, the AWS Secrets Manager
    /// format also has `engine`, `host`, `port` and `dbname`. Unknown keys are ignored.
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record FileCredentials(
            @Nullable String username,
            @Nullable String password
    ) {
    }

    public static final String SECRET_TYPE_BASIC_AUTH = "kubernetes.io/basic-auth";
    public static final String SECRET_DATA_BASIC_AUTH_USERNAME_KEY = "username";
    public static final String SECRET_DATA_BASIC_AUTH_PASSWORD_KEY = "password";

    public Credentials getAdminCredentials(
            KubernetesClient kubernetesClient,
            ClusterConnection clusterConnection
    ) {
        var spec = clusterConnection.getSpec();
        if (spec.getAdminSecretRef() != null) {
            var secretRef = spec.getAdminSecretRef();
            var defaultNamespace = clusterConnection.getMetadata().getNamespace();
            var credentials = getSecretRefCredentials(kubernetesClient, secretRef, defaultNamespace);
            if (credentials.username() == null) {
                var secretNamespace = getSecretNamespace(secretRef, defaultNamespace);
                throw new IllegalStateException(
                        "The Secret reference is missing required data username [secret.namespace=%s, secret.name=%s]".formatted(
                                secretNamespace, secretRef.getName()));
            }
            return credentials;
        } else if (spec.getAdminSecretFileRef() != null) {
            return getSecretFileRefCredentials(spec.getAdminSecretFileRef());
        }

        throw new IllegalStateException("Exactly one of 'adminSecretRef' or 'adminSecretFileRef' must be provided");
    }

    public Credentials getSecretFileRefCredentials(FileRef fileRef) {
        var path = Path.of(fileRef.getPath());

        try (var in = Files.newInputStream(path)) {
            var file = objectMapper.readValue(in, FileCredentials.class);
            if (file.username() == null) {
                throw new IllegalStateException(
                        "Credentials file is missing required field 'username' [path=%s]".formatted(path));
            }
            if (file.password() == null) {
                throw new IllegalStateException(
                        "Credentials file is missing required field 'password' [path=%s]".formatted(path));
            }
            return new Credentials(file.username(), file.password());
        } catch (NoSuchFileException e) {
            throw new IllegalStateException(
                    "Credentials file not found [path=%s]".formatted(path), e);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to read the credentials file [path=%s]".formatted(path), e);
        }
    }

    public Credentials getSecretRefCredentials(
            KubernetesClient kubernetesClient,
            ResourceRef secretRef,
            String defaultNamespace
    ) {
        var secretNamespace = getSecretNamespace(secretRef, defaultNamespace);

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

    private String getSecretNamespace(ResourceRef secretRef, String defaultNamespace) {
        return secretRef.getNamespace() != null
                ? secretRef.getNamespace()
                : defaultNamespace;
    }
}
