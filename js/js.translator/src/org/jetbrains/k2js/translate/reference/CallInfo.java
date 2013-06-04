package org.jetbrains.k2js.translate.reference;

import com.google.dart.compiler.backend.js.ast.JsExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jet.lang.resolve.calls.model.ResolvedCall;

import java.util.List;

public interface CallInfo {
    @NotNull
    List<JsExpression> getArguments();

    @NotNull
    ResolvedCall<?> getResolvedCall();

    @NotNull
    CallType getCallType();

    boolean isNative();

    boolean isOptionsObjectConstructor();
}