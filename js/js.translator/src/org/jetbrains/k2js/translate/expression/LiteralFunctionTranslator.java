package org.jetbrains.k2js.translate.expression;

import com.google.dart.compiler.backend.js.ast.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jet.lang.descriptors.ClassDescriptor;
import org.jetbrains.jet.lang.descriptors.FunctionDescriptor;
import org.jetbrains.jet.lang.descriptors.SimpleFunctionDescriptor;
import org.jetbrains.jet.lang.psi.JetClassOrObject;
import org.jetbrains.jet.lang.psi.JetFunctionLiteralExpression;
import org.jetbrains.jet.lang.psi.JetProperty;
import org.jetbrains.k2js.translate.LabelGenerator;
import org.jetbrains.k2js.translate.context.NamingScope;
import org.jetbrains.k2js.translate.context.TranslationContext;
import org.jetbrains.k2js.translate.declaration.ClassTranslator;

import java.util.ArrayList;
import java.util.List;

import static org.jetbrains.k2js.translate.utils.BindingUtils.getFunctionDescriptor;
import static org.jetbrains.k2js.translate.utils.FunctionBodyTranslator.translateFunctionBody;

// todo easy incremental compiler implementation — generated functions should be inside corresponding class/namespace definition
public class LiteralFunctionTranslator {
    private final List<JsPropertyInitializer> properties = new ArrayList<JsPropertyInitializer>();
    private final LabelGenerator labelGenerator = new LabelGenerator('f');
    private final JsNameRef containingVarRef = new JsNameRef("_f");

    private TranslationContext rootContext;

    public LiteralFunctionTranslator() {

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

    public JsExpression translate(@NotNull JetFunctionLiteralExpression declaration) {
        FunctionDescriptor descriptor = getFunctionDescriptor(rootContext.bindingContext(), declaration);


        NamingScope namingScope = rootContext.scope().innerScope();
        JsBlock body = new JsBlock();
        TranslationContext funContext = rootContext.contextWithScope(namingScope, body);

        JsFunction fun = new JsFunction(funContext.jsScope());
        fun.setBody(body);


        body.getStatements().addAll(translateFunctionBody(descriptor, declaration, funContext).getStatements());

        JsNameRef nameRef = new JsNameRef(labelGenerator.generate(), containingVarRef);
        properties.add(new JsPropertyInitializer(nameRef, fun));

        if (declaration.getParent() instanceof JetProperty && !(((JetProperty) declaration.getParent()).isLocal())) {
            FunctionTranslator.addParameters(fun.getParameters(), descriptor, funContext);
            return fun;
        }

        InnerFunctionTranslator translator = new InnerFunctionTranslator(declaration, descriptor, funContext, fun);
        return translator.translate(nameRef);
    }

    public JsExpression translate(@NotNull ClassDescriptor containingClass,
            JetClassOrObject declaration,
            ClassTranslator classTranslator) {
        NamingScope namingScope = rootContext.scope().innerScope();

        JsName outerThisName = namingScope.jsScope().declareName("$this");

        JsBlock body = new JsBlock();
        TranslationContext funContext = rootContext.contextWithScope(namingScope, body, rootContext.aliasingContext()
                .withThisAliased(containingClass, outerThisName));

        JsFunction fun = new JsFunction(funContext.jsScope());
        fun.setBody(body);

        body.getStatements().add(new JsReturn(classTranslator.translateClassOrObjectCreation(funContext)));

        JsNameRef nameRef = new JsNameRef(labelGenerator.generate(), containingVarRef);
        properties.add(new JsPropertyInitializer(nameRef, fun));

        InnerObjectTranslator translator = new InnerObjectTranslator(declaration, funContext, fun);
        return translator.translate(nameRef, funContext.aliasingContext().wasOuterThisCaptured() ? outerThisName.makeRef() : null);
    }
}

