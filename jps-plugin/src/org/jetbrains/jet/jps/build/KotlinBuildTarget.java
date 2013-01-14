package org.jetbrains.jet.jps.build;

import com.intellij.util.Consumer;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jet.jps.model.JpsJsExtensionService;
import org.jetbrains.jet.jps.model.JpsJsModuleExtension;
import org.jetbrains.jps.builders.*;
import org.jetbrains.jps.builders.impl.BuildRootDescriptorImpl;
import org.jetbrains.jps.builders.storage.BuildDataPaths;
import org.jetbrains.jps.cmdline.ProjectDescriptor;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.indices.IgnoredFileIndex;
import org.jetbrains.jps.indices.ModuleExcludeIndex;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.JpsSimpleElement;
import org.jetbrains.jps.model.java.JavaSourceRootProperties;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.java.JpsJavaDependenciesEnumerator;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.module.JpsTypedModuleSourceRoot;
import org.jetbrains.jps.util.JpsPathUtil;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class KotlinBuildTarget extends BuildTarget<BuildRootDescriptor> {
    private final JpsJsModuleExtension extension;

    public KotlinBuildTarget(JpsJsModuleExtension extension, BuildTargetType<?> targetType) {
        super(targetType);
        this.extension = extension;
    }

    @Override
    public String getId() {
        return extension.getModuleName();
    }

    public JpsJsModuleExtension getExtension() {
        return extension;
    }

    @Override
    public Collection<BuildTarget<?>> computeDependencies(BuildTargetRegistry targetRegistry, TargetOutputIndex outputIndex) {
        JpsModule module = extension.getModule();
        JpsJavaDependenciesEnumerator enumerator = JpsJavaExtensionService.dependencies(module).compileOnly();
        enumerator.productionOnly();
        final ArrayList<BuildTarget<?>> dependencies = new ArrayList<BuildTarget<?>>();
        final JpsJsExtensionService service = JpsJsExtensionService.getInstance();
        // todo refactor this shit - don't duplicate JsBuildTargetType.computeAllTargets code
        enumerator.processModules(new Consumer<JpsModule>() {
            @Override
            public void consume(JpsModule module) {
                JpsJsModuleExtension extension = service.getExtension(module);
                if (extension != null) {
                    dependencies.add(JsBuildTargetType.createTarget(extension));
                }
            }
        });
        dependencies.trimToSize();
        return dependencies;
    }

    @NotNull
    @Override
    public List<BuildRootDescriptor> computeRootDescriptors(
            JpsModel model, ModuleExcludeIndex index, IgnoredFileIndex ignoredFileIndex, BuildDataPaths dataPaths
    ) {
        List<BuildRootDescriptor> roots = new SmartList<BuildRootDescriptor>();
        for (JpsTypedModuleSourceRoot<JpsSimpleElement<JavaSourceRootProperties>> sourceRoot : extension.getModule().getSourceRoots(JavaSourceRootType.SOURCE)) {
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
        return "Kotlin " + ((KotlinBuildTargetType) getTargetType()).getLanguageName() + " in module '" + extension.getModuleName() + "'";
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

        return extension.equals(((KotlinBuildTarget) o).extension);
    }

    @Override
    public int hashCode() {
      return extension.hashCode();
    }

    private static class MyBuildRootDescriptor extends BuildRootDescriptorImpl {
        public MyBuildRootDescriptor(KotlinBuildTarget target, JpsTypedModuleSourceRoot<JpsSimpleElement<JavaSourceRootProperties>> sourceRoot) {
            super(target, JpsPathUtil.urlToFile(sourceRoot.getUrl()), true);
        }

        @Override
        public FileFilter createFileFilter(@NotNull ProjectDescriptor descriptor) {
            return KotlinSourceFileCollector.KOTLIN_SOURCES_FILTER;
        }
    }
}