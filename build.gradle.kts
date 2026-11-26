plugins {
    java
}

group = "io.centralmacatuba.mcraft"

val snapshotVersion = providers.fileContents(layout.projectDirectory.file("version.txt"))
    .asText
    .map { it.trim() + "-SNAPSHOT" }

version = providers.gradleProperty("pluginVersion")
    .orElse(snapshotVersion)
    .get()

// Capture the resolved version during configuration; do not access Project from the task action.
val pluginVersionForResources = project.version.toString()

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    constraints {
        // The Paper 1.21.11 API currently brings Guava 33.3.1-jre transitively.
        // Require the first patched release for CVE-2026-102554 on all plugin/test classpaths.
        compileOnly("com.google.guava:guava:33.7.2-jre") {
            version { require("33.7.2-jre") }
            because("Fixes CVE-2026-102554 in the Paper API dependency graph.")
        }
        testImplementation("com.google.guava:guava:33.7.2-jre") {
            version { require("33.7.2-jre") }
            because("Keep MockBukkit test runtime on the patched Guava release.")
        }
    }

    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    // Paper provides Gson at runtime; compileOnly avoids bundling a conflicting copy in the plugin JAR.
    compileOnly("com.google.code.gson:gson:2.13.2")

    testImplementation("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    testImplementation("com.google.code.gson:gson:2.13.2")
    testImplementation("org.junit.jupiter:junit-jupiter:5.12.2")
    testImplementation("org.mockbukkit.mockbukkit:mockbukkit-v1.21:4.116.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.12.2")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
}

tasks.test {
    useJUnitPlatform()
}

tasks.processResources {
    filteringCharset = "UTF-8"
    inputs.property("pluginVersion", pluginVersionForResources)
    filesMatching("plugin.yml") {
        expand("version" to pluginVersionForResources)
    }
}

tasks.jar {
    archiveBaseName.set("McraftVoiceChat")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(rootProject.file("LICENSE")) {
        into("META-INF")
    }
}
