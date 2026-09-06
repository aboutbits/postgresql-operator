rootProject.name="postgresql-operator"

include("operator")
include("generated")

pluginManagement {
    val quarkusPluginVersion: String by settings
    val quarkusPluginId: String by settings
    repositories {
        mavenCentral()
        gradlePluginPortal()
        mavenLocal()
    }
    plugins {
        id(quarkusPluginId) version quarkusPluginVersion
    }
}

// https://docs.gradle.org/current/userguide/best_practices_dependencies.html#set_up_repositories_in_settings
@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    // This is a best practice that ensures all projects use the repositories defined here.
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

    repositories {
        mavenCentral()
        mavenLocal()
    }
}

plugins {
    // https://docs.gradle.org/current/userguide/toolchains.html#sec:provisioning
    // https://plugins.gradle.org/plugin/org.gradle.toolchains.foojay-resolver-convention
    // https://github.com/gradle/foojay-toolchains
    id("org.gradle.toolchains.foojay-resolver-convention").version("1.0.0")
}
