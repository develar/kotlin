package org.jetbrains.jet.jps.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.service.JpsServiceManager;

public class JpsJsExtensionService {
    public static JpsJsExtensionService getInstance() {
        return JpsServiceManager.getInstance().getService(JpsJsExtensionService.class);
    }

    @Nullable
    public JpsJsModuleExtension getExtension(@Nullable JpsModule module) {
        return module != null ? module.getContainer().getChild(JpsJsModuleExtension.ROLE) : null;
    }

    public void setExtension(@NotNull JpsModule module, @NotNull JpsJsModuleExtension extension) {
        module.getContainer().setChild(JpsJsModuleExtension.ROLE, extension);
    }
}
