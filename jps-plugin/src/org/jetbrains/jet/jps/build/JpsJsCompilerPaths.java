package org.jetbrains.jet.jps.build;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.storage.BuildDataPaths;

import java.io.File;

public class JpsJsCompilerPaths {
    private JpsJsCompilerPaths() {
    }

    public static File getCompilerOutputRoot(@NotNull JsBuildTarget target, final BuildDataPaths dataPaths) {
    return new File(dataPaths.getTargetDataRoot(target), "k2js-output");
  }
}
