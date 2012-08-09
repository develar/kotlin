package org.jetbrains.k2js.translate.expression;

import com.google.dart.compiler.backend.js.ast.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jet.lang.descriptors.ClassDescriptor;
import org.jetbrains.jet.lang.psi.JetElement;
import org.jetbrains.k2js.translate.context.TranslationContext;

class InnerObjectTranslator extends InnerDeclarationTranslator {
    public InnerObjectTranslator(@NotNull JetElement element, @NotNull ClassDescriptor descriptor, @NotNull TranslationContext context, @NotNull JsFunction fun) {
        super(element, descriptor, context, fun);
    }

    @Override
    protected JsExpression createExpression(JsNameRef nameRef, JsExpression self) {
        return createInvocation(nameRef, self);
    }

    //@NotNull
    //@Override
    //public JsExpression translate(@NotNull JsNameRef nameRef) {
    //    throw new UnsupportedOperationException();
    //}

    @Override
    protected JsInvocation createInvocation(JsNameRef nameRef, JsExpression self) {
        JsInvocation invocation = new JsInvocation(nameRef);
        if (self != null) {
            fun.getParameters().add(new JsParameter(((JsNameRef) self).getName()));
            invocation.getArguments().add(JsLiteral.THIS);
        }
        return invocation;
    }
}
