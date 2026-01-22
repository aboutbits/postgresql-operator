package it.aboutbits.postgresql.crd.grant;

import com.fasterxml.jackson.annotation.JsonValue;
import it.aboutbits.postgresql.core.Privilege;
import lombok.Getter;
import lombok.experimental.Accessors;
import org.jooq.Keyword;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import static it.aboutbits.postgresql.core.Privilege.CONNECT;
import static it.aboutbits.postgresql.core.Privilege.CREATE;
import static it.aboutbits.postgresql.core.Privilege.DELETE;
import static it.aboutbits.postgresql.core.Privilege.INSERT;
import static it.aboutbits.postgresql.core.Privilege.REFERENCES;
import static it.aboutbits.postgresql.core.Privilege.SELECT;
import static it.aboutbits.postgresql.core.Privilege.TEMPORARY;
import static it.aboutbits.postgresql.core.Privilege.TRIGGER;
import static it.aboutbits.postgresql.core.Privilege.TRUNCATE;
import static it.aboutbits.postgresql.core.Privilege.UPDATE;
import static it.aboutbits.postgresql.core.Privilege.USAGE;
import static org.jooq.impl.DSL.keyword;

/**
 * <a href="https://www.postgresql.org/docs/current/sql-grant.html">
 * https://www.postgresql.org/docs/current/sql-grant.html
 * </a>
 */
@NullMarked
@Getter
@Accessors(fluent = true)
public enum GrantObjectType {
    DATABASE(
            List.of(
                    CREATE,
                    CONNECT,
                    TEMPORARY
            )
    ),
    SCHEMA(
            List.of(
                    USAGE,
                    CREATE
            )
    ),
    TABLE(
            List.of(
                    SELECT,
                    INSERT,
                    UPDATE,
                    DELETE,
                    TRUNCATE,
                    REFERENCES,
                    TRIGGER
                    //MAINTAIN // PostgreSQL 17+
            )
    ),
    SEQUENCE(
            List.of(
                    USAGE,
                    SELECT,
                    UPDATE
            )
    );

    @SuppressWarnings("ImmutableEnumChecker")
    private final List<Privilege> privileges;

    @SuppressWarnings("ImmutableEnumChecker")
    private final Set<Privilege> privilegesSet;

    GrantObjectType(
            List<Privilege> privileges
    ) {
        this.privileges = privileges;
        this.privilegesSet = Set.copyOf(privileges);
    }

    @JsonValue
    public String toValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    public Keyword objectType() {
        return keyword(
                name().toLowerCase(Locale.ROOT)
        );
    }
}
