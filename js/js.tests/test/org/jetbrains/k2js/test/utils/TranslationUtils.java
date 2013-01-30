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

package org.jetbrains.k2js.test.utils;

import com.google.common.base.Predicate;
import com.google.common.collect.Lists;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.SingletonSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jet.analyzer.AnalyzeExhaust;
import org.jetbrains.jet.lang.descriptors.ModuleDescriptor;
import org.jetbrains.jet.lang.psi.JetFile;
import org.jetbrains.jet.lang.resolve.AnalyzingUtils;
import org.jetbrains.jet.lang.resolve.TopDownAnalysisParameters;
import org.jetbrains.jet.lang.resolve.name.Name;
import org.jetbrains.k2js.config.EcmaVersion;
import org.jetbrains.k2js.facade.K2JSTranslator;
import org.jetbrains.k2js.facade.MainCallParameters;
import org.jetbrains.k2js.test.config.TestConfig;
import org.jetbrains.k2js.test.config.TestConfigFactory;
import org.jetbrains.kotlin.compiler.ModuleInfo;
import org.jetbrains.kotlin.lang.resolve.XAnalyzerFacade;

import java.lang.ref.SoftReference;
import java.util.Collections;
import java.util.List;

//TODO: use method object
public final class TranslationUtils {

    private TranslationUtils() {
    }

    @NotNull
    private static SoftReference<ModuleInfo> cachedLibraryContext = new SoftReference<ModuleInfo>(null);

    @Nullable
    private static List<JetFile> libFiles = null;

    @NotNull
    private static List<JetFile> getAllLibFiles(@NotNull Project project) {
        if (libFiles == null) {
            libFiles = initLibFiles(project);
        }
        return libFiles;
    }

    @NotNull
    public static ModuleInfo getLibraryContext(@NotNull Project project) {
        ModuleInfo context = cachedLibraryContext.get();
        if (context == null) {
            List<JetFile> allLibFiles = getAllLibFiles(project);
            Predicate<PsiFile> filesWithCode = new Predicate<PsiFile>() {
                @Override
                public boolean apply(PsiFile file) {
                    return isFileWithCode((JetFile) file);
                }
            };
            XAnalyzerFacade.checkForErrors(allLibFiles);
            ModuleInfo moduleConfiguration = new ModuleInfo(new ModuleDescriptor(ModuleInfo.STUBS_MODULE_NAME), project);
            AnalyzeExhaust exhaust = XAnalyzerFacade.analyzeFiles(moduleConfiguration, allLibFiles,
                                                                  new TopDownAnalysisParameters(filesWithCode), false);
            exhaust.throwIfError();
            cachedLibraryContext = new SoftReference<ModuleInfo>(moduleConfiguration);
            context = moduleConfiguration;
        }
        return context;
    }

    private static boolean isFileWithCode(@NotNull JetFile file) {
        for (String filename : TestConfig.LIB_FILES_WITH_CODE) {
            if (file.getName().contains(filename)) {
                return true;
            }
        }
        return false;
    }

    public static void translateFiles(
            @NotNull Project project, @NotNull List<String> inputFiles,
            @NotNull String outputFile,
            @NotNull MainCallParameters mainCallParameters,
            @NotNull EcmaVersion version, TestConfigFactory configFactory
    ) throws Exception {
        List<JetFile> psiFiles = createPsiFileList(project, inputFiles, null);
        ModuleInfo libraryContext = getLibraryContext(project);
        ModuleInfo moduleInfo = new ModuleInfo(new ModuleDescriptor(Name.special('<' + TestConfig.TEST_MODULE_NAME + '>')), project,
                                                        Collections.singletonList(libraryContext), new SingletonSet<ModuleInfo>(libraryContext));
        AnalyzeExhaust exhaust = XAnalyzerFacade.analyzeFiles(moduleInfo, psiFiles, true);
        exhaust.throwIfError();
        AnalyzingUtils.throwExceptionOnErrors(moduleInfo.getBindingContext());
        XAnalyzerFacade.checkForErrors(psiFiles);
        TestConfig config = configFactory.create(moduleInfo, version);
        K2JSTranslator.translateAndSaveToFile(mainCallParameters, psiFiles, outputFile, config,
                                              moduleInfo.getBindingContext(), null);
    }

    @NotNull
    private static List<JetFile> initLibFiles(@NotNull Project project) {
        return createPsiFileList(project, TestConfig.LIB_FILE_NAMES, TestConfig.LIBRARIES_LOCATION);
    }

    @NotNull
    private static List<JetFile> createPsiFileList(@NotNull Project project, @NotNull List<String> list, @Nullable String root) {
        List<JetFile> libFiles = Lists.newArrayList();
        PsiManager psiManager = PsiManager.getInstance(project);
        VirtualFileSystem fileSystem = VirtualFileManager.getInstance().getFileSystem(StandardFileSystems.FILE_PROTOCOL);
        VirtualFile rootFile = root == null ? null : fileSystem.findFileByPath(root);
        for (String libFileName : list) {
            VirtualFile virtualFile = rootFile == null ? fileSystem.findFileByPath(libFileName) : rootFile.findFileByRelativePath(libFileName);
            assert virtualFile != null;
            PsiFile psiFile = psiManager.findFile(virtualFile);
            libFiles.add((JetFile) psiFile);
        }
        return libFiles;
    }
}
