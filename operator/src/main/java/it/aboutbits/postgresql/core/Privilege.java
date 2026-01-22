package it.aboutbits.postgresql.core;

import com.fasterxml.jackson.annotation.JsonValue;
import org.jooq.impl.DSL;
import org.jspecify.annotations.NullMarked;

import java.util.Locale;

/**
 * <a href="https://www.postgresql.org/docs/current/sql-grant.html">
 * https://www.postgresql.org/docs/current/sql-grant.html
 * </a>
 */
@NullMarked
public enum Privilege {
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
    USAGE;
    //MAINTAIN; // PostgreSQL 17+

    @JsonValue
    public String toValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    public org.jooq.Privilege privilege() {
        return DSL.privilege(
                name().toLowerCase(Locale.ROOT)
        );
    }
}
