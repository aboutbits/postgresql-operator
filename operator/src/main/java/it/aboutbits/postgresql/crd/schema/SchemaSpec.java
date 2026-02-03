package it.aboutbits.postgresql.crd.schema;

import io.fabric8.generator.annotation.Required;
import io.fabric8.generator.annotation.ValidationRule;
import it.aboutbits.postgresql.core.ClusterReference;
import it.aboutbits.postgresql.core.ReclaimPolicy;
import lombok.Getter;
import lombok.Setter;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
@Getter
@Setter
public class SchemaSpec {
    @Required
    private ClusterReference clusterRef = new ClusterReference();

    @Required
    @ValidationRule(
            value = "self == oldSelf",
            message = "The Schema database is immutable. Moving a schema to another database requires dumping and restoring the schema to the new database."
    )
    @ValidationRule(
            value = "self.trim().size() > 0",
            message = "The Schema database must not be empty."
    )
    private String database = "";

    @Required
    @ValidationRule(
            value = "self == oldSelf",
            message = "The Schema name is immutable. Allowing to rename the Schema name using 'alter schema <old_name> rename to <new_name>' would add unwanted side-effects to the Operator."
    )
    @ValidationRule(
            value = "self.trim().size() > 0",
            message = "The Schema name must not be empty."
    )
    private String name = "";

    /// Whether the schema should be retained or deleted when the Schema CR instance is deleted.
    @io.fabric8.generator.annotation.Nullable
    private ReclaimPolicy reclaimPolicy = ReclaimPolicy.RETAIN;

    /// The owner of the schema.
    /// If not specified, the schema will be owned by the logged-in admin user specified in the clusterRef -> ClusterConnection#adminSecretRef CR instance.
    @Nullable
    @io.fabric8.generator.annotation.Nullable
    private String owner = null;
}
