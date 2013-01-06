package org.jetbrains.jet.jps.build;

import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.JpsSimpleElement;
import org.jetbrains.jps.model.java.JavaSourceRootProperties;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.java.JpsJavaClasspathKind;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.module.JpsTypedModuleSourceRoot;
import org.jetbrains.kotlin.compiler.ModuleInfoProvider;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class JpsModuleInfoProvider extends ModuleInfoProvider {
    private final JpsProject project;

    public JpsModuleInfoProvider(JpsProject project) {
        this.project = project;
    }

    @Override
    public List<String> getDependentModuleNames(String moduleName) {
        JpsModule module = getModule(moduleName);
        Set<JpsModule> dependentModules =
                JpsJavaExtensionService.dependencies(module).includedIn(JpsJavaClasspathKind.compile(false)).withoutModuleSourceEntries()
                        .recursively().exportedOnly().getModules();
        if (dependentModules.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> result = new ArrayList<String>(dependentModules.size());
        for (JpsModule dependentModule : dependentModules) {
            result.add(dependentModule.getName());
        }
        return result;
    }

    @Override
    public List<File> getSourceFiles(String moduleName) {
        JpsModule module = getModule(moduleName);
        Iterable<JpsTypedModuleSourceRoot<JpsSimpleElement<JavaSourceRootProperties>>> roots =
                module.getSourceRoots(JavaSourceRootType.SOURCE);
        List<File> result = new ArrayList<File>();
        for (JpsTypedModuleSourceRoot<JpsSimpleElement<JavaSourceRootProperties>> root : roots) {
            result.add(root.getFile());
        }
        return result;
    }

    private JpsModule getModule(String moduleName) {
        for (JpsModule module : project.getModules()) {
            if (module.getName().equals(moduleName)) {
                return module;
            }
        }
        throw new IllegalArgumentException("Cannot find module " + moduleName + " in project " + project);
    }
}
