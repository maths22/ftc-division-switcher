plugins {
    application
    id("intellij-form")
}

repositories {
    mavenCentral()
}

group = "com.maths22.ftc"
version = "1.1-SNAPSHOT"
description = "companion-switcher"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

application {
    mainClass = "com.maths22.ftc.companion.Switcher"
}

dependencies {
    implementation(group = "org.slf4j", name = "slf4j-simple", version = "2.0.11")
    implementation(project(":scoring-client"))
}
