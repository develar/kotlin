package org.jetbrains.jet.jps.build;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.storage.BuildDataPaths;

import java.io.File;

public class JpsKotlinCompilerPaths {
    private JpsKotlinCompilerPaths() {
    }

    public static File getCompilerOutputRoot(@NotNull KotlinBuildTarget target, final BuildDataPaths dataPaths) {
        return new File(dataPaths.getTargetDataRoot(target), "kotlin-output");
    }
}
