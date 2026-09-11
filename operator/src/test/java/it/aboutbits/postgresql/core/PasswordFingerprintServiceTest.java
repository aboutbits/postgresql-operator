package it.aboutbits.postgresql.core;

import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import it.aboutbits.postgresql.crd.role.PasswordEncryption;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@NullMarked
@EnableKubernetesMockClient(crud = true)
class PasswordFingerprintServiceTest {
    private static final String SECRET_NAME = "test-password-fingerprint-key";

    @SuppressWarnings("NullAway.Init")
    static KubernetesClient client;

    @BeforeEach
    void clearSecrets() {
        client.secrets().inAnyNamespace().delete();
    }

    /// Each instance has its own key cache, like a fresh operator process.
    private static PasswordFingerprintService newService() {
        var service = new PasswordFingerprintService(client);
        service.secretName = SECRET_NAME;

        return service;
    }

    @Nested
    class KeySecret {
        @Test
        @DisplayName("When the key Secret does not exist, should create it with a random 32 byte key")
        void whenSecretMissing_shouldCreateIt() {
            // given
            var service = newService();

            // when
            service.fingerprint("password", PasswordEncryption.SCRAM_SHA_256);

            // then
            var secret = client.secrets()
                    .inNamespace(client.getNamespace())
                    .withName(SECRET_NAME)
                    .get();

            assertThat(secret).isNotNull();
            assertThat(secret.getType()).isEqualTo("Opaque");
            assertThat(secret.getData()).containsOnlyKeys(PasswordFingerprintService.SECRET_DATA_KEY);

            var key = Base64.getDecoder().decode(secret.getData().get(PasswordFingerprintService.SECRET_DATA_KEY));
            assertThat(key).hasSize(32);
        }

        @Test
        @DisplayName("When the key Secret exists, should reuse its key")
        void whenSecretExists_shouldReuseKey() {
            // given
            var fingerprint = newService().fingerprint("password", PasswordEncryption.SCRAM_SHA_256);

            // when: a second operator process starts
            var fingerprintOfSecondProcess = newService().fingerprint("password", PasswordEncryption.SCRAM_SHA_256);

            // then
            assertThat(fingerprintOfSecondProcess).isEqualTo(fingerprint);
        }

        @Test
        @DisplayName("When the key Secret is lost, should create a new key and the fingerprints change")
        void whenSecretLost_shouldCreateNewKey() {
            // given
            var fingerprint = newService().fingerprint("password", PasswordEncryption.SCRAM_SHA_256);

            client.secrets()
                    .inNamespace(client.getNamespace())
                    .withName(SECRET_NAME)
                    .delete();

            // when
            var fingerprintWithNewKey = newService().fingerprint("password", PasswordEncryption.SCRAM_SHA_256);

            // then
            assertThat(fingerprintWithNewKey).isNotEqualTo(fingerprint);
        }

        @Test
        @DisplayName("When the key Secret has no 'key' entry, should fail with a message that names the Secret")
        void whenSecretHasNoKeyEntry_shouldFail() {
            // given
            client.secrets()
                    .inNamespace(client.getNamespace())
                    .resource(new SecretBuilder()
                            .withNewMetadata()
                            .withName(SECRET_NAME)
                            .endMetadata()
                            .addToData("other", Base64.getEncoder().encodeToString("value".getBytes(StandardCharsets.UTF_8)))
                            .build()
                    )
                    .create();

            var service = newService();

            // when / then
            assertThatThrownBy(() -> service.fingerprint("password", PasswordEncryption.SCRAM_SHA_256))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("missing required data 'key'")
                    .hasMessageContaining(SECRET_NAME);
        }
    }

    @Nested
    class Fingerprint {
        @Test
        @DisplayName("When the same password and encryption are given, should return the same fingerprint")
        void whenSameInput_shouldReturnSameFingerprint() {
            // given
            var service = newService();

            // when
            var first = service.fingerprint("password", PasswordEncryption.SCRAM_SHA_256);
            var second = service.fingerprint("password", PasswordEncryption.SCRAM_SHA_256);

            // then
            assertThat(second).isEqualTo(first);
        }

        @Test
        @DisplayName("When the password changes, should return a different fingerprint")
        void whenPasswordChanges_shouldReturnDifferentFingerprint() {
            // given
            var service = newService();

            // when
            var first = service.fingerprint("password", PasswordEncryption.SCRAM_SHA_256);
            var second = service.fingerprint("other-password", PasswordEncryption.SCRAM_SHA_256);

            // then
            assertThat(second).isNotEqualTo(first);
        }

        @Test
        @DisplayName("When the encryption changes, should return a different fingerprint")
        void whenEncryptionChanges_shouldReturnDifferentFingerprint() {
            // given
            var service = newService();

            // when
            var first = service.fingerprint("password", PasswordEncryption.SCRAM_SHA_256);
            var second = service.fingerprint("password", PasswordEncryption.SERVER);

            // then
            assertThat(second).isNotEqualTo(first);
        }

        @Test
        @DisplayName("Should be the Base64 HMAC-SHA256 of '<encryption> 0x00 <password>' with the key from the Secret")
        void shouldMatchDocumentedConstruction() throws GeneralSecurityException {
            // Fingerprints in existing Role statuses must stay valid after an upgrade, so the construction is fixed.

            // given
            var fingerprint = newService().fingerprint("password", PasswordEncryption.SCRAM_SHA_256);

            var key = Base64.getDecoder().decode(
                    client.secrets()
                            .inNamespace(client.getNamespace())
                            .withName(SECRET_NAME)
                            .require()
                            .getData()
                            .get(PasswordFingerprintService.SECRET_DATA_KEY)
            );

            // when
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            mac.update("scram-sha-256".getBytes(StandardCharsets.UTF_8));
            mac.update((byte) 0);
            mac.update("password".getBytes(StandardCharsets.UTF_8));

            var expected = Base64.getEncoder().encodeToString(mac.doFinal());

            // then
            assertThat(fingerprint).isEqualTo(expected);
        }
    }
}
