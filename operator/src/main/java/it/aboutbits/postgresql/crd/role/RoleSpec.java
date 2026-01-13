package it.aboutbits.postgresql.crd.role;

import io.fabric8.generator.annotation.Required;
import io.fabric8.generator.annotation.ValidationRule;
import it.aboutbits.postgresql.core.ClusterReference;
import it.aboutbits.postgresql.core.SecretRef;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@NullMarked
@Getter
@Setter
public class RoleSpec {
    @Required
    @ValidationRule(
            value = "self == oldSelf",
            message = "The Role name is immutable. Allowing to rename the Role name using 'alter role <old_name> rename to <new_name>' would add unwanted side-effects to the operator."
    )
    private String name = "";

    @Nullable
    @io.fabric8.generator.annotation.Nullable
    private String comment;

    @Required
    private ClusterReference clusterRef = new ClusterReference();

    @Nullable
    @io.fabric8.generator.annotation.Nullable
    private SecretRef passwordSecretRef;

    @io.fabric8.generator.annotation.Nullable
    private Flags flags = new Flags();

    @Getter
    @Setter
    @EqualsAndHashCode
    // The Fabric8 @Nullable annotation is relevant for generating nullable annotations in the resulting CRD YAML JSON Schema
    @SuppressWarnings({"NullablePrimitive"})
    public static class Flags {
        @io.fabric8.generator.annotation.Nullable
        private boolean superuser = false;

        @io.fabric8.generator.annotation.Nullable
        private boolean createdb = false;

        @io.fabric8.generator.annotation.Nullable
        private boolean createrole = false;

        @io.fabric8.generator.annotation.Nullable
        private boolean inherit = true;

        @io.fabric8.generator.annotation.Nullable
        private boolean replication = false;

        @io.fabric8.generator.annotation.Nullable
        private boolean bypassrls = false;

        @io.fabric8.generator.annotation.Nullable
        private int connectionLimit = -1;

        @Nullable
        @io.fabric8.generator.annotation.Nullable
        private OffsetDateTime validUntil = null;

        @io.fabric8.generator.annotation.Nullable
        private List<String> inRole = new ArrayList<>();

        @io.fabric8.generator.annotation.Nullable
        private List<String> role = new ArrayList<>();
    }
}
