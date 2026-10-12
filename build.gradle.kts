plugins {
    java
}

group = "dev.powderpass"
version = "1.0.0"
description = "PowderPass - a physics-driven alpine resort engine for Paper"

val paperVersion = "1.21.11-R0.1-SNAPSHOT"

repositories {
    // A locally built paper-api (e.g. from PaperMC sources) is used first when present.
    mavenLocal {
        content {
            includeModule("io.papermc.paper", "paper-api")
            includeModule("com.mojang", "brigadier")
        }
    }
    maven("https://repo.papermc.io/repository/maven-public/") {
        content {
            includeGroup("io.papermc.paper")
            includeGroup("com.mojang")
            includeGroup("net.md-5")
        }
    }
    mavenCentral()
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:$paperVersion")

    testImplementation("io.papermc.paper:paper-api:$paperVersion")
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
    options.compilerArgs.addAll(listOf("-Xlint:deprecation", "-Xlint:unchecked"))
}

tasks.processResources {
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") { expand(props) }
}

tasks.test {
    useJUnitPlatform()
    testLogging { events("failed"); exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
}

tasks.jar {
    archiveFileName.set("PowderPass-${project.version}.jar")
}
