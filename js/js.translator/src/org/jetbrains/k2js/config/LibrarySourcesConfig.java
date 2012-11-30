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

package org.jetbrains.k2js.config;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.*;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jet.lang.psi.JetFile;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author Pavel Talanov
 */
public class LibrarySourcesConfig extends Config {
    @NotNull
    public static final Key<String> EXTERNAL_MODULE_NAME = Key.create("externalModule");
    @NotNull
    public static final String UNKNOWN_EXTERNAL_MODULE_NAME = "<unknown>";

    @NotNull
    private final List<String> files;

    private final List<String> moduleDependencies = new ArrayList<String>();

    public LibrarySourcesConfig(
            @NotNull Project project,
            @NotNull String moduleId,
            @NotNull List<String> files,
            @NotNull EcmaVersion ecmaVersion,
            boolean sourcemap
    ) {
        super(project, moduleId, ecmaVersion, sourcemap);
        this.files = files;
    }

    @NotNull
    @Override
    protected List<JetFile> generateLibFiles() {
        if (files.isEmpty()) {
            return Collections.emptyList();
        }

        final List<JetFile> psiFiles = new ArrayList<JetFile>();
        String moduleName = UNKNOWN_EXTERNAL_MODULE_NAME;
        VirtualFileSystem fileSystem = VirtualFileManager.getInstance().getFileSystem(StandardFileSystems.FILE_PROTOCOL);
        final PsiManager psiManager = PsiManager.getInstance(project);
        for (String path : files) {
            if (path.charAt(0) == '@') {
                moduleName = path.substring(1);
                moduleDependencies.add(moduleName);
            }
            else {
                VirtualFile file = fileSystem.findFileByPath(path);
                assert file != null;
                VirtualFile jarFile = StandardFileSystems.getJarRootForLocalFile(file);
                if (jarFile != null) {
                    traverseFile(project, jarFile, psiFiles, UNKNOWN_EXTERNAL_MODULE_NAME);
                }
                else if (file.isDirectory()) {
                    traverseFile(project, file, psiFiles, moduleName);
                }
                else {
                    addPsiFile(psiFiles, moduleName, psiManager, file);
                }
            }
        }

        return psiFiles;
    }

    @NotNull
    @Override
    public List<String> getModuleDependencies() {
        return moduleDependencies;
    }

    private static void addPsiFile(
            final Collection<JetFile> result,
            @Nullable final String moduleName,
            final PsiManager psiManager,
            final VirtualFile file
    ) {
        PsiFile psiFile = psiManager.findFile(file);
        assert psiFile != null;
        if (moduleName != null) {
            psiFile.putUserData(EXTERNAL_MODULE_NAME, moduleName);
        }
        result.add((JetFile) psiFile);
    }

    public static void traverseFile(Project project, VirtualFile file, final Collection<JetFile> result, @Nullable final String moduleName) {
        final PsiManager psiManager = PsiManager.getInstance(project);
        VfsUtilCore.visitChildrenRecursively(file, new VirtualFileVisitor() {
            @Override
            public boolean visitFile(@NotNull VirtualFile file) {
                if (file.getName().endsWith(".kt")) {
                    addPsiFile(result, moduleName, psiManager, file);
                    return false;
                }
                return true;
            }
        });
    }
}