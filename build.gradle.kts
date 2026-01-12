import net.ltgt.gradle.errorprone.CheckSeverity
import net.ltgt.gradle.errorprone.errorprone
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    idea
    java
    checkstyle
    id("io.quarkus").apply(false)
    alias(libs.plugins.errorPronePlugin)
    alias(libs.plugins.jooqPlugin).apply(false)
}

description = "AboutBits PostgreSQL Operator"

allprojects {
    group = "it.aboutbits.postgresql"
    version = "0.0.1-SNAPSHOT"

    tasks.withType<Checkstyle>().configureEach {
        dependsOn(":checkstyleExtractConfig")

        reports {
            html.required.set(false)
            xml.required.set(false)
        }
    }
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "checkstyle")
    apply(plugin = rootProject.libs.plugins.errorPronePlugin.get().pluginId)

    java {
        sourceCompatibility = JavaVersion.VERSION_25
        targetCompatibility = JavaVersion.VERSION_25

        toolchain {
            languageVersion = JavaLanguageVersion.of(JavaVersion.VERSION_25.majorVersion)
            vendor = JvmVendorSpec.AMAZON
        }
    }

    val quarkusPlatformGroupId: String by rootProject
    val quarkusPlatformArtifactId: String by rootProject
    val quarkusPlatformVersion: String by rootProject

    dependencies {
        /**
         * Quarkus
         */
        // https://mvnrepository.com/artifact/io.quarkus.platform/quarkus-bom
        implementation(enforcedPlatform("${quarkusPlatformGroupId}:${quarkusPlatformArtifactId}:${quarkusPlatformVersion}"))
        // https://mvnrepository.com/artifact/io.quarkus.platform/quarkus-operator-sdk-bom
        implementation(enforcedPlatform("${quarkusPlatformGroupId}:quarkus-operator-sdk-bom:${quarkusPlatformVersion}"))

        /**
         * NullAway
         */
        errorprone(rootProject.libs.errorProne)
        errorprone(rootProject.libs.nullAway)
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.compilerArgs.add("-parameters")

        options.errorprone {
            check("NullAway", CheckSeverity.ERROR)
            option("NullAway:AnnotatedPackages", "it.aboutbits.postgresql")
        }
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()

        systemProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager")
        jvmArgs("--add-opens", "java.base/java.lang=ALL-UNNAMED")

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
}

val checkstyleConfig: Configuration by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    /**
     * AboutBits Libraries
     */
    checkstyleConfig(libs.checkstyleConfig)
}

tasks.register<Copy>("checkstyleExtractConfig") {
    description = "Extracts the AboutBits Checkstyle configuration from the classpath."
    group = JavaBasePlugin.CHECK_TASK_NAME

    from(zipTree(checkstyleConfig.singleFile)) {
        include("checkstyle.xml", "checkstyle-suppressions.xml")
    }
    into(layout.projectDirectory.dir("config/checkstyle/"))
}

checkstyle {
    toolVersion = libs.versions.checkstyle.get()
    isShowViolations = true
    configFile = rootProject.file("config/checkstyle/checkstyle.xml")
    configProperties = mapOf(
        "suppressionFile" to rootProject.file("config/checkstyle/checkstyle-suppressions.xml")
    )
}
