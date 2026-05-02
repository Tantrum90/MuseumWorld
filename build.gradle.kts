plugins {
    id("java-library")
}

group = providers.gradleProperty("group").get()
version = providers.gradleProperty("version").get()
description = "MuseumWorld"

val releaseChannel = providers.gradleProperty("channel").getOrElse("DEV").uppercase()
val pluginVersion = if (releaseChannel == "STABLE") {
    version.toString()
} else {
    "${version}-${releaseChannel}"
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.1.2.build.+")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(25)
}

tasks.withType<Javadoc> {
    (options as StandardJavadocDocletOptions).encoding = "UTF-8"
}

tasks.processResources {
    filteringCharset = "UTF-8"
    val props = mapOf(
        "version" to pluginVersion,
        "description" to (project.description ?: ""),
        "channel" to releaseChannel
    )
    inputs.properties(props)
    filesMatching("paper-plugin.yml") {
        expand(props)
    }
}

tasks.jar {
    archiveBaseName.set("MuseumWorld")
    archiveVersion.set(pluginVersion)
}