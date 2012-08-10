package org.jetbrains.k2js.translate.expression;

import com.google.dart.compiler.backend.js.ast.*;
import com.intellij.openapi.util.Trinity;
import com.intellij.util.containers.Stack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jet.lang.descriptors.*;
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

import org.jetbrains.k2js.translate.context.UsageTracker;
import org.jetbrains.k2js.translate.initializer.InitializerUtils;

import static org.jetbrains.k2js.translate.utils.FunctionBodyTranslator.translateFunctionBody;
import static org.jetbrains.k2js.translate.utils.JsDescriptorUtils.getExpectedReceiverDescriptor;

public class LiteralFunctionTranslator {
    private final Trinity<List<JsPropertyInitializer>, LabelGenerator, JsExpression> rootPlace =
            new Trinity<List<JsPropertyInitializer>, LabelGenerator, JsExpression>(new ArrayList<JsPropertyInitializer>(),
                                                                                   new LabelGenerator('f'), new JsNameRef("_f"));

    private TranslationContext rootContext;

    private final Stack<Trinity<List<JsPropertyInitializer>, LabelGenerator, JsExpression>> definitionPlaces =
            new Stack<Trinity<List<JsPropertyInitializer>, LabelGenerator, JsExpression>>();
    private Trinity<List<JsPropertyInitializer>, LabelGenerator, JsExpression> definitionPlace = rootPlace;

    public void setRootContext(@NotNull TranslationContext rootContext) {
        assert this.rootContext == null;
        this.rootContext = rootContext;
        JsNameRef ref = (JsNameRef) rootPlace.third;
        JsName containingVarName = rootContext.scope().declareName(ref.getIdent());
        ref.resolve(containingVarName);
    }

    public JsVar getDeclaration() {
        return new JsVar(((JsNameRef) rootPlace.third).getName(), rootPlace.first.isEmpty() ? null : new JsObjectLiteral(rootPlace.first, true));
    }

    public void setDefinitionPlace(@Nullable List<JsPropertyInitializer> value, @Nullable JsExpression reference) {
        if (value == null) {
            definitionPlaces.pop();
            definitionPlace = definitionPlaces.isEmpty() ? rootPlace : definitionPlaces.peek();
        }
        else {
            Trinity<List<JsPropertyInitializer>, LabelGenerator, JsExpression> newPlace = Trinity.create(value, new LabelGenerator('f'), reference);
            definitionPlaces.push(newPlace);
            definitionPlace = newPlace;
        }
    }

    public JsExpression translateFunction(@NotNull JetDeclarationWithBody declaration, @NotNull FunctionDescriptor descriptor, @NotNull TranslationContext outerContext) {
        JsFunction fun = createFunction();
        TranslationContext funContext;
        boolean asInner;
        ClassDescriptor outerClass;
        AliasingContext aliasingContext = rootContext.aliasingContext();
        DeclarationDescriptor receiverDescriptor = getExpectedReceiverDescriptor(descriptor);
        JsName receiverName;
        if (receiverDescriptor == null) {
            receiverName = null;
        }
        else {
            receiverName = fun.getScope().declareName(Namer.getReceiverParameterName());
            aliasingContext = aliasingContext.inner(receiverDescriptor, receiverName.makeRef());
        }

        if (descriptor.getContainingDeclaration() instanceof ConstructorDescriptor) {
            // KT-2388
            asInner = true;
            fun.setName(fun.getScope().declareName(Namer.CALLEE_NAME));
            outerClass = (ClassDescriptor) descriptor.getContainingDeclaration().getContainingDeclaration();
            assert outerClass != null;

            if (receiverDescriptor == null) {
                aliasingContext = aliasingContext.inner(outerClass, new JsNameRef("o", fun.getName().makeRef()));
            }

            funContext = rootContext.contextWithScope(fun, aliasingContext, new UsageTracker(outerClass));
        }
        else {
            outerClass = null;
            asInner = descriptor.getContainingDeclaration() instanceof NamespaceDescriptor;

            funContext = rootContext.contextWithScope(fun, aliasingContext, outerContext.usageTracker());
        }

        fun.getBody().getStatements().addAll(translateFunctionBody(descriptor, declaration, funContext).getStatements());

        InnerFunctionTranslator translator = null;
        if (!asInner) {
            translator = new InnerFunctionTranslator(declaration.asElement(), descriptor, funContext, fun);
        }

        if (asInner) {
            addRegularParameters(descriptor, fun, funContext, receiverName);
            if (outerClass != null) {
                if (funContext.usageTracker().isUsed()) {
                    return new JsInvocation(rootContext.namer().kotlin("assignOwner"), fun, JsLiteral.THIS);
                }
                else {
                    fun.setName(null);
                }
            }

            return fun;
        }

        JsExpression result = translator.translate(createReference(fun), outerContext);
        addRegularParameters(descriptor, fun, funContext, receiverName);
        return result;
    }

    private JsNameRef createReference(JsFunction fun) {
        JsNameRef nameRef = new JsNameRef(definitionPlace.second.generate(), definitionPlace.third);
        definitionPlace.first.add(new JsPropertyInitializer(nameRef, InitializerUtils.toDataDescriptor(fun, rootContext)));
        return nameRef;
    }

    private static void addRegularParameters(FunctionDescriptor descriptor,
            JsFunction fun,
            TranslationContext funContext,
            JsName receiverName) {
        if (receiverName != null) {
            fun.getParameters().add(new JsParameter(receiverName));
        }
        FunctionTranslator.addParameters(fun.getParameters(), descriptor, funContext);
    }

    private JsFunction createFunction() {
        return new JsFunction(rootContext.scope(), new JsBlock());
    }

    public JsExpression translate(@NotNull ClassDescriptor outerClass,
            @NotNull JetClassOrObject declaration,
            @NotNull ClassDescriptor descriptor,
            @NotNull ClassTranslator classTranslator) {
        JsFunction fun = createFunction();
        JsNameRef outerClassRef = fun.getScope().declareName("$this").makeRef();
        TranslationContext funContext = rootContext
                .contextWithScope(fun, rootContext.aliasingContext().inner(outerClass, outerClassRef), new UsageTracker(outerClass));

        fun.getBody().getStatements().add(new JsReturn(classTranslator.translateClassOrObjectCreation(funContext)));
        JetClassBody body = declaration.getBody();
        assert body != null;
        InnerObjectTranslator translator = new InnerObjectTranslator(body, descriptor, funContext, fun);
        return translator.translate(createReference(fun), funContext.usageTracker().isUsed() ? outerClassRef : null);
    }
}

