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
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.ProjectRootModificationTracker;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.util.Processor;
import com.intellij.util.SingletonSet;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jet.analyzer.AnalyzeExhaust;
import org.jetbrains.jet.analyzer.AnalyzerFacade;
import org.jetbrains.jet.analyzer.AnalyzerFacadeForEverything;
import org.jetbrains.jet.lang.descriptors.ModuleDescriptor;
import org.jetbrains.jet.lang.psi.JetFile;
import org.jetbrains.jet.lang.resolve.AnalyzerScriptParameter;
import org.jetbrains.jet.lang.resolve.BindingTrace;
import org.jetbrains.jet.lang.resolve.BodiesResolveContext;
import org.jetbrains.jet.lang.resolve.lazy.ResolveSession;
import org.jetbrains.jet.lang.resolve.lazy.declarations.FileBasedDeclarationProviderFactory;
import org.jetbrains.jet.lang.resolve.lazy.storage.LockBasedStorageManager;
import org.jetbrains.jet.lang.resolve.name.FqName;
import org.jetbrains.jet.lang.resolve.name.Name;
import org.jetbrains.jet.plugin.framework.KotlinFrameworkDetector;
import org.jetbrains.k2js.Traverser;
import org.jetbrains.kotlin.compiler.ModuleInfo;
import org.jetbrains.kotlin.lang.resolve.XAnalyzerFacade;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public enum JSAnalyzerFacadeForIDEA implements AnalyzerFacade {
    INSTANCE;
    public static final Name MODULE_NAME = Name.special("<module>");

    private static final Key<CachedValue<ModuleInfo>> LIB_MODULE_INFO = Key.create("KT_JS_LIB_MODULE");

    private JSAnalyzerFacadeForIDEA() {
    }


    // MUST BE PER MODULE
    public static ModuleInfo getLibModule(@NotNull final Project project) {
        CachedValue<ModuleInfo> result = project.getUserData(LIB_MODULE_INFO);
        if (result == null) {
            result = CachedValuesManager.getManager(project).createCachedValue(new CachedValueProvider<ModuleInfo>() {
                @Override
                public Result<ModuleInfo> compute() {
                    List<JetFile> allFiles = new ArrayList<JetFile>();
                    Set<Library> processedLibraries = new THashSet<Library>();
                    for (Module module : ModuleManager.getInstance(project).getModules()) {
                        if (KotlinFrameworkDetector.isJsKotlinModule(module)) {
                            getLibraryFiles(allFiles, project, module, processedLibraries);
                        }
                    }

                    ModuleInfo libraryModuleConfiguration = new ModuleInfo(ModuleInfo.STUBS_MODULE_NAME, project);
                    XAnalyzerFacade.analyzeFiles(libraryModuleConfiguration, allFiles, false);
                    return Result.create(libraryModuleConfiguration, ProjectRootModificationTracker.getInstance(project));
                }
            }, false);

            project.putUserData(LIB_MODULE_INFO, result);
        }

        return result.getValue();
    }

    @NotNull
    @Override
    public AnalyzeExhaust analyzeFiles(
            @NotNull Project project,
            @NotNull Collection<JetFile> files,
            @NotNull List<AnalyzerScriptParameter> scriptParameters,
            @NotNull Predicate<PsiFile> filesToAnalyzeCompletely
    ) {
        return XAnalyzerFacade.analyzeFilesAndStoreBodyContext(createModuleInfo(project, getLibModule(project)), files, false);
    }

    public static ModuleInfo createModuleInfo(Project project, ModuleInfo libraryModuleConfiguration) {
        return new ModuleInfo(MODULE_NAME, project, Collections.<ModuleInfo>singletonList(libraryModuleConfiguration),
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
            @NotNull ModuleDescriptor module
    ) {
        return AnalyzerFacadeForEverything.analyzeBodiesInFilesWithJavaIntegration(project, scriptParameters, filesForBodiesResolve,
                                                                                   traceContext, bodiesResolveContext, module);
    }

    @NotNull
    @Override
    public ResolveSession getLazyResolveSession(@NotNull Project project, @NotNull Collection<JetFile> files) {
        LockBasedStorageManager storageManager = new LockBasedStorageManager();
        ModuleInfo lazyModule = createModuleInfo(project, getLibModule(project));
        return new ResolveSession(project, storageManager, lazyModule.getModuleDescriptor(),
                                  new FileBasedDeclarationProviderFactory(storageManager, files, Predicates.<FqName>alwaysFalse()));
    }

    private static List<JetFile> getLibraryFiles(
            final List<JetFile> allFiles,
            final Project project,
            Module module,
            final Set<Library> processedLibraries
    ) {
        ModuleRootManager.getInstance(module).orderEntries().librariesOnly().forEachLibrary(new Processor<Library>() {
            @Override
            public boolean process(Library library) {
                if (!processedLibraries.add(library)) {
                    return true;
                }

                VirtualFile[] libraryFiles = library.getFiles(OrderRootType.CLASSES);
                if (libraryFiles.length > 0) {
                    for (VirtualFile file : libraryFiles) {
                        Traverser.traverseFile(project, file, allFiles);
                    }
                }
                return true;
            }
        });
        return allFiles;
    }
}