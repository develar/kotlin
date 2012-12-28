package org.jetbrains.jet.jps.build;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jet.jps.model.JpsJsModuleExtension;
import org.jetbrains.jps.builders.*;
import org.jetbrains.jps.builders.impl.BuildRootDescriptorImpl;
import org.jetbrains.jps.builders.storage.BuildDataPaths;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.indices.IgnoredFileIndex;
import org.jetbrains.jps.indices.ModuleExcludeIndex;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.JpsSimpleElement;
import org.jetbrains.jps.model.java.JavaSourceRootProperties;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.module.JpsTypedModuleSourceRoot;
import org.jetbrains.jps.util.JpsPathUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class JsBuildTarget extends BuildTarget<BuildRootDescriptor> {
    private final JpsJsModuleExtension extension;

    public JsBuildTarget(JpsJsModuleExtension extension) {
        super(JsBuildTargetType.INSTANCE);
        this.extension = extension;
    }

    @Override
    public String getId() {
        return extension.getModule().getName();
    }

    @Override
    public Collection<BuildTarget<?>> computeDependencies(BuildTargetRegistry targetRegistry, TargetOutputIndex outputIndex) {
        return Collections.<BuildTarget<?>>unmodifiableCollection(
                targetRegistry.getModuleBasedTargets(extension.getModule(), BuildTargetRegistry.ModuleTargetSelector.PRODUCTION));
    }

    @NotNull
    @Override
    public List<BuildRootDescriptor> computeRootDescriptors(
            JpsModel model, ModuleExcludeIndex index, IgnoredFileIndex ignoredFileIndex, BuildDataPaths dataPaths
    ) {
        List<BuildRootDescriptor> roots = new ArrayList<BuildRootDescriptor>();
        for (JpsTypedModuleSourceRoot<JpsSimpleElement<JavaSourceRootProperties>> sourceRoot : extension.getModule()
                .getSourceRoots(JavaSourceRootType.SOURCE)) {
            roots.add(new BuildRootDescriptorImpl(this, JpsPathUtil.urlToFile(sourceRoot.getUrl()), true));
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
        return "Kotlin JS in module '" + extension.getModule().getName() + "'";
    }

    @NotNull
    @Override
    public Collection<File> getOutputRoots(CompileContext context) {
        return Collections.singleton(JpsJsCompilerPaths.getCompilerOutputRoot(this, context.getProjectDescriptor().dataManager.getDataPaths()));
    }
}