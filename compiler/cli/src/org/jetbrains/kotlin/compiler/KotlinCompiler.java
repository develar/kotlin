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
import com.intellij.openapi.util.*;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import com.intellij.openapi.vfs.local.CoreLocalFileSystem;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.Consumer;
import com.intellij.util.Processor;
import com.intellij.util.SmartList;
import com.intellij.util.containers.StripedLockConcurrentHashMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jet.analyzer.AnalyzeExhaust;
import org.jetbrains.jet.cli.common.messages.AnalyzerWithCompilerReport;
import org.jetbrains.jet.cli.common.messages.CompilerMessageSeverity;
import org.jetbrains.jet.cli.common.messages.MessageCollector;
import org.jetbrains.jet.config.CompilerConfiguration;
import org.jetbrains.jet.lang.descriptors.ValueParameterDescriptor;
import org.jetbrains.jet.lang.diagnostics.Diagnostic;
import org.jetbrains.jet.lang.diagnostics.DiagnosticWithParameters1;
import org.jetbrains.jet.lang.diagnostics.Errors;
import org.jetbrains.jet.lang.diagnostics.Severity;
import org.jetbrains.jet.lang.psi.JetFile;
import org.jetbrains.jet.lang.resolve.TopDownAnalysisParameters;
import org.jetbrains.kotlin.lang.resolve.XAnalyzerFacade;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

import static org.jetbrains.jet.cli.common.messages.AnalyzerWithCompilerReport.reportDiagnostics;
import static org.jetbrains.jet.cli.common.messages.AnalyzerWithCompilerReport.reportIncompleteHierarchies;

/**
 * Kotlin compiler to any target (source-based, JavaScript as example).
 * Per project.
 * You must call dispose() if it is not need anymore.
 * You must not compile unsorted set of modules in parallel.
 */
public class KotlinCompiler {
    private static final Condition<Diagnostic> DIAGNOSTIC_LIBRARY_FILTER = new Condition<Diagnostic>() {
        @Override
        public boolean value(Diagnostic diagnostic) {
            return diagnostic.getSeverity().equals(Severity.ERROR);
        }
    };

    private static final Condition<Diagnostic> DIAGNOSTIC_CODE_FILTER = new Condition<Diagnostic>() {
        @Override
        public boolean value(Diagnostic diagnostic) {
            if (diagnostic.getFactory() == Errors.UNUSED_PARAMETER) {
                @SuppressWarnings("unchecked")
                ValueParameterDescriptor parameterDescriptor =
                        ((DiagnosticWithParameters1<PsiElement, ValueParameterDescriptor>) diagnostic).getA();
                return !AnnotationsUtils.isNativeByAnnotation(parameterDescriptor.getContainingDeclaration());
            }
            return true;
        }
    };

    private final ModuleInfo moduleWithErrors;

    private final ModuleInfoProvider moduleInfoProvider;
    private final MessageCollector messageCollector;
    private final CompileContext compileContext;

    private final Disposable compileContextParentDisposable = Disposer.newDisposable();

    private final ConcurrentMap<String, NotNullLazyValue<ModuleInfo>> analyzedModules =
            new StripedLockConcurrentHashMap<String, NotNullLazyValue<ModuleInfo>>();

    private final SubCompiler subCompiler;

    public KotlinCompiler(Class<SubCompiler> subCompilerClass, ModuleInfoProvider moduleInfoProvider, MessageCollector messageCollector) {
        this.moduleInfoProvider = moduleInfoProvider;
        this.messageCollector = messageCollector;
        compileContext = new CompileContext(compileContextParentDisposable);
        // initialization must be here, after compileContext creation
        moduleWithErrors = new ModuleInfo(compileContext.getProject());

        try {
            subCompiler = subCompilerClass.newInstance();
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void compile(@NotNull CompilerConfiguration configuration, Consumer<File> outputFileConsumer) throws IOException {
        String name = configuration.getNotNull(CompilerConfigurationKeys.MODULE_NAME);
        List<JetFile> sources = collectSourceFiles(name, null, false);
        final ModuleInfo moduleInfo = analyzeProjectModule(name, sources, true);
        if (moduleInfo == null) {
            return;
        }

        NotNullLazyValue<ModuleInfo> prev = analyzedModules.put(name, new PrecomputedModuleInfoValue(moduleInfo));
        assert prev == null;

        if (!configuration.get(CompilerConfigurationKeys.ANALYZE_ONLY, false)) {
            try {
                subCompiler.compile(configuration, moduleInfo, sources, outputFileConsumer);
            }
            catch (TranslationException e) {
                messageCollector.report(CompilerMessageSeverity.ERROR, e.getMessage(), e.getLocation());
            }
        }
    }

    @Nullable
    private ModuleInfo getModuleInfo(String moduleName, boolean analyzeCompletely, Object dependency) {
        NotNullLazyValue<ModuleInfo> moduleInfoRef = analyzedModules.get(moduleName);
        if (moduleInfoRef == null) {
            NotNullLazyValue<ModuleInfo> candidate = analyzedModules.putIfAbsent(moduleName, moduleInfoRef =
                    new ModuleInfoAtomicLazyValue(moduleName, dependency, analyzeCompletely));
            if (candidate != null) {
                moduleInfoRef = candidate;
            }
        }
        ModuleInfo moduleInfo = moduleInfoRef.getValue();
        return moduleInfo == moduleWithErrors ? null : moduleInfo;
    }

    protected List<JetFile> collectSourceFiles(String name, @Nullable Object object, final boolean collectFromJar) {
        final List<JetFile> result = new ArrayList<JetFile>();
        final PsiManager psiManager = PsiManager.getInstance(compileContext.getProject());
        final CoreLocalFileSystem localFileSystem = compileContext.getLocalFileSystem();
        moduleInfoProvider.processSourceFiles(name, object, new Processor<File>() {
            @Override
            public boolean process(File file) {
                VirtualFile virtualFile = localFileSystem.findFileByIoFile(file);
                if (virtualFile == null) {
                    throw new IllegalArgumentException("Cannot find " + file.getPath());
                }

                // root from library
                VirtualFile jarFile = collectFromJar ? StandardFileSystems.getJarRootForLocalFile(virtualFile) : null;
                if (jarFile == null) {
                    PsiFile psiFile = psiManager.findFile(virtualFile);
                    assert psiFile instanceof JetFile : virtualFile.getPath() + " is not Kotlin PSI file";
                    result.add((JetFile) psiFile);
                }
                else {
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
                }
                return true;
            }
        });
        return result;
    }

    @Nullable
    private ModuleInfo analyzeProjectModule(@NotNull String moduleName, @NotNull List<JetFile> sources, boolean analyzeCompletely) {
        Pair<List<ModuleInfo>, Set<ModuleInfo>> dependencies = collectDependencies(moduleName);
        if (dependencies == null) {
            return null;
        }
        return analyzeModule(moduleName, sources, analyzeCompletely, dependencies.first, dependencies.second, true);
    }

    @Nullable
    private ModuleInfo analyzeModule(
            @NotNull String moduleName,
            @NotNull List<JetFile> sources,
            boolean analyzeCompletely,
            @Nullable List<ModuleInfo> dependencies,
            @Nullable Set<ModuleInfo> providedDependencies,
            boolean checkSyntax
    ) {
        if (checkSyntax) {
            AnalyzerWithCompilerReport.ErrorReportingVisitor visitor =
                    new AnalyzerWithCompilerReport.ErrorReportingVisitor(messageCollector);
            for (JetFile file : sources) {
                file.accept(visitor);
                // don't stop if hasErrors, report about errors in all files
            }
            if (visitor.hasErrors()) {
                return null;
            }
        }

        ModuleInfo moduleConfiguration = new ModuleInfo(moduleName, compileContext.getProject(), dependencies, providedDependencies);
        AnalyzeExhaust exhaust =
                XAnalyzerFacade.analyzeFiles(moduleConfiguration, sources, new TopDownAnalysisParameters(analyzeCompletely), false);
        boolean hasErrors = reportDiagnostics(exhaust.getBindingContext(), messageCollector,
                                              checkSyntax ? DIAGNOSTIC_CODE_FILTER : DIAGNOSTIC_LIBRARY_FILTER);
        hasErrors |= reportIncompleteHierarchies(exhaust, messageCollector);
        return hasErrors ? null : moduleConfiguration;
    }

    @Nullable
    private Pair<List<ModuleInfo>, Set<ModuleInfo>> collectDependencies(@NotNull String moduleName) {
        final List<ModuleInfo> dependencies = new SmartList<ModuleInfo>();
        final Set<ModuleInfo> providedDependencies = new THashSet<ModuleInfo>();
        boolean completed = moduleInfoProvider.processDependencies(moduleName, new ModuleInfoProvider.DependenciesProcessor() {
            @Override
            public boolean process(String name, Object dependency, boolean isLibrary, boolean provided) {
                // todo now we assume that idea module name and library name are unique
                // todo dependencies for library?
                ModuleInfo moduleInfo = getModuleInfo(name, false, isLibrary ? dependency : null);
                if (moduleInfo == null) {
                    return false;
                }
                if (provided) {
                    providedDependencies.add(moduleInfo);
                }
                dependencies.add(moduleInfo);
                return true;
            }
        });

        if (completed) {
            return Pair.create(dependencies.isEmpty() ? Collections.<ModuleInfo>emptyList() : dependencies,
                               providedDependencies.isEmpty() ? Collections.<ModuleInfo>emptySet() : providedDependencies);
        }
        else {
            return null;
        }
    }

    public void dispose() {
        Disposer.dispose(compileContextParentDisposable);
    }

    private static final class PrecomputedModuleInfoValue extends NotNullLazyValue<ModuleInfo> {
        private final ModuleInfo moduleInfo;

        public PrecomputedModuleInfoValue(ModuleInfo moduleInfo) {
            this.moduleInfo = moduleInfo;
        }

        @NotNull
        @Override
        protected ModuleInfo compute() {
            return moduleInfo;
        }
    }

    private final class ModuleInfoAtomicLazyValue extends AtomicNotNullLazyValue<ModuleInfo> {
        private final String name;
        private final Object dependency;
        private final boolean analyzeCompletely;

        public ModuleInfoAtomicLazyValue(String name, Object dependency, boolean analyzeCompletely) {
            this.name = name;
            this.dependency = dependency;
            this.analyzeCompletely = analyzeCompletely;
        }

        @NotNull
        @Override
        protected ModuleInfo compute() {
            ModuleInfo result;
            boolean isProjectModule = dependency == null;
            List<JetFile> sources = collectSourceFiles(name, dependency, !isProjectModule);
            if (isProjectModule) {
                result = analyzeProjectModule(name, sources, analyzeCompletely);
            }
            else {
                result = analyzeModule(name, sources, false, null, null, false);
            }
            return result == null ? moduleWithErrors : result;
        }
    }
}
