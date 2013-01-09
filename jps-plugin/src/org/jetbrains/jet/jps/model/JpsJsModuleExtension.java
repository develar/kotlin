package org.jetbrains.jet.jps.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.jet.jps.build.JsBuildTargetType;
import org.jetbrains.jps.model.JpsElementChildRole;
import org.jetbrains.jps.model.ex.JpsElementBase;
import org.jetbrains.jps.model.ex.JpsElementChildRoleBase;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.module.JpsModuleReference;

public class JpsJsModuleExtension extends JpsElementBase<JpsJsModuleExtension> {
    public static final JpsElementChildRole<JpsJsModuleExtension> ROLE = JpsElementChildRoleBase.create(JsBuildTargetType.TYPE_ID);
    private final JpsModuleReference moduleReference;
    private JpsModule module;

    public JpsJsModuleExtension(@NotNull JpsModuleReference moduleReference) {
        this.moduleReference = moduleReference;
    }

    @TestOnly
    public JpsJsModuleExtension(@NotNull JpsModule module) {
        moduleReference = null;
        this.module = module;
    }

    private JpsJsModuleExtension(JpsJsModuleExtension original) {
        moduleReference = original.moduleReference;
    }

    @NotNull
    public JpsModule getModule() {
        if (module == null) {
            module = moduleReference.resolve();
            assert module != null;
        }
        return module;
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