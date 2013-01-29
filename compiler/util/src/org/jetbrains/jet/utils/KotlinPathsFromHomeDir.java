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

package org.jetbrains.jet.utils;

import org.jetbrains.annotations.NotNull;

import java.io.File;

public class KotlinPathsFromHomeDir implements KotlinPaths {
    // kotlinc directory
    private final File homePath;

    public KotlinPathsFromHomeDir(@NotNull File homePath) {
        this.homePath = homePath;
    }

    @Override
    @NotNull
    public File getHomePath() {
        return homePath;
    }

    @Override
    @NotNull
    public File getLibPath() {
        return new File(homePath, "lib");
    }

    @Override
    @NotNull
    public File getRuntimePath() {
        return getLibraryFile(getRuntimeName(true));
    }

    @NotNull
    @Override
    public File getRuntimePath(boolean forJvm) {
        return getLibraryFile(getRuntimeName(forJvm));
    }

    @Override
    @NotNull
    public File getJdkAnnotationsPath() {
        return getLibraryFile(PathUtil.JDK_ANNOTATIONS_JAR);
    }

    @NotNull
    private File getLibraryFile(@NotNull String fileName) {
        return new File(getLibPath(), fileName);
    }

    public static String getRuntimeName(boolean forJvm) {
        return forJvm ? "kotlin-runtime.jar" : "kotlin-js-lib.zip";
    }
}
