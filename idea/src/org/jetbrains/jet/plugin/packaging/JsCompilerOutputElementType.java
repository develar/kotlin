package org.jetbrains.jet.plugin.packaging;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModulePointer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.packaging.impl.elements.ModuleOutputElementTypeBase;
import com.intellij.packaging.impl.elements.ModuleOutputPackagingElementBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jet.jps.model.JsExternalizationConstants;
import org.jetbrains.jet.plugin.JetIcons;
import org.jetbrains.jet.plugin.framework.KotlinFrameworkDetector;

import javax.swing.*;

public class JsCompilerOutputElementType extends ModuleOutputElementTypeBase<JsModuleOutputPackagingElement> {
    public JsCompilerOutputElementType() {
        super(JsExternalizationConstants.COMPILER_OUTPUT_ELEMENT_ID, "k2js Compiler Output");
    }

    @Override
    protected ModuleOutputPackagingElementBase createElement(@NotNull Project project, @NotNull ModulePointer pointer) {
        return new JsModuleOutputPackagingElement(project, pointer);
    }

    @Override
    public boolean isSuitableModule(ModulesProvider modulesProvider, Module module) {
        return KotlinFrameworkDetector.isJsKotlinModule(module);
    }

    @NotNull
    @Override
    public JsModuleOutputPackagingElement createEmpty(@NotNull Project project) {
        return new JsModuleOutputPackagingElement(project);
    }

    public static JsCompilerOutputElementType getInstance() {
        return getInstance(JsCompilerOutputElementType.class);
    }

    @Nullable
    @Override
    public Icon getCreateElementIcon() {
        // todo icon
        return JetIcons.SMALL_LOGO;
    }
}
