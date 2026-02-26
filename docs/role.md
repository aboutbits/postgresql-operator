# Role

The `Role` Custom Resource Definition (CRD) manages PostgreSQL roles (users).

## Spec

| Field               | Type          | Description                                                                         | Required | Immutable |
|---------------------|---------------|-------------------------------------------------------------------------------------|----------|-----------|
| `clusterRef`        | `ResourceRef` | Reference to the `ClusterConnection` to use.                                        | Yes      | No        |
| `name`              | `string`      | The name of the role to create in the database.                                     | Yes      | Yes       |
| `comment`           | `string`      | A comment to add to the role.                                                       | No       | No        |
| `passwordSecretRef` | `ResourceRef` | Reference to a secret containing the password for the role to make it a LOGIN role. | No       | No        |
| `flags`             | `RoleFlags`   | Flags and attributes for the role.                                                  | No       | No        |

### ResourceRef (`clusterRef` and `passwordSecretRef`)

| Field       | Type     | Description                                                                             | Required |
|-------------|----------|-----------------------------------------------------------------------------------------|----------|
| `namespace` | `string` | Namespace of the referenced resource. If not specified, uses the owning CR's namespace. | No       |
| `name`      | `string` | Name of the referenced Kubernetes resource.                                             | Yes      |

**Note**:
When used as `passwordSecretRef`, the referenced Kubernetes Secret must be of type `kubernetes.io/basic-auth`.  
The `username` key in the Secret is not strictly required, as the role name is specified by the `name` field in the CRD. Only the `password` key is used.

### RoleFlags

| Field             | Type            | Default | Description                                                             |
|-------------------|-----------------|---------|-------------------------------------------------------------------------|
| `bypassrls`       | `boolean`       | `false` | Bypass Row Level Security.                                              |
| `connectionLimit` | `integer`       | `-1`    | Maximum number of concurrent connections. A value of -1 means no limit. |
| `createdb`        | `boolean`       | `false` | Ability to create databases.                                            |
| `createrole`      | `boolean`       | `false` | Ability to create new roles.                                            |
| `inRole`          | `array[string]` | `[]`    | List of roles this role should be added to.                             |
| `inherit`         | `boolean`       | `true`  | Whether to inherit privileges from roles it is a member of by default.  |
| `replication`     | `boolean`       | `false` | Ability to initiate replication.                                        |
| `role`            | `array[string]` | `[]`    | List of roles that should be members of this role.                      |
| `superuser`       | `boolean`       | `false` | Superuser status.                                                       |
| `validUntil`      | `string`        | `null`  | Date and time until the password is valid (ISO 8601).                   |

### Login vs No-Login Roles

The operator uses the presence of the `passwordSecretRef` field to determine if the role should have the `LOGIN` privilege (User) or not (Group).

- **Login Role (User)**: If `passwordSecretRef` is specified, the role is created with the `LOGIN` attribute. It uses the password from the referenced secret.
- **No-Login Role (Group)**: If `passwordSecretRef` is omitted, the role is created with the `NOLOGIN` attribute. This is useful for creating roles that serve as groups for permissions.

### Example

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: test-role-secret
type: kubernetes.io/basic-auth
stringData:
  password: securepassword
```

```yaml
apiVersion: postgresql.aboutbits.it/v1
kind: Role
metadata:
  name: test-role
spec:
  name: test_role
  comment: "A test role"
  clusterRef:
    name: my-postgres-connection
  flags:
    createdb: true
    validUntil: "2026-12-31T23:59:59Z"
  passwordSecretRef:
    name: test-role-secret
```

## Official Documentation

- [CREATE ROLE](https://www.postgresql.org/docs/current/sql-createrole.html)
- [ALTER ROLE](https://www.postgresql.org/docs/current/sql-alterrole.html)
