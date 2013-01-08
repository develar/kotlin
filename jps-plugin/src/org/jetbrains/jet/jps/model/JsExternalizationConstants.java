package org.jetbrains.jet.jps.model;

import org.jetbrains.annotations.NonNls;

public final class JsExternalizationConstants {
    @NonNls
    public static final String COMPILER_OUTPUT_ELEMENT_ID = "k2js-compiler-output";
    // todo move back to idea plugin
    public static final String JS_LIBRARY_NAME = "KotlinJsRuntime";

    private JsExternalizationConstants() {
    }
}
