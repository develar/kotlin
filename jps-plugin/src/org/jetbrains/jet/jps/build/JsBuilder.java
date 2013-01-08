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
import com.intellij.util.ArrayUtil;
import com.intellij.util.StringBuilderSpinAllocator;
import com.intellij.util.containers.ContainerUtilRt;
import com.intellij.util.containers.OrderedSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jet.cli.common.messages.MessageCollector;
import org.jetbrains.jet.compiler.runner.CompilerEnvironment;
import org.jetbrains.jet.compiler.runner.CompilerRunnerUtil;
import org.jetbrains.jet.config.CompilerConfiguration;
import org.jetbrains.jet.jps.model.JsExternalizationConstants;
import org.jetbrains.jet.utils.KotlinPathsFromHomeDir;
import org.jetbrains.jet.utils.PathUtil;
import org.jetbrains.jps.builders.BuildOutputConsumer;
import org.jetbrains.jps.builders.BuildRootDescriptor;
import org.jetbrains.jps.builders.DirtyFilesHolder;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.ProjectBuildException;
import org.jetbrains.jps.incremental.TargetBuilder;
import org.jetbrains.jps.model.JpsDummyElement;
import org.jetbrains.jps.model.JpsSimpleElement;
import org.jetbrains.jps.model.java.*;
import org.jetbrains.jps.model.library.JpsOrderRootType;
import org.jetbrains.jps.model.library.JpsTypedLibrary;
import org.jetbrains.jps.model.module.JpsDependencyElement;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.module.JpsModuleDependency;
import org.jetbrains.jps.model.module.JpsTypedModuleSourceRoot;
import org.jetbrains.kotlin.compiler.CompilerConfigurationKeys;
import org.jetbrains.kotlin.compiler.ModuleInfoProvider;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;

public class JsBuilder extends TargetBuilder<BuildRootDescriptor, JsBuildTarget> {
    public static final String NAME = JsBuildTargetType.TYPE_ID + " Builder";

    private static final Key<KotlinBuildContext> CONTEXT = Key.create("kbContext");

    public JsBuilder() {
        super(Collections.singletonList(JsBuildTargetType.INSTANCE));
    }
    
    private static class KotlinBuildContext {
        private final Object compiler;
        private final MessageCollector messageCollector;
        private final ModuleInfoProvider moduleInfoProvider;

        private final Method compile;

        public KotlinBuildContext(JpsModuleInfoProvider moduleInfoProvider, MessageCollector messageCollector, Object compiler, Method compile) {
            this.moduleInfoProvider = moduleInfoProvider;
            this.messageCollector = messageCollector;
            this.compiler = compiler;
            this.compile = compile;
        }
    }

    @NotNull
    @Override
    public String getPresentableName() {
        return NAME;
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
            @NotNull JsBuildTarget target,
            @NotNull DirtyFilesHolder<BuildRootDescriptor, JsBuildTarget> holder,
            @NotNull BuildOutputConsumer outputConsumer,
            @NotNull CompileContext context
    ) throws ProjectBuildException, IOException {
        if (!holder.hasDirtyFiles()) {
            return;
        }

        JpsModule module = target.getExtension().getModule();
        CompilerConfiguration compilerConfiguration = new CompilerConfiguration();
        compilerConfiguration.put(CompilerConfigurationKeys.MODULE_NAME, module.getName());

        File outputRoot = JpsJsCompilerPaths.getCompilerOutputRoot(target, context.getProjectDescriptor().dataManager.getDataPaths());
        compilerConfiguration.put(CompilerConfigurationKeys.OUTPUT_ROOT, outputRoot);
        
        KotlinBuildContext kotlinContext = context.getUserData(CONTEXT);
        if (kotlinContext == null) {
            kotlinContext = createKotlinBuildContext(context, outputRoot);
            if (kotlinContext == null) {
                return;
            }
            else {
                context.putUserData(CONTEXT, kotlinContext);
            }
        }

        try {
            kotlinContext.compile.invoke(kotlinContext.compiler, compilerConfiguration);
        }
        catch (Exception e) {
            throw new ProjectBuildException(e);
        }
    }

    private static KotlinBuildContext createKotlinBuildContext(CompileContext context, File outputRoot) throws ProjectBuildException {
        MessageCollector messageCollector = new KotlinBuilder.MessageCollectorAdapter(context);
        CompilerEnvironment environment =
                CompilerEnvironment.getEnvironmentFor(PathUtil.getKotlinPathsForJpsPluginOrJpsTests(), outputRoot);
        if (!environment.success()) {
            environment.reportErrorsTo(messageCollector);
            return null;
        }

        JpsModuleInfoProvider moduleInfoProvider = new JpsModuleInfoProvider(context.getProjectDescriptor().getProject());

        ClassLoader loader = CompilerRunnerUtil.getOrCreateClassLoader(environment.getKotlinPaths(), messageCollector);
        Object compiler;
        Method compile;
        try {
            Class<?> compilerClass = Class.forName("org.jetbrains.kotlin.compiler.KotlinCompiler", true, loader);
            Constructor<?> constructor = compilerClass.getConstructor(ModuleInfoProvider.class, MessageCollector.class);
            constructor.setAccessible(true);
            compiler = constructor.newInstance(moduleInfoProvider, messageCollector);

            compile = compilerClass.getMethod("compile", CompilerConfiguration.class);
            compile.setAccessible(true);
        }
        catch (Exception e) {
            throw new ProjectBuildException(e);
        }

        return new KotlinBuildContext(moduleInfoProvider, messageCollector, compiler, compile);
    }

    private static String[] constructArguments(JsBuildTarget target, CompileContext context, JpsModule module) {
        ArrayList<String> args = ContainerUtilRt.newArrayList("-tags", "-verbose", "-version");
        addSourceFiles(args, module);

        args.add("-output");
        args.add(JpsJsCompilerPaths.getCompilerOutputRoot(target, context.getProjectDescriptor().dataManager.getDataPaths()).getPath());

        addLibLocation(module, args);

        args.add("-target");
        // todo configurable
        args.add("v5");

        args.add("-sourcemap");

        return ArrayUtil.toStringArray(args);
    }

    private static void addSourceFiles(ArrayList<String> args, JpsModule module) {
        args.add("-sourceFiles");
        StringBuilder sb = StringBuilderSpinAllocator.alloc();
        try {
            appendModuleSourceRoots(module, sb);
            args.add(sb.substring(0, sb.length() - 1));
        }
        finally {
            StringBuilderSpinAllocator.dispose(sb);
        }
    }

    private static void appendModuleSourceRoots(JpsModule module, StringBuilder sb) {
        for (JpsTypedModuleSourceRoot<JpsSimpleElement<JavaSourceRootProperties>> root : module.getSourceRoots(JavaSourceRootType.SOURCE)) {
            sb.append(root.getFile().getPath()).append(',');
        }
    }

    private static void addLibLocation(@NotNull JpsModule module, @NotNull ArrayList<String> args) {
        StringBuilder sb = StringBuilderSpinAllocator.alloc();
        try {
            Set<JpsModule> modules = new OrderedSet<JpsModule>();
            collectModuleDependencies(module, modules, true);
            if (!modules.isEmpty()) {
                for (JpsModule dependency : modules) {
                    sb.append('@').append(dependency.getName()).append(',');
                    appendModuleSourceRoots(module, sb);
                }
            }

            File libraryFile = findLibrary(module);
            if (libraryFile != null) {
                sb.append(libraryFile.getPath()).append(',');
            }

            if (sb.length() > 0) {
                args.add("-libraryFiles");
                args.add(sb.substring(0, sb.length() - 1));
            }
        }
        finally {
            StringBuilderSpinAllocator.dispose(sb);
        }
    }

    private static File findLibrary(JpsModule module) {
        JpsTypedLibrary<JpsDummyElement> library =
                module.getLibraryCollection().findLibrary(JsExternalizationConstants.JS_LIBRARY_NAME, JpsJavaLibraryType.INSTANCE);
        if (library != null) {
            for (File file : library.getFiles(JpsOrderRootType.SOURCES)) {
                if (file.getName().equals(KotlinPathsFromHomeDir.getRuntimeName(false))) {
                    return file;
                }
            }
        }
        return null;
    }

    // todo I don't know why I don't use JpsJavaDependenciesEnumeratorImpl, but I am afraid do it :)
    // develar we need to investigate, is JpsJavaDependenciesEnumeratorImpl suitable for us
    // we cannot use OrderEnumerator because it has critical bug - try https://gist.github.com/2953261, processor will never be called for module dependency
    // we don't use context.getCompileScope().getAffectedModules() because we want to know about linkage type (well, we ignore scope right now, but in future...)
    private static void collectModuleDependencies(JpsModule dependentModule, Set<JpsModule> modules, boolean isDirectDependency) {
        for (JpsDependencyElement dependency : dependentModule.getDependenciesList().getDependencies()) {
            if (dependency instanceof JpsModuleDependency) {
                JpsModuleDependency moduleDependency = (JpsModuleDependency) dependency;
                JpsJavaDependencyExtension extension = JpsJavaExtensionService.getInstance().getDependencyExtension(moduleDependency);
                if (extension == null || !extension.getScope().isIncludedIn(JpsJavaClasspathKind.PRODUCTION_COMPILE)) {
                    continue;
                }

                JpsModule module = moduleDependency.getModule();
                if (module == null) {
                    continue;
                }

                if (isDirectDependency) {
                    if (modules.add(module)) {
                        collectModuleDependencies(module, modules, false);
                    }
                }
                else if (modules.add(module)) {
                    collectModuleDependencies(module, modules, false);
                }
            }
        }
    }
}
