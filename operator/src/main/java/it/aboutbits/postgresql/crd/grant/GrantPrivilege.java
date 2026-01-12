package it.aboutbits.postgresql.crd.grant;

import org.jspecify.annotations.NullMarked;

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
    EXECUTE,
    USAGE,
    SET,
    ALTER_SYSTEM,
    MAINTAIN
}
