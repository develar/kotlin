package org.jetbrains.jet.jps.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jet.jps.build.JsBuildTargetType;
import org.jetbrains.jps.model.JpsElementChildRole;
import org.jetbrains.jps.model.ex.JpsElementBase;
import org.jetbrains.jps.model.ex.JpsElementChildRoleBase;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.module.JpsModuleReference;

public class JpsJsModuleExtension extends JpsElementBase<JpsJsModuleExtension> {
    public static final JpsElementChildRole<JpsJsModuleExtension> ROLE = JpsElementChildRoleBase.create(JsBuildTargetType.TYPE_ID);
    private final JpsModuleReference moduleReference;

    public JpsJsModuleExtension(JpsModuleReference moduleReference) {
        this.moduleReference = moduleReference;
    }

    private JpsJsModuleExtension(JpsJsModuleExtension original) {
        moduleReference = original.moduleReference;
    }

    @NotNull
    public JpsModule getModule() {
        return moduleReference.resolve();
    }

    @NotNull
    @Override
    public JpsJsModuleExtension createCopy() {
        return new JpsJsModuleExtension(this);
    }

    @Override
    public void applyChanges(@NotNull JpsJsModuleExtension modified) {
    }
}