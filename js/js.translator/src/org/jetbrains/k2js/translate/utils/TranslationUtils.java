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

package org.jetbrains.k2js.translate.utils;

import com.google.dart.compiler.backend.js.ast.*;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jet.lang.descriptors.FunctionDescriptor;
import org.jetbrains.jet.lang.descriptors.PropertyDescriptor;
import org.jetbrains.jet.lang.descriptors.PropertyGetterDescriptor;
import org.jetbrains.jet.lang.psi.JetBinaryExpression;
import org.jetbrains.jet.lang.psi.JetExpression;
import org.jetbrains.jet.lang.psi.JetOperationExpression;
import org.jetbrains.jet.lang.psi.JetUnaryExpression;
import org.jetbrains.k2js.translate.context.Namer;
import org.jetbrains.k2js.translate.context.TemporaryVariable;
import org.jetbrains.k2js.translate.context.TranslationContext;
import org.jetbrains.k2js.translate.general.Translation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.jetbrains.k2js.translate.utils.BindingUtils.getFunctionDescriptorForOperationExpression;
import static org.jetbrains.k2js.translate.utils.JsAstUtils.*;

public final class TranslationUtils {
    private TranslationUtils() {
    }

    public static boolean isCacheNeeded(@NotNull JsExpression expression) {
        return !(expression instanceof JsLiteral) &&
               (!(expression instanceof JsNameRef) || ((JsNameRef) expression).getQualifier() != null);
    }

    @NotNull
    public static Pair<JsExpression, JsExpression> wrapAsTemporaryIfNeed(
            @NotNull JsExpression expression,
            @NotNull TranslationContext context
    ) {
        if (isCacheNeeded(expression)) {
            TemporaryVariable cachedValue = context.declareTemporary(expression);
            return new Pair<JsExpression, JsExpression>(cachedValue.assignmentExpression(), cachedValue.reference());
        }
        else {
            return Pair.create(expression, expression);
        }
    }

    @NotNull
    public static Pair<JsVar, JsExpression> createTemporaryIfNeed(@NotNull JsExpression expression,
            @NotNull TranslationContext context) {
        // don't create temp variable for simple expression
        if (isCacheNeeded(expression)) {
            return context.dynamicContext().createTemporary(expression);
        }
        else {
            return new Pair<JsVar, JsExpression>(null, expression);
        }
    }

    @NotNull
    public static JsPropertyInitializer translateFunctionAsEcma5PropertyDescriptor(@NotNull JsFunction function,
            @NotNull FunctionDescriptor descriptor,
            @NotNull TranslationContext context) {
        if (descriptor.getReceiverParameter() == null) {
            return new JsPropertyInitializer(descriptor instanceof PropertyGetterDescriptor ? "get" : "set", function);
        }
        else {
            return translateExtensionFunctionAsEcma5DataDescriptor(function, descriptor, context);
        }
    }

    @NotNull
    private static JsPropertyInitializer translateExtensionFunctionAsEcma5DataDescriptor(@NotNull JsFunction function,
            @NotNull FunctionDescriptor descriptor, @NotNull TranslationContext context) {
        JsObjectLiteral meta = createDataDescriptor(function, descriptor.getModality().isOverridable(), false);
        return new JsPropertyInitializer(context.getNameRefForDescriptor(descriptor).getName(), meta);
    }

    @NotNull
    public static JsBinaryOperation isNullCheck(@NotNull JsExpression expressionToCheck) {
        return nullCheck(expressionToCheck, false);
    }

    @NotNull
    public static JsBinaryOperation nullCheck(@NotNull JsExpression expressionToCheck, boolean isNegated) {
        JsBinaryOperator operator = isNegated ? JsBinaryOperator.NEQ : JsBinaryOperator.EQ;
        return new JsBinaryOperation(operator, expressionToCheck, JsLiteral.NULL);
    }

    @NotNull
    public static JsConditional sure(@NotNull JsExpression expression, @NotNull TranslationContext context) {
        return notNullConditional(expression, new JsInvocation(Namer.THROW_NPE_FUN_NAME_REF), context);
    }

    @NotNull
    public static JsConditional notNullConditional(
            @NotNull JsExpression expression,
            @NotNull JsExpression elseExpression,
            @NotNull TranslationContext context
    ) {
        JsExpression testExpression;
        JsExpression thenExpression;
        if (isCacheNeeded(expression)) {
            TemporaryVariable cachedValue = context.declareTemporary(expression);
            testExpression = new JsBinaryOperation(JsBinaryOperator.NEQ, cachedValue.assignmentExpression(), JsLiteral.NULL);
            thenExpression = cachedValue.reference();
        }
        else {
            testExpression = nullCheck(expression, true);
            thenExpression = expression;
        }
        return new JsConditional(testExpression, thenExpression, elseExpression);
    }

    @NotNull
    public static JsNameRef backingFieldReference(@NotNull TranslationContext context,
            @NotNull PropertyDescriptor descriptor) {
        JsNameRef backingFieldNameRef = context.getNameRefForDescriptor(descriptor);
        backingFieldNameRef.setQualifier(JsLiteral.THIS);
        return backingFieldNameRef;
    }

    @NotNull
    public static JsExpression assignmentToBackingField(@NotNull TranslationContext context,
            @NotNull PropertyDescriptor descriptor,
            @NotNull JsExpression assignTo) {
        return assignment(backingFieldReference(context, descriptor), assignTo);
    }

    @NotNull
    public static List<JsExpression> translateExpressionList(@NotNull TranslationContext context,
            @NotNull List<JetExpression> expressions) {
        List<JsExpression> result = new ArrayList<JsExpression>();
        for (JetExpression expression : expressions) {
            result.add(Translation.translateAsExpression(expression, context));
        }
        return result;
    }

    @NotNull
    public static JsExpression translateBaseExpression(@NotNull TranslationContext context,
            @NotNull JetUnaryExpression expression) {
        JetExpression baseExpression = PsiUtils.getBaseExpression(expression);
        return Translation.translateAsExpression(baseExpression, context);
    }

    @NotNull
    public static JsExpression translateLeftExpression(@NotNull TranslationContext context,
            @NotNull JetBinaryExpression expression) {
        JetExpression left = expression.getLeft();
        assert left != null : "Binary expression should have a left expression: " + expression.getText();
        return Translation.translateAsExpression(left, context);
    }

    @NotNull
    public static JsExpression translateRightExpression(@NotNull TranslationContext context,
            @NotNull JetBinaryExpression expression) {
        JetExpression rightExpression = expression.getRight();
        assert rightExpression != null : "Binary expression should have a right expression";
        return Translation.translateAsExpression(rightExpression, context);
    }

    public static boolean hasCorrespondingFunctionIntrinsic(@NotNull TranslationContext context,
            @NotNull JetOperationExpression expression) {
        FunctionDescriptor operationDescriptor = getFunctionDescriptorForOperationExpression(context.bindingContext(), expression);

        if (operationDescriptor == null) return true;
        if (context.intrinsics().getFunctionIntrinsics().getIntrinsic(operationDescriptor) != null) return true;

        return false;
    }

    @NotNull
    public static List<JsExpression> generateInvocationArguments(@NotNull JsExpression receiver, @NotNull List<JsExpression> arguments) {
        if (arguments.isEmpty()) {
            return Collections.singletonList(receiver);
        }

        List<JsExpression> argumentList = new ArrayList<JsExpression>(1 + arguments.size());
        argumentList.add(receiver);
        argumentList.addAll(arguments);
        return argumentList;
    }
}