# ClusterConnection

The `ClusterConnection` Custom Resource Definition (CRD) defines the connection details for a PostgreSQL cluster.  
It specifies the host, port, database, and the credentials to use for administrative operations.

Other Custom Resources (like `Database`, `Role`, `Schema`, `Grant`, `DefaultPrivilege`) reference a specific target PostgreSQL cluster using `clusterRef` on which to execute the operations.

## Spec

| Field            | Type                | Description                                                           | Required |
|------------------|---------------------|-----------------------------------------------------------------------|----------|
| `host`           | `string`            | The hostname of the PostgreSQL instance.                              | Yes      |
| `port`           | `integer`           | The port of the PostgreSQL instance (1-65535).                        | Yes      |
| `database`       | `string`            | The database to connect to (usually `postgres` for admin operations). | Yes      |
| `adminSecretRef` | `SecretRef`         | Reference to the secret containing admin credentials.                 | Yes      |
| `parameters`     | `map[string]string` | Additional connection parameters.                                     | No       |

### SecretRef

| Field       | Type     | Description                                                         | Required |
|-------------|----------|---------------------------------------------------------------------|----------|
| `name`      | `string` | Name of the secret.                                                 | Yes      |
| `namespace` | `string` | Namespace of the secret. If not specified, uses the CR's namespace. | No       |

The referenced secret must be of type `kubernetes.io/basic-auth` and contain the keys `username` and `password`.

### Example

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: my-db-secret
type: kubernetes.io/basic-auth
stringData:
  username: postgres
  password: password
```

```yaml
apiVersion: postgresql.aboutbits.it/v1
kind: ClusterConnection
metadata:
  name: my-postgres-connection
spec:
  adminSecretRef:
    name: my-db-secret
  host: localhost
  port: 5432
  database: postgres
```
