package org.jetbrains.jet.jps.model;

import gnu.trove.THashMap;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.module.JpsModuleReference;
import org.jetbrains.jps.service.JpsServiceManager;

import java.util.Map;

public class JpsJsExtensionService {
    // todo we need Nik review
    private final Map<String, JpsJsModuleExtension> nameToModule = new THashMap<String, JpsJsModuleExtension>();

    public static JpsJsExtensionService getInstance() {
        return JpsServiceManager.getInstance().getService(JpsJsExtensionService.class);
    }

    @Nullable
    public JpsJsModuleExtension getExtension(@Nullable JpsModule module) {
        return module != null ? nameToModule.get(module.getName()) : null;
    }

    //public void setExtension(@NotNull JpsModule module, @NotNull JpsJsModuleExtension extension) {
    //    module.getContainer().setChild(JpsJsModuleExtension.ROLE, extension);
    //}

    public void setExtension(JpsModuleReference moduleReference) {
        nameToModule.put(moduleReference.getModuleName(), new JpsJsModuleExtension(moduleReference));
    }
}
