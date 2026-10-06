package it.aboutbits.postgresql.crd.role;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;

/// Controls how the operator sends the password of a `Role` to PostgreSQL.
@RequiredArgsConstructor
@NullMarked
public enum PasswordEncryption {
    /// The operator computes a SCRAM-SHA-256 verifier and sends only the verifier.
    /// The cleartext password never reaches the server or its statement log.
    SCRAM_SHA_256("scram-sha-256"),

    /// The operator sends the cleartext password.
    /// The server hashes it according to its `password_encryption` setting.
    /// Use this for clients that only support MD5 authentication.
    SERVER("server");

    private final String value;

    @JsonValue
    public String toValue() {
        return value;
    }
}
