package it.aboutbits.postgresql.core;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.jooq.impl.DSL;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

/**
 * <a href="https://www.postgresql.org/docs/current/sql-grant.html">
 * https://www.postgresql.org/docs/current/sql-grant.html
 * </a>
 */
@NullMarked
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum Privilege {
    SELECT(null),
    INSERT(null),
    UPDATE(null),
    DELETE(null),
    TRUNCATE(null),
    REFERENCES(null),
    TRIGGER(null),
    CREATE(null),
    CONNECT(null),
    TEMPORARY(null),
    USAGE(null),
    MAINTAIN(17);

    @Nullable
    private final Integer minimumPostgresVersion;

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
