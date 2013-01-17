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

package org.jetbrains.kotlin.compiler;

import com.intellij.core.JavaCoreApplicationEnvironment;
import com.intellij.core.JavaCoreProjectEnvironment;
import com.intellij.mock.MockApplication;
import com.intellij.mock.MockProject;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.openapi.vfs.local.CoreLocalFileSystem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jet.lang.parsing.JetParserDefinition;
import org.jetbrains.jet.lang.parsing.JetScriptDefinitionProvider;
import org.jetbrains.jet.lang.types.lang.KotlinBuiltIns;
import org.jetbrains.jet.plugin.JetFileType;

public class CompileContext {
    protected final JavaCoreApplicationEnvironment applicationEnvironment;
    protected final JavaCoreProjectEnvironment projectEnvironment;

    public CompileContext(@NotNull Disposable parentDisposable) {
        this.applicationEnvironment = new JavaCoreApplicationEnvironment(parentDisposable);
        applicationEnvironment.registerFileType(JetFileType.INSTANCE, "kt");
        applicationEnvironment.registerFileType(JetFileType.INSTANCE, "kts");
        applicationEnvironment.registerFileType(JetFileType.INSTANCE, "ktm");
        applicationEnvironment.registerFileType(JetFileType.INSTANCE, JetParserDefinition.KTSCRIPT_FILE_SUFFIX); // should be renamed to kts
        applicationEnvironment.registerFileType(JetFileType.INSTANCE, "jet");
        applicationEnvironment.registerParserDefinition(new JetParserDefinition());

        projectEnvironment = new JavaCoreProjectEnvironment(parentDisposable, applicationEnvironment);

        MockProject project = projectEnvironment.getProject();
        project.registerService(JetScriptDefinitionProvider.class, new JetScriptDefinitionProvider());

        initializeKotlinBuiltIns();
    }

    protected void initializeKotlinBuiltIns() {
        KotlinBuiltIns.initialize(getProject());
    }

    public Project getProject() {
        return projectEnvironment.getProject();
    }

    public MockApplication getApplication() {
        return applicationEnvironment.getApplication();
    }

    public CoreLocalFileSystem getLocalFileSystem() {
        return applicationEnvironment.getLocalFileSystem();
    }

    public VirtualFileSystem getJarFileSystem() {
        return applicationEnvironment.getJarFileSystem();
    }
}
