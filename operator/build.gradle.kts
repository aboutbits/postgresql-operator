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
    testImplementation("io.quarkus:quarkus-junit5")
    testImplementation("io.quarkus:quarkus-junit5-mockito")
    testImplementation("org.awaitility:awaitility")
    testImplementation(libs.assertj)
    testImplementation(libs.datafaker)
}

tasks.quarkusDev {
    // Java 24+ issue. Remove after this has been fixed.
    // https://github.com/quarkusio/quarkus/issues/47769#issuecomment-3148789105
    // https://github.com/quarkusio/quarkus/pull/49920
    jvmArgs = listOf("--add-opens", "java.base/java.lang=ALL-UNNAMED")
}

tasks.withType<Test> {
    val mockitoAgent = configurations.testRuntimeClasspath.get().find {
        it.name.contains("mockito-core")
    }
    if (mockitoAgent != null) {
        jvmArgs("-javaagent:${mockitoAgent.absolutePath}")
    }
}
