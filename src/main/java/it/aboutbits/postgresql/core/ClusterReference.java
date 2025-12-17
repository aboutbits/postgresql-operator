package it.aboutbits.postgresql.core;

import io.fabric8.generator.annotation.Required;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
@Getter
@Setter
public class ClusterReference {
    @Required
    private String name = "";

    @Nullable
    @io.fabric8.generator.annotation.Nullable
    private String namespace;
}
