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
