# ClusterConnection

The `ClusterConnection` Custom Resource Definition (CRD) defines the connection details for a PostgreSQL cluster.  
It specifies the host, port, database, and the credentials to use for administrative operations.

Other Custom Resources (like `Database`, `Role`, `Schema`, `Grant`, `DefaultPrivilege`) reference a specific target PostgreSQL cluster using `clusterRef` on which to execute the operations.

## Spec

| Field               | Type                | Description                                                           | Required | Mutable |
|---------------------|---------------------|-----------------------------------------------------------------------|----------|---------|
| `host`              | `string`            | The hostname of the PostgreSQL instance.                              | Yes      | Yes     |
| `port`              | `integer`           | The port of the PostgreSQL instance (1-65535).                        | Yes      | Yes     |
| `database`          | `string`            | The database to connect to (usually `postgres` for admin operations). | Yes      | Yes     |
| `adminSecretRef`    | `ResourceRef`       | Reference to the Kubernetes Secret containing the admin credentials.  | No       | Yes     |
| `adminSecretFileRef`| `ResourceFileRef`   | Reference to a file containing the admin credentials.                 | No       | Yes     |
| `parameters`        | `map[string]string` | Additional connection parameters.                                     | No       | Yes     |

> **Note:** Exactly one of `adminSecretRef` or `adminSecretFileRef` must be provided.

### ResourceRef (`adminSecretRef`)

| Field       | Type     | Description                                                                                        | Required |
|-------------|----------|----------------------------------------------------------------------------------------------------|----------|
| `namespace` | `string` | Namespace of the referenced Kubernetes `Secret`. If not specified, uses the owning CR's namespace. | No       |
| `name`      | `string` | Name of the referenced Kubernetes `Secret`.                                                        | Yes      |

The referenced secret must be of type `kubernetes.io/basic-auth` and contain the keys `username` and `password`.

### ResourceFileRef (`adminSecretFileRef`)

| Field  | Type     | Description                                                    | Required |
|--------|----------|----------------------------------------------------------------|----------|
| `path` | `string` | The path to the file containing the admin credentials.         | Yes      |

Use this option when credentials are mounted as a file (e.g. via AWS Secrets Manager) instead of a Kubernetes Secret.

### Examples

#### Using a Kubernetes Secret (`adminSecretRef`)

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
  # Example parameters
  parameters:
    ApplicationName: "k8s-operator" # Helps identify this connection in Postgres logs
    #sslmode: "require" # Enforce SSL encryption
    #connectTimeout: "10" # Timeout in seconds for connection attempts
```

#### Using a file reference (`adminSecretFileRef`)

```yaml
apiVersion: postgresql.aboutbits.it/v1
kind: ClusterConnection
metadata:
  name: my-postgres-connection
spec:
  adminSecretFileRef:
    path: "/mnt/db-password"
  host: localhost
  port: 5432
  database: postgres
```
