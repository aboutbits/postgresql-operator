package it.aboutbits.postgresql.crd.grant;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.fabric8.generator.annotation.Required;
import io.fabric8.generator.annotation.ValidationRule;
import it.aboutbits.postgresql.core.ClusterReference;
import lombok.Getter;
import lombok.Setter;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;

@NullMarked
@Getter
@Setter
@ValidationRule(
        value = "self.objectType == 'database' || (has(self.schema) && self.schema.size() > 0)",
        message = "The Grant schema is required to be set, except if objectType is 'database'."
)
public class GrantSpec {
    @Required
    private ClusterReference clusterRef = new ClusterReference();

    /// The database to grant privileges on for this role.
    @Required
    @ValidationRule(
            value = "self == oldSelf",
            message = "The Grant database is immutable. Changing it would require revoking permissions from the old database before granting them in the new one."
    )
    @ValidationRule(
            value = "self.size() > 0",
            message = "The Grant database must not be empty."
    )
    private String database = "";

    /// The name of the role to grant privileges on.
    @Required
    @ValidationRule(
            value = "self == oldSelf",
            message = "The Grant role is immutable. Changing it would require revoking permissions from the old role before granting them to the new one."
    )
    @ValidationRule(
            value = "self.size() > 0",
            message = "The Grant role must not be empty."
    )
    private String role = "";

    /// The database schema to grant privileges on for this role (required except if objectType is "database")
    @ValidationRule(
            value = "self == oldSelf",
            message = "The Grant schema is immutable. Changing it would require revoking permissions from the old schema before granting them to objects in the new schema."
    )
    @ValidationRule(
            value = "self.size() > 0",
            message = "The Grant schema must not be empty."
    )
    private String schema = "";

    /// The PostgreSQL object type to grant the privileges on.
    ///
    /// Must be one of:
    /// - `database`
    /// - `schema`
    /// - `table`
    /// - `sequence`
    /// - `routine`
    /// - `foreign_data_wrapper`
    /// - `foreign_server`
    /// - `domain`
    /// - `language`
    /// - `parameter`
    /// - `tablespace`
    /// - `type`
    @Required
    @JsonFormat(with = JsonFormat.Feature.ACCEPT_CASE_INSENSITIVE_VALUES)
    @ValidationRule(
            value = "self == oldSelf",
            message = "The Grant objectType is immutable. Changing it would require revoking permissions and generating a completely different SQL statement."
    )
    private GrantObjectType objectType = GrantObjectType.DATABASE;

    /// The PostgreSQL objects to grant privileges on.
    /// As these are quoted, case-sensitivity is very important.
    /// In PostgreSQL leave everything as lower-case except you have a special case.
    @Required
    private List<String> objects = new ArrayList<>();

    /// The privileges to grant on the PostgreSQL objects.
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
    /// - `connect`
    /// - `temporary`
    /// - `execute`
    /// - `usage`
    /// - `set`
    /// - `alter_system`
    /// - `maintain`
    @Required
    @JsonFormat(with = JsonFormat.Feature.ACCEPT_CASE_INSENSITIVE_VALUES)
    private List<GrantPrivilege> privileges = new ArrayList<>();
}
