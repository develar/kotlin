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

import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildTargetLoader;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.java.JpsJavaModuleType;
import org.jetbrains.jps.model.module.JpsModule;

import java.util.List;

public class JsBuildTargetType extends KotlinBuildTargetType {
    public static final JsBuildTargetType INSTANCE = new JsBuildTargetType();
    public static final String TYPE_ID = "k2js";
    public static final String BUILDER_NAME = TYPE_ID + " Builder";

    private JsBuildTargetType() {
        super(TYPE_ID);
    }

    public static KotlinBuildTarget createTarget(@NotNull JpsModule module) {
        return new KotlinBuildTarget(module, INSTANCE);
    }

    @Override
    public String getLanguageName() {
        return "JavaScript";
    }

    @Override
    public String getSubCompilerClassName() {
        return "org.jetbrains.k2js.ToJsSubCompiler";
    }

    @NotNull
    @Override
    public List<KotlinBuildTarget> computeAllTargets(@NotNull JpsModel model) {
        List<KotlinBuildTarget> targets = new SmartList<KotlinBuildTarget>();
        // nik note - don't be smart, it is JPS responsibility to filter
        for (JpsModule module : model.getProject().getModules(JpsJavaModuleType.INSTANCE)) {
            //module.getContainer().setChild(X_COMPILER_FLAG, X_COMPILER_FLAG_VALUE);
            targets.add(new KotlinBuildTarget(module, this));
        }
        return targets;
    }

    @NotNull
    @Override
    public BuildTargetLoader<KotlinBuildTarget> createLoader(@NotNull JpsModel model) {
        return new Loader(model, this);
    }
}
