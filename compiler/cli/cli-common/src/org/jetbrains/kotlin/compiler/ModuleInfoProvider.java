package org.jetbrains.kotlin.compiler;

import java.io.File;
import java.util.List;

public abstract class ModuleInfoProvider {
    public abstract List<String> getDependentModuleNames(String moduleName);
    public abstract List<File> getSourceFiles(String moduleName);
}
