package org.jetbrains.kotlin.compiler;

import java.util.Collection;

public abstract class OutputConsumer {
    public abstract void registerSources(Collection<String> sourcePaths);
}
