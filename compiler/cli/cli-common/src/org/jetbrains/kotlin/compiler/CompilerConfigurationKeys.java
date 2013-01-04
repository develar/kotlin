package org.jetbrains.kotlin.compiler;

import org.jetbrains.jet.config.CompilerConfigurationKey;

import java.io.File;

public final class CompilerConfigurationKeys {
    public static final CompilerConfigurationKey<File> OUTPUT_ROOT = CompilerConfigurationKey.create("outputRoot");
    public static final CompilerConfigurationKey<String> MODULE_NAME = CompilerConfigurationKey.create("moduleName");

    private CompilerConfigurationKeys() {
    }
}
