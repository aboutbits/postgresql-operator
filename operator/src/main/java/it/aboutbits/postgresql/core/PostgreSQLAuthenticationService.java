package it.aboutbits.postgresql.core;

import com.ongres.scram.common.StringPreparation;
import it.aboutbits.postgresql.crd.role.PasswordEncryption;
import jakarta.inject.Singleton;
import org.jspecify.annotations.NullMarked;

import javax.crypto.Mac;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

/// Builds the password literal that the operator sends to PostgreSQL in `CREATE ROLE` and `ALTER ROLE`.
///
/// By default, the operator computes the SCRAM-SHA-256 verifier itself,
/// exactly like `psql \password` or libpq's `PQencryptPasswordConn`.
/// PostgreSQL stores a verifier as is, regardless of its `password_encryption` setting.
/// This keeps the cleartext password out of the server's statement log and out of extensions such as `pg_stat_statements` or `pgaudit`.
@Singleton
@NullMarked
public final class PostgreSQLAuthenticationService {
    public static final String SCRAM_SHA_256_PREFIX = "SCRAM-SHA-256$";

    /// PostgreSQL's default for `scram_iterations`.
    public static final int SCRAM_SHA_256_ITERATIONS = 4096;

    private static final String MD5 = "MD5";
    private static final int MD5_VERIFIER_LENGTH = 3 + 32;
    private static final String SHA_256 = "SHA-256";
    private static final String HMAC_SHA_256 = "HmacSHA256";
    private static final String PBKDF2_WITH_HMAC_SHA256 = "PBKDF2WithHmacSHA256";
    private static final int SCRAM_SALT_LENGTH_BYTES = 16;
    private static final int SCRAM_KEY_LENGTH_BYTES = 32;

    private final SecureRandom secureRandom = new SecureRandom();

    /// Convert the password from the Secret into the literal to send to PostgreSQL.
    ///
    /// A value that already is an MD5 or SCRAM-SHA-256 verifier is forwarded unchanged, so users keep
    /// full control over the stored hash.
    public String toServerPassword(
            String password,
            PasswordEncryption passwordEncryption
    ) {
        if (passwordEncryption == PasswordEncryption.SERVER || isEncrypted(password)) {
            return password;
        }

        return scramSha256Verifier(password);
    }

    /// Whether the given password is already an MD5 or SCRAM-SHA-256 verifier.
    public static boolean isEncrypted(String password) {
        return password.startsWith(SCRAM_SHA_256_PREFIX) || isMd5Verifier(password);
    }

    /// Compute a PostgreSQL SCRAM-SHA-256 verifier for the given cleartext password.
    ///
    /// Format: `SCRAM-SHA-256$<iterations>:<saltB64>$<storedKeyB64>:<serverKeyB64>`
    /// as described in RFC 5802 and RFC 7677 and used by PostgreSQL since version 10.
    public String scramSha256Verifier(String cleartextPassword) {
        // Prepare the cleartext password with SASLprep, as PostgreSQL does
        var preparedPassword = StringPreparation.POSTGRESQL_PREPARATION.normalize(
                cleartextPassword.toCharArray()
        );

        var salt = new byte[SCRAM_SALT_LENGTH_BYTES];
        secureRandom.nextBytes(salt);

        byte[] saltedPassword = null;
        byte[] clientKey = null;
        byte[] storedKey = null;
        byte[] serverKey = null;
        try {
            // RFC 5802/7677:
            // saltedPassword := Hi(password, salt, iterations) (PBKDF2-HMAC-SHA-256, 32 bytes)
            // clientKey      := HMAC(saltedPassword, "Client Key")
            // storedKey      := H(clientKey)  (SHA-256)
            // serverKey      := HMAC(saltedPassword, "Server Key")
            saltedPassword = pbkdf2HmacSha256(preparedPassword, salt, SCRAM_SHA_256_ITERATIONS, SCRAM_KEY_LENGTH_BYTES);
            clientKey = hmacSha256(saltedPassword, "Client Key".getBytes(StandardCharsets.UTF_8));
            storedKey = sha256(clientKey);
            serverKey = hmacSha256(saltedPassword, "Server Key".getBytes(StandardCharsets.UTF_8));

            var encoder = Base64.getEncoder();

            return "%s%d:%s$%s:%s".formatted(
                    SCRAM_SHA_256_PREFIX,
                    SCRAM_SHA_256_ITERATIONS,
                    encoder.encodeToString(salt),
                    encoder.encodeToString(storedKey),
                    encoder.encodeToString(serverKey)
            );
        } finally {
            Arrays.fill(preparedPassword, '\0');
            if (saltedPassword != null) {
                Arrays.fill(saltedPassword, (byte) 0);
            }
            if (clientKey != null) {
                Arrays.fill(clientKey, (byte) 0);
            }
            if (storedKey != null) {
                Arrays.fill(storedKey, (byte) 0);
            }
            if (serverKey != null) {
                Arrays.fill(serverKey, (byte) 0);
            }
        }
    }

    private static boolean isMd5Verifier(String password) {
        // PostgreSQL md5 is: "md5" + md5(password + username) as 32 hex characters
        if (password.length() != MD5_VERIFIER_LENGTH || !password.regionMatches(true, 0, MD5, 0, 3)) {
            return false;
        }

        try {
            HexFormat.of().parseHex(
                    password.toLowerCase(Locale.ROOT),
                    3,
                    password.length()
            );
            return true;
        } catch (IllegalArgumentException _) {
            return false;
        }
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
