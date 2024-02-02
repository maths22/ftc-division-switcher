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
            implementationClass = "com.maths22.gradle.IntellijFormPlugin"
        }
    }
}