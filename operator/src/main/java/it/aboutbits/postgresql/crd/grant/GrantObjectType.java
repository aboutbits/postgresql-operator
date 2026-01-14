package it.aboutbits.postgresql.crd.grant;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.experimental.Accessors;
import org.jooq.Keyword;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import static it.aboutbits.postgresql.crd.grant.GrantPrivilege.CONNECT;
import static it.aboutbits.postgresql.crd.grant.GrantPrivilege.CREATE;
import static it.aboutbits.postgresql.crd.grant.GrantPrivilege.DELETE;
import static it.aboutbits.postgresql.crd.grant.GrantPrivilege.INSERT;
import static it.aboutbits.postgresql.crd.grant.GrantPrivilege.MAINTAIN;
import static it.aboutbits.postgresql.crd.grant.GrantPrivilege.REFERENCES;
import static it.aboutbits.postgresql.crd.grant.GrantPrivilege.SELECT;
import static it.aboutbits.postgresql.crd.grant.GrantPrivilege.TEMPORARY;
import static it.aboutbits.postgresql.crd.grant.GrantPrivilege.TRIGGER;
import static it.aboutbits.postgresql.crd.grant.GrantPrivilege.TRUNCATE;
import static it.aboutbits.postgresql.crd.grant.GrantPrivilege.UPDATE;
import static it.aboutbits.postgresql.crd.grant.GrantPrivilege.USAGE;
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
                    CREATE,
                    USAGE
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
                    TRIGGER,
                    MAINTAIN
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
    private final List<GrantPrivilege> privileges;

    @SuppressWarnings("ImmutableEnumChecker")
    private final Set<GrantPrivilege> privilegesSet;

    GrantObjectType(
            List<GrantPrivilege> privileges
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
