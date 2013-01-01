package org.jetbrains.jet.jps.build;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jet.jps.model.JpsJsCompilerOutputPackagingElement;
import org.jetbrains.jet.jps.model.JpsJsExtensionService;
import org.jetbrains.jet.jps.model.JpsJsModuleExtension;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.TargetOutputIndex;
import org.jetbrains.jps.incremental.artifacts.builders.LayoutElementBuilderService;
import org.jetbrains.jps.incremental.artifacts.instructions.ArtifactCompilerInstructionCreator;
import org.jetbrains.jps.incremental.artifacts.instructions.ArtifactInstructionsBuilderContext;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.util.JpsPathUtil;

import java.util.Collection;
import java.util.Collections;

public class JsCompilerOutputLayoutElementBuilder extends LayoutElementBuilderService<JpsJsCompilerOutputPackagingElement> {
    public JsCompilerOutputLayoutElementBuilder() {
        super(JpsJsCompilerOutputPackagingElement.class);
    }

    @Override
    public void generateInstructions(
            JpsJsCompilerOutputPackagingElement element,
            ArtifactCompilerInstructionCreator instructionCreator,
            ArtifactInstructionsBuilderContext builderContext
    ) {
        JpsModule module = element.getModuleReference().resolve();
        JpsJsModuleExtension extension = JpsJsExtensionService.getInstance().getExtension(module);
        if (extension == null) {
            return;
        }

        instructionCreator.addDirectoryCopyInstructions(JpsPathUtil.urlToFile(
                JpsJavaExtensionService.getInstance().getOutputUrl(element.getModuleReference().resolve(), false)));
    }

    @Override
    public Collection<? extends BuildTarget<?>> getDependencies(
            @NotNull JpsJsCompilerOutputPackagingElement element,
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
