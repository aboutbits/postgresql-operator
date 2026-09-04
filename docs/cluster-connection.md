# ClusterConnection

The `ClusterConnection` Custom Resource Definition (CRD) defines the connection details for a PostgreSQL cluster.  
It specifies the host, port, database, and the credentials to use for administrative operations.

Other Custom Resources (like `Database`, `Role`, `Schema`, `Grant`, `DefaultPrivilege`) reference a specific target PostgreSQL cluster using `clusterRef` on which to execute the operations.

## Spec

| Field                | Type                | Description                                                           | Required | Mutable |
|----------------------|---------------------|-----------------------------------------------------------------------|----------|---------|
| `host`               | `string`            | The hostname of the PostgreSQL instance.                              | Yes      | Yes     |
| `port`               | `integer`           | The port of the PostgreSQL instance (1-65535).                        | Yes      | Yes     |
| `database`           | `string`            | The database to connect to (usually `postgres` for admin operations). | Yes      | Yes     |
| `adminSecretRef`     | `ResourceRef`       | Reference to the Kubernetes Secret containing the admin credentials.  | No       | Yes     |
| `adminSecretFileRef` | `FileRef`           | Reference to a file containing the admin credentials.                 | No       | Yes     |
| `parameters`         | `map[string]string` | Additional connection parameters.                                     | No       | Yes     |

> **Note:** Exactly one of `adminSecretRef` or `adminSecretFileRef` must be provided.

### ResourceRef (`adminSecretRef`)

| Field       | Type     | Description                                                                                        | Required |
|-------------|----------|----------------------------------------------------------------------------------------------------|----------|
| `namespace` | `string` | Namespace of the referenced Kubernetes `Secret`. If not specified, uses the owning CR's namespace. | No       |
| `name`      | `string` | Name of the referenced Kubernetes `Secret`.                                                        | Yes      |

The referenced secret must be of type `kubernetes.io/basic-auth` and contain the keys `username` and `password`.

### FileRef (`adminSecretFileRef`)

Use this option when the credentials should be mounted as a file inside the operator Pod instead of reading a Kubernetes Secret directly.

| Field  | Type     | Description                                                                             | Required |
|--------|----------|-----------------------------------------------------------------------------------------|----------|
| `path` | `string` | The absolute path inside the operator Pod to the file containing the admin credentials. | Yes      |


#### File format

The file must contain JSON with the following fields:

```json
{
  "username": "root",
  "password": "password"
}
```

- `password` **required**
- `username` **required**

#### Mount the credentials file

The file must be accessible inside the operator Pod at the path in `adminSecretFileRef.path`.

The Helm chart exposes the `app.volumes` and `app.volumeMounts` values for this.  
Both take the raw Kubernetes syntax, so any volume source that provides a file works.

The value of `adminSecretFileRef.path` is the `mountPath` plus the name of the file. The volume source decides the file name:

| Volume source                    | The file name comes from        |
|----------------------------------|---------------------------------|
| `secret`                         | the key of the Secret           |
| `csi` (Secrets Store CSI driver) | the `objectAlias` of the object |

See [Using a file reference](#using-a-file-reference-adminsecretfileref) in the examples for a complete setup with each volume source.

## Examples

### Using a Kubernetes Secret (`adminSecretRef`)

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

### Using a file reference (`adminSecretFileRef`)

```yaml
apiVersion: postgresql.aboutbits.it/v1
kind: ClusterConnection
metadata:
  name: my-postgres-connection
spec:
  adminSecretFileRef:
    path: "/mnt/secrets/db-credentials.json"
  host: localhost
  port: 5432
  database: postgres
  # Example parameters
  parameters:
    ApplicationName: "k8s-operator" # Helps identify this connection in Postgres logs
    #sslmode: "require" # Enforce SSL encryption
    #connectTimeout: "10" # Timeout in seconds for connection attempts
```

The mount that creates `/mnt/secrets/db-credentials.json` depends on the volume source.

#### From a Secret volume

Create the Secret. Its key becomes the file name:

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: db-credentials-secret
stringData:
  db-credentials.json: |
    {
      "username": "root",
      "password": "password"
    }
```

Then mount it through the chart values:

```yaml
app:
  volumes:
    - name: db-credentials
      secret:
        secretName: db-credentials-secret
  volumeMounts:
    - name: db-credentials
      mountPath: /mnt/secrets
      readOnly: true
```

#### From the Secrets Store CSI driver

Use this option to read the credentials from an external secret store, for example AWS Secrets Manager.

> **Note:** Install the [Secrets Store CSI driver](https://secrets-store-csi-driver.sigs.k8s.io/getting-started/installation) and the [provider](https://secrets-store-csi-driver.sigs.k8s.io/providers) for your secret store first. Neither the operator nor the chart installs them. Without the driver, the operator Pod stays in `ContainerCreating` and reports a failed mount.

The chart does not create the `SecretProviderClass`, so you have to apply it yourself. Its `objectAlias` becomes the file name:

```yaml
apiVersion: secrets-store.csi.x-k8s.io/v1
kind: SecretProviderClass
metadata:
  name: db-credentials
spec:
  provider: aws
  parameters:
    objects: |
      - objectName: "my/db/credentials"
        objectAlias: "db-credentials.json"
```

> **Note:** The `SecretProviderClass` must live in the namespace of the operator.

Then mount it through the chart values:

```yaml
app:
  volumes:
    - name: db-credentials
      csi:
        driver: secrets-store.csi.k8s.io
        readOnly: true
        volumeAttributes:
          secretProviderClass: db-credentials
  volumeMounts:
    - name: db-credentials
      mountPath: /mnt/secrets
      readOnly: true
```

#### Without the Helm chart

If you deploy the operator directly from the OCI image, set the same `volumes` and `volumeMounts` fields on the Deployment:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: postgresql-operator
spec:
  template:
    spec:
      containers:
        - name: postgresql-operator
          volumeMounts:
            - name: db-credentials
              mountPath: /mnt/secrets
              readOnly: true
      volumes:
        - name: db-credentials
          secret:
            secretName: db-credentials-secret
```
