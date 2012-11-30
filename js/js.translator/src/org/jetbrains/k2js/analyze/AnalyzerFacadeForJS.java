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

package org.jetbrains.k2js.analyze;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jet.analyzer.AnalyzeExhaust;
import org.jetbrains.jet.di.InjectorForTopDownAnalyzerForJs;
import org.jetbrains.jet.lang.descriptors.ModuleDescriptor;
import org.jetbrains.jet.lang.psi.JetFile;
import org.jetbrains.jet.lang.resolve.*;
import org.jetbrains.jet.lang.resolve.name.Name;
import org.jetbrains.k2js.config.Config;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author Pavel Talanov
 */
public final class AnalyzerFacadeForJS {

    private AnalyzerFacadeForJS() {
    }

    @NotNull
    public static AnalyzeExhaust analyzeFilesAndCheckErrors(
            @NotNull List<JetFile> files,
            @NotNull Config config
    ) {
        AnalyzeExhaust libraryExhaust = analyzeFiles(config.getLibFiles(), config.getProject(), null, false);
        libraryExhaust.throwIfError();

        checkForErrors(files);
        return analyzeFiles(files, config.getProject(), libraryExhaust.getBindingContext(), true);
    }

    @NotNull
    public static AnalyzeExhaust analyzeFiles(
            @NotNull Collection<JetFile> files,
            @NotNull Project project,
            @Nullable BindingContext parentBindingContext,
            boolean analyzeCompletely
    ) {
        return analyzeFiles(files, project, parentBindingContext, analyzeCompletely ? Predicates.<PsiFile>alwaysTrue() : Predicates.<PsiFile>alwaysFalse(), null, false);
    }

    @NotNull
    public static AnalyzeExhaust analyzeFilesAndStoreBodyContext(
            @NotNull Collection<JetFile> files,
            @NotNull Project project,
            @Nullable BindingContext parentBindingContext,
            boolean analyzeCompletely
    ) {
        return analyzeFiles(files, project, parentBindingContext, analyzeCompletely ? Predicates.<PsiFile>alwaysTrue() : Predicates.<PsiFile>alwaysFalse(), null, true);
    }

    @NotNull
    public static AnalyzeExhaust analyzeFiles(
            @NotNull Collection<JetFile> files,
            @NotNull Project project,
            BindingContext parentBindingContext,
            @NotNull Predicate<PsiFile> analyzeCompletely,
            @Nullable BodiesResolveContext bodiesResolveContext,
            boolean storeContextForBodiesResolve
    ) {
        ModuleDescriptor owner = new ModuleDescriptor(Name.special("<module>"));
        TopDownAnalysisParameters topDownAnalysisParameters =
                new TopDownAnalysisParameters(analyzeCompletely, false, false, Collections.<AnalyzerScriptParameter>emptyList());
        BindingTrace trace = parentBindingContext == null ?
                             new ObservableBindingTrace(new BindingTraceContext()) :
                             new DelegatingBindingTrace(parentBindingContext, "trace for analyzing library in js");
        InjectorForTopDownAnalyzerForJs injector = new InjectorForTopDownAnalyzerForJs(project, topDownAnalysisParameters, trace, owner,
                                                                                       new JsModuleConfiguration(project,
                                                                                                                 parentBindingContext));
        try {
            injector.getTopDownAnalyzer().analyzeFiles(files, Collections.<AnalyzerScriptParameter>emptyList());
            assert !storeContextForBodiesResolve || bodiesResolveContext == null;
            return AnalyzeExhaust.success(trace.getBindingContext(),
                                          storeContextForBodiesResolve ? new CachedBodiesResolveContext(
                                                  injector.getTopDownAnalysisContext()) : bodiesResolveContext,
                                          injector.getModuleConfiguration());
        }
        finally {
            injector.destroy();
        }
    }

    public static void checkForErrors(@NotNull Collection<JetFile> allFiles) {
        for (JetFile file : allFiles) {
            AnalyzingUtils.checkForSyntacticErrors(file);
        }
    }
}