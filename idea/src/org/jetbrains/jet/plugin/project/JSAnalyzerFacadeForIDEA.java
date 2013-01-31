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

package org.jetbrains.jet.plugin.project;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.util.SingletonSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jet.analyzer.AnalyzeExhaust;
import org.jetbrains.jet.analyzer.AnalyzerFacade;
import org.jetbrains.jet.analyzer.AnalyzerFacadeForEverything;
import org.jetbrains.jet.lang.ModuleConfiguration;
import org.jetbrains.jet.lang.descriptors.ModuleDescriptor;
import org.jetbrains.jet.lang.psi.JetFile;
import org.jetbrains.jet.lang.resolve.AnalyzerScriptParameter;
import org.jetbrains.jet.lang.resolve.BindingTrace;
import org.jetbrains.jet.lang.resolve.BodiesResolveContext;
import org.jetbrains.jet.lang.resolve.lazy.FileBasedDeclarationProviderFactory;
import org.jetbrains.jet.lang.resolve.lazy.LockBasedStorageManager;
import org.jetbrains.jet.lang.resolve.lazy.ResolveSession;
import org.jetbrains.jet.lang.resolve.name.FqName;
import org.jetbrains.jet.lang.resolve.name.Name;
import org.jetbrains.k2js.Traverser;
import org.jetbrains.kotlin.compiler.ModuleInfo;
import org.jetbrains.kotlin.lang.resolve.XAnalyzerFacade;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public enum JSAnalyzerFacadeForIDEA implements AnalyzerFacade {

    INSTANCE;
    private static final Name MODULE_NAME = Name.special("<module>");

    private JSAnalyzerFacadeForIDEA() {
    }

    @NotNull
    @Override
    public AnalyzeExhaust analyzeFiles(
            @NotNull Project project,
            @NotNull Collection<JetFile> files,
            @NotNull List<AnalyzerScriptParameter> scriptParameters,
            @NotNull Predicate<PsiFile> filesToAnalyzeCompletely
    ) {
        ModuleInfo libraryModuleConfiguration = new ModuleInfo(new ModuleDescriptor(ModuleInfo.STUBS_MODULE_NAME), project);
        XAnalyzerFacade.analyzeFiles(libraryModuleConfiguration, getLibraryFiles(project), false).getBindingContext();
        ModuleInfo moduleConfiguration = createModuleInfo(project, libraryModuleConfiguration);
        return XAnalyzerFacade.analyzeFilesAndStoreBodyContext(moduleConfiguration, files, false);
    }

    private static ModuleInfo createModuleInfo(Project project, ModuleInfo libraryModuleConfiguration) {
        return new ModuleInfo(new ModuleDescriptor(MODULE_NAME), project, Collections.<ModuleInfo>singletonList(libraryModuleConfiguration),
                              new SingletonSet<ModuleInfo>(libraryModuleConfiguration));
    }

    @NotNull
    @Override
    public AnalyzeExhaust analyzeBodiesInFiles(
            @NotNull Project project,
            @NotNull List<AnalyzerScriptParameter> scriptParameters,
            @NotNull Predicate<PsiFile> filesForBodiesResolve,
            @NotNull BindingTrace traceContext,
            @NotNull BodiesResolveContext bodiesResolveContext,
            @NotNull ModuleConfiguration configuration
    ) {
        return AnalyzerFacadeForEverything.analyzeBodiesInFilesWithJavaIntegration(project, scriptParameters, filesForBodiesResolve,
                                                                                   traceContext, bodiesResolveContext, configuration);
    }

    @NotNull
    @Override
    public ResolveSession getLazyResolveSession(@NotNull Project project, @NotNull Collection<JetFile> files) {
        Collection<JetFile> allFiles = getLibraryFiles(project);
        if (allFiles != null) {
            allFiles.addAll(files);
        }
        else {
            allFiles = files;
        }

        LockBasedStorageManager storageManager = new LockBasedStorageManager();
        FileBasedDeclarationProviderFactory declarationProviderFactory = new FileBasedDeclarationProviderFactory(storageManager, allFiles, Predicates.<FqName>alwaysFalse());
        ModuleDescriptor lazyModule = new ModuleDescriptor(Name.special("<lazy module>"));
        ModuleInfo libraryModuleConfiguration = new ModuleInfo(new ModuleDescriptor(ModuleInfo.STUBS_MODULE_NAME), project);
        XAnalyzerFacade.analyzeFiles(libraryModuleConfiguration, getLibraryFiles(project), false).getBindingContext();
        return new ResolveSession(project, storageManager, lazyModule, createModuleInfo(project, libraryModuleConfiguration), declarationProviderFactory);
    }

    private static List<JetFile> getLibraryFiles(Project project) {
        VirtualFile libraryFile = KotlinJsBuildConfigurationManager.getLibLocation(project);
        List<JetFile> allFiles = new ArrayList<JetFile>();
        Traverser.traverseFile(project, libraryFile, allFiles);
        return allFiles;
    }
}
