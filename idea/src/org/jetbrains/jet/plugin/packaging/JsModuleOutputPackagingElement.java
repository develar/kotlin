package org.jetbrains.jet.plugin.packaging;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModulePointer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.elements.ArtifactAntGenerationContext;
import com.intellij.packaging.elements.PackagingElementResolvingContext;
import com.intellij.packaging.impl.elements.ModuleOutputPackagingElementBase;
import com.intellij.packaging.impl.ui.DelegatedPackagingElementPresentation;
import com.intellij.packaging.impl.ui.ModuleElementPresentation;
import com.intellij.packaging.ui.ArtifactEditorContext;
import com.intellij.packaging.ui.PackagingElementPresentation;
import com.intellij.packaging.ui.PackagingElementWeights;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jet.plugin.JetIcons;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

public class JsModuleOutputPackagingElement extends ModuleOutputPackagingElementBase {
    public JsModuleOutputPackagingElement(@NotNull Project project) {
        super(JsCompilerOutputElementType.getInstance(), project);
    }

    public JsModuleOutputPackagingElement(@NotNull Project project, @NotNull ModulePointer modulePointer) {
        super(JsCompilerOutputElementType.getInstance(), project, modulePointer);
    }

    @NonNls
    @Override
    public String toString() {
        return "module:" + getModuleName();
    }

    @Override
    protected String getModuleOutputAntProperty(ArtifactAntGenerationContext generationContext) {
        return generationContext.getModuleOutputPath(myModulePointer.getModuleName());
    }

    @Override
    protected VirtualFile getModuleOutputPath(CompilerModuleExtension extension) {
        return extension.getCompilerOutputPath();
    }

    @NotNull
    @Override
    public Collection<VirtualFile> getSourceRoots(PackagingElementResolvingContext context) {
        Module module = findModule(context);
        if (module == null) {
            return Collections.emptyList();
        }

        ModuleRootModel rootModel = context.getModulesProvider().getRootModel(module);
        return Arrays.asList(rootModel.getSourceRoots(false));
    }

    @Override
    public PackagingElementPresentation createPresentation(@NotNull ArtifactEditorContext context) {
        return new DelegatedPackagingElementPresentation(new KotlinPackagingElementPresentation(myModulePointer, context, false));
    }

    private static final class KotlinPackagingElementPresentation extends ModuleElementPresentation {
        public KotlinPackagingElementPresentation(
                @Nullable ModulePointer modulePointer,
                @NotNull ArtifactEditorContext context,
                boolean testOutput
        ) {
            super(modulePointer, context, testOutput);
        }

        @Override
        public void render(
                @NotNull PresentationData presentationData, SimpleTextAttributes mainAttributes, SimpleTextAttributes commentAttributes
        ) {
            presentationData.setIcon(JetIcons.SMALL_LOGO);
            presentationData.addText("'" + getPresentableName() + "' k2js compiler output", canNavigateToSource() ? mainAttributes : SimpleTextAttributes.ERROR_ATTRIBUTES);
        }

        @Override
        public int getWeight() {
            return PackagingElementWeights.FACET;
        }
    }
}
