package org.jetbrains.k2js.translate.expression;

import com.google.dart.compiler.backend.js.ast.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jet.lang.descriptors.ClassDescriptor;
import org.jetbrains.jet.lang.descriptors.FunctionDescriptor;
import org.jetbrains.jet.lang.descriptors.ValueParameterDescriptor;
import org.jetbrains.jet.lang.psi.JetElement;
import org.jetbrains.k2js.translate.context.TranslationContext;

import java.util.List;

class InnerFunctionTranslator extends InnerDeclarationTranslator {
    private final FunctionDescriptor descriptor;

    public InnerFunctionTranslator(@NotNull JetElement element,
            @NotNull FunctionDescriptor descriptor,
            @NotNull TranslationContext context,
            @NotNull JsFunction fun) {
        super(element, descriptor, context, fun);
        this.descriptor = descriptor;
    }

    @Override
    protected List<ValueParameterDescriptor> getValueParameters() {
        return descriptor.getValueParameters();
    }

    @SuppressWarnings("MethodOverloadsMethodOfSuperclass")
    public JsExpression translate(@NotNull JsNameRef nameRef, @NotNull TranslationContext outerContext) {
        return translate(nameRef, getThis(outerContext));
    }

    @Override
    protected JsExpression createExpression(@NotNull JsNameRef nameRef, JsExpression self) {
        return nameRef;
    }

    @Override
    protected JsInvocation createInvocation(@NotNull JsNameRef nameRef, @Nullable JsExpression self) {
        JsInvocation bind = new JsInvocation(context.namer().kotlin(getBindMethodName()));
        bind.getArguments().add(nameRef);
        bind.getArguments().add(self);
        return bind;
    }

    @NotNull
    private JsExpression getThis(TranslationContext outerContext) {
        ClassDescriptor outerClassDescriptor = closureContext.outerClassDescriptor;
        if (outerClassDescriptor != null && !descriptor.getReceiverParameter().exists()) {
            return outerContext.getThisObject(outerClassDescriptor);
        }

        return JsLiteral.NULL;
    }

    @NotNull
    private String getBindMethodName() {
        if (closureContext.getDescriptors().isEmpty()) {
            return !hasArguments() ? "b3" : "b4";
        }
        else {
            return !hasArguments() ? (closureContext.getDescriptors().size() == 1 ? "b0" : "b1") : "b2";
        }
    }

    private boolean hasArguments() {
        return !getValueParameters().isEmpty() || descriptor.getReceiverParameter().exists();
    }

    @Override
    protected List<JsExpression> getCapturedValueParametersList(JsInvocation invocation) {
        if (closureContext.getDescriptors().size() > 1 || hasArguments()) {
            JsArrayLiteral values = new JsArrayLiteral();
            invocation.getArguments().add(values);
            return values.getExpressions();
        }

        return super.getCapturedValueParametersList(invocation);
    }
}
