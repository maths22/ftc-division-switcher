plugins {
    `java-gradle-plugin`
    `kotlin-dsl`
}

repositories {
    mavenCentral()
}

dependencies {
}

gradlePlugin {
    plugins {
        create("intellij-form") {
            id = "intellij-form"
            implementationClass = "org.firstinspires.ftc.gradle.IntellijFormPlugin"
        }
    }
}