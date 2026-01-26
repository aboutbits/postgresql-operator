# DefaultPrivilege

The `DefaultPrivilege` Custom Resource Definition (CRD) manages default privileges (ALTER DEFAULT PRIVILEGES) for objects created in the future.

## Spec

| Field        | Type               | Description                                                                                             | Required    | Immutable |
|--------------|--------------------|---------------------------------------------------------------------------------------------------------|-------------|-----------|
| `clusterRef` | `ClusterReference` | Reference to the `ClusterConnection` to use.                                                            | Yes         | No        |
| `database`   | `string`           | The database where default privileges apply.                                                            | Yes         | Yes       |
| `role`       | `string`           | The role to which default privileges are granted.                                                       | Yes         | Yes       |
| `owner`      | `string`           | The role that owns the objects (the creator). Default privileges apply to objects created by this role. | Yes         | Yes       |
| `schema`     | `string`           | The schema where default privileges apply. Required, unless `objectType` is `schema`.                   | Conditional | Yes       |
| `objectType` | `string`           | The type of object.                                                                                     | Yes         | Yes       |
| `privileges` | `array[string]`    | List of privileges to grant.                                                                            | Yes         | No        |

### Object Types

Supported object types:

- `schema`
- `sequence`
- `table`

### Privileges

Supported privileges depend on the `objectType`:

- `connect`
- `create`
- `delete`
- `insert`
- `maintain`
- `references`
- `select`
- `temporary`
- `trigger`
- `truncate`
- `update`
- `usage`

### ClusterReference

| Field       | Type     | Description                                                                      | Required |
|-------------|----------|----------------------------------------------------------------------------------|----------|
| `name`      | `string` | Name of the `ClusterConnection`.                                                 | Yes      |
| `namespace` | `string` | Namespace of the `ClusterConnection`. If not specified, uses the CR's namespace. | No       |

## Example

```yaml
apiVersion: postgresql.aboutbits.it/v1
kind: DefaultPrivilege
metadata:
  name: default-privileges-tables
spec:
  clusterRef:
    name: my-postgres-connection
  database: my_database
  role: read_only_role
  owner: app_user
  objectType: table
  schema: public
  privileges:
    - select
```

## Official Documentation

- [ALTER DEFAULT PRIVILEGES](https://www.postgresql.org/docs/current/sql-alterdefaultprivileges.html)
