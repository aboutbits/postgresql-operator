package it.aboutbits.postgresql.core;

import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import it.aboutbits.postgresql.crd.role.PasswordEncryption;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/// Computes keyed fingerprints (HMAC-SHA256) of `Role` passwords.
///
/// The operator stores the fingerprint of the password it applied last in the `Role` status.
/// On the next reconcile it compares the referenced Secret against that fingerprint.
/// This replaces a read of the password hash from `pg_authid`, which needs superuser rights.
/// Managed PostgreSQL services of cloud providers, such as AWS RDS, Google Cloud SQL, or Azure Database for PostgreSQL,
/// do not grant these rights.
///
/// The HMAC key is random, generated once, and kept in a Secret in the operator namespace.
/// Without the key, the fingerprint in the status is useless for an attack on the password.
/// If the key Secret is lost, the operator generates a new key and re-applies every `Role` password once.
@Slf4j
@Singleton
@RequiredArgsConstructor
@NullMarked
public class PasswordFingerprintService {
    public static final String SECRET_DATA_KEY = "key";

    private static final String HMAC_SHA_256 = "HmacSHA256";
    private static final int KEY_LENGTH_BYTES = 32;
    private static final byte SEPARATOR = 0;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final KubernetesClient kubernetesClient;

    @SuppressWarnings("NullAway.Init")
    @ConfigProperty(name = "postgresql-operator.password-fingerprint.secret-name")
    String secretName;

    private byte @Nullable [] key;

    /// Fingerprint of the password together with the requested encryption,
    /// so that a change of either re-applies the password.
    public String fingerprint(
            String password,
            PasswordEncryption passwordEncryption
    ) {
        var encryption = passwordEncryption.toValue().getBytes(StandardCharsets.UTF_8);
        var secret = password.getBytes(StandardCharsets.UTF_8);

        try {
            var mac = Mac.getInstance(HMAC_SHA_256);
            mac.init(new SecretKeySpec(getKey(), HMAC_SHA_256));
            mac.update(encryption);
            mac.update(SEPARATOR);
            mac.update(secret);

            return Base64.getEncoder().encodeToString(mac.doFinal());
        } catch (Exception e) {
            throw new IllegalStateException("%s not available".formatted(HMAC_SHA_256), e);
        }
    }

    private synchronized byte[] getKey() {
        var current = key;
        if (current == null) {
            current = loadOrCreateKey();
            key = current;
        }

        return current;
    }

    private byte[] loadOrCreateKey() {
        var namespace = kubernetesClient.getNamespace();
        if (namespace == null) {
            throw new IllegalStateException(
                    "Cannot determine the operator namespace to store the password fingerprint key Secret [secret.name=%s]".formatted(secretName)
            );
        }

        var secrets = kubernetesClient.secrets()
                .inNamespace(namespace)
                .withName(secretName);

        var secret = secrets.get();
        if (secret == null) {
            var generatedKey = new byte[KEY_LENGTH_BYTES];
            SECURE_RANDOM.nextBytes(generatedKey);

            var newSecret = new SecretBuilder()
                    .withNewMetadata()
                    .withNamespace(namespace)
                    .withName(secretName)
                    .endMetadata()
                    .withType("Opaque")
                    .addToData(SECRET_DATA_KEY, Base64.getEncoder().encodeToString(generatedKey))
                    .build();

            try {
                secret = kubernetesClient.secrets()
                        .inNamespace(namespace)
                        .resource(newSecret)
                        .create();

                log.info(
                        "Created password fingerprint key Secret [secret.namespace={}, secret.name={}]",
                        namespace,
                        secretName
                );
            } catch (KubernetesClientException e) {
                if (e.getCode() != HttpURLConnection.HTTP_CONFLICT) {
                    throw e;
                }

                // Another operator replica created the Secret in the meantime
                secret = secrets.require();
            }
        }

        var data = secret.getData();
        var keyBase64 = data == null
                ? null
                : data.get(SECRET_DATA_KEY);
        if (keyBase64 == null || keyBase64.isBlank()) {
            throw new IllegalStateException(
                    "The password fingerprint key Secret is missing required data '%s' [secret.namespace=%s, secret.name=%s]".formatted(
                            SECRET_DATA_KEY,
                            namespace,
                            secretName
                    )
            );
        }

        return Base64.getDecoder().decode(keyBase64);
    }
}
