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

package org.jetbrains.jet.cli.js;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jet.analyzer.AnalyzeExhaust;
import org.jetbrains.jet.cli.common.CLICompiler;
import org.jetbrains.jet.cli.common.ExitCode;
import org.jetbrains.jet.cli.common.messages.*;
import org.jetbrains.jet.cli.jvm.compiler.JetCoreEnvironment;
import org.jetbrains.jet.config.CommonConfigurationKeys;
import org.jetbrains.jet.config.CompilerConfiguration;
import org.jetbrains.jet.lang.descriptors.ModuleDescriptor;
import org.jetbrains.jet.lang.psi.JetFile;
import org.jetbrains.jet.lang.resolve.TopDownAnalysisParameters;
import org.jetbrains.k2js.ToJsSubCompiler;
import org.jetbrains.k2js.Traverser;
import org.jetbrains.k2js.config.ClassPathLibrarySourcesLoader;
import org.jetbrains.k2js.config.MetaInfServices;
import org.jetbrains.kotlin.compiler.JsCompilerConfigurationKeys;
import org.jetbrains.kotlin.compiler.ModuleInfo;
import org.jetbrains.kotlin.lang.resolve.XAnalyzerFacade;

import java.io.File;
import java.util.*;

import static org.jetbrains.jet.cli.common.messages.CompilerMessageLocation.NO_LOCATION;

public class K2JSCompiler extends CLICompiler<K2JSCompilerArguments> {
    @SuppressWarnings("UnusedDeclaration")
    public static void main(String... args) {
        doMain(new K2JSCompiler(), args);
    }

    @NotNull
    @Override
    protected K2JSCompilerArguments createArguments() {
        return new K2JSCompilerArguments();
    }

    @NotNull
    @Override
    protected ExitCode doExecute(K2JSCompilerArguments arguments, PrintingMessageCollector messageCollector, Disposable rootDisposable) {
        if (arguments.sourceFiles == null) {
            messageCollector.report(CompilerMessageSeverity.ERROR, "Specify sources location via -sourceFiles", NO_LOCATION);
            return ExitCode.INTERNAL_ERROR;
        }
        String outputFile = arguments.outputFile;
        if (outputFile == null) {
            messageCollector.report(CompilerMessageSeverity.ERROR, "Specify output file via -output", CompilerMessageLocation.NO_LOCATION);
            return ExitCode.INTERNAL_ERROR;
        }

        CompilerConfiguration configuration = new CompilerConfiguration();
        configuration.addAll(CommonConfigurationKeys.SOURCE_ROOTS_KEY, Arrays.asList(arguments.sourceFiles));
        JetCoreEnvironment environmentForJS = new JetCoreEnvironment(rootDisposable, configuration);

        Project project = environmentForJS.getProject();

        ClassPathLibrarySourcesLoader sourceLoader = new ClassPathLibrarySourcesLoader(project);
        environmentForJS.getSourceFiles().addAll(sourceLoader.findSourceFiles());

        if (arguments.isVerbose()) {
            reportCompiledSourcesList(messageCollector, environmentForJS);
        }

        final Map<String, List<JetFile>> modules = getConfig(arguments, project);

        List<JetFile> libraryFiles = modules.get(ModuleInfo.STUBS_MODULE_NAME.getName());
        ModuleInfo libraryModuleConfiguration = new ModuleInfo(
                new ModuleDescriptor(ModuleInfo.STUBS_MODULE_NAME), project);
        if (!analyze(messageCollector, libraryModuleConfiguration, libraryFiles, false)) {
            return ExitCode.COMPILATION_ERROR;
        }

        List<JetFile> otherModulesFiles;
        ModuleInfo parentLibraryConfiguration = libraryModuleConfiguration;
        if (!modules.isEmpty()) {
            otherModulesFiles = new ArrayList<JetFile>();
            // todo normal module dependency resolution (exhaust per module)
            for (Map.Entry<String, List<JetFile>> entry : modules.entrySet()) {
                if (entry.getKey() != ModuleInfo.STUBS_MODULE_NAME.getName()) {
                    otherModulesFiles.addAll(entry.getValue());
                }
            }

            if (!otherModulesFiles.isEmpty()) {
                parentLibraryConfiguration = new ModuleInfo("externalModules", project, Collections.singletonList(libraryModuleConfiguration));
                if (!analyze(messageCollector, parentLibraryConfiguration, otherModulesFiles, false)) {
                    return ExitCode.COMPILATION_ERROR;
                }
            }
        }

        String moduleId = FileUtil.getNameWithoutExtension(new File(arguments.outputFile));
        ModuleInfo moduleInfo = new ModuleInfo(moduleId, project, Collections.singletonList(parentLibraryConfiguration));
        if (!analyze(messageCollector, moduleInfo, environmentForJS.getSourceFiles(), true)) {
            return ExitCode.COMPILATION_ERROR;
        }

        configuration.put(JsCompilerConfigurationKeys.SOURCEMAP, arguments.sourcemap);
        configuration.put(JsCompilerConfigurationKeys.TARGET, arguments.target);
        configuration.put(JsCompilerConfigurationKeys.OUTPUT_FILE, outputFile);
        configuration.put(JsCompilerConfigurationKeys.MAIN, arguments.main);
        try {
            new ToJsSubCompiler().compile(configuration, moduleInfo, environmentForJS.getSourceFiles());
        }
        catch (Throwable e) {
            messageCollector.report(CompilerMessageSeverity.EXCEPTION, MessageRenderer.PLAIN.renderException(e),
                                    CompilerMessageLocation.NO_LOCATION);
            return ExitCode.INTERNAL_ERROR;
        }
        return ExitCode.OK;
    }

    private static boolean analyze(
            @NotNull PrintingMessageCollector messageCollector,
            @NotNull final ModuleInfo moduleConfiguration,
            @NotNull final List<JetFile> sources,
            final boolean analyzeCompletely
    ) {
        AnalyzeExhaust exhaust = new AnalyzerWithCompilerReport(messageCollector).analyzeAndReport(new Computable<AnalyzeExhaust>() {
            @Override
            public AnalyzeExhaust compute() {
                return XAnalyzerFacade
                        .analyzeFiles(moduleConfiguration, sources, new TopDownAnalysisParameters(analyzeCompletely), false);
            }
        }, sources);
        if (exhaust == null) {
            return false;
        }
        exhaust.throwIfError();
        return true;
    }

    private static void reportCompiledSourcesList(@NotNull PrintingMessageCollector messageCollector,
            @NotNull JetCoreEnvironment environmentForJS) {
        List<JetFile> files = environmentForJS.getSourceFiles();
        Iterable<String> fileNames = Iterables.transform(files, new Function<JetFile, String>() {
            @Override
            public String apply(@Nullable JetFile file) {
                assert file != null;
                VirtualFile virtualFile = file.getVirtualFile();
                if (virtualFile != null) {
                    return FileUtil.toSystemIndependentName(virtualFile.getPath());
                }
                return file.getName() + "(no virtual file)";
            }
        });
        messageCollector.report(CompilerMessageSeverity.LOGGING, "Compiling source files: " + Joiner.on(", ").join(fileNames),
                                CompilerMessageLocation.NO_LOCATION);
    }

    @NotNull
    private static Map<String, List<JetFile>> getConfig(@NotNull K2JSCompilerArguments arguments, @NotNull Project project) {
        if (arguments.libraryFiles != null) {
            return collectModules(project, arguments.libraryFiles);
        }
        else {
            return Collections.singletonMap(ModuleInfo.STUBS_MODULE_NAME.getName(), MetaInfServices
                    .loadServicesFiles("META-INF/services/org.jetbrains.kotlin.js.libraryDefinitions", project));
        }
    }

    @NotNull
    private static Map<String, List<JetFile>> collectModules(Project project, String[] files) {
        if (files.length == 0) {
            return Collections.emptyMap();
        }

        final Map<String, List<JetFile>> modules = Maps.newLinkedHashMap();
        List<JetFile> psiFiles = null;
        String moduleName = ModuleInfo.STUBS_MODULE_NAME.getName();
        VirtualFileSystem fileSystem = VirtualFileManager.getInstance().getFileSystem(StandardFileSystems.FILE_PROTOCOL);
        final PsiManager psiManager = PsiManager.getInstance(project);
        for (String path : files) {
            if (path.charAt(0) == '@') {
                moduleName = path.substring(1);
                psiFiles = null;
            }
            else {
                if (psiFiles == null) {
                    psiFiles = modules.get(moduleName);
                    if (psiFiles == null) {
                        psiFiles = new ArrayList<JetFile>();
                        modules.put(moduleName, psiFiles);
                    }
                }

                VirtualFile file = fileSystem.findFileByPath(path);
                assert file != null;
                VirtualFile jarFile = StandardFileSystems.getJarRootForLocalFile(file);
                if (jarFile != null) {
                    psiFiles = new ArrayList<JetFile>();
                    moduleName = null;
                    modules.put(ModuleInfo.STUBS_MODULE_NAME.getName(), psiFiles);
                    Traverser.traverseFile(project, jarFile, psiFiles);
                }
                else if (file.isDirectory()) {
                    Traverser.traverseFile(project, file, psiFiles);
                }
                else {
                    Traverser.addPsiFile(psiFiles, psiManager, file);
                }
            }
        }

        return modules;
    }
}
