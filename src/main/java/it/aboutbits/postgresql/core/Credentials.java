package it.aboutbits.postgresql.core;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public record Credentials(
        @Nullable String username,
        String password
) {
}
