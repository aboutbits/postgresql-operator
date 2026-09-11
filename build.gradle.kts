import net.ltgt.gradle.errorprone.errorprone
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    idea
    java
    id("io.quarkus").apply(false)
    alias(libs.plugins.axionReleasePlugin)
    alias(libs.plugins.errorPronePlugin)
    alias(libs.plugins.jooqPlugin).apply(false)
}

description = "AboutBits PostgreSQL Operator"

scmVersion {
    checks {
        aheadOfRemote = true
        snapshotDependencies = false
        uncommittedChanges = false
    }
    releaseBranchNames = setOf("main")
    releaseOnlyOnReleaseBranches = true
    versionCreator("simple")
}

version = scmVersion.version

allprojects {
    group = "it.aboutbits.postgresql"
    version = rootProject.version
}

subprojects {
    apply(plugin = "java")
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

        // Source code: A category that an Error Prone check already owns is deliberately absent
        options.compilerArgs.add("-Xlint:deprecation,removal,unchecked,cast,rawtypes,divzero,this-escape,identity,text-blocks,dangling-doc-comments,restricted")
        // The build itself: command-line options, path entries, output file collisions
        options.compilerArgs.add("-Xlint:options,path,output-file-clash")

        options.errorprone {
            // The checks live in errorprone.args, see https://github.com/tbroyer/gradle-errorprone-plugin#argument-files
            argumentFiles.from(rootProject.layout.projectDirectory.file("errorprone.args"))
        }
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()

        systemProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager")

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
