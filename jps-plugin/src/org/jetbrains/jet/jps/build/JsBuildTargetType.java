package org.jetbrains.jet.jps.build;

import com.intellij.util.SmartList;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jet.jps.model.JpsJsExtensionService;
import org.jetbrains.jet.jps.model.JpsJsModuleExtension;
import org.jetbrains.jps.builders.BuildTargetLoader;
import org.jetbrains.jps.builders.BuildTargetType;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.module.JpsModule;

import java.util.List;
import java.util.Map;

public class JsBuildTargetType extends BuildTargetType<JsBuildTarget> {
    public static final JsBuildTargetType INSTANCE = new JsBuildTargetType();
    public static final String TYPE_ID = "k2js";

    private JsBuildTargetType() {
        super(TYPE_ID);
    }

    @NotNull
    @Override
    public List<JsBuildTarget> computeAllTargets(@NotNull JpsModel model) {
        List<JsBuildTarget> targets = new SmartList<JsBuildTarget>();
        JpsJsExtensionService service = JpsJsExtensionService.getInstance();
        for (JpsModule module : model.getProject().getModules()) {
            JpsJsModuleExtension extension = service.getExtension(module);
            if (extension != null) {
                targets.add(new JsBuildTarget(extension));
            }
        }
        return targets;
    }

    @NotNull
    @Override
    public BuildTargetLoader<JsBuildTarget> createLoader(@NotNull JpsModel model) {
        return new Loader(model);
    }

    private static class Loader extends BuildTargetLoader<JsBuildTarget> {
        private final Map<String, JsBuildTarget> targets;

        public Loader(JpsModel model) {
            targets = new THashMap<String, JsBuildTarget>();
            JpsJsExtensionService service = JpsJsExtensionService.getInstance();
            for (JpsModule module : model.getProject().getModules()) {
                JpsJsModuleExtension extension = service.getExtension(module);
                if (extension != null) {
                    targets.put(module.getName(), new JsBuildTarget(extension));
                }
            }
        }

        @Nullable
        @Override
        public JsBuildTarget createTarget(@NotNull String targetId) {
            return targets.get(targetId);
        }
    }
}
