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
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jet.cli.common.messages.MessageCollector;
import org.jetbrains.jet.compiler.runner.CompilerRunnerUtil;
import org.jetbrains.jet.config.CompilerConfiguration;
import org.jetbrains.jet.utils.PathUtil;
import org.jetbrains.jps.builders.*;
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

public class KotlinTargetBuilder extends TargetBuilder<BuildRootDescriptor, KotlinBuildTarget> {
    private static final Key<KotlinBuildContext> CONTEXT = Key.create("kbContext");

    private final String outputLanguageName;

    public KotlinTargetBuilder(KotlinBuildTargetType targetType) {
        super(Collections.singletonList(targetType));
        outputLanguageName = targetType.getLanguageName();
    }
    
    private static class KotlinBuildContext {
        private final Object compiler;
        private final Method compile;

        public KotlinBuildContext(Object compiler, Method compile) {
            this.compiler = compiler;
            this.compile = compile;
        }
    }

    @NotNull
    @Override
    public String getPresentableName() {
        return "Kotlin to " + outputLanguageName + " Builder";
    }

    @Override
    public void buildFinished(CompileContext context) {
        KotlinBuildContext kotlinContext = context.getUserData(CONTEXT);
        if (kotlinContext != null) {
            try {
                kotlinContext.compiler.getClass().getMethod("dispose").invoke(kotlinContext.compiler);
            }
            catch (Exception e) {
                throw new RuntimeException(e);
            }
            context.putUserData(CONTEXT, null);
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

        JpsModule module = target.getExtension().getModule();

        context.processMessage(new ProgressMessage("Compiling Kotlin module '" + module.getName() + "' to " + outputLanguageName));
        KotlinSourceFileCollector.logCompiledFiles(filesToCompile, context, JsBuildTargetType.BUILDER_NAME);
        
        KotlinBuildContext kotlinContext = context.getUserData(CONTEXT);
        if (kotlinContext == null) {
            kotlinContext = createKotlinBuildContext(context);
            if (kotlinContext == null) {
                return;
            }
            else {
                context.putUserData(CONTEXT, kotlinContext);
            }
        }

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

        try {
            // todo nik why we should registerOutputFile?
            kotlinContext.compile.invoke(kotlinContext.compiler, compilerConfiguration, new OutputConsumer() {
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
                                    outputConsumer.registerOutputFile(file, sourcePaths);
                                }
                                catch (IOException e) {
                                    throw new RuntimeException(e);
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
        catch (Exception e) {
            throw new ProjectBuildException(e);
        }
    }

    private static KotlinBuildContext createKotlinBuildContext(CompileContext context) throws ProjectBuildException {
        JpsModuleInfoProvider moduleInfoProvider = new JpsModuleInfoProvider(context);
        MessageCollector messageCollector = new KotlinBuilder.MessageCollectorAdapter(context);
        ClassLoader loader = CompilerRunnerUtil.getOrCreateClassLoader(PathUtil.getKotlinPathsForJpsPluginOrJpsTests(), messageCollector);
        Object compiler;
        Method compile;
        try {
            Class<?> compilerClass = Class.forName("org.jetbrains.kotlin.compiler.KotlinCompiler", true, loader);
            Constructor<?> constructor = compilerClass.getConstructor(Class.class, ModuleInfoProvider.class, MessageCollector.class);
            constructor.setAccessible(true);
            compiler = constructor.newInstance(Class.forName("org.jetbrains.k2js.ToJsSubCompiler", false, loader), moduleInfoProvider, messageCollector);

            compile = compilerClass.getMethod("compile", CompilerConfiguration.class, OutputConsumer.class);
            compile.setAccessible(true);
        }
        catch (Exception e) {
            throw new ProjectBuildException(e);
        }

        return new KotlinBuildContext(compiler, compile);
    }
}
