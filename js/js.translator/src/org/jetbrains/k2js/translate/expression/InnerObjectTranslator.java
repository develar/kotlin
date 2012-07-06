package org.jetbrains.k2js.translate.expression;

import com.google.dart.compiler.backend.js.ast.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jet.lang.descriptors.ClassDescriptor;
import org.jetbrains.jet.lang.descriptors.DeclarationDescriptor;
import org.jetbrains.jet.lang.psi.JetElement;
import org.jetbrains.k2js.translate.context.AliasingContext;
import org.jetbrains.k2js.translate.context.TranslationContext;

public class InnerObjectTranslator extends InnerDeclarationTranslator {
    public InnerObjectTranslator(@NotNull JetElement declaration, @NotNull TranslationContext context, @NotNull JsFunction fun) {
        super(declaration, context, fun);
    }

    @Override
    protected JsExpression createExpression(JsNameRef nameRef, JsExpression self) {
        return createInvocation(nameRef, self);
    }

    @NotNull
    public JsExpression translate(@NotNull JsNameRef nameRef) {
        MyThisAliasProvider provider = (MyThisAliasProvider) context.thisAliasProvider();
        return super.translate(nameRef, provider.thisWasCaptured ? provider.name.makeRef() : null);
    }

    @Override
    protected JsInvocation createInvocation(JsNameRef nameRef, JsExpression self) {
        JsInvocation invocation = new JsInvocation(nameRef);
        if (((MyThisAliasProvider) context.thisAliasProvider()).thisWasCaptured) {
            fun.getParameters().add(new JsParameter(((JsNameRef) self).getName()));
            invocation.getArguments().add(JsLiteral.THIS);
        }
        return invocation;
    }

    static class MyThisAliasProvider extends AliasingContext.AbstractThisAliasProvider {
        private final ClassDescriptor descriptor;
        private final JsName name;
        private boolean thisWasCaptured;

        public MyThisAliasProvider(@NotNull ClassDescriptor descriptor, @NotNull JsName name) {
            this.descriptor = descriptor;
            this.name = name;
        }

        @Nullable
        @Override
        public JsNameRef get(@NotNull DeclarationDescriptor descriptor) {
            if (this.descriptor == normalize(descriptor)) {
                thisWasCaptured = true;
                return name.makeRef();
            }
            else {
                return null;
            }
        }
    }
}
