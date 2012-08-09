package org.jetbrains.k2js.translate.expression;

import com.google.dart.compiler.backend.js.ast.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jet.lang.descriptors.ClassDescriptor;
import org.jetbrains.jet.lang.descriptors.ConstructorDescriptor;
import org.jetbrains.jet.lang.descriptors.FunctionDescriptor;
import org.jetbrains.jet.lang.descriptors.NamespaceDescriptor;
import org.jetbrains.jet.lang.psi.JetClassBody;
import org.jetbrains.jet.lang.psi.JetClassOrObject;
import org.jetbrains.jet.lang.psi.JetDeclarationWithBody;
import org.jetbrains.k2js.translate.LabelGenerator;
import org.jetbrains.k2js.translate.context.AliasingContext;
import org.jetbrains.k2js.translate.context.Namer;
import org.jetbrains.k2js.translate.context.TranslationContext;
import org.jetbrains.k2js.translate.declaration.ClassTranslator;

import java.util.ArrayList;
import java.util.List;

import static com.google.dart.compiler.backend.js.ast.JsVars.JsVar;
import static org.jetbrains.k2js.translate.utils.FunctionBodyTranslator.translateFunctionBody;

// todo easy incremental compiler implementation — generated functions should be inside corresponding class/namespace definition
public class LiteralFunctionTranslator {
    private final List<JsPropertyInitializer> properties = new ArrayList<JsPropertyInitializer>();
    private final LabelGenerator labelGenerator = new LabelGenerator('f');
    private final JsNameRef containingVarRef = new JsNameRef("_f");

    private TranslationContext rootContext;

    public void setRootContext(@NotNull TranslationContext rootContext) {
        assert this.rootContext == null;
        this.rootContext = rootContext;
        JsName containingVarName = rootContext.scope().declareName(containingVarRef.getIdent());
        containingVarRef.resolve(containingVarName);
    }

    public JsVar getDeclaration() {
        return new JsVar(containingVarRef.getName(), properties.isEmpty() ? null : new JsObjectLiteral(properties, true));
    }

    public JsExpression translateFunction(@NotNull JetDeclarationWithBody declaration, @NotNull FunctionDescriptor descriptor, @NotNull TranslationContext context) {
        JsFunction fun = createFunction();
        TranslationContext funContext;
        boolean asInner;
        ClassDescriptor outerClass;
        if (descriptor.getContainingDeclaration() instanceof ConstructorDescriptor) {
            // KT-2388
            asInner = true;
            fun.setName(fun.getScope().declareName(Namer.CALLEE_NAME));
            outerClass = (ClassDescriptor) descriptor.getContainingDeclaration().getContainingDeclaration();
            assert outerClass != null;
            AliasingContext aliasingContext = rootContext.aliasingContext().inner(outerClass, new JsNameRef("o", fun.getName().makeRef()));
            funContext = rootContext.contextWithScope(fun, aliasingContext);
            funContext.usageTracker = new TranslationContext.UsageTracker(outerClass);
        }
        else {
            outerClass = null;
            asInner = descriptor.getContainingDeclaration() instanceof NamespaceDescriptor;
            funContext = rootContext.contextWithScope(fun);
            funContext.usageTracker = context.usageTracker;
        }

        fun.getBody().getStatements().addAll(translateFunctionBody(descriptor, declaration, funContext).getStatements());

        InnerFunctionTranslator translator = null;
        if (!asInner) {
            translator = new InnerFunctionTranslator(declaration.asElement(), descriptor, funContext, fun);
            if (translator.isLocalVariablesAffected()) {
                asInner = true;
            }
        }

        if (asInner) {
            FunctionTranslator.addParameters(fun.getParameters(), descriptor, funContext);
            if (outerClass != null) {
                if (funContext.usageTracker.isUsed()) {
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
        return translator.translate(nameRef, context);

        //return translate(translator, fun);
    }

    private JsFunction createFunction() {
        return new JsFunction(rootContext.scope(), new JsBlock());
    }

    public JsExpression translate(@NotNull ClassDescriptor containingClass,
            @NotNull JetClassOrObject declaration,
            @NotNull ClassDescriptor descriptor,
            @NotNull ClassTranslator classTranslator) {
        JsFunction fun = createFunction();
        JsName outerThisName = fun.getScope().declareName("$this");
        JsNameRef outerClassRef = outerThisName.makeRef();
        TranslationContext funContext = createThisTraceableContext(containingClass, fun, outerClassRef);

        fun.getBody().getStatements().add(new JsReturn(classTranslator.translateClassOrObjectCreation(funContext)));
        JetClassBody body = declaration.getBody();
        assert body != null;
        InnerObjectTranslator translator = new InnerObjectTranslator(body, descriptor, funContext, fun);


        JsNameRef nameRef = new JsNameRef(labelGenerator.generate(), containingVarRef);
                properties.add(new JsPropertyInitializer(nameRef, fun));

        return translator.translate(nameRef, funContext.aliasingContext().isCaptured(containingClass) ? outerClassRef : null);
    }

    //private JsExpression translate(@NotNull InnerDeclarationTranslator translator, @NotNull JsFunction fun) {
    //    JsNameRef nameRef = new JsNameRef(labelGenerator.generate(), containingVarRef);
    //    properties.add(new JsPropertyInitializer(nameRef, fun));
    //    return translator.translate(nameRef);
    //}

    private TranslationContext createThisTraceableContext(@NotNull ClassDescriptor classDescriptor,
            @NotNull JsFunction fun,
            @NotNull JsNameRef thisRef) {
        return rootContext.contextWithScope(fun, rootContext.aliasingContext().inner(classDescriptor, thisRef));
    }
}

