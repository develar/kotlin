package org.jetbrains.k2js.translate.expression;

import com.google.dart.compiler.backend.js.ast.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jet.lang.descriptors.ClassDescriptor;
import org.jetbrains.jet.lang.psi.JetElement;
import org.jetbrains.k2js.translate.context.TranslationContext;

class InnerObjectTranslator extends InnerDeclarationTranslator {
    public InnerObjectTranslator(@NotNull JetElement element, @NotNull ClassDescriptor descriptor, @NotNull TranslationContext context, @NotNull JsFunction fun) {
        super(element, descriptor, context, fun);
    }

    @Override
    protected JsExpression createExpression(@NotNull JsNameRef nameRef, @Nullable JsExpression self) {
        return createInvocation(nameRef, self);
    }

    @Override
    protected JsInvocation createInvocation(@NotNull JsNameRef nameRef, @Nullable JsExpression self) {
        JsInvocation invocation = new JsInvocation(nameRef);
        if (self != null) {
            fun.getParameters().add(new JsParameter(((JsNameRef) self).getName()));
            invocation.getArguments().add(JsLiteral.THIS);
        }
        return invocation;
    }
}
