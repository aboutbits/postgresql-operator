package it.aboutbits.postgresql.crd.role;

import it.aboutbits.postgresql.core.CRStatus;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/// Status Object for the `Role` Custom Resource.
@Getter
@Setter
@Accessors(chain = true)
@NullMarked
public class RoleStatus extends CRStatus {
    /// Keyed fingerprint (HMAC-SHA256) of the password the operator applied last.
    ///
    /// The operator cannot read the password hash from `pg_authid` without superuser rights,
    /// which managed PostgreSQL services of cloud providers do not grant. It therefore compares the referenced
    /// Secret against this fingerprint to detect a password change. The HMAC key is private to the
    /// operator, so a reader of this status learns nothing about the password.
    private @Nullable String passwordFingerprint = null;
}
