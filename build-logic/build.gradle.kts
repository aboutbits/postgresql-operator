plugins {
    `java-gradle-plugin`
}

dependencies {
    implementation(libs.spotlessPluginGradle)

    testImplementation(platform(libs.junitBom))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    // The plugin runs in the Gradle daemon, so it must not need a newer JVM than the daemon has
    options.release = 21
}

tasks.test {
    useJUnitPlatform()
}

gradlePlugin {
    plugins {
        create("javaFormat") {
            id = "it.aboutbits.java-format"
            implementationClass = "it.aboutbits.gradle.format.JavaFormatPlugin"
        }
    }
}
