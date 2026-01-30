# AboutBits PostgreSQL Operator

AboutBits PostgreSQL Operator is a Kubernetes operator that helps you manage PostgreSQL databases, roles (users), and privileges in a declarative way using Custom Resource Definitions (CRDs).

## Architecture

```
┌──────────────────────────────────────────────────────────────────────────┐
│                            Kubernetes Cluster                            │
│  ┌────────────────────────┐   ┌────────────────────────────────────────┐ │
│  │ PostgreSQL             │   │          PostgreSQL Operator           │ │
│  │ Operator CRDs          │──▶│  ┌──────────────────────────────────┐  │ │
│  │                        │   │  │   ClusterConnection Controller   │  │ │
│  │ ┌────────────────────┐ │   │  ├──────────────────────────────────┤  │ │
│  │ │ ClusterConnection  │ │   │  │       Database Controller        │  │ │
│  │ └─────────▲──────────┘ │   │  ├──────────────────────────────────┤  │ │
│  │           │            │   │  │        Schema Controller         │  │ │
│  │ ┌─────────┴──────────┐ │   │  ├──────────────────────────────────┤  │ │
│  │ │ - Database         │ │   │  │         Role Controller          │  │ │
│  │ │ - Schema           │ │   │  ├──────────────────────────────────┤  │ │
│  │ │ - Role             │ │   │  │         Grant Controller         │  │ │
│  │ │ - Grant            │ │   │  ├──────────────────────────────────┤  │ │
│  │ │ - DefaultPrivilege │ │   │  │   DefaultPrivilege Controller    │  │ │
│  │ └────────────────────┘ │   │  └──────────────────────────────────┘  │ │
│  └────────────────────────┘   └───────────────────┬────────────────────┘ │
│                                                   │                      │
│                                  ┌────────────────▼─────────────────┐    │
│                                  │        PostgreSQL Server         │    │
│                                  │            (JDBC/SQL)            │    │
│                                  └──────────────────────────────────┘    │
└──────────────────────────────────────────────────────────────────────────┘
```

## Installation

### Helm Chart

```bash
helm install postgresql-operator https://github.com/aboutbits/postgresql-operator/releases/download/v0.2.4/postgresql-operator-0.2.4.tgz
```

With the Helm chart, the Custom Resource Definitions (CRDs) are installed automatically.  
However, if you deploy the operator directly from the OCI image, the CRDs are not automatically applied and must be installed separately.  
See the release notes for the [latest version](https://github.com/aboutbits/postgresql-operator/releases/latest) for more information.

## Usage

This operator allows you to manage PostgreSQL resources using Kubernetes manifests.  
Further documentation of each Custom Resource can be found here:

- [ClusterConnection](docs/cluster-connection.md) – Define a connection to a PostgreSQL cluster.
- [Database](docs/database.md) - Manage databases.
- [Role](docs/role.md) - Manage roles (users).
- [Schema](docs/schema.md) - Manage schemas.
- [Grant](docs/grant.md) - Manage privileges.
- [DefaultPrivilege](docs/default-privilege.md) - Manage default privileges.

### Declarative Management

The Operator leverages the power of Kubernetes Custom Resource Definitions (CRDs) to manage PostgreSQL resources declaratively.  
This means the Operator continuously reconciles the state of the cluster to match your desired state defined in the CRs.

**Updates**

If you modify a mutable field in a Custom Resource, the Operator automatically applies these changes to the PostgreSQL cluster.  
The operator, for example, handles:

- Changing a `Role`'s flags, password, or comment.
- Updating the `Role` password if the password in the referenced Secret changes.
- Updating `Grant`/`DefaultPrivilege` objects or privileges.
- Changing a `Schema` or `Database` owner.

**Deletions**

Deleting a Custom Resource triggers the cleanup of the corresponding PostgreSQL object:

- For `Grant`, `DefaultPrivilege`, and `Role` resources, the operator revokes privileges or drops the role.
- For `Database` and `Schema` resources, the behavior depends on the `reclaimPolicy` (defaults to `Retain` to prevent accidental data loss).

This ensures that your PostgreSQL cluster configuration always reflects your Kubernetes manifests, simplifying management and automation.

### Showcase

The following example shows how to set up a connection to a PostgreSQL cluster, create a database and schema, a login role (user), and configure permissions.

If you want to try this out locally, you can follow the [Docker Environment](docs/docker-environment.md) guide.

```yaml
# Define a ClusterConnection resource to connect to a PostgreSQL cluster.
---
apiVersion: v1
kind: Secret
metadata:
  name: my-postgres-secret
type: kubernetes.io/basic-auth
stringData:
  username: postgres
  password: password
---
apiVersion: postgresql.aboutbits.it/v1
kind: ClusterConnection
metadata:
  name: my-postgres-connection
spec:
  host: postgres-host
  port: 5432
  database: postgres
  adminSecretRef:
    name: my-postgres-secret

# Create a Database
---
apiVersion: postgresql.aboutbits.it/v1
kind: Database
metadata:
  name: my-database
spec:
  clusterRef:
    name: my-postgres-connection
  name: my_app_db
  reclaimPolicy: Retain
  owner: dba_user

# Create a Schema
---
apiVersion: postgresql.aboutbits.it/v1
kind: Schema
metadata:
  name: my-schema
spec:
  clusterRef:
    name: my-postgres-connection
  name: my_app_schema
  reclaimPolicy: Retain
  owner: dba_user

# Create a Login Role (User)
---
apiVersion: v1
kind: Secret
metadata:
  name: my-app-user-secret
type: kubernetes.io/basic-auth
stringData:
  password: secret_password
---
apiVersion: postgresql.aboutbits.it/v1
kind: Role
metadata:
  name: my-role
spec:
  clusterRef:
    name: my-postgres-connection
  name: my_app_user
  passwordSecretRef:
    name: my-app-user-secret
  flags:
    createdb: false

# Configure Permissions
---
apiVersion: postgresql.aboutbits.it/v1
kind: Grant
metadata:
  name: my-grant
spec:
  clusterRef:
    name: my-postgres-connection
  database: my_app_db
  role: my_app_user
  objectType: schema
  schema: my_app_schema
  privileges:
    - usage

# Configure Default Privileges
---
apiVersion: postgresql.aboutbits.it/v1
kind: DefaultPrivilege
metadata:
  name: my-default-privilege
spec:
  clusterRef:
    name: my-postgres-connection
  database: my_app_db
  role: my_app_user
  owner: shared_developer_user
  objectType: table
  schema: my_app_schema
  privileges:
    - select
    - insert
    - update
    - delete
```

## Contribute

These instructions will get you a copy of the project up and running on your local machine for development and testing purposes.

### Prerequisites

To build the project, the following prerequisites must be met:

- Java JDK (e.g. [OpenJDK](https://openjdk.java.net/))
- [Gradle](https://gradle.org/) (Optional)
- [Docker](https://www.docker.com/)

### Setup

To get started, call:

```bash
make init
```

### Development

You can run your application in dev mode that enables live coding and continuous testing using:

```bash
make run

# or

./gradlew :operator:quarkusDev
```

The app service will be available at [http://localhost:8080](http://localhost:8080).

You can also use the Dev UI (available in dev mode only) at [http://localhost:8080/q/dev-ui/welcome](http://localhost:8080/q/dev-ui/welcome).  
There you can find a set of guides for the used extensions in this project to get up-to-speed faster.

To execute the test without continuous testing in the dev mode, you can run the following command:

```bash
make test

# or

./gradlew :operator:test
```

#### Run the project as a service in IntelliJ

1. Open the `Services` tool on the left side of the IDE
2. Click on "+" and select "Quarkus"

Afterward, the project can be started in IntelliJ by navigating to `Run` -> `Run '...'`.

#### Generating jOOQ sources

To update the generated jOOQ sources from schema `pg_catalog`, you need to run the application in dev mode first to start the PostgreSQL Dev Service:

```bash
make run

# or

./gradlew :operator:quarkusDev
```

Once the application is running (and the database is available on port 5432), run the following command:

```bash
make generate-jooq

# or

./gradlew :generated:jooqCodegen
```

### Docker Environment

See [Docker Environment](docs/docker-environment.md) for setting up a local development environment using Quarkus Dev Services.

## Build

The application can be packaged using:

```bash
./gradlew :operator:build
```

It produces the `quarkus-run.jar` file in the `build/quarkus-app/` directory.
Be aware that it’s not an _über-jar_ as the dependencies are copied into the `build/quarkus-app/lib/` directory.

The application is now runnable using `java -jar build/quarkus-app/quarkus-run.jar`.

If you want to build an _über-jar_, execute the following command:

```bash
./gradlew :operator:build -Dquarkus.package.jar.type=uber-jar
```

The application, packaged as an _über-jar_, is now runnable using `java -jar build/*-runner.jar`.

### Creating a native executable

You can create a native executable using:

```bash
./gradlew :operator:build -Dquarkus.native.enabled=true
```

Or, if you don't have GraalVM installed, you can run the native executable build in a container using:

```bash
./gradlew :operator:build -Dquarkus.native.enabled=true -Dquarkus.native.container-build=true
```

You can then execute your native executable with: `./build/postgresql-operator-1.0.0-SNAPSHOT-runner`

## Information

About Bits is a company based in South Tyrol, Italy. You can find more information about us on [our website](https://aboutbits.it).

### Support

For support, please contact [info@aboutbits.it](mailto:info@aboutbits.it).

### Credits

- [All Contributors](https://github.com/aboutbits/postgresql-operator/graphs/contributors)

### License

The MIT License (MIT). Please see the [license file](LICENSE) for more information.
