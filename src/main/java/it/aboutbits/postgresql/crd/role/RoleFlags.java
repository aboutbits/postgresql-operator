package it.aboutbits.postgresql.crd.role;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.jspecify.annotations.NullMarked;

@NullMarked
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum RoleFlags {
    SUPERUSER("SUPERUSER"),
    NO_SUPERUSER("NOSUPERUSER"),

    CREATEDB("CREATEDB"),
    NO_CREATEDB("NOCREATEDB"),

    CREATEROLE("CREATEROLE"),
    NO_CREATEROLE("NOCREATEROLE"),

    INHERIT("INHERIT"),
    NO_INHERIT("NOINHERIT"),

    LOGIN("LOGIN"),
    NO_LOGIN("NOLOGIN"),

    REPLICATION("REPLICATION"),
    NO_REPLICATION("NOREPLICATION"),

    BYPASSRLS("BYPASSRLS"),
    NO_BYPASSRLS("NOBYPASSRLS"),

    CONNECTION_LIMIT("CONNECTION LIMIT"),

    PASSWORD("PASSWORD"),

    VALID_UNTIL("VALID UNTIL"),

    IN_ROLE("IN ROLE"),

    ROLE("ROLE");

    private final String flag;
}
