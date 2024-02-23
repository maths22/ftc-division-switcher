plugins {
    `java-library`
    id("intellij-form")
}

repositories {
    mavenCentral()
}

group = "com.maths22.ftc"
version = "1.0-SNAPSHOT"
description = "scoring-client"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

dependencies {
    implementation(group = "org.slf4j", name = "slf4j-simple", version = "2.0.11")
    implementation(group = "com.google.code.gson", name = "gson", version = "2.10.1")
}