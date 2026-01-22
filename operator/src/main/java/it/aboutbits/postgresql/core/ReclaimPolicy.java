package it.aboutbits.postgresql.core;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;

@NullMarked
@RequiredArgsConstructor
public enum ReclaimPolicy {
    RETAIN("Retain"),
    DELETE("Delete");

    private final String policy;

    @JsonValue
    public String toValue() {
        return policy;
    }
}
