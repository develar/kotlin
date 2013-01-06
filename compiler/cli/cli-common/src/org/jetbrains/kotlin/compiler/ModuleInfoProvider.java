package org.jetbrains.kotlin.compiler;

import java.io.File;
import java.util.List;

public abstract class ModuleInfoProvider {
    public abstract List<String> getDependentModuleNames(String moduleName);
    // now it is list of module source roots, but in the future this information will be cached and actual kotlin files will be returned
    public abstract List<File> getSourceFiles(String moduleName);
}
