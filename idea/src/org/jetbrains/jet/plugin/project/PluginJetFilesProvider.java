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

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.roots.ModuleFileIndex;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.Function;
import com.intellij.util.containers.OrderedSet;
import com.intellij.util.indexing.FileBasedIndex;
import gnu.trove.THashSet;
import org.jetbrains.jet.lang.psi.JetFile;
import org.jetbrains.jet.lang.resolve.java.JetFilesProvider;
import org.jetbrains.jet.plugin.JetFileType;

import java.util.Collection;
import java.util.Set;

public class PluginJetFilesProvider extends JetFilesProvider {
    private final Project project;

    public PluginJetFilesProvider(Project project) {
        this.project = project;
    }

    public static final Function<JetFile, Collection<JetFile>> WHOLE_PROJECT_DECLARATION_PROVIDER = new Function<JetFile, Collection<JetFile>>() {

        @Override
        public Collection<JetFile> fun(final JetFile rootFile) {
            final Project project = rootFile.getProject();
            final Set<JetFile> files = new OrderedSet<JetFile>();

            Module rootModule = ModuleUtilCore.findModuleForPsiElement(rootFile);
            if (rootModule != null) {
                Set<Module> allModules = new THashSet<Module>();
                ModuleUtilCore.getDependencies(rootModule, allModules);

                for (Module module : allModules) {
                    final ModuleFileIndex index = ModuleRootManager.getInstance(module).getFileIndex();
                    index.iterateContent(new ContentIterator() {
                        @Override
                        public boolean processFile(VirtualFile file) {
                            if (file.isDirectory()) return true;
                            if (!index.isInSourceContent(file) && !index.isInTestSourceContent(file)) return true;

                            FileType fileType = FileTypeManager.getInstance().getFileTypeByFile(file);
                            if (fileType != JetFileType.INSTANCE) return true;
                            PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
                            if (psiFile instanceof JetFile) {
                                if (rootFile.getOriginalFile() != psiFile) {
                                    files.add((JetFile) psiFile);
                                }
                            }
                            return true;
                        }
                    });
                }
            }

            files.add(rootFile);
            return files;
        }
    };

    @Override
    public Function<JetFile, Collection<JetFile>> sampleToAllFilesInModule() {
        return WHOLE_PROJECT_DECLARATION_PROVIDER;
    }

    @Override
    public Collection<JetFile> allInScope(GlobalSearchScope scope) {
        final PsiManager manager = PsiManager.getInstance(project);
        final Set<JetFile> files = new THashSet<JetFile>();
        FileBasedIndex.getInstance().processValues(FileTypeIndex.NAME, JetFileType.INSTANCE, null, new FileBasedIndex.ValueProcessor<Void>() {
            @Override
            public boolean process(VirtualFile file, Void value) {
                PsiFile psiFile = manager.findFile(file);
                if (psiFile instanceof JetFile) {
                    files.add((JetFile) psiFile);
                }
                return true;
            }
        }, scope);
        return files;
    }
}
