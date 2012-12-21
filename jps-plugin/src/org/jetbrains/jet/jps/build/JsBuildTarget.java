package org.jetbrains.jet.jps.build;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jet.jps.build.model.JpsJsModuleExtension;
import org.jetbrains.jps.builders.*;
import org.jetbrains.jps.builders.storage.BuildDataPaths;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.indices.IgnoredFileIndex;
import org.jetbrains.jps.indices.ModuleExcludeIndex;
import org.jetbrains.jps.model.JpsModel;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class JsBuildTarget extends BuildTarget<BuildRootDescriptor> {
    private JpsJsModuleExtension extension;

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
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Nullable
    @Override
    public BuildRootDescriptor findRootDescriptor(String rootId, BuildRootIndex rootIndex) {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @NotNull
    @Override
    public String getPresentableName() {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @NotNull
    @Override
    public Collection<File> getOutputRoots(CompileContext context) {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }
}