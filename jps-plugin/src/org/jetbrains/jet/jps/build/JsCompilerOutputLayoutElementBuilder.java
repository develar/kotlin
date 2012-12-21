package org.jetbrains.jet.jps.build;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jet.jps.build.model.JpsJsExtensionService;
import org.jetbrains.jet.jps.build.model.JpsJsModuleExtension;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.TargetOutputIndex;
import org.jetbrains.jps.incremental.artifacts.builders.LayoutElementBuilderService;
import org.jetbrains.jps.incremental.artifacts.instructions.ArtifactCompilerInstructionCreator;
import org.jetbrains.jps.incremental.artifacts.instructions.ArtifactInstructionsBuilderContext;
import org.jetbrains.jps.model.module.JpsModule;

import java.util.Collection;
import java.util.Collections;

public class JsCompilerOutputLayoutElementBuilder extends LayoutElementBuilderService<JsCompilerOutputPackagingElement> {
    public JsCompilerOutputLayoutElementBuilder() {
        super(JsCompilerOutputPackagingElement.class);
    }

    @Override
    public void generateInstructions(
            JsCompilerOutputPackagingElement element,
            ArtifactCompilerInstructionCreator instructionCreator,
            ArtifactInstructionsBuilderContext builderContext
    ) {

    }

    @Override
    public Collection<? extends BuildTarget<?>> getDependencies(
            @NotNull JsCompilerOutputPackagingElement element,
            TargetOutputIndex outputIndex
    ) {
        JpsModule module = element.getModuleReference().resolve();
        JpsJsModuleExtension extension = JpsJsExtensionService.getInstance().getExtension(module);
        if (extension != null) {
            return Collections.singletonList(new JsBuildTarget(extension));
        }
        return Collections.emptyList();
    }
}
