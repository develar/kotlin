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
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import com.intellij.openapi.vfs.local.CoreLocalFileSystem;
import com.intellij.openapi.vfs.local.CoreLocalVirtualFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.Processor;
import com.intellij.util.SmartList;
import gnu.trove.THashMap;
import jet.Function0;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jet.analyzer.AnalyzeExhaust;
import org.jetbrains.jet.cli.common.messages.AnalyzerWithCompilerReport;
import org.jetbrains.jet.cli.common.messages.MessageCollector;
import org.jetbrains.jet.config.CompilerConfiguration;
import org.jetbrains.jet.lang.descriptors.ModuleDescriptor;
import org.jetbrains.jet.lang.psi.JetFile;
import org.jetbrains.jet.lang.resolve.TopDownAnalysisParameters;
import org.jetbrains.jet.lang.resolve.name.Name;
import org.jetbrains.k2js.analyze.AnalyzerFacadeForJS;
import org.jetbrains.k2js.analyze.JsModuleConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Per project.
 *
 * Kotlin compiler to any target (source-based, JavaScript as example).
 *
 * You must call dispose() if it is not need anymore.
 */
public class KotlinCompiler {
    private final ModuleInfoProvider moduleInfoProvider;
    private final MessageCollector messageCollector;
    private final CompileContext compileContext;

    private final Disposable compileContextParentDisposable = Disposer.newDisposable();

    private final Map<String, JsModuleConfiguration> compiledModules = new THashMap<String, JsModuleConfiguration>();

    public KotlinCompiler(ModuleInfoProvider moduleInfoProvider, MessageCollector messageCollector) {
        this.moduleInfoProvider = moduleInfoProvider;
        this.messageCollector = messageCollector;
        compileContext = new CompileContext(compileContextParentDisposable);
    }

    public void compile(CompilerConfiguration configuration) {
        compileModule(configuration.get(CompilerConfigurationKeys.MODULE_NAME), true);
    }

    protected List<JetFile> collectSourceFiles(List<File> sourceRoots) {
        final List<JetFile> result = new ArrayList<JetFile>();
        final PsiManager psiManager = PsiManager.getInstance(compileContext.getProject());
        final CoreLocalFileSystem localFileSystem = compileContext.getLocalFileSystem();
        for (File sourceRoot : sourceRoots) {
            // root from library
            VirtualFile jarFile = StandardFileSystems.getJarRootForLocalFile(new CoreLocalVirtualFile(localFileSystem, sourceRoot));
            if (jarFile != null) {
                VfsUtilCore.visitChildrenRecursively(jarFile, new VirtualFileVisitor() {
                    @Override
                    public boolean visitFile(@NotNull VirtualFile file) {
                        if (file.getName().endsWith(".kt")) {
                            result.add((JetFile) psiManager.findFile(file));
                            return false;
                        }
                        return true;
                    }
                });
                continue;
            }

            FileUtil.processFilesRecursively(sourceRoot, new Processor<File>() {
                @Override
                public boolean process(File file) {
                    if (file.isFile()) {
                        if (file.getName().endsWith(".kt")) {
                            // todo add findFileByIoFile to CoreLocalFileSystem (the same as in LocalFileSystem)
                            result.add((JetFile) psiManager.findFile(new CoreLocalVirtualFile(localFileSystem, file)));
                        }
                        return false;
                    }
                    return true;
                }
            });
        }

        return result;
    }

    @Nullable
    protected JsModuleConfiguration compileModule(String moduleName, final boolean analyzeCompletely) {
        List<JsModuleConfiguration> dependencies = collectDependencies(moduleName);
        if (dependencies == null) {
            return null;
        }

        JsModuleConfiguration moduleConfiguration = analyzeModule(moduleName, null, analyzeCompletely, dependencies);
        if (moduleConfiguration == null) {
            return null;
        }

        return moduleConfiguration;
    }

    @Nullable
    private JsModuleConfiguration analyzeModule(
            String moduleName,
            @Nullable Object moduleObject,
            final boolean analyzeCompletely,
            List<JsModuleConfiguration> dependencies
    ) {
        final List<JetFile> sources = collectSourceFiles(moduleInfoProvider.getSourceFiles(moduleName, moduleObject));

        final JsModuleConfiguration moduleConfiguration =
                new JsModuleConfiguration(new ModuleDescriptor(Name.special('<' + moduleName + '>')), compileContext.getProject(),
                                          dependencies);
        AnalyzeExhaust exhaust = new AnalyzerWithCompilerReport(messageCollector).analyzeAndReport(new Function0<AnalyzeExhaust>() {
            @Override
            public AnalyzeExhaust invoke() {
                return AnalyzerFacadeForJS.analyzeFiles(moduleConfiguration, sources, new TopDownAnalysisParameters(analyzeCompletely),
                                                        false);
            }
        }, sources);
        if (exhaust == null) {
            return null;
        }
        exhaust.throwIfError();

        compiledModules.put(moduleName, moduleConfiguration);
        return moduleConfiguration;
    }

    @Nullable
    private List<JsModuleConfiguration> collectDependencies(String moduleName) {
        final List<JsModuleConfiguration> dependencies = new SmartList<JsModuleConfiguration>();
        boolean completed = moduleInfoProvider.consumeDependencies(moduleName, new ModuleInfoProvider.DependenciesProcessor() {
            @Override
            public boolean process(String name, Object dependency, boolean isLibrary) {
                // todo now we assume that idea module name and library name are unique
                JsModuleConfiguration moduleConfiguration = compiledModules.get(name);
                if (moduleConfiguration == null) {
                    // todo dependencies for library?
                    moduleConfiguration = isLibrary
                                          ? analyzeModule(name, dependency, false, Collections.<JsModuleConfiguration>emptyList())
                                          : compileModule(name, false);
                    if (moduleConfiguration == null) {
                        return false;
                    }
                }
                dependencies.add(moduleConfiguration);
                return true;
            }
        });

        return completed ? dependencies : null;
    }

    public void dispose() {
        Disposer.dispose(compileContextParentDisposable);
    }
}
