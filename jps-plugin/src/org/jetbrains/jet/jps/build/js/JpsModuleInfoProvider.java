package org.jetbrains.jet.jps.build.js;

import org.jetbrains.kotlin.compiler.ModuleInfoProvider;

import java.io.File;
import java.util.Collections;
import java.util.List;

public class JpsModuleInfoProvider extends ModuleInfoProvider {
    @Override
    public List<String> getDependentModuleNames(String moduleName) {
        return Collections.emptyList();
    }

    @Override
    public List<File> getSourceFiles(String moduleName) {
        return null;
    }
}
