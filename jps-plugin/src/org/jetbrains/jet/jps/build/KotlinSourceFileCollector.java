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

package org.jetbrains.jet.jps.build;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildRootDescriptor;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.DirtyFilesHolder;
import org.jetbrains.jps.builders.FileProcessor;
import org.jetbrains.jps.builders.logging.ProjectBuilderLogger;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.ModuleBuildTarget;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.module.JpsModuleSourceRoot;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.List;
import java.util.Collection;

public class KotlinSourceFileCollector {
    private static final String KOTLIN_EXTENSION = ".kt";

    public static final FileFilter KOTLIN_SOURCES_FILTER =
            SystemInfo.isFileSystemCaseSensitive ?
            new FileFilter() {
                @Override
                public boolean accept(@NotNull File file) {
                    return file.getPath().endsWith(KOTLIN_EXTENSION);
                }
            } :
            new FileFilter() {
                @Override
                public boolean accept(@NotNull File file) {
                    return StringUtil.endsWithIgnoreCase(file.getPath(), KOTLIN_EXTENSION);
                }
            };

    // For incremental compilation
    public static <R extends BuildRootDescriptor, T extends BuildTarget<R>> List<File> getDirtySourceFiles(DirtyFilesHolder<R, T> dirtyFilesHolder)
            throws IOException {
        final List<File> files = ContainerUtil.newArrayList();
        dirtyFilesHolder.processDirtyFiles(new FileProcessor<R, T>() {
            @Override
            public boolean apply(T target, File file, R descriptor) throws IOException {
                if (KOTLIN_SOURCES_FILTER.accept(file)) {
                    files.add(file);
                }
                return true;
            }
        });
        return files;
    }

    public static void logCompiledFiles(Collection<File> filesToCompile, CompileContext context, String builderName) throws IOException {
        ProjectBuilderLogger logger = context.getLoggingManager().getProjectBuilderLogger();
        if (logger.isEnabled()) {
            logger.logCompiledFiles(filesToCompile, builderName, "Compiling kotlin files:");
        }
    }

    @NotNull
    public static List<File> getAllKotlinSourceFiles(@NotNull ModuleBuildTarget target) {
        final List<File> result = ContainerUtil.newArrayList();
        for (JpsModuleSourceRoot sourceRoot : getRelevantSourceRoots(target)) {
            FileUtil.processFilesRecursively(sourceRoot.getFile(), new Processor<File>() {
                @Override
                public boolean process(File file) {
                    if (file.isFile() && KOTLIN_SOURCES_FILTER.accept(file)) {
                        result.add(file);
                    }
                    return true;
                }
            });
        }
        return result;
    }

    private static Iterable<JpsModuleSourceRoot> getRelevantSourceRoots(ModuleBuildTarget target) {
        JavaSourceRootType sourceRootType = target.isTests() ? JavaSourceRootType.TEST_SOURCE : JavaSourceRootType.SOURCE;

        //noinspection unchecked
        return (Iterable) target.getModule().getSourceRoots(sourceRootType);
    }

    private KotlinSourceFileCollector() {}
}
