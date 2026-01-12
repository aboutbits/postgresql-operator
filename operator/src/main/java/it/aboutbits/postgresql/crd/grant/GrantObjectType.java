package it.aboutbits.postgresql.crd.grant;

import com.google.errorprone.annotations.Immutable;
import it.aboutbits.postgresql.core.infrastructure.persistence.Routines;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.jooq.Field;
import org.jspecify.annotations.NullMarked;

import java.util.List;

import static it.aboutbits.postgresql.crd.grant.GrantPrivilege.ALTER_SYSTEM;
import static it.aboutbits.postgresql.crd.grant.GrantPrivilege.CONNECT;
import static it.aboutbits.postgresql.crd.grant.GrantPrivilege.CREATE;
import static it.aboutbits.postgresql.crd.grant.GrantPrivilege.DELETE;
import static it.aboutbits.postgresql.crd.grant.GrantPrivilege.EXECUTE;
import static it.aboutbits.postgresql.crd.grant.GrantPrivilege.INSERT;
import static it.aboutbits.postgresql.crd.grant.GrantPrivilege.MAINTAIN;
import static it.aboutbits.postgresql.crd.grant.GrantPrivilege.REFERENCES;
import static it.aboutbits.postgresql.crd.grant.GrantPrivilege.SELECT;
import static it.aboutbits.postgresql.crd.grant.GrantPrivilege.SET;
import static it.aboutbits.postgresql.crd.grant.GrantPrivilege.TEMPORARY;
import static it.aboutbits.postgresql.crd.grant.GrantPrivilege.TRIGGER;
import static it.aboutbits.postgresql.crd.grant.GrantPrivilege.TRUNCATE;
import static it.aboutbits.postgresql.crd.grant.GrantPrivilege.UPDATE;
import static it.aboutbits.postgresql.crd.grant.GrantPrivilege.USAGE;

/**
 * <a href="https://www.postgresql.org/docs/current/sql-grant.html">
 * https://www.postgresql.org/docs/current/sql-grant.html
 * </a>
 */
@NullMarked
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum GrantObjectType {
    DATABASE(
            Routines::hasDatabasePrivilege1,
            List.of(
                    CREATE,
                    CONNECT,
                    TEMPORARY
            )
    ),
    SCHEMA(
            Routines::hasSchemaPrivilege1,
            List.of(
                    CREATE,
                    USAGE
            )
    ),
    TABLE(
            Routines::hasTablePrivilege1,
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
            Routines::hasSequencePrivilege1,
            List.of(
                    USAGE,
                    SELECT,
                    UPDATE
            )
    ),
    ROUTINE(
            Routines::hasFunctionPrivilege1,
            List.of(
                    EXECUTE
            )
    ),
    FOREIGN_DATA_WRAPPER(
            Routines::hasForeignDataWrapperPrivilege1,
            List.of(
                    USAGE
            )
    ),
    FOREIGN_SERVER(
            Routines::hasServerPrivilege1,
            List.of(
                    USAGE
            )
    ),
    DOMAIN(
            Routines::hasTypePrivilege1,
            List.of(
                    USAGE
            )
    ),
    LANGUAGE(
            Routines::hasLanguagePrivilege1,
            List.of(
                    USAGE
            )
    ),
    PARAMETER(
            Routines::hasParameterPrivilege1,
            List.of(
                    SET,
                    ALTER_SYSTEM
            )
    ),
    TABLESPACE(
            Routines::hasTablespacePrivilege1,
            List.of(
                    CREATE
            )
    ),
    TYPE(
            Routines::hasTypePrivilege1,
            List.of(
                    USAGE
            )
    );

    private final PrivilegeFunction checkPrivilegeFunction;

    @SuppressWarnings("ImmutableEnumChecker")
    private final List<GrantPrivilege> privileges;

    @Immutable
    @FunctionalInterface
    public interface PrivilegeFunction {
        Field<Boolean> apply(
                String role,
                String object,
                String privilege
        );
    }
}
