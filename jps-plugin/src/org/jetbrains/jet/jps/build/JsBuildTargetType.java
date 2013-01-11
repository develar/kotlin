package org.jetbrains.jet.jps.build;

import com.intellij.util.SmartList;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jet.jps.model.JpsJsExtensionService;
import org.jetbrains.jet.jps.model.JpsJsModuleExtension;
import org.jetbrains.jps.builders.BuildTargetLoader;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.module.JpsModule;

import java.util.List;
import java.util.Map;

public class JsBuildTargetType extends KotlinBuildTargetType {
    public static final JsBuildTargetType INSTANCE = new JsBuildTargetType();
    public static final String TYPE_ID = "k2js";
    public static final String BUILDER_NAME = TYPE_ID + " Builder";

    private JsBuildTargetType() {
        super(TYPE_ID);
    }

    public static KotlinBuildTarget createTarget(JpsJsModuleExtension extension) {
        return new KotlinBuildTarget(extension, INSTANCE);
    }

    @Override
    public String getLanguageName() {
        return "JS";
    }

    @NotNull
    @Override
    public List<KotlinBuildTarget> computeAllTargets(@NotNull JpsModel model) {
        List<KotlinBuildTarget> targets = new SmartList<KotlinBuildTarget>();
        JpsJsExtensionService service = JpsJsExtensionService.getInstance();
        for (JpsModule module : model.getProject().getModules()) {
            JpsJsModuleExtension extension = service.getExtension(module);
            if (extension != null) {
                targets.add(createTarget(extension));
            }
        }
        return targets;
    }

    @NotNull
    @Override
    public BuildTargetLoader<KotlinBuildTarget> createLoader(@NotNull JpsModel model) {
        return new Loader(model);
    }

    private static class Loader extends BuildTargetLoader<KotlinBuildTarget> {
        private final Map<String, KotlinBuildTarget> targets;

        public Loader(JpsModel model) {
            targets = new THashMap<String, KotlinBuildTarget>();
            JpsJsExtensionService service = JpsJsExtensionService.getInstance();
            for (JpsModule module : model.getProject().getModules()) {
                JpsJsModuleExtension extension = service.getExtension(module);
                if (extension != null) {
                    targets.put(module.getName(), JsBuildTargetType.createTarget(extension));
                }
            }
        }

        @Nullable
        @Override
        public KotlinBuildTarget createTarget(@NotNull String targetId) {
            return targets.get(targetId);
        }
    }
}
