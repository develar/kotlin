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

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.Processor;
import gnu.trove.THashSet;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jet.jps.model.JsExternalizationConstants;
import org.jetbrains.jps.builders.BuildRootDescriptor;
import org.jetbrains.jps.builders.BuildRootIndex;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.model.java.*;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.library.JpsOrderRootType;
import org.jetbrains.jps.model.module.JpsDependencyElement;
import org.jetbrains.jps.model.module.JpsLibraryDependency;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.module.JpsModuleDependency;
import org.jetbrains.kotlin.compiler.ModuleInfoProvider;

import java.io.File;
import java.io.FileFilter;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class JpsModuleInfoProvider extends ModuleInfoProvider {
    private final CompileContext context;

    public JpsModuleInfoProvider(CompileContext context) {
        this.context = context;
    }

    @Override
    public boolean processDependencies(String moduleName, DependenciesProcessor processor) {
        return processModuleDependencies(processor, getModule(moduleName));
    }

    private static boolean processModuleDependencies(DependenciesProcessor consumer, JpsModule dependentModule) {
        Set<JpsDependencyElement> processed = new THashSet<JpsDependencyElement>();
        LinkedList<JpsModule> queue = new LinkedList<JpsModule>();
        boolean isDirectDependency = true;
        queue.add(dependentModule);
        do {
            for (JpsDependencyElement dependency : queue.removeFirst().getDependenciesList().getDependencies()) {
                boolean isModule = dependency instanceof JpsModuleDependency;
                if (!(isModule || dependency instanceof JpsLibraryDependency) || !processed.add(dependency)) {
                    continue;
                }

                JpsJavaDependencyExtension extension = JpsJavaExtensionService.getInstance().getDependencyExtension(dependency);
                if (extension == null ||
                    !extension.getScope().isIncludedIn(JpsJavaClasspathKind.PRODUCTION_COMPILE) ||
                    !(isDirectDependency || extension.isExported())) {
                    continue;
                }

                if (isModule) {
                    JpsModule module = ((JpsModuleDependency) dependency).getModule();
                    if (module != null) {
                        if (consumer.process(module.getName(), module, false, extension.getScope().equals(JpsJavaDependencyScope.PROVIDED))) {
                            queue.add(module);
                        }
                        else {
                            return false;
                        }
                    }
                }
                else {
                    JpsLibrary library = ((JpsLibraryDependency) dependency).getLibrary();
                    if (library != null &&
                        isKotlinLibrary(library) &&
                        !consumer.process(library.getName(), library, true,
                                          library.getName().equals(JsExternalizationConstants.JS_LIBRARY_NAME) ||
                                          extension.getScope().equals(JpsJavaDependencyScope.PROVIDED))) {
                        return false;
                    }
                }
            }
            isDirectDependency = false;
        }
        while (!queue.isEmpty());
        return true;
    }

    private static boolean isKotlinLibrary(JpsLibrary library) {
        return library.getType() instanceof JpsJavaLibraryType &&
               !library.getRoots(JpsOrderRootType.COMPILED).isEmpty();
    }

    @Override
    public void processSourceFiles(String name, @Nullable Object object, final Processor<File> processor) {
        if (object instanceof JpsLibrary) {
            for (File file : ((JpsLibrary) object).getFiles(JpsOrderRootType.COMPILED)) {
                if (!processor.process(file)) {
                    return;
                }
            }
            return;
        }

        BuildRootIndex buildRootIndex = context.getProjectDescriptor().getBuildRootIndex();
        for (KotlinBuildTarget buildTarget : context.getProjectDescriptor().getBuildTargetIndex().getAllTargets(JsBuildTargetType.INSTANCE)) {
            if (buildTarget.getId().equals(name)) {
                List<BuildRootDescriptor> roots = buildRootIndex.getTargetRoots(buildTarget, context);
                for (BuildRootDescriptor root : roots) {
                    final FileFilter fileFilter = root.createFileFilter();
                    FileUtil.processFilesRecursively(root.getRootFile(), new Processor<File>() {
                        @Override
                        public boolean process(File file) {
                            if (fileFilter.accept(file)) {
                                processor.process(file);
                            }
                            return true;
                        }
                    });
                }
                return;
            }
        }

        throw new IllegalStateException("Cannot find kotlin build target for module " + name);
    }

    private JpsModule getModule(String moduleName) {
        for (JpsModule module : context.getProjectDescriptor().getProject().getModules()) {
            if (module.getName().equals(moduleName)) {
                return module;
            }
        }
        throw new IllegalArgumentException("Cannot find module " + moduleName + " in project " + context.getProjectDescriptor().getProject());
    }
}
