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

package org.jetbrains.kotlin.compiler;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.jet.config.CompilerConfiguration;

import java.util.List;

/**
 * Per project.
 *
 * Kotlin compiler to any target (source-based, JavaScript as example).
 *
 * You must call dispose() if it is not need anymore.
 */
public class KotlinCompiler {
    private final ModuleInfoProvider moduleInfoProvider;
    private final CompileContext compileContext;

    private final Disposable compileContextParentDisposable = Disposer.newDisposable();

    public KotlinCompiler(ModuleInfoProvider moduleInfoProvider) {
        this.moduleInfoProvider = moduleInfoProvider;
        compileContext = new CompileContext(compileContextParentDisposable);
    }

    public void compile(CompilerConfiguration configuration) {
        compileModule(configuration.get(CompilerConfigurationKeys.MODULE_NAME));
    }

    protected void compileModule(String moduleName) {
        List<String> dependentModuleNames = moduleInfoProvider.getDependentModuleNames(moduleName);
        for (String dependentModuleName : dependentModuleNames) {
            //if (!isCompiled(dependentModuleName)) {
            //    compileModule(dependentModuleName);
            //}
        }
        throw new Error(moduleName + " will be compiled");
    }

    public void dispose() {
        Disposer.dispose(compileContextParentDisposable);
    }
}
