package org.jetbrains.jet.jps.build;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jet.jps.model.JpsKotlinCompilerOutputPackagingElement;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.TargetOutputIndex;
import org.jetbrains.jps.incremental.artifacts.builders.LayoutElementBuilderService;
import org.jetbrains.jps.incremental.artifacts.instructions.ArtifactCompilerInstructionCreator;
import org.jetbrains.jps.incremental.artifacts.instructions.ArtifactInstructionsBuilderContext;
import org.jetbrains.jps.model.module.JpsModule;

import java.util.Collection;
import java.util.Collections;

public class KotlinCompilerOutputLayoutElementBuilder extends LayoutElementBuilderService<JpsKotlinCompilerOutputPackagingElement> {
    public KotlinCompilerOutputLayoutElementBuilder() {
        super(JpsKotlinCompilerOutputPackagingElement.class);
    }

    @Override
    public void generateInstructions(
            JpsKotlinCompilerOutputPackagingElement element,
            ArtifactCompilerInstructionCreator instructionCreator,
            ArtifactInstructionsBuilderContext builderContext
    ) {
        JpsModule module = element.getModuleReference().resolve();
        if (module == null) {
            return;
        }

        instructionCreator.addDirectoryCopyInstructions(
                JpsKotlinCompilerPaths.getCompilerOutputRoot(JsBuildTargetType.createTarget(module), builderContext.getDataPaths()));
    }

    @Override
    public Collection<? extends BuildTarget<?>> getDependencies(
            @NotNull JpsKotlinCompilerOutputPackagingElement element,
            TargetOutputIndex outputIndex
    ) {
        JpsModule module = element.getModuleReference().resolve();
        if (module != null) {
            return Collections.singletonList(JsBuildTargetType.createTarget(module));
        }
        return Collections.emptyList();
    }
}
