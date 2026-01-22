package it.aboutbits.postgresql.crd.defaultprivilege;

import com.fasterxml.jackson.annotation.JsonValue;
import it.aboutbits.postgresql.core.Privilege;
import lombok.Getter;
import lombok.experimental.Accessors;
import org.jooq.Keyword;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import static it.aboutbits.postgresql.core.Privilege.CREATE;
import static it.aboutbits.postgresql.core.Privilege.DELETE;
import static it.aboutbits.postgresql.core.Privilege.INSERT;
import static it.aboutbits.postgresql.core.Privilege.MAINTAIN;
import static it.aboutbits.postgresql.core.Privilege.REFERENCES;
import static it.aboutbits.postgresql.core.Privilege.SELECT;
import static it.aboutbits.postgresql.core.Privilege.TRIGGER;
import static it.aboutbits.postgresql.core.Privilege.TRUNCATE;
import static it.aboutbits.postgresql.core.Privilege.UPDATE;
import static it.aboutbits.postgresql.core.Privilege.USAGE;
import static org.jooq.impl.DSL.keyword;

/**
 * <a href="https://www.postgresql.org/docs/current/sql-alterdefaultprivileges.html">
 * https://www.postgresql.org/docs/current/sql-alterdefaultprivileges.html
 * </a>
 */
@NullMarked
@Getter
@Accessors(fluent = true)
public enum DefaultPrivilegeObjectType {
    SCHEMA(
            "n",
            List.of(
                    USAGE,
                    CREATE
            )
    ),
    TABLE(
            "r",
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
            "S",
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

    private final String objectTypeChar;

    DefaultPrivilegeObjectType(
            String objectTypeChar,
            List<Privilege> privileges
    ) {
        this.objectTypeChar = objectTypeChar;

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
