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

package org.jetbrains.kotlin.lang.resolve;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jet.analyzer.AnalyzeExhaust;
import org.jetbrains.jet.lang.psi.JetFile;
import org.jetbrains.jet.lang.resolve.*;
import org.jetbrains.kotlin.compiler.ModuleInfo;
import org.jetbrains.kotlin.di.InjectorForTopDownXAnalyzer;

import java.util.Collection;
import java.util.Collections;

public final class XAnalyzerFacade {
    private XAnalyzerFacade() {
    }

    @NotNull
    public static AnalyzeExhaust analyzeFiles(
            ModuleInfo moduleConfiguration,
            @NotNull Collection<JetFile> files,
            boolean analyzeCompletely
    ) {
        return analyzeFiles(moduleConfiguration, files, new TopDownAnalysisParameters(analyzeCompletely), false);
    }

    @NotNull
    public static AnalyzeExhaust analyzeFilesAndStoreBodyContext(
            ModuleInfo moduleConfiguration,
            @NotNull Collection<JetFile> files,
            boolean analyzeCompletely
    ) {
        return analyzeFiles(moduleConfiguration, files, new TopDownAnalysisParameters(analyzeCompletely), true);
    }

    @NotNull
    public static AnalyzeExhaust analyzeFiles(
            ModuleInfo moduleConfiguration,
            @NotNull Collection<JetFile> files,
            @NotNull TopDownAnalysisParameters topDownAnalysisParameters,
            boolean storeContextForBodiesResolve
    ) {
        return analyzeFiles(moduleConfiguration, files, topDownAnalysisParameters, null, storeContextForBodiesResolve);
    }

    @NotNull
    private static AnalyzeExhaust analyzeFiles(
            ModuleInfo moduleConfiguration,
            @NotNull Collection<JetFile> files,
            @NotNull TopDownAnalysisParameters topDownAnalysisParameters,
            @Nullable BodiesResolveContext bodiesResolveContext,
            boolean storeContextForBodiesResolve

    ) {
        BindingTrace trace = new BindingTraceContext();
        InjectorForTopDownXAnalyzer
                injector = new InjectorForTopDownXAnalyzer(moduleConfiguration.getProject(), topDownAnalysisParameters, trace, moduleConfiguration.getModuleDescriptor(),
                                                                                       moduleConfiguration);
        try {
            injector.getTopDownAnalyzer().analyzeFiles(files, Collections.<AnalyzerScriptParameter>emptyList());
            assert !storeContextForBodiesResolve || bodiesResolveContext == null;
            moduleConfiguration.bindingContext = trace.getBindingContext();
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
