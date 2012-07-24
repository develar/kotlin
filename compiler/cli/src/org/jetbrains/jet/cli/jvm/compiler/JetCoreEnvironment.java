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

package org.jetbrains.jet.cli.jvm.compiler;

import com.intellij.core.CoreApplicationEnvironment;
import com.intellij.core.JavaCoreProjectEnvironment;
import com.intellij.lang.java.JavaParserDefinition;
import com.intellij.mock.MockApplication;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElementFinder;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jet.asJava.JavaElementFinder;
import org.jetbrains.jet.cli.jvm.JVMConfigurationKeys;
import org.jetbrains.jet.config.CommonConfigurationKeys;
import org.jetbrains.jet.config.CompilerConfiguration;
import org.jetbrains.jet.lang.parsing.JetParser;
import org.jetbrains.jet.lang.parsing.JetParserDefinition;
import org.jetbrains.jet.lang.psi.JetFile;
import org.jetbrains.jet.lang.resolve.java.JetFilesProvider;
import org.jetbrains.jet.lang.resolve.java.extAnnotations.CoreAnnotationsProvider;
import org.jetbrains.jet.lang.resolve.java.extAnnotations.ExternalAnnotationsProvider;
import org.jetbrains.jet.lang.types.lang.JetStandardLibrary;
import org.jetbrains.jet.plugin.JetFileType;
import org.jetbrains.jet.utils.PathUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class JetCoreEnvironment extends JavaCoreProjectEnvironment {
    private final List<JetFile> sourceFiles = new ArrayList<JetFile>();
    private final CoreAnnotationsProvider annotationsProvider;
    private final CompilerConfiguration configuration;
    private boolean initialized = false;

    @NotNull
    public static JetCoreEnvironment createCoreEnvironmentForJS(Disposable disposable, @NotNull CompilerConfiguration configuration) {
        return new JetCoreEnvironment(disposable, configuration);
    }

    @NotNull
    public static JetCoreEnvironment createCoreEnvironmentForJVM(Disposable disposable, @NotNull CompilerConfiguration configuration) {
        return new JetCoreEnvironment(disposable, configuration);
    }

    public JetCoreEnvironment(Disposable parentDisposable, @NotNull CompilerConfiguration configuration) {
        super(parentDisposable, new CoreApplicationEnvironment(parentDisposable));
        this.configuration = configuration;

        getEnvironment().registerFileType(JetFileType.INSTANCE, "kt");
        getEnvironment().registerFileType(JetFileType.INSTANCE, "kts");
        getEnvironment().registerFileType(JetFileType.INSTANCE, "ktm");
        getEnvironment().registerFileType(JetFileType.INSTANCE, JetParser.KTSCRIPT_FILE_SUFFIX); // should be renamed to kts
        getEnvironment().registerFileType(JetFileType.INSTANCE, "jet");
        getEnvironment().registerParserDefinition(new JavaParserDefinition());
        getEnvironment().registerParserDefinition(new JetParserDefinition());


        myProject.registerService(JetFilesProvider.class, new CliJetFilesProvider(this));
        Extensions.getArea(myProject)
                .getExtensionPoint(PsiElementFinder.EP_NAME)
                .registerExtension(new JavaElementFinder(myProject));

        annotationsProvider = new CoreAnnotationsProvider();
        myProject.registerService(ExternalAnnotationsProvider.class, annotationsProvider);

        for (File path : configuration.getList(JVMConfigurationKeys.CLASSPATH_KEY)) {
            addJarToClassPath(path);
        }
        for (File path : configuration.getList(JVMConfigurationKeys.ANNOTATIONS_PATH_KEY)) {
            addExternalAnnotationsRoot(PathUtil.jarFileOrDirectoryToVirtualFile(path));
        }
        for (String path : configuration.getList(CommonConfigurationKeys.SOURCE_ROOTS_KEY)) {
            addSources(path);
        }

        JetStandardLibrary.initialize(getProject());
        initialized = true;
    }

    public CompilerConfiguration getConfiguration() {
        return configuration;
    }

    public MockApplication getApplication() {
        return getEnvironment().getApplication();
    }

    private void addExternalAnnotationsRoot(VirtualFile root) {
        annotationsProvider.addExternalAnnotationsRoot(root);
    }

    private void addSources(File file) {
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File child : files) {
                    addSources(child);
                }
            }
        }
        else {
            VirtualFile fileByPath = getEnvironment().getLocalFileSystem().findFileByPath(file.getAbsolutePath());
            if (fileByPath != null) {
                PsiFile psiFile = PsiManager.getInstance(getProject()).findFile(fileByPath);
                if (psiFile instanceof JetFile) {
                    sourceFiles.add((JetFile) psiFile);
                }
            }
        }
    }

    private void addSources(String path) {
        if (path == null) {
            return;
        }

        VirtualFile vFile = getEnvironment().getLocalFileSystem().findFileByPath(path);
        if (vFile == null) {
            throw new CompileEnvironmentException("File/directory not found: " + path);
        }
        if (!vFile.isDirectory() && vFile.getFileType() != JetFileType.INSTANCE) {
            throw new CompileEnvironmentException("Not a Kotlin file: " + path);
        }

        addSources(new File(path));
    }

    @Override
    public void addJarToClassPath(File path) {
        if (initialized) {
            throw new IllegalStateException("Cannot add class path when JetCoreEnvironment is already initialized");
        }
        super.addJarToClassPath(path);
    }

    public List<JetFile> getSourceFiles() {
        return sourceFiles;
    }
}
