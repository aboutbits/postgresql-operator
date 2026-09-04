plugins {
    id("io.quarkus")
}

dependencies {
    /**
     * Quarkus Extensions
     */
    implementation("io.quarkus:quarkus-arc")
    implementation("io.quarkus:quarkus-config-yaml")
    implementation("io.quarkus:quarkus-jackson")
    implementation("io.quarkus:quarkus-jdbc-postgresql")
    implementation("io.quarkus:quarkus-kubernetes-client")
    implementation("io.quarkus:quarkus-logging-json")
    implementation("io.quarkus:quarkus-micrometer")
    implementation("io.quarkus:quarkus-micrometer-registry-prometheus")
    implementation("io.quarkus:quarkus-smallrye-health")

    /**
     * Fabric8 Kubernetes Client
     */
    implementation("io.fabric8:generator-annotations")
    implementation("io.fabric8:crd-generator-api-v2")

    /**
     * jOOQ
     */
    implementation(libs.jooq)
    implementation(project(":generated"))

    /**
     * JSpecify
     */
    implementation(libs.jspecify)

    /**
     * Lombok
     */
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    testImplementation(libs.lombok)
    testAnnotationProcessor(libs.lombok)

    /**
     * Quarkiverse Helm
     */
    implementation(libs.quarkiverse.helm)

    /**
     * Quarkiverse Operator SDK
     */
    implementation("io.quarkiverse.operatorsdk:quarkus-operator-sdk")
    implementation("io.quarkiverse.operatorsdk:quarkus-operator-sdk-annotations")

    /**
     * SCRAM
     */
    implementation(libs.scram.client)

    /**
     * Testing
     */
    testImplementation("io.quarkus:quarkus-junit")
    testImplementation("io.quarkus:quarkus-junit-mockito")
    testImplementation("io.fabric8:kubernetes-server-mock")
    testImplementation("org.awaitility:awaitility")
    testImplementation(libs.assertj)
    testImplementation(libs.datafaker)
}

tasks.quarkusAppPartsBuild {
    doNotTrackState("Always execute Gradle task quarkusAppPartsBuild to generate the K8s deploy manifest kubernetes.yml, the CRDs, and to publish the Helm chart")
}

val mockitoAgentProvider = configurations.named("testRuntimeClasspath").map { classpath ->
    classpath.find { it.name.contains("mockito-core") }
}

tasks.withType<Test>().configureEach {
    // Required for the HelmTest
    dependsOn(tasks.quarkusAppPartsBuild)

    jvmArgumentProviders.add(MockitoArgumentProvider(mockitoAgentProvider))
}

class MockitoArgumentProvider(
    @get:Optional
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    val agentProvider: Provider<File>
) : CommandLineArgumentProvider {
    override fun asArguments(): Iterable<String> {
        val agentFile = agentProvider.orNull
        return if (agentFile != null) {
            listOf("-javaagent:${agentFile.absolutePath}")
        } else {
            emptyList()
        }
    }
}
