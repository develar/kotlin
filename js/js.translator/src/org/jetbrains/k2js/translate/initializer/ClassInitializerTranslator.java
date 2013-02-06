/*
 * Copyright 2010-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.k2js.translate.initializer;

import com.google.dart.compiler.backend.js.ast.*;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jet.lang.descriptors.*;
import org.jetbrains.jet.lang.psi.JetClassOrObject;
import org.jetbrains.jet.lang.psi.JetDelegationSpecifier;
import org.jetbrains.jet.lang.psi.JetDelegatorToSuperCall;
import org.jetbrains.jet.lang.resolve.BindingContext;
import org.jetbrains.jet.lang.resolve.DescriptorUtils;
import org.jetbrains.jet.lang.types.JetType;
import org.jetbrains.k2js.translate.context.Namer;
import org.jetbrains.k2js.translate.context.TranslationContext;
import org.jetbrains.k2js.translate.expression.FunctionTranslator;
import org.jetbrains.k2js.translate.general.AbstractTranslator;

import java.util.List;

import static org.jetbrains.k2js.translate.utils.TranslationUtils.translateArgumentList;

public final class ClassInitializerTranslator extends AbstractTranslator {
    @NotNull
    private final JetClassOrObject declaration;
    private final ClassDescriptor descriptor;
    @NotNull
    private final List<JsStatement> initializerStatements = new SmartList<JsStatement>();

    private final JsFunction initializerFunction;

    public static JsFunction translateInitializerFunction(
            @NotNull JetClassOrObject declaration,
            @NotNull ClassDescriptor descriptor,
            @NotNull TranslationContext classContext
    ) {
        JsFunction fun = new JsFunction(classContext.scope(), new JsBlock());
        TranslationContext context = classContext.newFunctionBody(fun, null, null);
        new ClassInitializerTranslator(declaration, descriptor, fun, context).translate();
        return fun;
    }

    private ClassInitializerTranslator(
            @NotNull JetClassOrObject declaration,
            @NotNull ClassDescriptor descriptor,
            @NotNull JsFunction initializerFunction,
            @NotNull TranslationContext context
    ) {
        super(context);
        this.initializerFunction = initializerFunction;
        this.declaration = declaration;
        this.descriptor = descriptor;
    }

    private void translate() {
        //NOTE: while we translate constructor parameters we also add property initializer statements
        // for properties declared as constructor parameters
        translatePrimaryConstructorParameters(initializerFunction.getParameters());

        new InitializerVisitor(initializerStatements).traverseContainer(declaration, context());

        List<JsStatement> funStatements = initializerFunction.getBody().getStatements();
        for (JsStatement statement : initializerStatements) {
            if (statement instanceof JsBlock) {
                funStatements.addAll(((JsBlock) statement).getStatements());
            }
            else {
                funStatements.add(statement);
            }
        }
    }

    private void mayBeAddCallToSuperMethod(JsFunction initializer) {
        for (JetType type : descriptor.getTypeConstructor().getSupertypes()) {
            ClassDescriptor superClassDescriptor = DescriptorUtils.getClassDescriptorForType(type);
            if (superClassDescriptor.getKind() == ClassKind.CLASS) {
                JetDelegatorToSuperCall superCall = getSuperCall();
                if (superCall != null) {
                    addCallToSuperMethod(superCall, initializer);
                }
                return;
            }
        }
    }

    private void addCallToSuperMethod(@NotNull JetDelegatorToSuperCall superCall, JsFunction initializer) {
        JsInvocation call;
        if (context().isEcma5()) {
            String ref = context().scope().declareName(Namer.CALLEE_NAME);
            initializer.setName(ref);
            call = new JsInvocation(new JsNameRef("call", new JsNameRef("baseInitializer", new JsNameRef(ref))));
            call.getArguments().add(JsLiteral.THIS);
        }
        else {
            String superMethodName = context().scope().declareName(Namer.superMethodName());
            call = new JsInvocation(new JsNameRef(superMethodName, JsLiteral.THIS));
        }
        translateArgumentList(context(), superCall.getValueArguments(), call.getArguments());
        initializerStatements.add(call.asStatement());
    }

    @Nullable
    private JetDelegatorToSuperCall getSuperCall() {
        for (JetDelegationSpecifier specifier : declaration.getDelegationSpecifiers()) {
            if (specifier instanceof JetDelegatorToSuperCall) {
                return (JetDelegatorToSuperCall) specifier;
            }
        }
        return null;
    }

    private void translatePrimaryConstructorParameters(List<JsParameter> result) {
        ConstructorDescriptor primaryConstructor = descriptor.getUnsubstitutedPrimaryConstructor();
        List<ValueParameterDescriptor> parameters;
        if (primaryConstructor == null) {
            parameters = null;
        }
        else {
            parameters = primaryConstructor.getValueParameters();
            FunctionTranslator.translateDefaultParametersInitialization(primaryConstructor, context(), initializerStatements);
        }

        mayBeAddCallToSuperMethod(initializerFunction);

        if (parameters == null || parameters.isEmpty()) {
            return;
        }

        for (ValueParameterDescriptor parameter : parameters) {
            JsParameter jsParameter = new JsParameter(context().getNameForDescriptor(parameter));
            PropertyDescriptor propertyDescriptor = bindingContext().get(BindingContext.VALUE_PARAMETER_AS_PROPERTY, parameter);
            if (propertyDescriptor != null) {
                initializerStatements.add(InitializerUtils.generateInitializerForProperty(context(), propertyDescriptor,
                                                                                          new JsNameRef(jsParameter.getName())));
            }
            result.add(jsParameter);
        }
    }
}
