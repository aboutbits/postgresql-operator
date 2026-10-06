package it.aboutbits.postgresql._support;

import com.ongres.scram.common.StringPreparation;
import org.jooq.DSLContext;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import javax.crypto.Mac;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

import static it.aboutbits.postgresql.core.infrastructure.persistence.Tables.PG_AUTHID;

/// Test helper that verifies a cleartext password against the verifier stored in `pg_authid`.
///
/// Reading `pg_authid` needs superuser rights, so the given `DSLContext` must connect as a superuser.
/// The operator itself does not read `pg_authid` anymore.
@NullMarked
public final class PostgreSQLPasswordVerifier {
    private static final String SCRAM_SHA_256_PREFIX = "SCRAM-SHA-256$";
    private static final String MD5 = "MD5";

    /// The verifier as stored in `pg_authid.rolpassword`.
    public static @Nullable String storedVerifier(
            DSLContext superuserDsl,
            String roleName
    ) {
        return superuserDsl
                .select(PG_AUTHID.ROLPASSWORD)
                .from(PG_AUTHID)
                .where(PG_AUTHID.ROLNAME.eq(roleName))
                .fetchSingle(PG_AUTHID.ROLPASSWORD);
    }

    public static boolean passwordMatches(
            DSLContext superuserDsl,
            String roleName,
            String expectedPassword
    ) {
        var currentPasswordVerifier = storedVerifier(superuserDsl, roleName);

        if (currentPasswordVerifier == null || currentPasswordVerifier.isBlank()) {
            return false;
        }

        // PostgreSQL stores either:
        // - SCRAM verifier: SCRAM-SHA-256$<iterations>:<saltB64>$<storedKeyB64>:<serverKeyB64>
        // - or legacy md5: md5<md5(password + username)>
        if (currentPasswordVerifier.startsWith(SCRAM_SHA_256_PREFIX)) {
            return verifyScramSha256(currentPasswordVerifier, expectedPassword);
        }

        if (currentPasswordVerifier.startsWith(MD5.toLowerCase(Locale.ROOT))) {
            return verifyMd5(currentPasswordVerifier, expectedPassword, roleName);
        }

        return false;
    }

    private static boolean verifyScramSha256(
            String postgresVerifier,
            String cleartextPassword
    ) {
        var preparedPassword = StringPreparation.POSTGRESQL_PREPARATION.normalize(
                cleartextPassword.toCharArray()
        );

        var afterPrefix = postgresVerifier.substring(SCRAM_SHA_256_PREFIX.length());
        var dollar = afterPrefix.indexOf('$');
        var iterationsAndSalt = afterPrefix.substring(0, dollar);
        var keys = afterPrefix.substring(dollar + 1);

        var colonIterationsAndSalt = iterationsAndSalt.indexOf(':');
        var iterations = Integer.parseInt(iterationsAndSalt.substring(0, colonIterationsAndSalt));
        var salt = Base64.getDecoder().decode(iterationsAndSalt.substring(colonIterationsAndSalt + 1));

        var storedKey = Base64.getDecoder().decode(keys.substring(0, keys.indexOf(':')));

        try {
            var secretKeyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            var saltedPassword = secretKeyFactory.generateSecret(
                    new PBEKeySpec(preparedPassword, salt, iterations, 32 * 8)
            ).getEncoded();

            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(saltedPassword, "HmacSHA256"));
            var clientKey = mac.doFinal("Client Key".getBytes(StandardCharsets.UTF_8));

            var expectedStoredKey = MessageDigest.getInstance("SHA-256").digest(clientKey);

            return MessageDigest.isEqual(storedKey, expectedStoredKey);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean verifyMd5(
            String postgresMd5,
            String expectedPassword,
            String username
    ) {
        try {
            var currentDigest = HexFormat.of().parseHex(postgresMd5, 3, postgresMd5.length());

            var md5 = MessageDigest.getInstance(MD5);
            md5.update((expectedPassword + username).getBytes(StandardCharsets.UTF_8));

            return MessageDigest.isEqual(currentDigest, md5.digest());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private PostgreSQLPasswordVerifier() {
    }
}
