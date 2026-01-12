package it.aboutbits.postgresql.crd.grant;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.fabric8.generator.annotation.Required;
import it.aboutbits.postgresql.core.ClusterReference;
import lombok.Getter;
import lombok.Setter;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;

@NullMarked
@Getter
@Setter
public class GrantSpec {
    @Required
    private ClusterReference clusterRef = new ClusterReference();

    /// The database to grant privileges on for this role.
    @Required
    private String database = "";

    /// The name of the role to grant privileges on.
    @Required
    private String role = "";

    /// The database schema to grant privileges on for this role (required except if objectType is "database")
    @Required
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
    private GrantObjectType objectType = GrantObjectType.DATABASE;

    /// The PostgreSQL objects to grant privileges on.
    /// As these are quoted, case-sensitivity is very important.
    /// In PostgreSQL leave everything as lower-case except you have a special case.
    @Required
    private List<String> objects = new ArrayList<>();

    /// The privileges to grant on the PostgreSQL objects.
    /// The operator also validates if the objectType supports the privileges.
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
