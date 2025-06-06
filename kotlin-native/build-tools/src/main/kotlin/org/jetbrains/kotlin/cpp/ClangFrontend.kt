/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.cpp

import org.gradle.api.DefaultTask
import org.gradle.api.file.*
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkerExecutor
import org.jetbrains.kotlin.ExecClang
import org.jetbrains.kotlin.bitcode.CompileToBitcodePlugin
import org.jetbrains.kotlin.konan.target.PlatformManager
import org.jetbrains.kotlin.platformManagerProvider
import javax.inject.Inject

private abstract class ClangFrontendJob : WorkAction<ClangFrontendJob.Parameters> {
    interface Parameters : WorkParameters {
        val workingDirectory: DirectoryProperty
        val targetName: Property<String>
        val inputPathRelativeToWorkingDir: Property<String>
        val outputFile: RegularFileProperty
        val compilerExecutable: Property<String>
        val arguments: ListProperty<String>
        val platformManager: Property<PlatformManager>
    }

    @get:Inject
    abstract val objects: ObjectFactory

    override fun execute() {
        with(parameters) {
            val execClang = ExecClang.create(objects, platformManager.get())

            outputFile.get().asFile.parentFile.mkdirs()
            execClang.execKonanClang(targetName.get(), compilerExecutable.get()) {
                workingDir = workingDirectory.asFile.get()
                args = arguments.get() + listOf(inputPathRelativeToWorkingDir.get(), "-o", outputFile.get().asFile.absolutePath)
            }
        }
    }
}

/**
 * Compiling [inputFiles] with clang into LLVM bitcode in [outputFiles].
 *
 * @see CompileToBitcodePlugin
 */
@CacheableTask
open class ClangFrontend @Inject constructor(
        objects: ObjectFactory,
        private val workerExecutor: WorkerExecutor,
        private val layout: ProjectLayout,
) : DefaultTask() {
    protected data class WorkUnit(
            /**
             * Input file for [compiler].
             */
            @get:Input
            val inputPathRelativeToWorkingDir: String,

            /**
             * Output file produced by [compiler] for [inputPathRelativeToWorkingDir]
             */
            @get:OutputFile
            val outputFile: RegularFile,
    )

    /**
     * Where to put bitcode files generated by clang.
     */
    @get:Internal("Used to compute workUnits")
    val outputDirectory: DirectoryProperty = objects.directoryProperty()

    /**
     * Source files to compile from.
     */
    @get:SkipWhenEmpty
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE) // manually computed: [workUnits]
    val inputFiles: ConfigurableFileCollection = objects.fileCollection()

    /**
     * Will select the appropriate compiler and additional flags.
     */
    // TODO: Consider specifying full clang execution here and configure it from the plugin.
    @get:Input
    val targetName: Property<String> = objects.property(String::class.java)

    /**
     * The compiler to be used.
     *
     * Currently only `clang` and `clang++` are supported.
     */
    @get:Input
    val compiler: Property<String> = objects.property(String::class.java)

    /**
     * Extra arguments for [compiler].
     */
    @get:Input
    val arguments: ListProperty<String> = objects.listProperty(String::class.java)

    /**
     * Working directory for [compiler].
     *
     * All inputs will be passed to the compiler as relative paths to this directory.
     */
    @get:Internal("Used to compute workUnits and headersPathsRelativeToWorkingDir")
    val workingDirectory: DirectoryProperty = objects.directoryProperty()

    /**
     * Locations to search for headers.
     *
     * Will be passed to the compiler as `-I…` and will also be used to compute task dependencies: recompile if the headers change.
     */
    @get:Nested
    val headersDirs: CppHeadersSet = objects.cppHeadersSet().apply {
        workingDir.set(this@ClangFrontend.workingDirectory)
    }

    /**
     * [WorkUnit]s for [compiler].
     *
     * For each [WorkUnit] one [compiler] invocation will be executed.
     */
    @get:Nested
    protected val workUnits = workingDirectory.zip(outputDirectory) { l, r -> l to r }.zip(inputFiles.elements) { (base, out), files ->
        files.map {
            val relativePath = it.asFile.toRelativeString(base.asFile)
            WorkUnit(relativePath, out.file(relativePath.replaceAfterLast(".", "bc")))
        }
    }

    @get:Nested
    protected val platformManagerProvider = objects.platformManagerProvider(project)

    @TaskAction
    fun compile() {
        val workQueue = workerExecutor.noIsolation()

        workUnits.get().forEach { workUnit ->
            workQueue.submit(ClangFrontendJob::class.java) {
                workingDirectory.set(this@ClangFrontend.workingDirectory)
                targetName.set(this@ClangFrontend.targetName)
                inputPathRelativeToWorkingDir.set(workUnit.inputPathRelativeToWorkingDir)
                outputFile.set(workUnit.outputFile)
                compilerExecutable.set(this@ClangFrontend.compiler)
                arguments.set(defaultCompilerFlags(this@ClangFrontend.headersDirs))
                arguments.addAll(this@ClangFrontend.arguments)
                platformManager.set(this@ClangFrontend.platformManagerProvider.platformManager)
            }
        }
    }

    companion object {
        internal fun defaultCompilerFlags(headersDirs: CppHeadersSet): List<String> = buildList {
            add("-c")
            add("-emit-llvm")
            addAll(headersDirs.asCompilerArguments.get())
        }
    }
}
