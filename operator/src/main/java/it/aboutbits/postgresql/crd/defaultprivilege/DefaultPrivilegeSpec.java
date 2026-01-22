package it.aboutbits.postgresql.crd.defaultprivilege;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.fabric8.generator.annotation.Required;
import io.fabric8.generator.annotation.ValidationRule;
import it.aboutbits.postgresql.core.ClusterReference;
import it.aboutbits.postgresql.core.Privilege;
import lombok.Getter;
import lombok.Setter;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

@NullMarked
@Getter
@Setter
@ValidationRule(
        value = "self.objectType == 'schema' ? !has(self.schema) : (has(self.schema) && self.schema.trim().size() > 0)",
        message = "The DefaultPrivilege schema must be not set if objectType is 'schema', for all other objectType's it is required."
)
public class DefaultPrivilegeSpec {
    @Required
    private ClusterReference clusterRef = new ClusterReference();

    /// The database to grant default privileges on for this role.
    @Required
    @ValidationRule(
            value = "self == oldSelf",
            message = "The DefaultPrivilege database is immutable. Changing it would require revoking permissions from the old database before granting them in the new one."
    )
    @ValidationRule(
            value = "self.trim().size() > 0",
            message = "The DefaultPrivilege database must not be empty."
    )
    private String database = "";

    /// The name of the role to grant default privileges on.
    @Required
    @ValidationRule(
            value = "self == oldSelf",
            message = "The DefaultPrivilege role is immutable. Changing it would require revoking permissions from the old role before granting them to the new one."
    )
    @ValidationRule(
            value = "self.trim().size() > 0",
            message = "The DefaultPrivilege role must not be empty."
    )
    private String role = "";

    /// The name of the owner on which newly created objects default privileges are granted on to the role specified in this spec.
    @Required
    @ValidationRule(
            value = "self == oldSelf",
            message = "The DefaultPrivilege owner is immutable. Changing it would require revoking permissions from the old role before granting them to the new one."
    )
    @ValidationRule(
            value = "self.trim().size() > 0",
            message = "The DefaultPrivilege owner must not be empty."
    )
    private String owner = "";

    /// The database schema to grant default privileges on for this role
    @Nullable
    @io.fabric8.generator.annotation.Nullable
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @ValidationRule(
            value = "self == oldSelf",
            message = "The DefaultPrivilege schema is immutable. Changing it would require revoking permissions from the old schema before granting them to objects in the new schema."
    )
    @ValidationRule(
            value = "self.trim().size() > 0",
            message = "The DefaultPrivilege schema must not be empty."
    )
    private String schema = null;

    /// The PostgreSQL object type to grant default privileges on.
    ///
    /// Must be one of:
    /// - `schema`
    /// - `table`
    /// - `sequence`
    @Required
    @JsonFormat(with = JsonFormat.Feature.ACCEPT_CASE_INSENSITIVE_VALUES)
    @ValidationRule(
            value = "self == oldSelf",
            message = "The DefaultPrivilege objectType is immutable. Changing it would require revoking permissions and generating a completely different SQL statement."
    )
    private DefaultPrivilegeObjectType objectType = DefaultPrivilegeObjectType.SCHEMA;

    /// The privileges to grant default privileges on the PostgreSQL objects.
    /// The Operator also validates if the objectType supports the privileges.
    ///
    /// There are different kinds of privileges:
    /// - `select`
    /// - `insert`
    /// - `update`
    /// - `delete`
    /// - `truncate`
    /// - `references`
    /// - `trigger`
    /// - `create`
    /// - `usage`
    /// - `maintain`
    @Required
    @JsonFormat(with = JsonFormat.Feature.ACCEPT_CASE_INSENSITIVE_VALUES)
    @ValidationRule(
            value = "self.size() > 0",
            message = "The DefaultPrivilege privileges must not be empty. The Operator currently does not support revoking all privileges from existing roles (e.g. public user) by specifying an empty array."
    )
    private List<Privilege> privileges = new ArrayList<>();
}
