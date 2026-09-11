# Role

The `Role` Custom Resource Definition (CRD) manages PostgreSQL roles (users).

## Spec

| Field                | Type          | Description                                                                         | Required | Mutable |
|----------------------|---------------|-------------------------------------------------------------------------------------|----------|---------|
| `clusterRef`         | `ResourceRef` | Reference to the `ClusterConnection` to use.                                        | Yes      | Yes     |
| `name`               | `string`      | The name of the role to create in the database.                                     | Yes      | No      |
| `comment`            | `string`      | A comment to add to the role.                                                       | No       | Yes     |
| `passwordSecretRef`  | `ResourceRef` | Reference to a secret containing the password for the role to make it a LOGIN role. | No       | Yes     |
| `passwordEncryption` | `string`      | How the password is sent to PostgreSQL: `scram-sha-256` (default) or `server`.      | No       | Yes     |
| `flags`              | `RoleFlags`   | Flags and attributes for the role.                                                  | No       | Yes     |

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

### Password handling

The operator does not read the password hash from `pg_authid`.  
That catalog is readable by superusers only, and managed PostgreSQL services of cloud providers, such as AWS RDS, Google Cloud SQL, or Azure Database for PostgreSQL, revoke it from every role, including the master user.

Instead, the operator stores a keyed fingerprint of the password it applied last in `status.passwordFingerprint`.  
On each reconcile it compares the referenced Secret against that fingerprint. When they differ, the operator runs `ALTER ROLE ... PASSWORD`.

The fingerprint is an `HMAC-SHA256`. Its key is random and private to the operator.  
The operator generates the key once and stores it in a Secret named `postgresql-operator-password-fingerprint-key` in its own namespace. A reader of the `Role` status learns nothing about the password without that key.  
The Secret name is set by the configuration property `postgresql-operator.password-fingerprint.secret-name`, for example through the environment variable `POSTGRESQL_OPERATOR_PASSWORD_FINGERPRINT_SECRET_NAME`.  
The Helm chart grants `create` on Secrets through a `Role` and `RoleBinding` in the operator namespace only. The `ClusterRole` of the operator keeps read access to Secrets.

**Consequences:**

- The Secret is the source of truth. A password change made directly in PostgreSQL is not detected.
- If the key Secret is lost, the operator generates a new key and re-applies every `Role` password once.
- After the upgrade to the version that introduced the fingerprint, every existing `Role` gets one password update, because its status has no fingerprint yet.

#### `passwordEncryption`

| Value           | Behavior                                                                                                                                                                                                 |
|-----------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `scram-sha-256` | **Default**. The operator computes the `SCRAM-SHA-256` verifier itself and sends only the verifier. The cleartext password never reaches the server, its statement log, or extensions such as `pgaudit`. |
| `server`        | The operator sends the cleartext password. The server hashes it according to its `password_encryption` setting. Use this for clients that only support MD5 authentication.                               |

If the Secret already contains an `MD5` or `SCRAM-SHA-256` verifier, the operator forwards it unchanged in both modes.

**Note:**
A pre-hashed password bypasses server-side password policies. The `credcheck` extension rejects it unless `credcheck.encrypted_password_allowed` is on. For example a Cloud SQL password policy does not apply to hashed passwords. Set `passwordEncryption: server` when such a policy must apply.

### Non-superuser admins

The admin role of the `ClusterConnection` does not need to be a superuser. `CREATEROLE` is sufficient for `Role` resources.  
See [ClusterConnection](cluster-connection.md#admin-privileges) for the full list of privileges. The following limits apply when the admin is not a superuser:

- The flags `superuser`, `replication`, and `bypassrls` cannot be set. PostgreSQL rejects them, and the `Role` status shows the error.
- On PostgreSQL 16 and later, the admin can only alter roles on which it holds `ADMIN OPTION`. Roles created by the operator qualify. Roles created by another user do not, unless that user grants the admin `ADMIN OPTION`.

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
