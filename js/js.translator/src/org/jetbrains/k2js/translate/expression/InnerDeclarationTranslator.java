package org.jetbrains.k2js.translate.expression;

import com.google.dart.compiler.backend.js.ast.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jet.lang.descriptors.ClassDescriptor;
import org.jetbrains.jet.lang.descriptors.DeclarationDescriptor;
import org.jetbrains.jet.lang.descriptors.ValueParameterDescriptor;
import org.jetbrains.jet.lang.descriptors.VariableDescriptor;
import org.jetbrains.jet.lang.psi.JetElement;
import org.jetbrains.k2js.translate.context.AliasingContext;
import org.jetbrains.k2js.translate.context.TranslationContext;
import org.jetbrains.k2js.translate.utils.closure.ClosureContext;
import org.jetbrains.k2js.translate.utils.closure.ClosureUtils;

import java.util.Collections;
import java.util.List;

abstract class InnerDeclarationTranslator {
    protected final ClosureContext closureContext;
    protected final TranslationContext context;
    protected final JsFunction fun;

    public InnerDeclarationTranslator(@NotNull JetElement declaration, @NotNull TranslationContext context, @NotNull JsFunction fun) {
        this.context = context;
        closureContext = ClosureUtils.captureClosure(this.context.bindingContext(), declaration);
        this.fun = fun;
    }

    protected List<ValueParameterDescriptor> getValueParameters() {
        return Collections.emptyList();
    }

    protected JsExpression translate(@NotNull JsNameRef nameRef, @Nullable JsExpression self) {
        if (closureContext.getDescriptors().isEmpty() && self == JsLiteral.NULL) {
            return createExpression(nameRef, self);
        }
        else {
            JsInvocation invocation = createInvocation(nameRef, self);
            addCapturedValueParameters(invocation);
            return invocation;
        }
    }

    protected abstract JsExpression createExpression(JsNameRef nameRef, JsExpression self);

    protected abstract JsInvocation createInvocation(JsNameRef nameRef, JsExpression self);

    private void addCapturedValueParameters(JsInvocation bind) {
        if (closureContext.getDescriptors().isEmpty()) {
            return;
        }

        List<JsExpression> expressions = getCapturedValueParametersList(bind);
        for (VariableDescriptor variableDescriptor : closureContext.getDescriptors()) {
            JsName name = context.getNameForDescriptor(variableDescriptor);
            fun.getParameters().add(new JsParameter(name));
            expressions.add(name.makeRef());
        }
    }

    protected List<JsExpression> getCapturedValueParametersList(JsInvocation invocation) {
        return invocation.getArguments();
    }

    static class TraceableThisAliasProvider extends AliasingContext.AbstractThisAliasProvider {
        private final ClassDescriptor descriptor;
        private final JsNameRef thisRef;
        private boolean thisWasCaptured;

        public boolean wasThisCaptured() {
            return thisWasCaptured;
        }

        public TraceableThisAliasProvider(@NotNull ClassDescriptor descriptor, @NotNull JsNameRef thisRef) {
            this.descriptor = descriptor;
            this.thisRef = thisRef;
        }

        @Nullable
        public JsNameRef getRefIfWasCaptured() {
            return thisWasCaptured ? thisRef : null;
        }

        @Nullable
        @Override
        public JsNameRef get(@NotNull DeclarationDescriptor descriptor) {
            if (this.descriptor == normalize(descriptor)) {
                thisWasCaptured = true;
                return thisRef;
            }
            else {
                return null;
            }
        }
    }
}
