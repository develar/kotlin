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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jet.cli.common.messages.MessageCollector;
import org.jetbrains.jet.compiler.runner.CompilerRunnerUtil;
import org.jetbrains.jet.config.CompilerConfiguration;
import org.jetbrains.jet.utils.PathUtil;
import org.jetbrains.jps.builders.BuildOutputConsumer;
import org.jetbrains.jps.builders.BuildRootDescriptor;
import org.jetbrains.jps.builders.DirtyFilesHolder;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.ProjectBuildException;
import org.jetbrains.jps.incremental.TargetBuilder;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.util.JpsPathUtil;
import org.jetbrains.kotlin.compiler.CompilerConfigurationKeys;
import org.jetbrains.kotlin.compiler.JsCompilerConfigurationKeys;
import org.jetbrains.kotlin.compiler.ModuleInfoProvider;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Collections;

public class JsBuilder extends TargetBuilder<BuildRootDescriptor, JsBuildTarget> {
    public static final String NAME = JsBuildTargetType.TYPE_ID + " Builder";

    private static final Key<KotlinBuildContext> CONTEXT = Key.create("kbContext");

    public JsBuilder() {
        super(Collections.singletonList(JsBuildTargetType.INSTANCE));
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
        //if (!holder.hasDirtyFiles()) {
        //    return;
        //}
        
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

        JpsModule module = target.getExtension().getModule();

        CompilerConfiguration compilerConfiguration = new CompilerConfiguration();
        compilerConfiguration.put(CompilerConfigurationKeys.MODULE_NAME, module.getName());
        File outputRoot = JpsPathUtil.urlToFile(JpsJavaExtensionService.getInstance().getOutputUrl(module, false));
        compilerConfiguration.put(CompilerConfigurationKeys.OUTPUT_ROOT, outputRoot);
        // todo configurable
        compilerConfiguration.put(JsCompilerConfigurationKeys.TARGET, "5");
        if (!"true".equalsIgnoreCase(System.getProperty("kotlin.jps.tests"))) {
        compilerConfiguration.put(JsCompilerConfigurationKeys.SOURCEMAP, true);
        }

        try {
            kotlinContext.compile.invoke(kotlinContext.compiler, compilerConfiguration);
        }
        catch (Exception e) {
            throw new ProjectBuildException(e);
        }
    }

    private static KotlinBuildContext createKotlinBuildContext(CompileContext context) throws ProjectBuildException {
        JpsModuleInfoProvider moduleInfoProvider = new JpsModuleInfoProvider(context.getProjectDescriptor().getProject());
        MessageCollector messageCollector = new KotlinBuilder.MessageCollectorAdapter(context);
        ClassLoader loader = CompilerRunnerUtil.getOrCreateClassLoader(PathUtil.getKotlinPathsForJpsPluginOrJpsTests(), messageCollector);
        Object compiler;
        Method compile;
        try {
            Class<?> compilerClass = Class.forName("org.jetbrains.kotlin.compiler.KotlinCompiler", true, loader);
            Constructor<?> constructor = compilerClass.getConstructor(Class.class, ModuleInfoProvider.class, MessageCollector.class);
            constructor.setAccessible(true);
            compiler = constructor.newInstance(Class.forName("org.jetbrains.k2js.ToJsSubCompiler", true, loader), moduleInfoProvider, messageCollector);

            compile = compilerClass.getMethod("compile", CompilerConfiguration.class);
            compile.setAccessible(true);
        }
        catch (Exception e) {
            throw new ProjectBuildException(e);
        }

        return new KotlinBuildContext(compiler, compile);
    }
}
