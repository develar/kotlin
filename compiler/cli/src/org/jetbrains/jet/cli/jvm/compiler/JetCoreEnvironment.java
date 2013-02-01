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

package org.jetbrains.jet.cli.jvm.compiler;

import com.intellij.mock.MockApplication;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jet.cli.common.CLIConfigurationKeys;
import org.jetbrains.jet.cli.common.messages.CompilerMessageLocation;
import org.jetbrains.jet.cli.common.messages.CompilerMessageSeverity;
import org.jetbrains.jet.cli.common.messages.MessageCollector;
import org.jetbrains.jet.cli.jvm.JVMConfigurationKeys;
import org.jetbrains.jet.config.CommonConfigurationKeys;
import org.jetbrains.jet.config.CompilerConfiguration;
import org.jetbrains.jet.lang.parsing.JetScriptDefinitionProvider;
import org.jetbrains.jet.lang.psi.JetFile;
import org.jetbrains.jet.plugin.JetFileType;
import org.jetbrains.jet.utils.PathUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.jetbrains.jet.cli.common.messages.CompilerMessageSeverity.ERROR;
import static org.jetbrains.jet.cli.common.messages.CompilerMessageSeverity.WARNING;

public class JetCoreEnvironment {
    private final JavaCompileContext context;
    private final List<JetFile> sourceFiles = new ArrayList<JetFile>();

    private final CompilerConfiguration configuration;

    private boolean initialized = false;

    public JetCoreEnvironment(@NotNull Disposable parentDisposable, @NotNull CompilerConfiguration configuration) {
        this.configuration = configuration.copy();
        this.configuration.setReadOnly(true);

        context = new JavaCompileContext(parentDisposable, sourceFiles);

        for (File path : configuration.getList(JVMConfigurationKeys.CLASSPATH_KEY)) {
            addToClasspath(path);
        }
        for (File path : configuration.getList(JVMConfigurationKeys.ANNOTATIONS_PATH_KEY)) {
            addExternalAnnotationsRoot(PathUtil.jarFileOrDirectoryToVirtualFile(path));
        }
        for (String path : configuration.getList(CommonConfigurationKeys.SOURCE_ROOTS_KEY)) {
            addSources(path);
        }

        Project project = context.getProject();
        JetScriptDefinitionProvider.getInstance(project).addScriptDefinitions(configuration.getList(CommonConfigurationKeys.SCRIPT_DEFINITIONS_KEY));
        context.doInitializeKotlinBuiltIns();
        initialized = true;
    }

    public CompilerConfiguration getConfiguration() {
        return configuration;
    }

    @NotNull
    public MockApplication getApplication() {
        return context.getApplication();
    }

    @NotNull
    public Project getProject() {
        return context.getProject();
    }

    private void addExternalAnnotationsRoot(VirtualFile root) {
        context.getAnnotationsManager().addExternalAnnotationsRoot(root);
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
            VirtualFile fileByPath = context.getLocalFileSystem().findFileByPath(file.getAbsolutePath());
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

        VirtualFile vFile = context.getLocalFileSystem().findFileByPath(path);
        if (vFile == null) {
            report(ERROR, "Source file or directory not found: " + path);
            return;
        }
        if (!vFile.isDirectory() && vFile.getFileType() != JetFileType.INSTANCE) {
            report(ERROR, "Source entry is not a Kotlin file: " + path);
            return;
        }

        addSources(new File(path));
    }

    public void addToClasspath(File path) {
        if (initialized) {
            throw new IllegalStateException("Cannot add class path when JetCoreEnvironment is already initialized");
        }
        if (path.isFile()) {
            VirtualFile jarFile = context.getApplicationEnvironment().getJarFileSystem().findFileByPath(path + "!/");
            if (jarFile == null) {
                report(WARNING, "Classpath entry points to a file that is not a JAR archive: " + path);
                return;
            }
            context.getProjectEnvironment().addJarToClassPath(path);
        }
        else {
            final VirtualFile root = context.getLocalFileSystem().findFileByPath(path.getAbsolutePath());
            if (root == null) {
                report(WARNING, "Classpath entry points to a non-existent location: " + path);
                return;
            }
            context.getProjectEnvironment().addSourcesToClasspath(root);
        }
    }

    public List<JetFile> getSourceFiles() {
        return sourceFiles;
    }

    private void report(@NotNull CompilerMessageSeverity severity, @NotNull String message) {
        MessageCollector messageCollector = configuration.get(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY);
        if (messageCollector != null) {
            messageCollector.report(severity, message, CompilerMessageLocation.NO_LOCATION);
        }
        else {
            throw new CompileEnvironmentException(message);
        }
    }
}
