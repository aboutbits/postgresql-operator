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
        val githubUser = providers.gradleProperty("gpr.user")
            .orElse(providers.environmentVariable("GITHUB_USER_NAME"))
        val githubToken = providers.gradleProperty("gpr.key")
            .orElse(providers.environmentVariable("GITHUB_ACCESS_TOKEN"))

        fun addGitHubRepo(name: String): MavenArtifactRepository {
            return maven {
                this.name = name
                url = uri("https://maven.pkg.github.com/aboutbits/$name")
                credentials {
                    username = githubUser.orNull
                    password = githubToken.orNull
                }
            }
        }

        // https://docs.gradle.org/current/userguide/best_practices_dependencies.html#use_content_filtering
        exclusiveContent {
            forRepositories(
                addGitHubRepo("java-checkstyle-config"),
                mavenLocal()
            )
            filter {
                includeGroupAndSubgroups("it.aboutbits")
            }
        }

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
