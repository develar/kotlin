package org.jetbrains.kotlin.compiler;

import com.intellij.util.Processor;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public abstract class ModuleInfoProvider {
    public abstract boolean processDependencies(String moduleName, DependenciesProcessor processor);

    // list of kotlin files or library jar roots
    public abstract void processSourceFiles(String name, @Nullable Object object, Processor<File> processor);

    public interface DependenciesProcessor {
        // dependency - depends on implementation. JPS: JpsModuleDependency or JpsLibraryDependency
        boolean process(String name, Object dependency, boolean isLibrary, boolean provided);
    }
}
