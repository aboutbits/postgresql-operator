package it.aboutbits.postgresql.core;

import com.ongres.scram.common.StringPreparation;
import it.aboutbits.postgresql.crd.role.RoleSpec;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jooq.DSLContext;
import org.jspecify.annotations.NullMarked;

import javax.crypto.Mac;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

import static it.aboutbits.postgresql.core.infrastructure.persistence.Tables.PG_AUTHID;

@NullMarked
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PostgreSQLAuthenticationUtil {
    private static final String MD5 = "MD5";
    private static final String SHA_256 = "SHA-256";
    private static final String HMAC_SHA_256 = "HmacSHA256";
    private static final String PBKDF2_WITH_HMAC_SHA256 = "PBKDF2WithHmacSHA256";

    public static boolean passwordMatches(
            DSLContext dsl,
            RoleSpec spec,
            String expectedPassword
    ) {
        var currentPasswordVerifier = dsl
                .select(PG_AUTHID.ROLPASSWORD)
                .from(PG_AUTHID)
                .where(PG_AUTHID.ROLNAME.eq(spec.getName()))
                .fetchSingle(PG_AUTHID.ROLPASSWORD);

        if (currentPasswordVerifier == null || currentPasswordVerifier.isBlank()) {
            return false;
        }

        // PostgreSQL stores either:
        // - SCRAM verifier: SCRAM-SHA-256$<iterations>:<saltB64>$<storedKeyB64>:<serverKeyB64>
        // - or legacy md5: md5<md5(password + username)>
        if (currentPasswordVerifier.startsWith("SCRAM-SHA-256$")) {
            return verifyPostgresScramSha256(
                    currentPasswordVerifier,
                    expectedPassword
            );
        }

        if (currentPasswordVerifier.startsWith(MD5.toLowerCase(Locale.ROOT))) {
            return verifyPostgresMd5(
                    currentPasswordVerifier,
                    expectedPassword,
                    spec.getName()
            );
        }

        // Unknown format (or plain text, which PG should not store in rolpassword)
        return false;
    }

    private static boolean verifyPostgresScramSha256(String postgresVerifier, String cleartextPassword) {
        // Prepare the cleartext password with SASLprep
        var preparedPassword = StringPreparation.POSTGRESQL_PREPARATION.normalize(
                cleartextPassword.toCharArray()
        );

        // Format: SCRAM-SHA-256$<iterations>:<saltB64>$<storedKeyB64>:<serverKeyB64>
        var afterPrefix = postgresVerifier.substring("SCRAM-SHA-256$".length());
        var dollar = afterPrefix.indexOf('$');
        if (dollar < 0) {
            return false;
        }

        // <iterations>:<saltB64>
        var iterationsAndSalt = afterPrefix.substring(0, dollar);
        // <storedKeyB64>:<serverKeyB64>
        var keys = afterPrefix.substring(dollar + 1);

        var colonIterationsAndSalt = iterationsAndSalt.indexOf(':');
        if (colonIterationsAndSalt < 0) {
            return false;
        }

        int iterations;
        try {
            iterations = Integer.parseInt(iterationsAndSalt.substring(0, colonIterationsAndSalt));
        } catch (NumberFormatException ex) {
            return false;
        }
        if (iterations <= 0) {
            return false;
        }

        var saltB64 = iterationsAndSalt.substring(colonIterationsAndSalt + 1);

        var colonKeys = keys.indexOf(':');
        if (colonKeys < 0) {
            return false;
        }

        var storedKeyB64 = keys.substring(0, colonKeys);

        byte[] salt;
        byte[] currentStoredKey;
        try {
            salt = Base64.getDecoder().decode(saltB64);
            currentStoredKey = Base64.getDecoder().decode(storedKeyB64);
        } catch (IllegalArgumentException ex) {
            return false;
        }

        byte[] saltedPassword = null;
        byte[] clientKey = null;
        byte[] expectedStoredKey = null;
        try {
            // RFC 5802/7677:
            // saltedPassword := Hi(password, salt, iterations)  (PBKDF2-HMAC-SHA-256, 32 bytes)
            // clientKey      := HMAC(saltedPassword, "Client Key")
            // storedKey      := H(clientKey)  (SHA-256)
            saltedPassword = pbkdf2HmacSha256(preparedPassword, salt, iterations, 32);
            clientKey = hmacSha256(saltedPassword, "Client Key".getBytes(StandardCharsets.UTF_8));
            expectedStoredKey = sha256(clientKey);

            return MessageDigest.isEqual(
                    currentStoredKey,
                    expectedStoredKey
            );
        } finally {
            if (saltedPassword != null) {
                Arrays.fill(saltedPassword, (byte) 0);
            }
            if (clientKey != null) {
                Arrays.fill(clientKey, (byte) 0);
            }
            if (expectedStoredKey != null) {
                Arrays.fill(expectedStoredKey, (byte) 0);
            }
        }
    }

    private static boolean verifyPostgresMd5(
            String postgresMd5,
            String expectedPassword,
            String username
    ) {
        // PostgreSQL md5 is: "md5" + md5(password + username)
        if (postgresMd5.length() != 3 + 32 || !postgresMd5.regionMatches(true, 0, MD5, 0, 3)) {
            return false;
        }

        byte[] currentDigest;
        try {
            currentDigest = HexFormat.of().parseHex(
                    postgresMd5,
                    3,
                    postgresMd5.length()
            );
        } catch (IllegalArgumentException ex) {
            return false; // not valid hex
        }

        MessageDigest md5;
        try {
            md5 = MessageDigest.getInstance(MD5);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("%s not available".formatted(MD5), e);
        }

        md5.update((expectedPassword + username).getBytes(StandardCharsets.UTF_8));
        var expectedDigest = md5.digest();

        return MessageDigest.isEqual(currentDigest, expectedDigest);
    }

    private static byte[] pbkdf2HmacSha256(
            char[] password,
            byte[] salt,
            int iterations,
            int keyLenBytes
    ) {
        try {
            var secretKeyFactory = SecretKeyFactory.getInstance(PBKDF2_WITH_HMAC_SHA256);
            var spec = new PBEKeySpec(password, salt, iterations, keyLenBytes * 8);
            return secretKeyFactory.generateSecret(spec).getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException("%s not available".formatted(PBKDF2_WITH_HMAC_SHA256), e);
        }
    }

    private static byte[] hmacSha256(byte[] key, byte[] data) {
        try {
            var mac = Mac.getInstance(HMAC_SHA_256);
            mac.init(new SecretKeySpec(key, HMAC_SHA_256));
            return mac.doFinal(data);
        } catch (Exception e) {
            throw new IllegalStateException("%s not available".formatted(HMAC_SHA_256), e);
        }
    }

    private static byte[] sha256(byte[] data) {
        try {
            return MessageDigest.getInstance(SHA_256).digest(data);
        } catch (Exception e) {
            throw new IllegalStateException("%s not available".formatted(SHA_256), e);
        }
    }
}
