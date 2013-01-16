/*
 * Copyright 2010-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.jet.jps.build;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jet.cli.common.messages.MessageCollector;
import org.jetbrains.jet.compiler.runner.CompilerRunnerUtil;
import org.jetbrains.jet.config.CompilerConfiguration;
import org.jetbrains.jet.utils.PathUtil;
import org.jetbrains.jps.builders.BuildOutputConsumer;
import org.jetbrains.jps.builders.BuildRootDescriptor;
import org.jetbrains.jps.builders.DirtyFilesHolder;
import org.jetbrains.jps.builders.FileProcessor;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.ProjectBuildException;
import org.jetbrains.jps.incremental.TargetBuilder;
import org.jetbrains.jps.incremental.messages.ProgressMessage;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.kotlin.compiler.CompilerConfigurationKeys;
import org.jetbrains.kotlin.compiler.JsCompilerConfigurationKeys;
import org.jetbrains.kotlin.compiler.ModuleInfoProvider;
import org.jetbrains.kotlin.compiler.OutputConsumer;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class KotlinTargetBuilder extends TargetBuilder<BuildRootDescriptor, KotlinBuildTarget> {
    private static final Key<AtomicReference<Pair<Object, Method>>> CONTEXT = Key.create("kotlinBuildContext");

    private final String outputLanguageName;
    private final String subCompilerClassName;

    public KotlinTargetBuilder(KotlinBuildTargetType targetType) {
        super(Collections.singletonList(targetType));
        outputLanguageName = targetType.getLanguageName();
        subCompilerClassName = targetType.getSubCompilerClassName();
    }

    @NotNull
    @Override
    public String getPresentableName() {
        return "Kotlin to " + outputLanguageName + " Builder";
    }

    @Override
    public void buildFinished(CompileContext context) {
        final AtomicReference<Pair<Object, Method>> kotlinContextRef = CONTEXT.get(context);
        if (kotlinContextRef == null) {
            return;
        }

        CONTEXT.set(context, null);
        final Pair<Object, Method> kotlinContext = kotlinContextRef.getAndSet(null);
        if (kotlinContext == null) {
            return;
        }

        try {
            kotlinContext.first.getClass().getMethod("dispose").invoke(kotlinContext.first);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }

        super.buildFinished(context);
    }

    @Override
    public void build(
            @NotNull final KotlinBuildTarget target,
            @NotNull DirtyFilesHolder<BuildRootDescriptor, KotlinBuildTarget> dirtyFilesHolder,
            @NotNull final BuildOutputConsumer outputConsumer,
            @NotNull final CompileContext context
    ) throws ProjectBuildException, IOException {
        final List<File> filesToCompile = new ArrayList<File>();
        dirtyFilesHolder.processDirtyFiles(new FileProcessor<BuildRootDescriptor, KotlinBuildTarget>() {
            @Override
            public boolean apply(KotlinBuildTarget target, File file, BuildRootDescriptor root) throws IOException {
                filesToCompile.add(file);
                return true;
            }
        });
        if (filesToCompile.isEmpty() && !dirtyFilesHolder.hasRemovedFiles()) {
            return;
        }

        JpsModule module = target.getModule();

        context.processMessage(new ProgressMessage("Compiling Kotlin module '" + module.getName() + "' to " + outputLanguageName));
        KotlinSourceFileCollector.logCompiledFiles(filesToCompile, context, JsBuildTargetType.BUILDER_NAME);

        CompilerConfiguration compilerConfiguration = new CompilerConfiguration();
        compilerConfiguration.put(CompilerConfigurationKeys.MODULE_NAME, module.getName());
        final File outputRoot = JpsKotlinCompilerPaths
                .getCompilerOutputRoot(target, context.getProjectDescriptor().dataManager.getDataPaths());
        compilerConfiguration.put(CompilerConfigurationKeys.OUTPUT_ROOT, outputRoot);
        // todo configurable
        compilerConfiguration.put(JsCompilerConfigurationKeys.TARGET, "5");
        if (!"true".equalsIgnoreCase(System.getProperty("kotlin.jps.tests"))) {
            compilerConfiguration.put(JsCompilerConfigurationKeys.SOURCEMAP, true);
        }

        final Ref<IOException> ioExceptionRef = Ref.create();
        try {
            Pair<Object, Method> kotlinContext = getKotlinContext(context);
            kotlinContext.second.invoke(kotlinContext.first, compilerConfiguration, new OutputConsumer() {
                @Override
                public void registerSources(final Collection<String> sourcePaths) {
                    if (context.getCancelStatus().isCanceled()) {
                        return;
                    }

                    FileUtil.processFilesRecursively(outputRoot, new Processor<File>() {
                        @Override
                        public boolean process(File file) {
                            if (file.isFile()) {
                                try {
                                    // todo change according to nik notes (well, we should check - Can JPS to understand how delete sourcemap file (or anything else?) from artifact  output if module was renamed?)
                                    outputConsumer.registerOutputFile(file, sourcePaths);
                                }
                                catch (IOException e) {
                                    ioExceptionRef.set(e);
                                    return false;
                                }
                            }
                            return true;
                        }
                    });
                }
            });
            context.checkCanceled();
        }
        catch (InvocationTargetException e) {
            // hide InvocationTargetException
            throw new ProjectBuildException(e.getMessage(), e.getCause());
        }
        catch (ProjectBuildException e) {
            throw e;
        }
        catch (Exception e) {
            throw new ProjectBuildException(e);
        }

        if (!ioExceptionRef.isNull()) {
            throw ioExceptionRef.get();
        }
    }

    @NotNull
    private Pair<Object, Method> getKotlinContext(CompileContext context) throws Exception {
        AtomicReference<Pair<Object, Method>> kotlinContextRef = CONTEXT.get(context);
        if (kotlinContextRef == null) {
            return createKotlinBuildContext(context, subCompilerClassName);
        }
        else {
            Pair<Object, Method> kotlinContext = kotlinContextRef.get();
            assert kotlinContext != null : "ref can be null only if build finished";
            return kotlinContext;
        }
    }

    @NotNull
    private static synchronized Pair<Object, Method> createKotlinBuildContext(CompileContext context, String subCompilerClassName) throws Exception {
        context.checkCanceled();

        AtomicReference<Pair<Object, Method>> kotlinContextRef = CONTEXT.get(context);
        if (kotlinContextRef != null) {
            return kotlinContextRef.get();
        }

        JpsModuleInfoProvider moduleInfoProvider = new JpsModuleInfoProvider(context);
        MessageCollector messageCollector = new KotlinBuilder.MessageCollectorAdapter(context);
        ClassLoader loader = CompilerRunnerUtil.getOrCreateClassLoader(PathUtil.getKotlinPathsForJpsPluginOrJpsTests(), messageCollector);

        Class<?> compilerClass = Class.forName("org.jetbrains.kotlin.compiler.KotlinCompiler", true, loader);
        Constructor<?> constructor = compilerClass.getConstructor(Class.class, ModuleInfoProvider.class, MessageCollector.class);
        constructor.setAccessible(true);
        Object compiler = constructor.newInstance(Class.forName(subCompilerClassName, false, loader), moduleInfoProvider, messageCollector);

        Method compile = compilerClass.getMethod("compile", CompilerConfiguration.class, OutputConsumer.class);
        compile.setAccessible(true);

        Pair<Object, Method> result = Pair.create(compiler, compile);
        CONTEXT.set(context, new AtomicReference<Pair<Object, Method>>(result));
        return result;
    }
}
