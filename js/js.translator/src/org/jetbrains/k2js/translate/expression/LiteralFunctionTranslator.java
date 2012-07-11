package org.jetbrains.k2js.translate.expression;

import com.google.dart.compiler.backend.js.ast.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jet.lang.descriptors.ClassDescriptor;
import org.jetbrains.jet.lang.descriptors.ConstructorDescriptor;
import org.jetbrains.jet.lang.descriptors.FunctionDescriptor;
import org.jetbrains.jet.lang.descriptors.NamespaceDescriptor;
import org.jetbrains.jet.lang.psi.JetClassOrObject;
import org.jetbrains.jet.lang.psi.JetFunctionLiteralExpression;
import org.jetbrains.k2js.translate.LabelGenerator;
import org.jetbrains.k2js.translate.context.Namer;
import org.jetbrains.k2js.translate.context.TranslationContext;
import org.jetbrains.k2js.translate.declaration.ClassTranslator;

import java.util.ArrayList;
import java.util.List;

import static com.google.dart.compiler.backend.js.ast.JsVars.JsVar;
import static org.jetbrains.k2js.translate.expression.InnerDeclarationTranslator.TraceableThisAliasProvider;
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

    public JsVar getDeclaration() {
        return new JsVar(containingVarRef.getName(), properties.isEmpty() ? null : new JsObjectLiteral(properties, true));
    }

    public JsExpression translate(@NotNull JetFunctionLiteralExpression declaration) {
        FunctionDescriptor descriptor = getFunctionDescriptor(rootContext.bindingContext(), declaration);

        JsFunction fun = createFunction();
        TranslationContext funContext;
        boolean asInner;
        if (descriptor.getContainingDeclaration() instanceof ConstructorDescriptor) {
            // KT-2388
            asInner = true;
            fun.setName(fun.getScope().declareName(Namer.CALLEE_NAME));
            funContext = createThisTraceableContext((ClassDescriptor) descriptor.getContainingDeclaration().getContainingDeclaration(), fun,
                                                    new JsNameRef("o", fun.getName().makeRef()));
        }
        else {
            asInner = descriptor.getContainingDeclaration() instanceof NamespaceDescriptor;
            funContext = rootContext.contextWithScope(fun);
        }

        fun.getBody().getStatements().addAll(translateFunctionBody(descriptor, declaration, funContext).getStatements());

        if (asInner) {
            FunctionTranslator.addParameters(fun.getParameters(), descriptor, funContext);
            if (funContext.thisAliasProvider() instanceof TraceableThisAliasProvider) {
                TraceableThisAliasProvider provider = (TraceableThisAliasProvider) funContext.thisAliasProvider();
                if (provider.wasThisCaptured()) {
                    return new JsInvocation(rootContext.namer().kotlin("assignOwner"), fun, JsLiteral.THIS);
                }
                else {
                    fun.setName(null);
                }
            }

            return fun;
        }

        JsNameRef nameRef = new JsNameRef(labelGenerator.generate(), containingVarRef);
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
        TranslationContext funContext = createThisTraceableContext(containingClass, fun, outerThisName.makeRef());

        fun.getBody().getStatements().add(new JsReturn(classTranslator.translateClassOrObjectCreation(funContext)));

        JsNameRef nameRef = new JsNameRef(labelGenerator.generate(), containingVarRef);
        properties.add(new JsPropertyInitializer(nameRef, fun));

        return new InnerObjectTranslator(declaration, funContext, fun).translate(nameRef);
    }

    private TranslationContext createThisTraceableContext(ClassDescriptor containingClass, JsFunction fun, JsNameRef thisRef) {
        return rootContext.contextWithScope(fun, rootContext.aliasingContext().inner(
                new TraceableThisAliasProvider(containingClass, thisRef)));
    }
}

