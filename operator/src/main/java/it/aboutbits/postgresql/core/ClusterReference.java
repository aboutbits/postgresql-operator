package it.aboutbits.postgresql.core;

import io.fabric8.crdv2.generator.v1.SchemaCustomizer;
import io.fabric8.generator.annotation.Max;
import io.fabric8.generator.annotation.Required;
import io.fabric8.generator.annotation.ValidationRule;
import it.aboutbits.postgresql.core.schema_customizer.KubernetesNameCustomizer;
import lombok.Getter;
import lombok.Setter;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
@Getter
@Setter
@SchemaCustomizer(KubernetesNameCustomizer.class)
public class ClusterReference {
    @Required
    @Max(63)
    @ValidationRule(
            value = "self.trim().size() > 0",
            message = "The ClusterReference name must not be empty."
    )
    private String name = "";

    @Nullable
    @Max(63)
    @io.fabric8.generator.annotation.Nullable
    private String namespace;
}
