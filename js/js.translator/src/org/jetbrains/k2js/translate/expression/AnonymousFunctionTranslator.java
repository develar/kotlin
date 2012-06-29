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
import org.jetbrains.k2js.translate.reference.ReferenceTranslator;
import org.jetbrains.k2js.translate.utils.closure.ClosureContext;
import org.jetbrains.k2js.translate.utils.closure.ClosureUtils;

import java.util.ArrayList;
import java.util.List;

import static org.jetbrains.k2js.translate.utils.BindingUtils.getFunctionDescriptor;
import static org.jetbrains.k2js.translate.utils.FunctionBodyTranslator.translateFunctionBody;

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

    public JsNode translate(@NotNull JetFunctionLiteralExpression declaration, @NotNull TranslationContext context) {
        ClosureContext closureContext = ClosureUtils.captureClosure(context, declaration);

        NamingScope namingScope = rootContext.scope().innerScope((String) null);
        JsBlock body = new JsBlock();
        TranslationContext funContext = context.contextWithScope(namingScope, body);

        JsFunction fun = new JsFunction(funContext.jsScope());
        fun.setBody(body);

        FunctionDescriptor descriptor = getFunctionDescriptor(context.bindingContext(), declaration);
        body.getStatements().addAll(translateFunctionBody(descriptor, declaration, funContext).getStatements());

        for (ValueParameterDescriptor valueParameter : descriptor.getValueParameters()) {
            fun.getParameters().add(new JsParameter(context.getNameForDescriptor(valueParameter)));
        }

        JsNameRef nameRef = new JsNameRef(labelGenerator.generate(), containingVarRef);
        properties.add(new JsPropertyInitializer(nameRef, fun));

        if (closureContext.getDescriptors().isEmpty()) {
            return nameRef;
        }
        else {
            return wrapInClosureCaptureExpression(context, fun, closureContext);
        }

        //return nameRef;
    }

    @NotNull
    private JsExpression wrapInClosureCaptureExpression(@NotNull TranslationContext context, @NotNull JsExpression wrappedExpression,
            @NotNull ClosureContext closureContext) {
        JsFunction dummyFunction = new JsFunction(context.jsScope());
        JsInvocation dummyFunctionInvocation = new JsInvocation(dummyFunction);
        for (VariableDescriptor variableDescriptor : closureContext.getDescriptors()) {
            dummyFunction.getParameters().add(new JsParameter(context.getNameForDescriptor(variableDescriptor)));
            dummyFunctionInvocation.getArguments().add(ReferenceTranslator.translateAsLocalNameReference(variableDescriptor, context));
        }
        dummyFunction.setBody(new JsBlock(new JsReturn(wrappedExpression)));
        return dummyFunctionInvocation;
    }
}

