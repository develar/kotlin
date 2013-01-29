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

import com.intellij.util.Consumer;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.*;
import org.jetbrains.jps.builders.impl.BuildRootDescriptorImpl;
import org.jetbrains.jps.builders.storage.BuildDataPaths;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.indices.IgnoredFileIndex;
import org.jetbrains.jps.indices.ModuleExcludeIndex;
import org.jetbrains.jps.model.*;
import org.jetbrains.jps.model.ex.JpsElementChildRoleBase;
import org.jetbrains.jps.model.java.JavaSourceRootProperties;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.java.JpsJavaDependenciesEnumerator;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.module.JpsTypedModuleSourceRoot;
import org.jetbrains.jps.util.JpsPathUtil;

import java.io.File;
import java.io.FileFilter;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class KotlinBuildTarget extends BuildTarget<BuildRootDescriptor> {
    private final JpsModule module;

    // todo find normal solution
    public static final JpsElementChildRole<JpsDummyElement> X_COMPILER_FLAG = JpsElementChildRoleBase.create("kotlinXCompilerFlag");

    KotlinBuildTarget(@NotNull JpsModule module, @NotNull BuildTargetType<?> targetType) {
        super(targetType);
        this.module = module;
    }

    @Override
    public String getId() {
        return module.getName();
    }

    public JpsModule getModule() {
        return module;
    }

    @Override
    public Collection<BuildTarget<?>> computeDependencies(BuildTargetRegistry targetRegistry, TargetOutputIndex outputIndex) {
        module.getContainer().setChild(X_COMPILER_FLAG, JpsElementFactory.getInstance().createDummyElement());

        JpsJavaDependenciesEnumerator enumerator = JpsJavaExtensionService.dependencies(module).compileOnly().productionOnly();
        final List<BuildTarget<?>> dependencies = new SmartList<BuildTarget<?>>();
        enumerator.processModules(new Consumer<JpsModule>() {
            @Override
            public void consume(JpsModule dependency) {
                dependency.getContainer().setChild(X_COMPILER_FLAG, JpsElementFactory.getInstance().createDummyElement());
                // we must compile module even if it is not included in any artifact — module will be compiled, but not copied to some artifact output directory
                dependencies.add(JsBuildTargetType.createTarget(dependency));
            }
        });
        return dependencies;
    }

    @NotNull
    @Override
    public List<BuildRootDescriptor> computeRootDescriptors(
            JpsModel model, ModuleExcludeIndex index, IgnoredFileIndex ignoredFileIndex, BuildDataPaths dataPaths
    ) {
        List<BuildRootDescriptor> roots = new SmartList<BuildRootDescriptor>();
        for (JpsTypedModuleSourceRoot<JpsSimpleElement<JavaSourceRootProperties>> sourceRoot : module.getSourceRoots(JavaSourceRootType.SOURCE)) {
            roots.add(new MyBuildRootDescriptor(this, sourceRoot));
        }
        return roots;
    }

    @Nullable
    @Override
    public BuildRootDescriptor findRootDescriptor(String rootId, BuildRootIndex rootIndex) {
        for (BuildRootDescriptor descriptor : rootIndex.getTargetRoots(this, null)) {
            if (descriptor.getRootId().equals(rootId)) {
                return descriptor;
            }
        }
        return null;
    }

    @NotNull
    @Override
    public String getPresentableName() {
        return "Kotlin " + ((KotlinBuildTargetType) getTargetType()).getLanguageName() + " in module '" + module.getName() + "'";
    }

    @NotNull
    @Override
    public Collection<File> getOutputRoots(CompileContext context) {
        return Collections.singleton(
                JpsKotlinCompilerPaths.getCompilerOutputRoot(this, context.getProjectDescriptor().dataManager.getDataPaths()));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        return module.equals(((KotlinBuildTarget) o).module);
    }

    @Override
    public int hashCode() {
      return module.hashCode();
    }

    private static class MyBuildRootDescriptor extends BuildRootDescriptorImpl {
        public MyBuildRootDescriptor(KotlinBuildTarget target, JpsTypedModuleSourceRoot<JpsSimpleElement<JavaSourceRootProperties>> sourceRoot) {
            super(target, JpsPathUtil.urlToFile(sourceRoot.getUrl()), true);
        }

        @NotNull
        @Override
        public FileFilter createFileFilter() {
            return KotlinSourceFileCollector.KOTLIN_SOURCES_FILTER;
        }
    }
}