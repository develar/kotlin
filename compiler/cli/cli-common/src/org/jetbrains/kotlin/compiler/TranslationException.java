package org.jetbrains.kotlin.compiler;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jet.cli.common.messages.CompilerMessageLocation;

public class TranslationException extends RuntimeException {
    private CompilerMessageLocation location;

    public TranslationException(@NotNull String message) {
        super(message);
    }

    public TranslationException(@NotNull Throwable cause, @NotNull CompilerMessageLocation location) {
        super(cause);
        this.location = location;
    }

    public CompilerMessageLocation getLocation() {
        return location;
    }

    public void setLocation(@NotNull CompilerMessageLocation value) {
        assert location == null;
        location = value;
    }

    @NotNull
    @Override
    public String toString() {
        String result = super.toString();
        if (location == null) {
            return result;
        }
        return location.getPath() + ":" + location.getLine() + ":" + location.getColumn() + " " + result;
    }
}
