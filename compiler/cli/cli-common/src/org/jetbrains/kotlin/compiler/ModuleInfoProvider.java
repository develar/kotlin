package org.jetbrains.kotlin.compiler;

import org.jetbrains.annotations.Nullable;

import java.io.File;

public abstract class ModuleInfoProvider {
    public abstract boolean processDependencies(String moduleName, DependenciesProcessor processor);

    // list of kotlin files or library jar roots
    public abstract void processSourceFiles(String name, @Nullable Object object, Processor<File> processor);

    // todo cannot use PairProcessor due to jarjar org.jetbrains.jet.internal.com.intellij
    public interface DependenciesProcessor {
        // dependency - depends on implementation. JPS: JpsModuleDependency or JpsLibraryDependency
        boolean process(String name, Object dependency, boolean isLibrary, boolean provided);
    }

    // todo cannot use Processor due to jarjar org.jetbrains.jet.internal.com.intellij
    public interface Processor<T> {
        boolean process(T t);
    }
}
