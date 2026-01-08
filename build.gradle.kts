import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    idea
    java
    checkstyle
    id("io.quarkus")
    alias(libs.plugins.jooqPlugin)
}

description = "AboutBits PostgreSQL Operator"
group = "com.aboutbits.postgresql"
version = "0.0.1-SNAPSHOT"

val quarkusPlatformGroupId: String by project
val quarkusPlatformArtifactId: String by project
val quarkusPlatformVersion: String by project

val checkstyleConfig by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    // AboutBits Libraries
    checkstyleConfig(libs.checkstyleConfig)

    /**
     * Quarkus Extensions
     */
    // https://mvnrepository.com/artifact/io.quarkus.platform/quarkus-bom
    implementation(enforcedPlatform("${quarkusPlatformGroupId}:${quarkusPlatformArtifactId}:${quarkusPlatformVersion}"))
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
    compileOnly(libs.jooqMeta)
    // PostgreSQL JDBC Driver for jOOQ generation
    jooqCodegen(libs.postgresql)

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
    // https://mvnrepository.com/artifact/io.quarkus.platform/quarkus-operator-sdk-bom
    implementation(enforcedPlatform("${quarkusPlatformGroupId}:quarkus-operator-sdk-bom:${quarkusPlatformVersion}"))
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

sourceSets {
    main {
        java {
            srcDir("src/generated/jooq/main")
        }
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25

    toolchain {
        languageVersion = JavaLanguageVersion.of(JavaVersion.VERSION_25.majorVersion)
        vendor = JvmVendorSpec.AMAZON
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
}

tasks.quarkusDev {
    // Java 24+ issue. Remove after this has been fixed.
    // https://github.com/quarkusio/quarkus/issues/47769#issuecomment-3148789105
    // https://github.com/quarkusio/quarkus/pull/49920
    jvmArgs = listOf("--add-opens", "java.base/java.lang=ALL-UNNAMED")
}

tasks.withType<Test> {
    useJUnitPlatform()

    systemProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager")
    jvmArgs("--add-opens", "java.base/java.lang=ALL-UNNAMED")

    val mockitoAgent = configurations.testRuntimeClasspath.get().find {
        it.name.contains("mockito-core")
    }
    if (mockitoAgent != null) {
        jvmArgs("-javaagent:${mockitoAgent.absolutePath}")
    }

    testLogging {
        exceptionFormat = TestExceptionFormat.FULL

        info {
            showStandardStreams = !providers.environmentVariable("CI").isPresent
            events(
                *listOfNotNull(
                    TestLogEvent.PASSED,
                    TestLogEvent.SKIPPED,
                    TestLogEvent.FAILED,
                    TestLogEvent.STANDARD_ERROR,
                    if (!providers.environmentVariable("CI").isPresent) TestLogEvent.STANDARD_OUT else null
                ).toTypedArray()
            )
        }
    }

    if (!project.hasProperty("createTestReports")) {
        reports.html.required = false
        reports.junitXml.required = false
    }

    filter {
        if (project.hasProperty("excludeTests")) {
            val excludePatterns = project.property("excludeTests").toString().split(",")
            excludePatterns.forEach { pattern ->
                excludeTestsMatching(pattern.trim())
            }
        }
    }
}

tasks.register<Copy>("checkstyleExtractConfig") {
    description = "Extracts the AboutBits Checkstyle configuration from the classpath."
    group = JavaBasePlugin.CHECK_TASK_NAME

    from(zipTree(checkstyleConfig.singleFile)) {
        include("checkstyle.xml", "checkstyle-suppressions.xml")
    }
    into(layout.projectDirectory.dir("config/checkstyle/"))
}

tasks.withType<Checkstyle>().configureEach {
    dependsOn(tasks.named("checkstyleExtractConfig"))

    reports {
        html.required.set(false)
        xml.required.set(false)
    }
}

checkstyle {
    toolVersion = libs.versions.checkstyle.get()
    isShowViolations = true
    configFile = rootProject.file("config/checkstyle/checkstyle.xml")
    configProperties = mapOf(
        "suppressionFile" to rootProject.file("config/checkstyle/checkstyle-suppressions.xml")
    )
}

jooq {
    configuration {
        jdbc {
            driver = "org.postgresql.Driver"
            url = "jdbc:postgresql://localhost:5432/postgres"
            user = "root"
            password = "password"
        }
        generator {
            database {
                name = "org.jooq.meta.postgres.PostgresDatabase"
                schemata {
                    schema {
                        inputSchema = "pg_catalog"
                    }
                }
                includes = """
                  pg_auth_members
                | pg_authid
                | pg_db_role_setting
                | shobj_description
                """.trimIndent()
                excludes = """
                """.trimIndent()
            }
            generate {
                deprecated = false
                fluentSetters = true
                generatedAnnotation = true
                pojos = false
            }
            target {
                packageName = "it.aboutbits.postgresql.core.infrastructure.persistence"
                directory = "src/generated/jooq/main"
            }
        }
    }
}
