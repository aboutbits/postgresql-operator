package it.aboutbits.postgresql.core;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.NamespaceableResource;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import it.aboutbits.postgresql.crd.clusterconnection.ClusterConnection;
import it.aboutbits.postgresql.crd.clusterconnection.ClusterConnectionSpec;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@NullMarked
class KubernetesServiceTest {
    private final KubernetesService service = new KubernetesService();

    @TempDir
    Path tempDir;

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

            var fileRef = new ResourceFileRef();
            fileRef.setPath(file.toString());

            // when
            var result = service.getSecretFileRefCredentials(fileRef);

            // then
            assertThat(result.username()).isEqualTo("admin");
            assertThat(result.password()).isEqualTo("s3cret");
        }

        @Test
        @DisplayName("when username key missing, should return null username")
        void whenUsernameKeyMissing_shouldReturnNullUsername() throws IOException {
            // given
            var file = tempDir.resolve("secret.json");
            Files.writeString(file, """
                    {"password": "s3cret"}
                    """);

            var fileRef = new ResourceFileRef();
            fileRef.setPath(file.toString());

            // when
            var result = service.getSecretFileRefCredentials(fileRef);

            // then
            assertThat(result.username()).isNull();
            assertThat(result.password()).isEqualTo("s3cret");
        }

        @Test
        @DisplayName("when username is null, should return null username")
        void whenUsernameIsNull_shouldReturnNullUsername() throws IOException {
            // given
            var file = tempDir.resolve("secret.json");
            Files.writeString(file, """
                    {"username": null, "password": "s3cret"}
                    """);

            var fileRef = new ResourceFileRef();
            fileRef.setPath(file.toString());

            // when
            var result = service.getSecretFileRefCredentials(fileRef);

            // then
            assertThat(result.username()).isNull();
            assertThat(result.password()).isEqualTo("s3cret");
        }

        @Test
        @DisplayName("when password key missing, should throw")
        void whenPasswordKeyMissing_shouldThrow() throws IOException {
            // given
            var file = tempDir.resolve("secret.json");
            Files.writeString(file, """
                    {"username": "admin"}
                    """);

            var fileRef = new ResourceFileRef();
            fileRef.setPath(file.toString());

            // when / then
            assertThatThrownBy(() -> service.getSecretFileRefCredentials(fileRef))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("missing required field 'password'");
        }

        @Test
        @DisplayName("when password is null, should throw")
        void whenPasswordIsNull_shouldThrow() throws IOException {
            // given
            var file = tempDir.resolve("secret.json");
            Files.writeString(file, """
                    {"username": "admin", "password": null}
                    """);

            var fileRef = new ResourceFileRef();
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
            var fileRef = new ResourceFileRef();
            fileRef.setPath(tempDir.resolve("nonexistent.json").toString());

            // when / then
            assertThatThrownBy(() -> service.getSecretFileRefCredentials(fileRef))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("file not found");
        }

        @Test
        @DisplayName("when invalid JSON, should throw")
        void whenInvalidJson_shouldThrow() throws IOException {
            // given
            var file = tempDir.resolve("secret.json");
            Files.writeString(file, "not valid json {{{");

            var fileRef = new ResourceFileRef();
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
            var secretRef = new ResourceRef();
            secretRef.setNamespace("my-ns");
            secretRef.setName("my-secret");

            var secret = new Secret();
            secret.setType(KubernetesService.SECRET_TYPE_BASIC_AUTH);
            secret.setData(Map.of(
                    "username", base64("admin"),
                    "password", base64("s3cret")
            ));

            var client = mockKubernetesClient("my-ns", "my-secret", secret);

            // when
            var result = service.getSecretRefCredentials(client, secretRef, "default-ns");

            // then
            assertThat(result.username()).isEqualTo("admin");
            assertThat(result.password()).isEqualTo("s3cret");
        }

        @Test
        @DisplayName("when secret not found, should throw")
        void whenSecretNotFound_shouldThrow() {
            // given
            var secretRef = new ResourceRef();
            secretRef.setNamespace("my-ns");
            secretRef.setName("my-secret");

            var client = mockKubernetesClient("my-ns", "my-secret", null);

            // when / then
            assertThatThrownBy(() -> service.getSecretRefCredentials(client, secretRef, "default-ns"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Secret reference not found");
        }

        @Test
        @DisplayName("when secret wrong type, should throw")
        void whenSecretWrongType_shouldThrow() {
            // given
            var secretRef = new ResourceRef();
            secretRef.setNamespace("my-ns");
            secretRef.setName("my-secret");

            var secret = new Secret();
            secret.setType("Opaque");
            secret.setData(Map.of("password", base64("s3cret")));

            var client = mockKubernetesClient("my-ns", "my-secret", secret);

            // when / then
            assertThatThrownBy(() -> service.getSecretRefCredentials(client, secretRef, "default-ns"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("wrong type");
        }

        @Test
        @DisplayName("when secret has no data, should throw")
        void whenSecretHasNoData_shouldThrow() {
            // given
            var secretRef = new ResourceRef();
            secretRef.setNamespace("my-ns");
            secretRef.setName("my-secret");

            var secret = new Secret();
            secret.setType(KubernetesService.SECRET_TYPE_BASIC_AUTH);
            secret.setData(null);

            var client = mockKubernetesClient("my-ns", "my-secret", secret);

            // when / then
            assertThatThrownBy(() -> service.getSecretRefCredentials(client, secretRef, "default-ns"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("has no data set");
        }

        @Test
        @DisplayName("when secret missing password, should throw")
        void whenSecretMissingPassword_shouldThrow() {
            // given
            var secretRef = new ResourceRef();
            secretRef.setNamespace("my-ns");
            secretRef.setName("my-secret");

            var secret = new Secret();
            secret.setType(KubernetesService.SECRET_TYPE_BASIC_AUTH);
            secret.setData(Map.of("username", base64("admin")));

            var client = mockKubernetesClient("my-ns", "my-secret", secret);

            // when / then
            assertThatThrownBy(() -> service.getSecretRefCredentials(client, secretRef, "default-ns"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("missing required data password");
        }

        @Test
        @DisplayName("when namespace null, should use default namespace")
        void whenNamespaceNull_shouldUseDefaultNamespace() {
            // given
            var secretRef = new ResourceRef();
            secretRef.setNamespace(null);
            secretRef.setName("my-secret");

            var secret = new Secret();
            secret.setType(KubernetesService.SECRET_TYPE_BASIC_AUTH);
            secret.setData(Map.of(
                    "username", base64("admin"),
                    "password", base64("s3cret")
            ));

            var client = mockKubernetesClient("default-ns", "my-secret", secret);

            // when
            var result = service.getSecretRefCredentials(client, secretRef, "default-ns");

            // then
            assertThat(result.username()).isEqualTo("admin");
            assertThat(result.password()).isEqualTo("s3cret");
        }

        @Test
        @DisplayName("when secret has empty data map, should throw")
        void whenSecretHasEmptyDataMap_shouldThrow() {
            // given
            var secretRef = new ResourceRef();
            secretRef.setNamespace("my-ns");
            secretRef.setName("my-secret");

            var secret = new Secret();
            secret.setType(KubernetesService.SECRET_TYPE_BASIC_AUTH);
            secret.setData(Map.of());

            var client = mockKubernetesClient("my-ns", "my-secret", secret);

            // when / then
            assertThatThrownBy(() -> service.getSecretRefCredentials(client, secretRef, "default-ns"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("has no data set");
        }

        @Test
        @DisplayName("when secret has username only (no password), should throw")
        void whenSecretHasUsernameOnly_shouldThrow() {
            // given
            var secretRef = new ResourceRef();
            secretRef.setNamespace("my-ns");
            secretRef.setName("my-secret");

            var secret = new Secret();
            secret.setType(KubernetesService.SECRET_TYPE_BASIC_AUTH);
            secret.setData(Map.of("username", base64("admin")));

            var client = mockKubernetesClient("my-ns", "my-secret", secret);

            // when / then
            assertThatThrownBy(() -> service.getSecretRefCredentials(client, secretRef, "default-ns"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("missing required data password");
        }

        @Test
        @DisplayName("when secret has password only (no username), should return null username")
        void whenSecretHasPasswordOnly_shouldReturnNullUsername() {
            // given
            var secretRef = new ResourceRef();
            secretRef.setNamespace("my-ns");
            secretRef.setName("my-secret");

            var secret = new Secret();
            secret.setType(KubernetesService.SECRET_TYPE_BASIC_AUTH);
            secret.setData(Map.of("password", base64("s3cret")));

            var client = mockKubernetesClient("my-ns", "my-secret", secret);

            // when
            var result = service.getSecretRefCredentials(client, secretRef, "default-ns");

            // then
            assertThat(result.username()).isNull();
            assertThat(result.password()).isEqualTo("s3cret");
        }
    }

    @Nested
    class GetCredentialsDispatcher {
        @Test
        @DisplayName("when only adminSecretRef set, should delegate to secret ref")
        void whenOnlyAdminSecretRefSet_shouldDelegateToSecretRef() {
            // given
            var secretRef = new ResourceRef();
            secretRef.setNamespace("my-ns");
            secretRef.setName("my-secret");

            var spec = new ClusterConnectionSpec();
            spec.setAdminSecretRef(secretRef);

            var clusterConnection = buildClusterConnection(spec, "cr-ns");

            var secret = new Secret();
            secret.setType(KubernetesService.SECRET_TYPE_BASIC_AUTH);
            secret.setData(Map.of(
                    "username", base64("admin"),
                    "password", base64("s3cret")
            ));

            var client = mockKubernetesClient("my-ns", "my-secret", secret);

            // when
            var result = service.getSecretRefCredentials(client, clusterConnection);

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

            var fileRef = new ResourceFileRef();
            fileRef.setPath(file.toString());

            var spec = new ClusterConnectionSpec();
            spec.setAdminSecretFileRef(fileRef);

            var clusterConnection = buildClusterConnection(spec, "cr-ns");

            var client = mock(KubernetesClient.class);

            // when
            var result = service.getSecretRefCredentials(client, clusterConnection);

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

            var client = mock(KubernetesClient.class);

            // when / then
            assertThatThrownBy(() -> service.getSecretRefCredentials(client, clusterConnection))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Exactly one of");
        }

        @Test
        @DisplayName("when adminSecretRef set, should use its namespace over CR namespace")
        void whenAdminSecretRefHasNamespace_shouldUseSecretRefNamespace() {
            // given
            var secretRef = new ResourceRef();
            secretRef.setNamespace("explicit-ns");
            secretRef.setName("my-secret");

            var spec = new ClusterConnectionSpec();
            spec.setAdminSecretRef(secretRef);

            var clusterConnection = buildClusterConnection(spec, "cr-ns");

            var secret = new Secret();
            secret.setType(KubernetesService.SECRET_TYPE_BASIC_AUTH);
            secret.setData(Map.of(
                    "username", base64("admin"),
                    "password", base64("s3cret")
            ));

            var client = mockKubernetesClient("explicit-ns", "my-secret", secret);

            // when
            var result = service.getSecretRefCredentials(client, clusterConnection);

            // then
            assertThat(result.username()).isEqualTo("admin");
            assertThat(result.password()).isEqualTo("s3cret");
        }

        @Test
        @DisplayName("when adminSecretRef has null namespace, should fall back to CR namespace")
        void whenAdminSecretRefHasNullNamespace_shouldUseCrNamespace() {
            // given
            var secretRef = new ResourceRef();
            secretRef.setNamespace(null);
            secretRef.setName("my-secret");

            var spec = new ClusterConnectionSpec();
            spec.setAdminSecretRef(secretRef);

            var clusterConnection = buildClusterConnection(spec, "cr-ns");

            var secret = new Secret();
            secret.setType(KubernetesService.SECRET_TYPE_BASIC_AUTH);
            secret.setData(Map.of(
                    "username", base64("admin"),
                    "password", base64("s3cret")
            ));

            var client = mockKubernetesClient("cr-ns", "my-secret", secret);

            // when
            var result = service.getSecretRefCredentials(client, clusterConnection);

            // then
            assertThat(result.username()).isEqualTo("admin");
            assertThat(result.password()).isEqualTo("s3cret");
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

    @SuppressWarnings("unchecked")
    private static KubernetesClient mockKubernetesClient(
            String namespace,
            String name,
            @Nullable Secret secret
    ) {
        var client = mock(KubernetesClient.class);
        var secrets = mock(MixedOperation.class);
        var nsOp = mock(NonNamespaceOperation.class);
        var resource = mock(NamespaceableResource.class);

        when(client.secrets()).thenReturn(secrets);
        when(secrets.inNamespace(namespace)).thenReturn(nsOp);
        when(nsOp.withName(name)).thenReturn(resource);
        when(resource.get()).thenReturn(secret);

        return client;
    }

    private static String base64(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(Charset.defaultCharset()));
    }
}
