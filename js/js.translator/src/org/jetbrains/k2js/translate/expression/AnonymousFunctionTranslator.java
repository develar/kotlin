package org.jetbrains.k2js.translate.expression;

import com.google.dart.compiler.backend.js.ast.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jet.lang.descriptors.FunctionDescriptor;
import org.jetbrains.jet.lang.descriptors.ValueParameterDescriptor;
import org.jetbrains.jet.lang.descriptors.VariableDescriptor;
import org.jetbrains.jet.lang.psi.JetFunctionLiteralExpression;
import org.jetbrains.k2js.translate.LabelGenerator;
import org.jetbrains.k2js.translate.context.NamingScope;
import org.jetbrains.k2js.translate.context.TranslationContext;
import org.jetbrains.k2js.translate.utils.closure.ClosureContext;
import org.jetbrains.k2js.translate.utils.closure.ClosureUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.jetbrains.k2js.translate.utils.BindingUtils.getFunctionDescriptor;
import static org.jetbrains.k2js.translate.utils.FunctionBodyTranslator.translateFunctionBody;

// todo easy incremental compiler implementation — generated functions should be inside corresponding class/namespace definition
public class AnonymousFunctionTranslator {
    private final List<JsPropertyInitializer> properties = new ArrayList<JsPropertyInitializer>();
    private final LabelGenerator labelGenerator = new LabelGenerator('f');
    private final JsNameRef containingVarRef = new JsNameRef("_f");

    private TranslationContext rootContext;

    public AnonymousFunctionTranslator() {

    }

    public void setRootContext(TranslationContext rootContext) {
        assert this.rootContext == null;
        this.rootContext = rootContext;
        JsName containingVarName = rootContext.jsScope().declareName(containingVarRef.getIdent());
        containingVarRef.resolve(containingVarName);
    }

    public JsStatement toJsStatement() {
        return new JsVars(new JsVars.JsVar(containingVarRef.getName(), new JsObjectLiteral(properties)));
    }

    public JsExpression translate(@NotNull JetFunctionLiteralExpression declaration, @NotNull TranslationContext context) {
        ClosureContext closureContext = ClosureUtils.captureClosure(context, declaration);

        NamingScope namingScope = rootContext.scope().innerScope((String) null);
        JsBlock body = new JsBlock();
        TranslationContext funContext = context.contextWithScope(namingScope, body);

        JsFunction fun = new JsFunction(funContext.jsScope());
        fun.setBody(body);

        FunctionDescriptor descriptor = getFunctionDescriptor(context.bindingContext(), declaration);
        body.getStatements().addAll(translateFunctionBody(descriptor, declaration, funContext).getStatements());

        JsNameRef nameRef = new JsNameRef(labelGenerator.generate(), containingVarRef);
        properties.add(new JsPropertyInitializer(nameRef, fun));

        JsExpression result;
        JsExpression self = getThis(descriptor, context, closureContext);
        if (closureContext.getDescriptors().isEmpty() && self == context.program().getNullLiteral()) {
            result = nameRef;
        }
        else {
            String bindMethodName = getBindMethodName(closureContext.getDescriptors(), descriptor.getValueParameters());
            JsInvocation bind = new JsInvocation(context.namer().kotlin(bindMethodName));
            bind.getArguments().add(nameRef);
            bind.getArguments().add(self);

            if (!closureContext.getDescriptors().isEmpty()) {
                List<JsExpression> expressions;
                if (closureContext.getDescriptors().size() > 1 || !descriptor.getValueParameters().isEmpty()) {
                    JsArrayLiteral values = new JsArrayLiteral();
                    bind.getArguments().add(values);
                    expressions = values.getExpressions();
                }
                else {
                    expressions = bind.getArguments();
                }

                for (VariableDescriptor variableDescriptor : closureContext.getDescriptors()) {
                    JsName name = context.getNameForDescriptor(variableDescriptor);
                    fun.getParameters().add(new JsParameter(name));
                    expressions.add(name.makeRef());
                }
            }

            result = bind;
        }

        FunctionTranslator.addParameters(fun.getParameters(), descriptor, context);
        return result;
    }

    @NotNull
    private static String getBindMethodName(@NotNull Collection<VariableDescriptor> capturedValues,
            @NotNull Collection<ValueParameterDescriptor> values) {
        if (capturedValues.isEmpty()) {
            return values.isEmpty() ? "b3" : "b4";
        }
        else {
            return values.isEmpty() ? (capturedValues.size() == 1 ? "b0" : "b1") : "b2";
        }
    }

    @NotNull
    private static JsExpression getThis(@NotNull FunctionDescriptor descriptor,
            @NotNull TranslationContext context,
            @NotNull ClosureContext closureContext) {
        if (closureContext.hasReferenceToThis && !descriptor.getReceiverParameter().exists()) {
            return context.program().getThisLiteral();
        }
        return context.program().getNullLiteral();
    }
}

