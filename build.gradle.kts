import com.github.gradle.node.yarn.task.YarnTask
import cz.habarta.typescript.generator.EnumMapping
import cz.habarta.typescript.generator.JsonLibrary
import cz.habarta.typescript.generator.TypeScriptFileType
import cz.habarta.typescript.generator.TypeScriptOutputKind

plugins {
    application
    id("com.github.node-gradle.node") version "7.0.1"
    id("cz.habarta.typescript-generator") version "3.2.1263"
    id("intellij-form")
}

repositories {
    mavenCentral()

    maven(url = "https://www.jetbrains.com/intellij-repository/releases/")
    maven(url = "https://cache-redirector.jetbrains.com/intellij-dependencies")
}

group = "com.maths22.ftc"
version = "2.0-SNAPSHOT"
description = "division-switcher"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

node {
    download = true
    version = "20.11.0"
}

application {
    mainClass = "com.maths22.ftc.DivisionSwitcher"
}

dependencies {
    implementation(group = "io.javalin", name = "javalin", version = "6.0.0-beta.4")

    implementation(group = "com.google.code.gson", name = "gson", version = "2.10.1")
    implementation(group = "com.google.api-client", name = "google-api-client", version = "2.2.0")
    implementation(group = "com.google.oauth-client", name = "google-oauth-client-java6", version = "1.34.1")
    implementation(group = "com.google.apis", name = "google-api-services-sheets", version = "v4-rev20230526-2.0.0")
    implementation(group = "org.slf4j", name = "slf4j-simple", version = "2.0.11")

    implementation(group = "com.jetbrains.intellij.java", name = "java-gui-forms-rt", version = "233.14015.61")
}

val buildJs = tasks.register("buildJs", YarnTask::class.java) {
    dependsOn(tasks.npmInstall)
    dependsOn(tasks.generateTypeScript)

    args = listOf("run", "build")

    workingDir.set(project.file("src/main/javascript"))
}

tasks {
    generateTypeScript {
        jsonLibrary = JsonLibrary.gson
        classesWithAnnotations = listOf("com.maths22.ftc.TypeScriptExport")
        mapEnum = EnumMapping.asEnum
        outputKind = TypeScriptOutputKind.module
        outputFileType = TypeScriptFileType.implementationFile
        outputFile = project.file("src/main/javascript/javaTypes.ts").absolutePath
    }
    processResources {
        dependsOn(buildJs)
    }
}
