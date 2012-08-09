package org.jetbrains.k2js.translate.expression;

import com.google.dart.compiler.backend.js.ast.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jet.lang.descriptors.CallableDescriptor;
import org.jetbrains.jet.lang.descriptors.DeclarationDescriptor;
import org.jetbrains.jet.lang.descriptors.ValueParameterDescriptor;
import org.jetbrains.jet.lang.descriptors.VariableDescriptor;
import org.jetbrains.jet.lang.psi.JetElement;
import org.jetbrains.k2js.translate.context.TranslationContext;
import org.jetbrains.k2js.translate.utils.closure.ClosureContext;
import org.jetbrains.k2js.translate.utils.closure.ClosureUtils;

import java.util.Collections;
import java.util.List;

abstract class InnerDeclarationTranslator {
    protected final ClosureContext closureContext;
    protected final TranslationContext context;
    protected final JsFunction fun;

    public InnerDeclarationTranslator(@NotNull JetElement element,
            @NotNull DeclarationDescriptor descriptor,
            @NotNull TranslationContext context,
            @NotNull JsFunction fun) {
        this.context = context;
        closureContext = ClosureUtils.captureClosure(context.bindingContext(), element, descriptor);
        this.fun = fun;
    }

    protected List<ValueParameterDescriptor> getValueParameters() {
        return Collections.emptyList();
    }

    public JsExpression translate(@NotNull JsNameRef nameRef, @Nullable JsExpression self) {
        if (closureContext.getDescriptors().isEmpty() && self == JsLiteral.NULL) {
            return createExpression(nameRef, self);
        }
        else {
            JsInvocation invocation = createInvocation(nameRef, self);
            addCapturedValueParameters(invocation);
            return invocation;
        }
    }

    protected abstract JsExpression createExpression(@NotNull JsNameRef nameRef, @Nullable JsExpression self);

    protected abstract JsInvocation createInvocation(@NotNull JsNameRef nameRef, @Nullable JsExpression self);

    private void addCapturedValueParameters(JsInvocation bind) {
        if (closureContext.getDescriptors().isEmpty()) {
            return;
        }

        List<JsExpression> expressions = getCapturedValueParametersList(bind);
        for (CallableDescriptor descriptor : closureContext.getDescriptors()) {
            JsName name;
            if (descriptor instanceof VariableDescriptor) {
                name = context.getNameForDescriptor(descriptor);
            }
            else {
                name = ((JsNameRef) context.getAliasForDescriptor(descriptor)).getName();
                assert name != null;
            }
            fun.getParameters().add(new JsParameter(name));
            expressions.add(name.makeRef());
        }
    }

    protected List<JsExpression> getCapturedValueParametersList(JsInvocation invocation) {
        return invocation.getArguments();
    }
}
