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
import com.intellij.util.Consumer;
import com.intellij.util.containers.StripedLockConcurrentHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jet.cli.common.messages.MessageCollector;
import org.jetbrains.jet.compiler.runner.CompilerRunnerUtil;
import org.jetbrains.jet.config.CompilerConfiguration;
import org.jetbrains.jet.utils.PathUtil;
import org.jetbrains.jps.builders.*;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.GlobalContextKey;
import org.jetbrains.jps.incremental.ProjectBuildException;
import org.jetbrains.jps.incremental.TargetBuilder;
import org.jetbrains.jps.incremental.messages.ProgressMessage;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.kotlin.compiler.CompilerConfigurationKeys;
import org.jetbrains.kotlin.compiler.JsCompilerConfigurationKeys;
import org.jetbrains.kotlin.compiler.ModuleInfoProvider;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public class KotlinTargetBuilder extends TargetBuilder<BuildRootDescriptor, KotlinBuildTarget> {
    private static final Key<AtomicReference<Pair<Object, Method>>> CONTEXT = GlobalContextKey.create("kotlinBuildContext");
    // dirty because some dependencies was changed, but not because some source file was changed
    private static final Key<Map<JpsModule, Long>> DIRTY_MODULES = GlobalContextKey.create("kotlinDirtyModules");

    private final String outputLanguageName;
    private final String subCompilerClassName;

    private static final short FORMAT_VERSION = 2;

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
    public void buildStarted(CompileContext context) {
        DIRTY_MODULES.set(context, new StripedLockConcurrentHashMap<JpsModule, Long>());
    }

    @Override
    public void buildFinished(CompileContext context) {
        DIRTY_MODULES.set(context, null);

        AtomicReference<Pair<Object, Method>> kotlinContextRef = CONTEXT.get(context);
        if (kotlinContextRef == null) {
            return;
        }

        CONTEXT.set(context, null);
        Pair<Object, Method> kotlinContext = kotlinContextRef.getAndSet(null);
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

    private static boolean hasDirtyDependencies(
            KotlinBuildTarget target,
            CompileContext context,
            Map<JpsModule, Long> dirtyModules,
            TargetTimestamps timestamps
    ) throws IOException {
        for (BuildTarget<?> buildTarget : context.getProjectDescriptor().getBuildTargetIndex().getDependencies(target, context)) {
            KotlinBuildTarget thatTarget = (KotlinBuildTarget) buildTarget;
            Long cachedThatTargetTimestamp = dirtyModules.get(thatTarget.getModule());
            long thatTargetTimestamp;
            if (cachedThatTargetTimestamp == null) {
                thatTargetTimestamp = new TargetTimestamps(context, thatTarget).getTimestamp();
                dirtyModules.put(thatTarget.getModule(), thatTargetTimestamp);
            }
            else {
                thatTargetTimestamp = cachedThatTargetTimestamp;
            }

            if (thatTargetTimestamp > timestamps.getTimestamp()) {
                // todo don't reanalyze if public API was not changed (i.e. changes affect only internal code)
                return true;
            }
        }

        return false;
    }

    @Override
    public void build(
            @NotNull KotlinBuildTarget target,
            @NotNull DirtyFilesHolder<BuildRootDescriptor, KotlinBuildTarget> dirtyFilesHolder,
            @NotNull BuildOutputConsumer outputConsumer,
            @NotNull CompileContext context
    ) throws ProjectBuildException, IOException {
        final List<File> filesToCompile = new ArrayList<File>();
        dirtyFilesHolder.processDirtyFiles(new FileProcessor<BuildRootDescriptor, KotlinBuildTarget>() {
            @Override
            public boolean apply(KotlinBuildTarget target, File file, BuildRootDescriptor root) throws IOException {
                filesToCompile.add(file);
                return true;
            }
        });

        TargetTimestamps timestamps = new TargetTimestamps(context, target);
        Map<JpsModule, Long> dirtyModules = DIRTY_MODULES.get(context);
        assert dirtyModules != null;
        boolean analyzeOnly = false;
        if (filesToCompile.isEmpty() && !dirtyFilesHolder.hasRemovedFiles() && timestamps.getFormatVersion() == FORMAT_VERSION) {
            if (hasDirtyDependencies(target, context, dirtyModules, timestamps)) {
                analyzeOnly = true;
            }
            else {
                return;
            }
        }

        JpsModule module = target.getModule();

        // we must reanalyze all dependents if something was changed
        // todo don't reanalyze if public API was not changed (i.e. changes affect only internal code)
        dirtyModules.put(module, context.getCompilationStartStamp());
        timestamps.set(context.getCompilationStartStamp(), FORMAT_VERSION);

        context.processMessage(new ProgressMessage("Compiling Kotlin module '" + module.getName() + "' to " + outputLanguageName));
        KotlinSourceFileCollector.logCompiledFiles(filesToCompile, context, JsBuildTargetType.BUILDER_NAME);

        CompilerConfiguration compilerConfiguration = new CompilerConfiguration();
        compilerConfiguration.put(CompilerConfigurationKeys.MODULE_NAME, module.getName());
        File outputRoot = JpsKotlinCompilerPaths.getCompilerOutputRoot(target, context.getProjectDescriptor().dataManager.getDataPaths());
        compilerConfiguration.put(CompilerConfigurationKeys.OUTPUT_ROOT, outputRoot);
        // todo configurable
        compilerConfiguration.put(JsCompilerConfigurationKeys.TARGET, "5");
        if (!"true".equalsIgnoreCase(System.getProperty("kotlin.jps.tests"))) {
            compilerConfiguration.put(JsCompilerConfigurationKeys.SOURCEMAP, true);
        }

        if (analyzeOnly) {
            compilerConfiguration.put(CompilerConfigurationKeys.ANALYZE_ONLY, true);
        }

        compile(outputConsumer, context, compilerConfiguration);
        context.checkCanceled();
    }

    private void compile(final BuildOutputConsumer outputConsumer, CompileContext context, CompilerConfiguration compilerConfiguration)
            throws ProjectBuildException, IOException {
        final Ref<IOException> ioExceptionRef = Ref.create();
        Pair<Object, Method> kotlinContext;
        try {
            kotlinContext = getKotlinContext(context);
            kotlinContext.second.invoke(kotlinContext.first, compilerConfiguration, new Consumer<File>() {
                @Override
                public void consume(File file) {
                    try {
                        outputConsumer.registerOutputFile(file, Collections.<String>emptyList());
                    }
                    catch (IOException e) {
                        ioExceptionRef.set(e);
                    }
                }
            });
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

        Method compile = compilerClass.getMethod("compile", CompilerConfiguration.class, Consumer.class);
        compile.setAccessible(true);

        Pair<Object, Method> result = Pair.create(compiler, compile);
        CONTEXT.set(context, new AtomicReference<Pair<Object, Method>>(result));
        return result;
    }
}
