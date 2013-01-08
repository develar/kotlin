package org.jetbrains.jet.jps.build;

import gnu.trove.THashSet;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.JpsSimpleElement;
import org.jetbrains.jps.model.java.*;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.library.JpsOrderRootType;
import org.jetbrains.jps.model.module.*;
import org.jetbrains.kotlin.compiler.ModuleInfoProvider;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class JpsModuleInfoProvider extends ModuleInfoProvider {
    private final JpsProject project;

    public JpsModuleInfoProvider(JpsProject project) {
        this.project = project;
    }

    @Override
    public boolean consumeDependencies(String moduleName, DependenciesProcessor consumer) {
        JpsModule module = getModule(moduleName);
        return processModuleDependencies(consumer, module, new THashSet<JpsDependencyElement>(), true);
    }

    private static boolean processModuleDependencies(
            DependenciesProcessor consumer,
            JpsModule dependentModule,
            Set<JpsDependencyElement> processed,
            boolean isDirectDependency
    ) {
        for (JpsDependencyElement dependency : dependentModule.getDependenciesList().getDependencies()) {
            boolean isModule = dependency instanceof JpsModuleDependency;
            if ((!isModule && !(dependency instanceof JpsLibraryDependency)) || !processed.add(dependency)) {
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
                if (module != null &&
                    (!consumer.process(module.getName(), module, false) || !processModuleDependencies(consumer, module, processed, false))) {
                    return false;
                }
            }
            else {
                JpsLibrary library = ((JpsLibraryDependency) dependency).getLibrary();
                if (library != null && isKotlinLibrary(library) && !consumer.process(library.getName(), library, true)) {
                    return false;
                }
            }
        }

        return true;
    }

    private static boolean isKotlinLibrary(JpsLibrary library) {
        return library.getType() instanceof JpsJavaLibraryType &&
               !library.getRoots(JpsOrderRootType.SOURCES).isEmpty();
    }

    @Override
    public List<File> getSourceFiles(String name, @Nullable Object object) {
        if (object instanceof JpsLibrary) {
            return ((JpsLibrary) object).getFiles(JpsOrderRootType.SOURCES);
        }

        JpsModule module = object == null ? getModule(name) : ((JpsModule) object);
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
