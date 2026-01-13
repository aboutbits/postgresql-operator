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
public class ClusterReference {
    @Required
    @ValidationRule(
            value = "self.size() > 0",
            message = "The ClusterReference name must not be empty."
    )
    private String name = "";

    @Nullable
    @io.fabric8.generator.annotation.Nullable
    private String namespace;
}
