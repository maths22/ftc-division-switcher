package org.firstinspires.ftc.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.kotlin.dsl.configure

class IntellijFormPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val javaExtension = project.extensions.getByType(JavaPluginExtension::class.java)
        val sourceSet = javaExtension.sourceSets.getByName("main")

        val compilerSourceSet = javaExtension.sourceSets.create("formCompiler")
        project.dependencies.add(compilerSourceSet.implementationConfigurationName,
            mapOf("group" to "com.jetbrains.intellij.java", "name" to "java-compiler-ant-tasks", "version" to "233.14015.61"))

        val compileJavaTask = project.tasks.getByName("compileJava")
        sourceSet.java.srcDirs.forEach {
            compileJavaTask.inputs.files(project.fileTree(it).matching{
                include("**/*.form")
            })
        }

        compileJavaTask.doLast {
            project.ant.invokeMethod("taskdef", mapOf(
                "name" to "instrumentIdeaExtensions",
                "classname" to "com.intellij.ant.InstrumentIdeaExtensions",
                "classpath" to project.configurations.getByName(compilerSourceSet.runtimeClasspathConfigurationName).asPath
            ))

            sourceSet.java.srcDirs.forEach {
                project.ant.invokeMethod("instrumentIdeaExtensions", mapOf(
                    "srcDir" to it.toString(),
                    "destDir" to sourceSet.output.classesDirs.singleFile.toString(),
                    "bootClassPath" to sourceSet.runtimeClasspath.asPath,
                    "includeantruntime" to false
                ))
            }
        }
    }
}