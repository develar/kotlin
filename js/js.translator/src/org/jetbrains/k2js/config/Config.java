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

package org.jetbrains.k2js.config;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.compiler.ModuleInfo;
import org.jetbrains.k2js.translate.test.JSTester;
import org.jetbrains.k2js.translate.test.QUnitTester;

/**
 * Base class representing a configuration of translator.
 */
public class Config {
    protected final ModuleInfo module;

    private final EcmaVersion target;
    private final boolean sourcemap;

    public Config(@NotNull ModuleInfo module, @NotNull EcmaVersion ecmaVersion) {
        this(module, ecmaVersion, false);
    }

    public Config(@NotNull ModuleInfo module) {
        this(module, EcmaVersion.defaultVersion(), false);
    }

    public Config(@NotNull ModuleInfo module, @NotNull EcmaVersion ecmaVersion, boolean sourcemap) {
        this.module = module;
        this.target = ecmaVersion;
        this.sourcemap = sourcemap;
    }

    //NOTE: used by mvn build
    @SuppressWarnings("UnusedDeclaration")
    @NotNull
    public static Config getEmptyConfig(@NotNull Project project) {
        return new Config(new ModuleInfo(project));
    }

    public boolean isSourcemap() {
        return sourcemap;
    }

    @NotNull
    public Project getProject() {
        return module.getProject();
    }

    @NotNull
    public EcmaVersion getTarget() {
        return target;
    }

    @NotNull
    public ModuleInfo getModule() {
        return module;
    }

    //TODO: should be null by default I suppose but we can't communicate it to K2JSCompiler atm
    @Nullable
    public JSTester getTester() {
        return new QUnitTester();
    }
}
