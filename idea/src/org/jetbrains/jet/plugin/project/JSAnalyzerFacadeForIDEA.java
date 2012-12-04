/*
 * Copyright 2010-2012 JetBrains s.r.o.
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
import org.jetbrains.jet.lang.resolve.lazy.ResolveSession;
import org.jetbrains.jet.lang.resolve.name.FqName;
import org.jetbrains.jet.lang.resolve.name.Name;
import org.jetbrains.k2js.analyze.AnalyzerFacadeForJS;
import org.jetbrains.k2js.analyze.JsModuleConfiguration;
import org.jetbrains.k2js.config.LibrarySourcesConfig;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Pavel Talanov
 */
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
        JsModuleConfiguration libraryModuleConfiguration = new JsModuleConfiguration(new ModuleDescriptor(JsModuleConfiguration.STUBS_MODULE_NAME), project);
        AnalyzerFacadeForJS.analyzeFiles(libraryModuleConfiguration, getLibraryFiles(project), false).getBindingContext();
        JsModuleConfiguration moduleConfiguration = new JsModuleConfiguration(new ModuleDescriptor(MODULE_NAME), project, libraryModuleConfiguration);
        return AnalyzerFacadeForJS.analyzeFilesAndStoreBodyContext(moduleConfiguration, files, false);
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

        FileBasedDeclarationProviderFactory declarationProviderFactory = new FileBasedDeclarationProviderFactory(allFiles, Predicates.<FqName>alwaysFalse());
        ModuleDescriptor lazyModule = new ModuleDescriptor(Name.special("<lazy module>"));
        JsModuleConfiguration libraryModuleConfiguration = new JsModuleConfiguration(new ModuleDescriptor(JsModuleConfiguration.STUBS_MODULE_NAME), project);
        AnalyzerFacadeForJS.analyzeFiles(libraryModuleConfiguration, getLibraryFiles(project), false).getBindingContext();
        return new ResolveSession(project, lazyModule, new JsModuleConfiguration(new ModuleDescriptor(MODULE_NAME), project, libraryModuleConfiguration), declarationProviderFactory);
    }

    private static List<JetFile> getLibraryFiles(Project project) {
        VirtualFile libraryFile = KotlinJsBuildConfigurationManager.getLibLocation(project);
        List<JetFile> allFiles = new ArrayList<JetFile>();
        LibrarySourcesConfig.traverseFile(project, libraryFile, allFiles, null);
        return allFiles;
    }
}
