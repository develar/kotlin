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

package org.jetbrains.k2js.translate.operation;

import com.google.dart.compiler.backend.js.ast.JsBinaryOperation;
import com.google.dart.compiler.backend.js.ast.JsBinaryOperator;
import com.google.dart.compiler.backend.js.ast.JsExpression;
import com.google.dart.compiler.backend.js.ast.JsNumberLiteral;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jet.lang.descriptors.FunctionDescriptor;
import org.jetbrains.jet.lang.psi.JetBinaryExpression;
import org.jetbrains.jet.lang.resolve.calls.model.ResolvedCall;
import org.jetbrains.jet.lang.types.expressions.OperatorConventions;
import org.jetbrains.jet.lexer.JetToken;
import org.jetbrains.jet.lexer.JetTokens;
import org.jetbrains.k2js.translate.context.TranslationContext;
import org.jetbrains.k2js.translate.intrinsic.operation.BinaryOperationIntrinsic;
import org.jetbrains.k2js.translate.reference.CallBuilder;
import org.jetbrains.k2js.translate.reference.CallType;
import org.jetbrains.k2js.translate.utils.TranslationUtils;

import static org.jetbrains.k2js.translate.operation.AssignmentTranslator.isAssignmentOperator;
import static org.jetbrains.k2js.translate.utils.BindingUtils.getFunctionDescriptorForOperationExpression;
import static org.jetbrains.k2js.translate.utils.BindingUtils.getResolvedCall;
import static org.jetbrains.k2js.translate.utils.JsAstUtils.not;
import static org.jetbrains.k2js.translate.utils.PsiUtils.*;
import static org.jetbrains.k2js.translate.utils.TranslationUtils.translateLeftExpression;
import static org.jetbrains.k2js.translate.utils.TranslationUtils.translateRightExpression;

public final class BinaryOperationTranslator {
    private BinaryOperationTranslator() {
    }

    @NotNull
    public static JsExpression translate(JetBinaryExpression expression, TranslationContext context) {
        BinaryOperationIntrinsic intrinsic = context.intrinsics().getBinaryOperationIntrinsics().getIntrinsic(expression, context);
        if (intrinsic != null) {
            return intrinsic.apply(expression,
                                   translateLeftExpression(context, expression),
                                   translateRightExpression(context, expression),
                                   context);
        }
        if (getOperationToken(expression).equals(JetTokens.ELVIS)) {
            return TranslationUtils.notNullConditional(translateLeftExpression(context, expression),
                                                       translateRightExpression(context, expression), context);
        }
        if (isAssignmentOperator(expression)) {
            return AssignmentTranslator.translate(expression, context);
        }
        
        FunctionDescriptor operationDescriptor = getFunctionDescriptorForOperationExpression(context.bindingContext(), expression);
        if (operationDescriptor == null) {
            return translateAsUnOverloadableBinaryOperation(expression, context);
        }
        if (operationDescriptor.getName().equals(OperatorConventions.COMPARE_TO)) {
            JsExpression methodCall = translateAsOverloadedBinaryOperation(expression, context).source(expression);
            return new JsBinaryOperation(OperatorTable.getBinaryOperator(getOperationToken(expression)), methodCall, JsNumberLiteral.V_0);
        }
        return translateAsOverloadedBinaryOperation(expression, context);
    }

    @NotNull
    private static JsExpression translateAsUnOverloadableBinaryOperation(JetBinaryExpression expression, TranslationContext context) {
        JetToken token = getOperationToken(expression);
        JsBinaryOperator operator = OperatorTable.getBinaryOperator(token);
        assert OperatorConventions.NOT_OVERLOADABLE.contains(token);
        JsExpression left = translateLeftExpression(context, expression);
        JsExpression right = translateRightExpression(context, expression);
        return new JsBinaryOperation(operator, left, right);
    }

    @NotNull
    public static JsExpression translateAsOverloadedBinaryOperation(JetBinaryExpression expression, TranslationContext context) {
        JsExpression leftExpression = translateLeftExpression(context, expression);
        JsExpression rightExpression = translateRightExpression(context, expression);

        CallBuilder callBuilder = CallBuilder.build(context);
        if (isInOrNotInOperation(expression)) {
            callBuilder.receiver(rightExpression).args(leftExpression);
        }
        else {
            callBuilder.receiver(leftExpression).args(rightExpression);
        }
        ResolvedCall<?> resolvedCall = getResolvedCall(context.bindingContext(), expression.getOperationReference());
        JsExpression result = callBuilder.resolvedCall(resolvedCall).type(CallType.NORMAL).translate();
        return isNegatedOperation(expression) ? not(result) : result;
    }
}