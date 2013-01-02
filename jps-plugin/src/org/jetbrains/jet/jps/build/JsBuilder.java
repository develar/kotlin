package org.jetbrains.jet.jps.build;

import com.intellij.util.ArrayUtil;
import com.intellij.util.StringBuilderSpinAllocator;
import com.intellij.util.containers.ContainerUtilRt;
import com.intellij.util.containers.OrderedSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jet.jps.model.JsExternalizationConstants;
import org.jetbrains.jet.utils.KotlinPathsFromHomeDir;
import org.jetbrains.jps.builders.BuildOutputConsumer;
import org.jetbrains.jps.builders.BuildRootDescriptor;
import org.jetbrains.jps.builders.DirtyFilesHolder;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.ProjectBuildException;
import org.jetbrains.jps.incremental.TargetBuilder;
import org.jetbrains.jps.model.JpsDummyElement;
import org.jetbrains.jps.model.JpsSimpleElement;
import org.jetbrains.jps.model.java.*;
import org.jetbrains.jps.model.library.JpsOrderRootType;
import org.jetbrains.jps.model.library.JpsTypedLibrary;
import org.jetbrains.jps.model.module.JpsDependencyElement;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.module.JpsModuleDependency;
import org.jetbrains.jps.model.module.JpsTypedModuleSourceRoot;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;

public class JsBuilder extends TargetBuilder<BuildRootDescriptor, JsBuildTarget> {
    public static final String NAME = JsBuildTargetType.TYPE_ID + " Builder";

    public JsBuilder() {
        super(Collections.singletonList(JsBuildTargetType.INSTANCE));
    }

    @NotNull
    @Override
    public String getPresentableName() {
        return NAME;
    }

    @Override
    public void build(
            @NotNull JsBuildTarget target,
            @NotNull DirtyFilesHolder<BuildRootDescriptor, JsBuildTarget> holder,
            @NotNull BuildOutputConsumer outputConsumer,
            @NotNull CompileContext context
    ) throws ProjectBuildException, IOException {
        if (!holder.hasDirtyFiles()) {
            return;
        }

        JpsModule module = target.getExtension().getModule();

        constructArguments(target, context, module);
    }

    private static String[] constructArguments(JsBuildTarget target, CompileContext context, JpsModule module) {
        ArrayList<String> args = ContainerUtilRt.newArrayList("-tags", "-verbose", "-version");
        addSourceFiles(args, module);

        args.add("-output");
        args.add(JpsJsCompilerPaths.getCompilerOutputRoot(target, context.getProjectDescriptor().dataManager.getDataPaths()).getPath());

        addLibLocation(module, args);

        args.add("-target");
        // todo configurable
        args.add("v5");

        args.add("-sourcemap");

        return ArrayUtil.toStringArray(args);
    }

    private static void addSourceFiles(ArrayList<String> args, JpsModule module) {
        args.add("-sourceFiles");
        StringBuilder sb = StringBuilderSpinAllocator.alloc();
        try {
            appendModuleSourceRoots(module, sb);
            args.add(sb.substring(0, sb.length() - 1));
        }
        finally {
            StringBuilderSpinAllocator.dispose(sb);
        }
    }

    private static void appendModuleSourceRoots(JpsModule module, StringBuilder sb) {
        for (JpsTypedModuleSourceRoot<JpsSimpleElement<JavaSourceRootProperties>> root : module.getSourceRoots(JavaSourceRootType.SOURCE)) {
            sb.append(root.getFile().getPath()).append(',');
        }
    }

    private static void addLibLocation(@NotNull JpsModule module, @NotNull ArrayList<String> args) {
        StringBuilder sb = StringBuilderSpinAllocator.alloc();
        try {
            Set<JpsModule> modules = new OrderedSet<JpsModule>();
            collectModuleDependencies(module, modules, true);
            if (!modules.isEmpty()) {
                for (JpsModule dependency : modules) {
                    sb.append('@').append(dependency.getName()).append(',');
                    appendModuleSourceRoots(module, sb);
                }
            }

            File libraryFile = findLibrary(module);
            if (libraryFile != null) {
                sb.append(libraryFile.getPath()).append(',');
            }

            if (sb.length() > 0) {
                args.add("-libraryFiles");
                args.add(sb.substring(0, sb.length() - 1));
            }
        }
        finally {
            StringBuilderSpinAllocator.dispose(sb);
        }
    }

    private static File findLibrary(JpsModule module) {
        JpsTypedLibrary<JpsDummyElement> library =
                module.getLibraryCollection().findLibrary(JsExternalizationConstants.JS_LIBRARY_NAME, JpsJavaLibraryType.INSTANCE);
        if (library!= null) {
            for (File file : library.getFiles(JpsOrderRootType.SOURCES)) {
                if (file.getName().equals(KotlinPathsFromHomeDir.getRuntimeName(false))) {
                    return file;
                }
            }
        }
        return null;
    }

    // todo I don't know why I don't use JpsJavaDependenciesEnumeratorImpl, but I am afraid do it :)
    // develar we need to investigate, is JpsJavaDependenciesEnumeratorImpl suitable for us
    // we cannot use OrderEnumerator because it has critical bug - try https://gist.github.com/2953261, processor will never be called for module dependency
    // we don't use context.getCompileScope().getAffectedModules() because we want to know about linkage type (well, we ignore scope right now, but in future...)
    private static void collectModuleDependencies(JpsModule dependentModule, Set<JpsModule> modules, boolean isDirectDependency) {
        for (JpsDependencyElement dependency : dependentModule.getDependenciesList().getDependencies()) {
            if (dependency instanceof JpsModuleDependency) {
                JpsModuleDependency moduleDependency = (JpsModuleDependency) dependency;
                JpsJavaDependencyExtension extension = JpsJavaExtensionService.getInstance().getDependencyExtension(moduleDependency);
                if (extension == null || !extension.getScope().isIncludedIn(JpsJavaClasspathKind.PRODUCTION_COMPILE)) {
                    continue;
                }

                JpsModule module = moduleDependency.getModule();
                if (module == null) {
                    continue;
                }

                if (isDirectDependency) {
                    if (modules.add(module)) {
                        collectModuleDependencies(module, modules, false);
                    }
                }
                else if (modules.add(module)) {
                    collectModuleDependencies(module, modules, false);
                }
            }
        }
    }
}
