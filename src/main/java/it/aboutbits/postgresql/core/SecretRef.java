package it.aboutbits.postgresql.core;

import io.fabric8.generator.annotation.Required;
import lombok.Getter;
import lombok.Setter;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
@Getter
@Setter
public class SecretRef {
    @Required
    private String name;

    /**
     * The namespace where the Secret is located.
     * If it is null, it means the Secret is in the same namespace as the resource referencing it.
     */
    @Nullable
    @io.fabric8.generator.annotation.Nullable
    private String namespace;
}
