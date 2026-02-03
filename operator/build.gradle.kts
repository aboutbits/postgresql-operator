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
    implementation("io.quarkus:quarkus-kubernetes-client")
    implementation("io.quarkus:quarkus-logging-json")
    implementation("io.quarkus:quarkus-micrometer")
    implementation("io.quarkus:quarkus-micrometer-registry-prometheus")
    implementation("io.quarkus:quarkus-jdbc-postgresql")
    implementation("io.quarkus:quarkus-smallrye-health")

    /**
     * Fabric8 Kubernetes Client
     */
    implementation("io.fabric8:generator-annotations")

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

    /**
     * SCRAM
     */
    implementation(libs.scram.client)

    /**
     * Testing
     */
    testImplementation("io.quarkus:quarkus-junit")
    testImplementation("io.quarkus:quarkus-junit-mockito")
    testImplementation("org.awaitility:awaitility")
    testImplementation(libs.assertj)
    testImplementation(libs.datafaker)
}

tasks.quarkusAppPartsBuild {
    doNotTrackState("Always execute Gradle task quarkusAppPartsBuild to generate the K8s deploy manifest kubernetes.yml, the CRDs, and to publish the Helm chart")
}

tasks.withType<Test> {
    val mockitoAgent = configurations.testRuntimeClasspath.get().find {
        it.name.contains("mockito-core")
    }
    if (mockitoAgent != null) {
        jvmArgs("-javaagent:${mockitoAgent.absolutePath}")
    }
}
