package it.aboutbits.postgresql.core;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import it.aboutbits.postgresql.crd.clusterconnection.ClusterConnection;
import it.aboutbits.postgresql.crd.clusterconnection.ClusterConnectionSpec;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@NullMarked
@EnableKubernetesMockClient(crud = true)
class KubernetesServiceTest {
    private final KubernetesService service = new KubernetesService();

    @SuppressWarnings("NullAway.Init")
    static KubernetesClient client;

    @TempDir
    Path tempDir;

    @BeforeEach
    void clearSecrets() {
        client.secrets().inAnyNamespace().delete();
    }

    @Nested
    class GetSecretFileRefCredentials {
        @Test
        @DisplayName("when both username and password present, should return credentials")
        void whenBothUsernameAndPassword_shouldReturnCredentials() throws IOException {
            // given
            var file = tempDir.resolve("secret.json");
            Files.writeString(file, """
                    {"username": "admin", "password": "s3cret"}
                    """);

            var fileRef = new FileRef();
            fileRef.setPath(file.toString());

            // when
            var result = service.getSecretFileRefCredentials(fileRef);

            // then
            assertThat(result.username()).isEqualTo("admin");
            assertThat(result.password()).isEqualTo("s3cret");
        }

        @ParameterizedTest(name = "when username {0}, should throw")
        @ValueSource(strings = {
                "{\"password\": \"s3cret\"}",
                "{\"username\": null, \"password\": \"s3cret\"}"
        })
        void whenUsernameMissingOrNull_shouldThrow(String json) throws IOException {
            // given
            var file = tempDir.resolve("secret.json");
            Files.writeString(file, json);

            var fileRef = new FileRef();
            fileRef.setPath(file.toString());

            // when / then
            assertThatThrownBy(() -> service.getSecretFileRefCredentials(fileRef))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("missing required field 'username'");
        }

        @ParameterizedTest(name = "when password {0}, should throw")
        @ValueSource(strings = {
                "{\"username\": \"admin\"}",
                "{\"username\": \"admin\", \"password\": null}"
        })
        void whenPasswordMissingOrNull_shouldThrow(String json) throws IOException {
            // given
            var file = tempDir.resolve("secret.json");
            Files.writeString(file, json);

            var fileRef = new FileRef();
            fileRef.setPath(file.toString());

            // when / then
            assertThatThrownBy(() -> service.getSecretFileRefCredentials(fileRef))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("missing required field 'password'");
        }

        @Test
        @DisplayName("when file not found, should throw")
        void whenFileNotFound_shouldThrow() {
            // given
            var fileRef = new FileRef();
            fileRef.setPath(tempDir.resolve("nonexistent.json").toString());

            // when / then
            assertThatThrownBy(() -> service.getSecretFileRefCredentials(fileRef))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Credential file not found");
        }

        @Test
        @DisplayName("when invalid JSON, should throw")
        void whenInvalidJson_shouldThrow() throws IOException {
            // given
            var file = tempDir.resolve("secret.json");
            Files.writeString(file, "not valid json {{{");

            var fileRef = new FileRef();
            fileRef.setPath(file.toString());

            // when / then
            assertThatThrownBy(() -> service.getSecretFileRefCredentials(fileRef))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Failed to read");
        }
    }

    @Nested
    class GetSecretRefCredentials {
        @Test
        @DisplayName("when secret exists, should return decoded credentials")
        void whenSecretExists_shouldReturnDecodedCredentials() {
            // given
            var secret = basicAuthSecret("my-ns", "my-secret", "admin", "s3cret");
            client.secrets().inNamespace("my-ns").resource(secret).create();

            // when
            var result = service.getSecretRefCredentials(client, secretRef("my-ns", "my-secret"), "default-ns");

            // then
            assertThat(result.username()).isEqualTo("admin");
            assertThat(result.password()).isEqualTo("s3cret");
        }

        @Test
        @DisplayName("when secret not found, should throw")
        void whenSecretNotFound_shouldThrow() {
            // given — no secret created

            // when / then
            assertThatThrownBy(() -> service.getSecretRefCredentials(client, secretRef("my-ns", "my-secret"), "default-ns"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Secret reference not found");
        }

        @Test
        @DisplayName("when secret wrong type, should throw")
        void whenSecretWrongType_shouldThrow() {
            // given
            var secret = new SecretBuilder()
                    .withNewMetadata().withNamespace("my-ns").withName("my-secret").endMetadata()
                    .withType("Opaque")
                    .addToData("password", base64("s3cret"))
                    .build();
            client.secrets().inNamespace("my-ns").resource(secret).create();

            // when / then
            assertThatThrownBy(() -> service.getSecretRefCredentials(client, secretRef("my-ns", "my-secret"), "default-ns"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("wrong type");
        }

        @Test
        @DisplayName("when secret has no data, should throw")
        void whenSecretHasNoData_shouldThrow() {
            // given
            var secret = new SecretBuilder()
                    .withNewMetadata().withNamespace("my-ns").withName("my-secret").endMetadata()
                    .withType(KubernetesService.SECRET_TYPE_BASIC_AUTH)
                    .build();
            client.secrets().inNamespace("my-ns").resource(secret).create();

            // when / then
            assertThatThrownBy(() -> service.getSecretRefCredentials(client, secretRef("my-ns", "my-secret"), "default-ns"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("has no data set");
        }

        @Test
        @DisplayName("when secret missing password, should throw")
        void whenSecretMissingPassword_shouldThrow() {
            // given
            var secret = new SecretBuilder()
                    .withNewMetadata().withNamespace("my-ns").withName("my-secret").endMetadata()
                    .withType(KubernetesService.SECRET_TYPE_BASIC_AUTH)
                    .addToData("username", base64("admin"))
                    .build();
            client.secrets().inNamespace("my-ns").resource(secret).create();

            // when / then
            assertThatThrownBy(() -> service.getSecretRefCredentials(client, secretRef("my-ns", "my-secret"), "default-ns"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("missing required data password");
        }

        @Test
        @DisplayName("when namespace null, should use default namespace")
        void whenNamespaceNull_shouldUseDefaultNamespace() {
            // given
            var secret = basicAuthSecret("default-ns", "my-secret", "admin", "s3cret");
            client.secrets().inNamespace("default-ns").resource(secret).create();

            // when
            var result = service.getSecretRefCredentials(client, secretRef(null, "my-secret"), "default-ns");

            // then
            assertThat(result.username()).isEqualTo("admin");
            assertThat(result.password()).isEqualTo("s3cret");
        }

        @Test
        @DisplayName("when secret has empty data map, should throw")
        void whenSecretHasEmptyDataMap_shouldThrow() {
            // given
            var secret = new SecretBuilder()
                    .withNewMetadata().withNamespace("my-ns").withName("my-secret").endMetadata()
                    .withType(KubernetesService.SECRET_TYPE_BASIC_AUTH)
                    .withData(Map.of())
                    .build();
            client.secrets().inNamespace("my-ns").resource(secret).create();

            // when / then
            assertThatThrownBy(() -> service.getSecretRefCredentials(client, secretRef("my-ns", "my-secret"), "default-ns"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("has no data set");
        }

        @Test
        @DisplayName("when secret has username only (no password), should throw")
        void whenSecretHasUsernameOnly_shouldThrow() {
            // given
            var secret = new SecretBuilder()
                    .withNewMetadata().withNamespace("my-ns").withName("my-secret").endMetadata()
                    .withType(KubernetesService.SECRET_TYPE_BASIC_AUTH)
                    .addToData("username", base64("admin"))
                    .build();
            client.secrets().inNamespace("my-ns").resource(secret).create();

            // when / then
            assertThatThrownBy(() -> service.getSecretRefCredentials(client, secretRef("my-ns", "my-secret"), "default-ns"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("missing required data password");
        }

        @Test
        @DisplayName("when secret has password only (no username), should return null username")
        void whenSecretHasPasswordOnly_shouldReturnNullUsername() {
            // given
            var secret = new SecretBuilder()
                    .withNewMetadata().withNamespace("my-ns").withName("my-secret").endMetadata()
                    .withType(KubernetesService.SECRET_TYPE_BASIC_AUTH)
                    .addToData("password", base64("s3cret"))
                    .build();
            client.secrets().inNamespace("my-ns").resource(secret).create();

            // when
            var result = service.getSecretRefCredentials(client, secretRef("my-ns", "my-secret"), "default-ns");

            // then
            assertThat(result.username()).isNull();
            assertThat(result.password()).isEqualTo("s3cret");
        }
    }

    @Nested
    class GetAdminCredentials {
        @Test
        @DisplayName("when only adminSecretRef set, should delegate to secret ref")
        void whenOnlyAdminSecretRefSet_shouldDelegateToSecretRef() {
            // given
            var secret = basicAuthSecret("my-ns", "my-secret", "admin", "s3cret");
            client.secrets().inNamespace("my-ns").resource(secret).create();

            var spec = new ClusterConnectionSpec();
            spec.setAdminSecretRef(secretRef("my-ns", "my-secret"));

            var clusterConnection = buildClusterConnection(spec, "cr-ns");

            // when
            var result = service.getAdminCredentials(client, clusterConnection);

            // then
            assertThat(result.username()).isEqualTo("admin");
            assertThat(result.password()).isEqualTo("s3cret");
        }

        @Test
        @DisplayName("when only adminSecretFileRef set, should delegate to file ref")
        void whenOnlyAdminSecretFileRefSet_shouldDelegateToFileRef() throws IOException {
            // given
            var file = tempDir.resolve("secret.json");
            Files.writeString(file, """
                    {"username": "file-admin", "password": "file-s3cret"}
                    """);

            var fileRef = new FileRef();
            fileRef.setPath(file.toString());

            var spec = new ClusterConnectionSpec();
            spec.setAdminSecretFileRef(fileRef);

            var clusterConnection = buildClusterConnection(spec, "cr-ns");

            // when
            var result = service.getAdminCredentials(client, clusterConnection);

            // then
            assertThat(result.username()).isEqualTo("file-admin");
            assertThat(result.password()).isEqualTo("file-s3cret");
        }

        @Test
        @DisplayName("when neither ref set, should throw")
        void whenNeitherRefSet_shouldThrow() {
            // given
            var spec = new ClusterConnectionSpec();

            var clusterConnection = buildClusterConnection(spec, "cr-ns");

            // when / then
            assertThatThrownBy(() -> service.getAdminCredentials(client, clusterConnection))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Exactly one of");
        }
    }

    private static ClusterConnection buildClusterConnection(ClusterConnectionSpec spec, String namespace) {
        var meta = new ObjectMeta();
        meta.setNamespace(namespace);

        var clusterConnection = new ClusterConnection();
        clusterConnection.setSpec(spec);
        clusterConnection.setMetadata(meta);

        return clusterConnection;
    }

    private static ResourceRef secretRef(@Nullable String namespace, String name) {
        var ref = new ResourceRef();
        ref.setNamespace(namespace);
        ref.setName(name);
        return ref;
    }

    private static Secret basicAuthSecret(String namespace, String name,
            @Nullable String username, String password) {
        var builder = new SecretBuilder()
                .withNewMetadata().withNamespace(namespace).withName(name).endMetadata()
                .withType(KubernetesService.SECRET_TYPE_BASIC_AUTH)
                .addToData("password", base64(password));
        if (username != null) {
            builder.addToData("username", base64(username));
        }
        return builder.build();
    }

    private static String base64(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(Charset.defaultCharset()));
    }
}
