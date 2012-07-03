package org.jetbrains.k2js.translate.expression;

import com.google.dart.compiler.backend.js.ast.*;
import org.jetbrains.jet.lang.psi.JetElement;
import org.jetbrains.k2js.translate.context.TranslationContext;

public class InnerObjectTranslator extends InnerDeclarationTranslator {
    public InnerObjectTranslator(JetElement declaration, TranslationContext context, JsFunction fun) {
        super(declaration, context, fun);
    }

    @Override
    protected JsExpression createExpression(JsNameRef nameRef, JsExpression self) {
        return createInvocation(nameRef, self);
    }

    @Override
    protected JsInvocation createInvocation(JsNameRef nameRef, JsExpression self) {
        JsInvocation invocation = new JsInvocation(nameRef);
        if (context.aliasingContext().wasOuterThisCaptured()) {
            fun.getParameters().add(new JsParameter(((JsNameRef) self).getName()));
            invocation.getArguments().add(context.program().getThisLiteral());
        }
        return invocation;
    }
}
