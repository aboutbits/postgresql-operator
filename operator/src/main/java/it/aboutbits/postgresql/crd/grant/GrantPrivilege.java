package it.aboutbits.postgresql.crd.grant;

import org.jooq.Privilege;
import org.jooq.impl.DSL;
import org.jspecify.annotations.NullMarked;

import java.util.Locale;

/**
 * <a href="https://www.postgresql.org/docs/current/sql-grant.html">
 * https://www.postgresql.org/docs/current/sql-grant.html
 * </a>
 */
@NullMarked
public enum GrantPrivilege {
    SELECT,
    INSERT,
    UPDATE,
    DELETE,
    TRUNCATE,
    REFERENCES,
    TRIGGER,
    CREATE,
    CONNECT,
    TEMPORARY,
    USAGE,
    MAINTAIN;

    public Privilege privilege() {
        return DSL.privilege(
                name().toLowerCase(Locale.ROOT)
        );
    }
}
