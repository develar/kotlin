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
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class JetCoreEnvironment extends JavaCoreProjectEnvironment {
    private final List<JetFile> sourceFiles = new ArrayList<JetFile>();
    private final CoreAnnotationsProvider annotationsProvider;

    @NotNull
    public static JetCoreEnvironment createCoreEnvironmentForJS(Disposable disposable) {
        return new JetCoreEnvironment(disposable, new CompilerConfiguration());
    }

    @NotNull
    public static JetCoreEnvironment createCoreEnvironmentForJVM(Disposable disposable, @NotNull CompilerConfiguration configuration) {
        return new JetCoreEnvironment(disposable, configuration);
    }

    public JetCoreEnvironment(Disposable parentDisposable, @NotNull CompilerConfiguration configuration) {
        super(parentDisposable, new CoreApplicationEnvironment(parentDisposable));

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

        configure(configuration);

        JetStandardLibrary.initialize(getProject());
    }

    public void addExternalAnnotationsRoot(VirtualFile root) {
        annotationsProvider.addExternalAnnotationsRoot(root);
    }

    public MockApplication getApplication() {
        return getEnvironment().getApplication();
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

    public void addSources(VirtualFile vFile) {
        if (vFile.isDirectory()) {
            for (VirtualFile virtualFile : vFile.getChildren()) {
                addSources(virtualFile);
            }
        }
        else {
            if (vFile.getFileType() == JetFileType.INSTANCE) {
                PsiFile psiFile = PsiManager.getInstance(getProject()).findFile(vFile);
                if (psiFile instanceof JetFile) {
                    sourceFiles.add((JetFile) psiFile);
                }
            }
        }
    }

    public void addSources(String path) {
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

    public List<JetFile> getSourceFiles() {
        return sourceFiles;
    }

    public void addToClasspathFromClassLoader(ClassLoader loader) {
        ClassLoader parent = loader.getParent();
        if (parent != null) {
            addToClasspathFromClassLoader(parent);
        }

        if (loader instanceof URLClassLoader) {
            for (URL url : ((URLClassLoader) loader).getURLs()) {
                File file = new File(url.getPath());
                if (file.exists() && (!file.isFile() || file.getPath().endsWith(".jar"))) {
                    addJarToClassPath(file);
                }
            }
        }
    }

    public void configure(@NotNull CompilerConfiguration compilerConfiguration) {
        File[] classpath = compilerConfiguration.getUserData(JVMConfigurationKeys.CLASSPATH_KEY);
        File[] annotationsPath = compilerConfiguration.getUserData(JVMConfigurationKeys.ANNOTATIONS_PATH_KEY);
        if (classpath != null) {
            for (File path : classpath) {
                addJarToClassPath(path);
            }
        }
        if (annotationsPath != null) {
            for (File path : annotationsPath) {
                addExternalAnnotationsRoot(PathUtil.jarFileOrDirectoryToVirtualFile(path));
            }
        }
    }
}
