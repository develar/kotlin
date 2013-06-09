package org.jetbrains.k2js.translate.expression;

import com.google.dart.compiler.backend.js.ast.JsFunction;
import com.google.dart.compiler.backend.js.ast.JsNameRef;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jet.lang.descriptors.MemberDescriptor;
import org.jetbrains.k2js.translate.context.TranslationContext;

public interface GenerationPlace {
    JsNameRef createReference(@NotNull JsFunction fun, @NotNull MemberDescriptor descriptor, @NotNull TranslationContext context);
}
