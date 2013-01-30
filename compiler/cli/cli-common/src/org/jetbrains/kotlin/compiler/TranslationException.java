package org.jetbrains.kotlin.compiler;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jet.cli.common.messages.CompilerMessageLocation;

public class TranslationException extends RuntimeException {
    private CompilerMessageLocation location;

    public TranslationException(@NotNull String message) {
        super(message);
    }

    public CompilerMessageLocation getLocation() {
        return location;
    }

    public void setLocation(@NotNull CompilerMessageLocation value) {
        assert location == null;
        location = value;
    }
}
