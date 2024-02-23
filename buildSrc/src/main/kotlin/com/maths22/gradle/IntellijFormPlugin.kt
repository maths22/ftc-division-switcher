package com.maths22.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.maven
import org.gradle.kotlin.dsl.repositories
import org.gradle.kotlin.dsl.withGroovyBuilder

class IntellijFormPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val javaExtension = project.extensions.getByType(JavaPluginExtension::class.java)
        val sourceSet = javaExtension.sourceSets.getByName("main")
        project.repositories {
            maven(url = "https://www.jetbrains.com/intellij-repository/releases/")
            maven(url = "https://cache-redirector.jetbrains.com/intellij-dependencies")
        }

        val compilerSourceSet = javaExtension.sourceSets.create("formCompiler")
        project.dependencies.add(compilerSourceSet.implementationConfigurationName,
            mapOf("group" to "com.jetbrains.intellij.java", "name" to "java-compiler-ant-tasks", "version" to "233.14015.61"))
        project.dependencies.add("implementation",
            mapOf("group" to "com.jetbrains.intellij.java", "name" to "java-gui-forms-rt", "version" to "233.14015.61"))

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
            project.ant.invokeMethod("typedef", mapOf(
                "name" to "prefixedpath",
                "classname" to "com.intellij.ant.PrefixedPath",
                "classpath" to project.configurations.getByName(compilerSourceSet.runtimeClasspathConfigurationName).asPath
            ))

            sourceSet.java.srcDirs.forEach {srcDir ->
                    val dirs = mutableListOf(srcDir.toString())
                    project.configurations.getByName("implementation").dependencies.withType(ProjectDependency::class.java).forEach {
                        val depJavaExtension = it.dependencyProject.extensions.getByType(JavaPluginExtension::class.java)
                        val depSourceSet = depJavaExtension.sourceSets.getByName("main")
                        dirs.add(depSourceSet.java.srcDirs.single().toString())
                    }

                    ant.withGroovyBuilder {
                    "instrumentIdeaExtensions"(
                        "srcDir" to srcDir.toString(),
                        "destDir" to sourceSet.output.classesDirs.singleFile.toString(),
                        "bootClassPath" to sourceSet.runtimeClasspath.asPath,
                        "includeantruntime" to false) {
                        "nestedformdirs" {
                            "prefixedpath" {
                                dirs.forEach {
                                    "dirset"("dir" to it)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}