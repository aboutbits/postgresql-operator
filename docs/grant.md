# Grant

The `Grant` Custom Resource Definition (CRD) is responsible for managing privileges (GRANT/REVOKE) on PostgreSQL objects.

## Spec

| Field        | Type            | Description                                                                                                                                | Required    | Immutable |
|--------------|-----------------|--------------------------------------------------------------------------------------------------------------------------------------------|-------------|-----------|
| `clusterRef` | `ResourceRef`   | Reference to the `ClusterConnection` to use.                                                                                               | Yes         | No        |
| `database`   | `string`        | The database containing the objects.                                                                                                       | Yes         | Yes       |
| `role`       | `string`        | The role to which privileges are granted.                                                                                                  | Yes         | Yes       |
| `schema`     | `string`        | The schema containing the objects. Required, unless `objectType` is `database`.                                                            | Conditional | Yes       |
| `objectType` | `string`        | The type of object.                                                                                                                        | Yes         | Yes       |
| `objects`    | `array[string]` | List of object names. If empty, all objects of this `objectType` will be granted. Required, unless `objectType` is `database` or `schema`. | Conditional | No        |
| `privileges` | `array[string]` | List of privileges to grant.                                                                                                               | Yes         | No        |

### Object Types

Supported object types:

- `database`
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

### ResourceRef (`clusterRef`)

| Field       | Type     | Description                                                                                        | Required |
|-------------|----------|----------------------------------------------------------------------------------------------------|----------|
| `namespace` | `string` | Namespace of the referenced `ClusterConnection`. If not specified, uses the owning CR's namespace. | No       |
| `name`      | `string` | Name of the referenced `ClusterConnection`.                                                        | Yes      |

## Example

```yaml
apiVersion: postgresql.aboutbits.it/v1
kind: Grant
metadata:
  name: grant-select-tables
spec:
  clusterRef:
    name: my-postgres-connection
  database: my_database
  role: my_role
  objectType: table
  schema: public
  objects:
    - my_table
    - another_table
  privileges:
    - select
    - insert
```

## Official Documentation

- [GRANT](https://www.postgresql.org/docs/current/sql-grant.html)
- [REVOKE](https://www.postgresql.org/docs/current/sql-revoke.html)
