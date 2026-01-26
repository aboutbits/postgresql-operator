# Grant

The `Grant` Custom Resource Definition (CRD) is responsible for managing privileges (GRANT/REVOKE) on PostgreSQL objects.

## Spec

| Field        | Type               | Description                                                                                      | Required    | Immutable |
|--------------|--------------------|--------------------------------------------------------------------------------------------------|-------------|-----------|
| `clusterRef` | `ClusterReference` | Reference to the `ClusterConnection` to use.                                                     | Yes         | No        |
| `database`   | `string`           | The database containing the objects.                                                             | Yes         | Yes       |
| `role`       | `string`           | The role to which privileges are granted.                                                        | Yes         | Yes       |
| `objectType` | `string`           | The type of object.                                                                              | Yes         | Yes       |
| `objects`    | `array[string]`    | List of object names. Required if `objectType` is `sequence` or `table`.                         | Conditional | No        |
| `schema`     | `string`           | The schema containing the objects. Required if `objectType` is `sequence`, `table`, or `schema`. | Conditional | Yes       |
| `privileges` | `array[string]`    | List of privileges to grant.                                                                     | Yes         | No        |

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

### ClusterReference

| Field       | Type     | Description                                                                      | Required |
|-------------|----------|----------------------------------------------------------------------------------|----------|
| `name`      | `string` | Name of the `ClusterConnection`.                                                 | Yes      |
| `namespace` | `string` | Namespace of the `ClusterConnection`. If not specified, uses the CR's namespace. | No       |

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
