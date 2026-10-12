plugins {
    java
}

group = "com.powderpass"
version = "1.0.0"
description = "Backrooms: a seven-level alternate dimension built from custom worlds and display entities."

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

repositories {
    // Optional local mirror (used when repo.papermc.io is unreachable): -PlocalPaperRepo=/path/to/maven/repo
    (findProperty("localPaperRepo") as String?)?.let { maven { url = uri(it) } }
    maven("https://repo.papermc.io/repository/maven-public/")
    mavenCentral()
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")

    testImplementation("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    testImplementation("org.mockbukkit.mockbukkit:mockbukkit-v1.21:4.116.3")
    testImplementation(platform("org.junit:junit-bom:6.0.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
    options.compilerArgs.addAll(listOf("-Xlint:all,-processing,-serial", "-Xlint:-deprecation"))
}

tasks.test {
    useJUnitPlatform()
    maxHeapSize = "1g"
    testLogging { events("failed"); showStandardStreams = false; exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
}

tasks.processResources {
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filesMatching("plugin.yml") { expand(props) }
}

tasks.jar {
    archiveFileName.set("Backrooms-${project.version}.jar")
}
