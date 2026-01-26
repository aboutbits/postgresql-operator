package it.aboutbits.postgresql.crd.database;

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
public class DatabaseSpec {
    @Required
    private ClusterReference clusterRef = new ClusterReference();

    @Required
    @ValidationRule(
            value = "self == oldSelf",
            message = "The Database name is immutable. Allowing to rename the Database name using 'alter database <old_name> rename to <new_name>' would add unwanted side-effects to the Operator."
    )
    @ValidationRule(
            value = "self.trim().size() > 0",
            message = "The Database name must not be empty."
    )
    private String name = "";

    /// Whether the database should be retained or deleted when the Database CR instance is deleted.
    @io.fabric8.generator.annotation.Nullable
    private ReclaimPolicy reclaimPolicy = ReclaimPolicy.RETAIN;

    /// The owner of the database.
    /// If not specified, the database will be owned by the logged-in admin user specified in the clusterRef -> ClusterConnection#adminSecretRef CR instance.
    @Nullable
    @io.fabric8.generator.annotation.Nullable
    private String owner = null;
}
