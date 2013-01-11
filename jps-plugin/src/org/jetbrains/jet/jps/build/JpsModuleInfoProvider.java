package org.jetbrains.jet.jps.build;

import com.intellij.openapi.util.io.FileUtil;
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
import java.util.List;
import java.util.Set;

public class JpsModuleInfoProvider extends ModuleInfoProvider {
    private final CompileContext context;

    public JpsModuleInfoProvider(CompileContext context) {
        this.context = context;
    }

    @Override
    public boolean processDependencies(String moduleName, DependenciesProcessor processor) {
        JpsModule module = getModule(moduleName);
        return processModuleDependencies(processor, module, new THashSet<JpsDependencyElement>(), true);
    }

    private static boolean processModuleDependencies(
            DependenciesProcessor consumer,
            JpsModule dependentModule,
            Set<JpsDependencyElement> processed,
            boolean isDirectDependency
    ) {
        for (JpsDependencyElement dependency : dependentModule.getDependenciesList().getDependencies()) {
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
                if (module != null &&
                    (!consumer.process(module.getName(), module, false, extension.getScope().equals(JpsJavaDependencyScope.PROVIDED)) ||
                     !processModuleDependencies(consumer, module, processed, false))) {
                    return false;
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

        return true;
    }

    private static boolean isKotlinLibrary(JpsLibrary library) {
        return library.getType() instanceof JpsJavaLibraryType &&
               !library.getRoots(JpsOrderRootType.SOURCES).isEmpty();
    }

    @Override
    public void processSourceFiles(String name, @Nullable Object object, final ModuleInfoProvider.Processor<File> processor) {
        if (object instanceof JpsLibrary) {
            for (File file : ((JpsLibrary) object).getFiles(JpsOrderRootType.SOURCES)) {
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
                    final FileFilter fileFilter = root.createFileFilter(context.getProjectDescriptor());
                    //noinspection UnnecessaryFullyQualifiedName
                    FileUtil.processFilesRecursively(root.getRootFile(), new com.intellij.util.Processor<File>() {
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
