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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jet.lang.descriptors.*;
import org.jetbrains.jet.lang.psi.JetClassOrObject;
import org.jetbrains.jet.lang.psi.JetDelegationSpecifier;
import org.jetbrains.jet.lang.psi.JetDelegatorToSuperCall;
import org.jetbrains.jet.lang.resolve.BindingContext;
import org.jetbrains.jet.lang.resolve.DescriptorUtils;
import org.jetbrains.jet.lang.types.JetType;
import org.jetbrains.k2js.translate.context.Namer;
import org.jetbrains.k2js.translate.context.TranslationContext;
import org.jetbrains.k2js.translate.declaration.PropertyTranslator;
import org.jetbrains.k2js.translate.expression.FunctionTranslator;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.jetbrains.k2js.translate.initializer.InitializerUtils.generateInitializerForProperty;
import static org.jetbrains.k2js.translate.utils.TranslationUtils.translateArgumentList;

public final class ClassInitializerTranslator {
    private ClassInitializerTranslator() {
    }

    private static void mayBeAddCallToSuperMethod(
            ClassDescriptor descriptor,
            JetClassOrObject declaration,
            JsFunction initializer,
            TranslationContext context
    ) {
        for (JetType type : descriptor.getTypeConstructor().getSupertypes()) {
            ClassDescriptor superClassDescriptor = DescriptorUtils.getClassDescriptorForType(type);
            if (superClassDescriptor.getKind() == ClassKind.CLASS) {
                for (JetDelegationSpecifier specifier : declaration.getDelegationSpecifiers()) {
                    if (specifier instanceof JetDelegatorToSuperCall) {
                        addCallToSuperMethod((JetDelegatorToSuperCall) specifier, initializer, context);
                        return;
                    }
                }
                return;
            }
        }
    }

    private static void addCallToSuperMethod(
            @NotNull JetDelegatorToSuperCall superCall,
            @NotNull JsFunction initializer,
            @NotNull TranslationContext context
    ) {
        JsInvocation call;
        if (context.isEcma5()) {
            String ref = context.scope().declareName(Namer.CALLEE_NAME);
            initializer.setName(ref);
            call = new JsInvocation(new JsNameRef("call", new JsNameRef("baseInitializer", new JsNameRef(ref))));
            call.getArguments().add(JsLiteral.THIS);
        }
        else {
            String superMethodName = context.scope().declareName(Namer.superMethodName());
            call = new JsInvocation(new JsNameRef(superMethodName, JsLiteral.THIS));
        }
        translateArgumentList(context, superCall.getValueArguments(), call.getArguments());
        initializer.getBody().getStatements().add(call.asStatement());
    }

    public static List<JsParameter> translate(
            @NotNull JetClassOrObject declaration,
            @NotNull ClassDescriptor descriptor,
            @NotNull TranslationContext context,
            @NotNull JsFunction initializer,
            @NotNull List<JsPropertyInitializer> properties,
            @NotNull TranslationContext declarationContext
    ) {
        ConstructorDescriptor primaryConstructor = descriptor.getUnsubstitutedPrimaryConstructor();
        List<ValueParameterDescriptor> parameters;
        List<JsStatement> statements = initializer.getBody().getStatements();
        if (primaryConstructor == null) {
            parameters = null;
        }
        else {
            parameters = primaryConstructor.getValueParameters();
            FunctionTranslator.translateDefaultParametersInitialization(primaryConstructor, context, statements);
        }

        mayBeAddCallToSuperMethod(descriptor, declaration, initializer, context);

        if (parameters == null || parameters.isEmpty()) {
            return Collections.emptyList();
        }

        JsParameter[] result = new JsParameter[parameters.size()];
        for (int i = 0; i < parameters.size(); i++) {
            ValueParameterDescriptor parameter = parameters.get(i);
            JsParameter jsParameter = new JsParameter(context.getNameForDescriptor(parameter));
            PropertyDescriptor propertyDescriptor = context.bindingContext().get(BindingContext.VALUE_PARAMETER_AS_PROPERTY, parameter);
            if (propertyDescriptor != null) {
                PropertyTranslator.translateAccessors(propertyDescriptor, properties, declarationContext);
                statements.add(generateInitializerForProperty(context, propertyDescriptor, new JsNameRef(jsParameter.getName())));
            }
            result[i] = jsParameter;
        }
        return Arrays.asList(result);
    }
}
