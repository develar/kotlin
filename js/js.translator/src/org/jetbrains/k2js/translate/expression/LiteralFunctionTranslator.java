package org.jetbrains.k2js.translate.expression;

import com.google.dart.compiler.backend.js.ast.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jet.lang.descriptors.ClassDescriptor;
import org.jetbrains.jet.lang.descriptors.FunctionDescriptor;
import org.jetbrains.jet.lang.psi.JetClassOrObject;
import org.jetbrains.jet.lang.psi.JetFunctionLiteralExpression;
import org.jetbrains.jet.lang.psi.JetProperty;
import org.jetbrains.k2js.translate.LabelGenerator;
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

    public void setRootContext(TranslationContext rootContext) {
        assert this.rootContext == null;
        this.rootContext = rootContext;
        JsName containingVarName = rootContext.scope().declareName(containingVarRef.getIdent());
        containingVarRef.resolve(containingVarName);
    }

    public JsStatement toJsStatement() {
        if (properties.isEmpty()) {
            return rootContext.program().getEmptyStmt();
        }

        return new JsVars(new JsVars.JsVar(containingVarRef.getName(), new JsObjectLiteral(properties, true)));
    }

    public JsExpression translate(@NotNull JetFunctionLiteralExpression declaration) {
        FunctionDescriptor descriptor = getFunctionDescriptor(rootContext.bindingContext(), declaration);

        JsFunction fun = createFunction();
        TranslationContext funContext = rootContext.contextWithScope(fun);

        fun.getBody().getStatements().addAll(translateFunctionBody(descriptor, declaration, funContext).getStatements());

        JsNameRef nameRef = new JsNameRef(labelGenerator.generate(), containingVarRef);

        if (declaration.getParent() instanceof JetProperty && !(((JetProperty) declaration.getParent()).isLocal())) {
            FunctionTranslator.addParameters(fun.getParameters(), descriptor, funContext);
            return fun;
        }

        properties.add(new JsPropertyInitializer(nameRef, fun));
        return new InnerFunctionTranslator(declaration, descriptor, funContext, fun).translate(nameRef);
    }

    private JsFunction createFunction() {
        return new JsFunction(rootContext.scope(), new JsBlock());
    }

    public JsExpression translate(@NotNull ClassDescriptor containingClass,
            JetClassOrObject declaration,
            ClassTranslator classTranslator) {
        JsFunction fun = createFunction();
        JsName outerThisName = fun.getScope().declareName("$this");
        TranslationContext funContext = rootContext.contextWithScope(fun, rootContext.aliasingContext()
                .inner(new InnerObjectTranslator.MyThisAliasProvider(containingClass, outerThisName)));

        fun.getBody().getStatements().add(new JsReturn(classTranslator.translateClassOrObjectCreation(funContext)));

        JsNameRef nameRef = new JsNameRef(labelGenerator.generate(), containingVarRef);
        properties.add(new JsPropertyInitializer(nameRef, fun));

        return new InnerObjectTranslator(declaration, funContext, fun).translate(nameRef);
    }
}

