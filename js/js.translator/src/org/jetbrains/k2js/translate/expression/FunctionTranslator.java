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

package org.jetbrains.k2js.translate.expression;

import com.google.dart.compiler.backend.js.ast.*;
import com.intellij.psi.PsiElement;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jet.lang.descriptors.DeclarationDescriptor;
import org.jetbrains.jet.lang.descriptors.FunctionDescriptor;
import org.jetbrains.jet.lang.descriptors.ReceiverParameterDescriptor;
import org.jetbrains.jet.lang.descriptors.ValueParameterDescriptor;
import org.jetbrains.jet.lang.psi.JetDeclarationWithBody;
import org.jetbrains.jet.lang.psi.JetExpression;
import org.jetbrains.jet.lang.psi.JetParameter;
import org.jetbrains.jet.lang.resolve.BindingContext;
import org.jetbrains.jet.lang.resolve.BindingContextUtils;
import org.jetbrains.jet.lang.types.JetType;
import org.jetbrains.jet.lang.types.lang.KotlinBuiltIns;
import org.jetbrains.k2js.translate.context.AliasingContext;
import org.jetbrains.k2js.translate.context.Namer;
import org.jetbrains.k2js.translate.context.TranslationContext;
import org.jetbrains.k2js.translate.general.Translation;
import org.jetbrains.k2js.translate.utils.TranslationUtils;
import org.jetbrains.k2js.translate.utils.mutator.Mutator;

import java.util.Collections;
import java.util.List;

import static org.jetbrains.k2js.translate.general.Translation.translateAsExpression;
import static org.jetbrains.k2js.translate.utils.JsAstUtils.assignment;
import static org.jetbrains.k2js.translate.utils.JsAstUtils.equality;
import static org.jetbrains.k2js.translate.utils.JsDescriptorUtils.getDeclarationDescriptorForReceiver;
import static org.jetbrains.k2js.translate.utils.mutator.LastExpressionMutator.mutateLastExpression;

public final class FunctionTranslator {
    private FunctionTranslator() {
    }

    public static JsFunction translate(@NotNull JetDeclarationWithBody expression, @NotNull FunctionDescriptor descriptor,  @NotNull TranslationContext context) {
        JsFunction function = new JsFunction(context.scope(), new JsBlock());
        AliasingContext aliasingContext;
        ReceiverParameterDescriptor receiverParameter = descriptor.getReceiverParameter();
        String extensionFunctionReceiverName;
        if (receiverParameter == null) {
            aliasingContext = null;
            extensionFunctionReceiverName = null;
        }
        else {
            DeclarationDescriptor expectedReceiverDescriptor = getDeclarationDescriptorForReceiver(receiverParameter.getValue());
            extensionFunctionReceiverName = function.getScope().declareName(Namer.getReceiverParameterName());
            //noinspection ConstantConditions
            aliasingContext = context.aliasingContext().inner(expectedReceiverDescriptor, new JsNameRef(extensionFunctionReceiverName));
        }

        TranslationContext bodyContext = context.newFunctionBody(function, aliasingContext, null);
        function.setParameters(translateParameters(descriptor, extensionFunctionReceiverName, bodyContext));
        translateBodyAndAdd(function, descriptor, expression, bodyContext);
        return function;
    }

    @NotNull
    public static JsPropertyInitializer translate(
            boolean asEs5PropertyDescriptor,
            @NotNull JetDeclarationWithBody expression,
            @NotNull FunctionDescriptor descriptor,
            @NotNull TranslationContext context
    ) {
        JsFunction function = translate(expression, descriptor, context);
        if (asEs5PropertyDescriptor) {
            return TranslationUtils.translateFunctionAsEcma5PropertyDescriptor(function, descriptor, context);
        }
        else {
            return new JsPropertyInitializer(context.getNameRefForDescriptor(descriptor).getName(), function);
        }
    }

    static void translateBodyAndAdd(
            @NotNull JsFunction function,
            @NotNull FunctionDescriptor descriptor,
            @NotNull JetDeclarationWithBody declaration,
            @NotNull TranslationContext context
    ) {
        JsNode node = translateBody(descriptor, declaration, context);
        List<JsStatement> statements = function.getBody().getStatements();

        translateDefaultParametersInitialization(descriptor, context, statements);

        if (node instanceof JsBlock) {
            statements.addAll(((JsBlock) node).getStatements());
        }
        else {
            statements.add(node.asStatement());
        }
    }

    public static void translateDefaultParametersInitialization(
            @NotNull FunctionDescriptor descriptor,
            @NotNull TranslationContext context,
            @NotNull List<JsStatement> statements
    ) {
        for (ValueParameterDescriptor valueParameter : descriptor.getValueParameters()) {
            if (!valueParameter.hasDefaultValue()) {
                continue;
            }

            ValueParameterDescriptor declarator = valueParameter;
            if (!valueParameter.declaresDefaultValue()) {
                for (ValueParameterDescriptor parameterDescriptor : valueParameter.getOverriddenDescriptors()) {
                    if (parameterDescriptor.declaresDefaultValue()) {
                        declarator = parameterDescriptor;
                        break;
                    }
                }
            }
            else if (!descriptor.getModality().isOverridable()) {
                JetExpression parameter = getDefaultValue(context.bindingContext(), valueParameter);
                JsNameRef parameterRef = new JsNameRef(context.getNameForDescriptor(valueParameter));
                statements.add(new JsIf(equality(parameterRef, JsLiteral.UNDEFINED),
                                        assignment(parameterRef, translateAsExpression(parameter, context)).asStatement()));
                continue;
            }

            JsNameRef parameterRef = new JsNameRef(context.getNameForDescriptor(valueParameter));
            String defaultParameterFunName = createDefaultValueGetterName(descriptor, declarator, context);
            statements.add(new JsIf(equality(parameterRef, JsLiteral.UNDEFINED),
                                    assignment(parameterRef, new JsInvocation(new JsNameRef(defaultParameterFunName, JsLiteral.THIS)))
                                            .asStatement()));
        }
    }

    public static String createDefaultValueGetterName(
            @NotNull FunctionDescriptor descriptor,
            @NotNull ValueParameterDescriptor valueParameter,
            @NotNull TranslationContext context
    ) {
        return "$dv$" + valueParameter.getName().asString() + '_' + context.getNameRefForDescriptor(descriptor).getName();
    }

    @NotNull
    public static JetExpression getDefaultValue(
            @NotNull BindingContext context,
            @NotNull ValueParameterDescriptor descriptor
    ) {
        PsiElement element = BindingContextUtils.descriptorToDeclaration(context, descriptor);
        assert element != null;
        JetExpression defaultValue = ((JetParameter) element).getDefaultValue();
        assert defaultValue != null;
        return defaultValue;
    }

    @NotNull
    private static List<JsParameter> translateParameters(
            FunctionDescriptor descriptor,
            String extensionFunctionReceiverName,
            TranslationContext functionBodyContext
    ) {
        if (extensionFunctionReceiverName == null && descriptor.getValueParameters().isEmpty()) {
            return Collections.emptyList();
        }

        List<JsParameter> jsParameters = new SmartList<JsParameter>();
        addRegularParameters(descriptor, jsParameters, functionBodyContext, extensionFunctionReceiverName);
        return jsParameters;
    }

    public static void addRegularParameters(
            @NotNull FunctionDescriptor descriptor,
            @NotNull List<JsParameter> parameters,
            @NotNull TranslationContext funContext,
            @Nullable String receiverName
    ) {
        if (receiverName != null) {
            parameters.add(new JsParameter(receiverName));
        }
        for (ValueParameterDescriptor valueParameter : descriptor.getValueParameters()) {
            parameters.add(new JsParameter(funContext.getNameForDescriptor(valueParameter)));
        }
    }

    @NotNull
    public static JsNode translateBody(
            @NotNull FunctionDescriptor descriptor,
            @NotNull JetDeclarationWithBody declaration,
            @NotNull TranslationContext context
    ) {
        JetExpression jetBodyExpression = declaration.getBodyExpression();
        assert jetBodyExpression != null : "Cannot translate a body of an abstract function.";
        JsNode body = Translation.translateExpression(jetBodyExpression, context);
        JetType functionReturnType = descriptor.getReturnType();
        assert functionReturnType != null : "Function return typed type must be resolved.";
        if (declaration.hasBlockBody() || KotlinBuiltIns.getInstance().isUnit(functionReturnType)) {
            return body;
        }
        return mutateLastExpression(body, new Mutator() {
            @Override
            @NotNull
            public JsNode mutate(@NotNull JsNode node) {
                if (!(node instanceof JsExpression)) {
                    return node;
                }
                return new JsReturn((JsExpression) node);
            }
        });
    }
}
