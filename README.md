# AboutBits PostgreSQL Operator

## Getting started

These instructions will get you a copy of the project up and running on your local machine for development and testing purposes.

### Prerequisites

To build the project, the following prerequisites must be met:

- Java JDK (e.g. [OpenJDK](https://openjdk.java.net/))
- [Gradle](https://gradle.org/) (Optional)
- [Docker](https://www.docker.com/)

### Setup configuration

To get started, call:

```bash
make init
```

### Running the project in the console

You can run your application in dev mode that enables live coding and continuous testing using:

```shell script
make run

# or

./gradlew quarkusDev
```

The app service will be available at http://localhost:8080,
and you can also use the Dev UI (available in dev mode only) at <http://localhost:8080/q/dev/>.

To execute the test without continuous testing in the dev mode, you can run the following command:

```bash
make test

# or

./gradlew test
```

### Run the project as a service in IntelliJ

1. Open the `Services` tool on the left side of the IDE
2. Click on "+" and select "Quarkus"

Afterward, the project can be started in IntelliJ by navigating to `Run` -> `Run '...'`.

## Test the CRD on the Dev Services cluster and AI assistance

This example demonstrates how to set up a local development environment using Quarkus Dev Services and AI assistance to generate Kubernetes resources.

### 1. Configure Kubeconfig from Dev Services

When running in dev mode (`make run` or via IntelliJ), Quarkus starts the pre-configured K3s and PostgreSQL Dev Services.

1.  Access the Quarkus Dev UI at [http://localhost:8080/q/dev-ui/dev-services](http://localhost:8080/q/dev-ui/dev-services).
2.  Locate the properties for the `kubernetes-client` Dev Service.
3.  Ask an AI to convert these properties into a **Kubeconfig YAML** format.
4.  Merge this configuration into your local `~/.kube/config`. This allows your local environment to communicate with the ephemeral Kubernetes cluster provided by Dev Services.

### 2. Create PostgreSQL Connection and Secret

For the `postgresql` Dev Service, you can generate the necessary Custom Resources to test the operator:

1.  From the Dev UI, get the `postgresql` Dev Service properties (username, password, host, port).
2.  Ask the AI to convert the `postgresql` Dev Service properties to a **Basic Auth Secret** and a **ClusterConnection** CR instance.
3.  Provide the AI with the `ClusterConnection` CRD definition from `build/kubernetes/clusterconnections.postgresql.aboutbits.it-v1.yml` as a reference.
4.  Apply the generated files using IntelliJ or `kubectl`.
    ![Apply Cluster Connection](docs/apply-cluster-connection.png)

**Example Secret (`secret.yml`):**

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: quarkus-db-secret
  labels:
    app.kubernetes.io/name: quarkus-postgres
type: kubernetes.io/basic-auth
stringData:
  # extracted from quarkus.datasource.username
  username: root
  # extracted from quarkus.datasource.password
  password: password
```

**Example ClusterConnection (`cluster-connection.yml`):**

```yaml
apiVersion: postgresql.aboutbits.it/v1
kind: ClusterConnection
metadata:
  name: quarkus-postgres-connection
spec:
  adminSecretRef:
    name: quarkus-db-secret
  host: localhost
  port: 5432
  maintenanceDatabase: postgres
```

![Established Cluster Connection](docs/established-cluster-connection.png)

### 3. Create a Role

Similarly, you can create a `Role` resource:

1.  Ask the AI to convert the desired role properties to a **Role** CR instance.
2.  Provide the AI with the `Role` CRD definition from `build/kubernetes/roles.postgresql.aboutbits.it-v1.yml` as a reference.
3.  Apply the file using IntelliJ or `kubectl`.

**Example Role (`role.yml`):**

```yaml
apiVersion: postgresql.aboutbits.it/v1
kind: Role
metadata:
  name: test-role-from-crd
spec:
  # The actual name of the role to be created in the PostgreSQL database
  name: test-role-from-crd
  comment: It simply works
  # Connects this role definition to the specific Postgres ClusterConnection CR instance
  clusterRef:
    name: quarkus-postgres-connection
  flags:
    createdb: true
    validUntil: "2026-12-31T23:59:59Z"
```

![Created Role](docs/created-role.png)
![Role in pg_authid](docs/role-in-table-pg-authid.png)

## Packaging and running the application

The application can be packaged using:

```shell script
./gradlew build
```

It produces the `quarkus-run.jar` file in the `build/quarkus-app/` directory.
Be aware that it’s not an _über-jar_ as the dependencies are copied into the `build/quarkus-app/lib/` directory.

The application is now runnable using `java -jar build/quarkus-app/quarkus-run.jar`.

If you want to build an _über-jar_, execute the following command:

```shell script
./gradlew build -Dquarkus.package.jar.type=uber-jar
```

The application, packaged as an _über-jar_, is now runnable using `java -jar build/*-runner.jar`.

## Creating a native executable

You can create a native executable using:

```shell script
./gradlew build -Dquarkus.native.enabled=true
```

Or, if you don't have GraalVM installed, you can run the native executable build in a container using:

```shell script
./gradlew build -Dquarkus.native.enabled=true -Dquarkus.native.container-build=true
```

You can then execute your native executable with: `./build/postgresql-operator-1.0.0-SNAPSHOT-runner`

If you want to learn more about building native executables, please consult <https://quarkus.io/guides/gradle-tooling>.

## Related Guides

- Operator SDK ([guide](https://docs.quarkiverse.io/quarkus-operator-sdk/dev/index.html)): Quarkus extension for the Java Operator SDK (https://javaoperatorsdk.io)
- Helm ([guide](https://docs.quarkiverse.io/quarkus-helm/dev/index.html)): Quarkus extension for Kubernetes Helm charts
- SmallRye Health ([guide](https://quarkus.io/guides/smallrye-health)): Monitor service health
- Micrometer metrics ([guide](https://quarkus.io/guides/micrometer)): Instrument the runtime and your application with dimensional metrics using Micrometer.
- YAML Configuration ([guide](https://quarkus.io/guides/config-yaml)): Use YAML to configure your Quarkus application
