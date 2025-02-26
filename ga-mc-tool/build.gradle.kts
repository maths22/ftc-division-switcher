import com.github.gradle.node.NodeExtension
import com.github.gradle.node.npm.task.NpmTask
import com.github.gradle.node.variant.VariantComputer
import com.github.gradle.node.variant.computeNodeExec
import com.github.gradle.node.variant.computeNpmScriptFile
import com.github.gradle.node.yarn.task.YarnTask
import com.github.psxpaul.task.ExecFork
import cz.habarta.typescript.generator.EnumMapping
import cz.habarta.typescript.generator.JsonLibrary
import cz.habarta.typescript.generator.TypeScriptFileType
import cz.habarta.typescript.generator.TypeScriptOutputKind

plugins {
    application
    id("com.github.node-gradle.node") version "7.0.1"
    id("cz.habarta.typescript-generator") version "3.2.1263"
    id("intellij-form")
    id("com.github.psxpaul.execfork") version "0.2.0"
}

repositories {
    mavenCentral()
}

group = "com.maths22.ftc"
version = "1.2-SNAPSHOT"
description = "ga-mc-tool"

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
    implementation(group = "io.javalin", name = "javalin", version = "6.4.0")

    implementation(group = "com.google.code.gson", name = "gson", version = "2.10.1")
    implementation(group = "com.google.api-client", name = "google-api-client", version = "2.7.2")
    implementation(group = "com.google.oauth-client", name = "google-oauth-client-java6", version = "1.34.1")
    implementation(group = "com.google.apis", name = "google-api-services-sheets", version = "v4-rev20230526-2.0.0")
    implementation(group = "org.slf4j", name = "slf4j-simple", version = "2.0.11")
    implementation(project(":scoring-client"))
}

// adapted from https://github.com/node-gradle/gradle-node-plugin/blob/cf424265f3b27760bd84280020174c9a50673d7a/src/main/kotlin/com/github/gradle/node/npm/exec/NpmExecRunner.kt

val serveJs = tasks.register("serveJs", ExecFork::class.java) {
    dependsOn(tasks.npmInstall)
    dependsOn(tasks.generateTypeScript)

    // There is a lot of reimplementation/copying of innards of the gradle node plugin, as the right components
    // are not actually exposed via public APIs

    val variantComputer = VariantComputer()

    val additionalBinPathProvider = computeAdditionalBinPath(node, variantComputer)
    val executableAndScriptProvider = computeExecutable(node, variantComputer)

    val argsPrefix =
        if (executableAndScriptProvider.get().script != null) listOf(executableAndScriptProvider.get().script.toString()) else listOf()

    executable = executableAndScriptProvider.get().executable
    args = argsPrefix.plus(listOf("run", "dev")).toMutableList()
    environment = computeEnvironment(mapOf(), additionalBinPathProvider.get())
    workingDir = project.file("${project.projectDir}/src/main/javascript")
}

fun computeEnvironment(environment: Map<String, String>, additionalBinPaths: List<String>): Map<String, String> {
    val execEnvironment = mutableMapOf<String, String>()
    execEnvironment += System.getenv()
    execEnvironment += environment
    if (additionalBinPaths.isNotEmpty()) {
        // Take care of Windows environments that may contain "Path" OR "PATH" - both existing
        // possibly (but not in parallel as of now)
        val pathEnvironmentVariableName = if (execEnvironment["Path"] != null) "Path" else "PATH"
        val actualPath = execEnvironment[pathEnvironmentVariableName]
        val additionalPathsSerialized = additionalBinPaths.joinToString(File.pathSeparator)
        execEnvironment[pathEnvironmentVariableName] =
            "${additionalPathsSerialized}${File.pathSeparator}${actualPath}"
    }
    return execEnvironment
}

// non-public functions extracted from gradle-node-plugin

internal fun <A, B> zip(aProvider: Provider<A>, bProvider: Provider<B>): Provider<Pair<A, B>> {
    return aProvider.flatMap { a -> bProvider.map { b -> Pair(a!!, b!!) } }
}

internal fun <A, B, C> zip(aProvider: Provider<A>, bProvider: Provider<B>, cProvider: Provider<C>):
        Provider<Triple<A, B, C>> {
    return zip(aProvider, bProvider).flatMap { pair -> cProvider.map { c -> Triple(pair.first, pair.second, c!!) } }
}

internal fun <A, B, C, D, E> zip(aProvider: Provider<A>, bProvider: Provider<B>, cProvider: Provider<C>,
                                 dProvider: Provider<D>, eProvider: Provider<E>): Provider<Tuple5<A, B, C, D, E>> {
    return zip(zip(aProvider, bProvider), zip(cProvider, dProvider, eProvider))
        .map { pairs ->
            Tuple5(pairs.first.first, pairs.first.second, pairs.second.first, pairs.second.second,
                pairs.second.third)
        }
}

internal data class Tuple5<A, B, C, D, E>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
    val fifth: E
)

private fun computeAdditionalBinPath(nodeExtension: NodeExtension, variantComputer: VariantComputer): Provider<List<String>> {
    return nodeExtension.download.flatMap { download ->
        if (!download) {
            providers.provider { listOf<String>() }
        }
        val nodeDirProvider = nodeExtension.resolvedNodeDir
        val nodeBinDirProvider = variantComputer.computeNodeBinDir(nodeDirProvider, nodeExtension.resolvedPlatform)
        val npmDirProvider = variantComputer.computeNpmDir(nodeExtension, nodeDirProvider)
        val npmBinDirProvider = variantComputer.computeNpmBinDir(npmDirProvider, nodeExtension.resolvedPlatform)
        zip(npmBinDirProvider, nodeBinDirProvider).map { (npmBinDir, nodeBinDir) ->
            listOf(npmBinDir, nodeBinDir).map { file -> file.asFile.absolutePath }
        }
    }
}

private fun computeExecutable(
    nodeExtension: NodeExtension,
    variantComputer: VariantComputer
): Provider<ExecutableAndScript> {
    val nodeDirProvider = nodeExtension.resolvedNodeDir
    val npmDirProvider = variantComputer.computeNpmDir(nodeExtension, nodeDirProvider)
    val nodeBinDirProvider = variantComputer.computeNodeBinDir(nodeDirProvider, nodeExtension.resolvedPlatform)
    val npmBinDirProvider = variantComputer.computeNpmBinDir(npmDirProvider, nodeExtension.resolvedPlatform)
    val nodeExecProvider = computeNodeExec(nodeExtension, nodeBinDirProvider)
    val executableProvider = variantComputer.computeNpmExec(nodeExtension, npmBinDirProvider)
    val isWindows = nodeExtension.resolvedPlatform.get().isWindows()
    val npmScriptFileProvider = computeNpmScriptFile(nodeDirProvider, "npm", isWindows)
    return zip(
        nodeExtension.download, nodeExtension.nodeProjectDir, executableProvider, nodeExecProvider,
        npmScriptFileProvider
    ).map {
        val (download, nodeProjectDir, executable, nodeExec,
            npmScriptFile) = it
        if (download) {
            val localCommandScript = nodeProjectDir.dir("node_modules/npm/bin")
                .file("npm-cli.js").asFile
            if (localCommandScript.exists()) {
                return@map ExecutableAndScript(nodeExec, localCommandScript.absolutePath)
            } else if (!File(executable).exists()) {
                return@map ExecutableAndScript(nodeExec, npmScriptFile)
            }
        }
        return@map ExecutableAndScript(executable)
    }
}

private data class ExecutableAndScript(
    val executable: String,
    val script: String? = null
)

val buildJs = tasks.register("buildJs", NpmTask::class.java) {
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
        outputFile = project.file("src/main/javascript/src/javaTypes.ts").absolutePath
        tsNoCheck = true
    }
    processResources {
        mustRunAfter(buildJs)
    }
    jar {
        dependsOn(buildJs)
        duplicatesStrategy = DuplicatesStrategy.FAIL
    }
}

afterEvaluate {
    val disableViteServe = (project.properties.getOrDefault("disableViteServe", "false") as String).toBoolean()
    val isDevRun = project.gradle.startParameter.taskNames.map {
        project.rootProject.tasks.findByPath(it)
    }.all {
        it is JavaExec && it.mainClass.get() == application.mainClass.get()
    }

    if(!isDevRun) {
        return@afterEvaluate
    }

    // Add this as a resource when we are going to serve because we need to know
    // where to find the generated resources
    project.tasks.filterIsInstance<JavaExec>().filter {
        it.mainClass.get() == application.mainClass.get()
    }.forEach {
        if(disableViteServe) {
            it.dependsOn(buildJs)
        } else {
            it.dependsOn(serveJs)
            it.systemProperty("serve.port", "8887")
        }
    }
}
