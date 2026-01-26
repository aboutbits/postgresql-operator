package it.aboutbits.postgresql.core;

import io.fabric8.generator.annotation.Required;
import io.fabric8.generator.annotation.ValidationRule;
import lombok.Getter;
import lombok.Setter;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
@Getter
@Setter
public class SecretRef {
    @Required
    @ValidationRule(
            value = "self.trim().size() > 0",
            message = "The SecretRef name must not be empty."
    )
    private String name = "";

    /**
     * The namespace where the Secret is located.
     * If it is null, it means the Secret is in the same namespace as the resource referencing it.
     */
    @Nullable
    @io.fabric8.generator.annotation.Nullable
    private String namespace;
}
