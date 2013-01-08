package org.jetbrains.kotlin.compiler;

import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;

public abstract class ModuleInfoProvider {
    public abstract boolean consumeDependencies(String moduleName, DependenciesProcessor consumer);
    // now it is list of module source roots, but in the future this information will be cached and actual kotlin files will be returned
    public abstract List<File> getSourceFiles(String name, @Nullable Object object);

    // todo cannot use PairProcessor due to jarjar org.jetbrains.jet.internal.com.intellij
    public interface DependenciesProcessor {
        // dependency - depends on implementation. JPS: JpsModuleDependency or JpsLibraryDependency
        boolean process(String name, Object dependency, boolean isLibrary);
    }
}
