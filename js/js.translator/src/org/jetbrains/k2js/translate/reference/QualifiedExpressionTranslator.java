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

package org.jetbrains.k2js.translate.reference;

import com.google.dart.compiler.backend.js.ast.JsExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jet.lang.descriptors.DeclarationDescriptor;
import org.jetbrains.jet.lang.descriptors.NamespaceDescriptor;
import org.jetbrains.jet.lang.descriptors.PropertyDescriptor;
import org.jetbrains.jet.lang.psi.*;
import org.jetbrains.jet.lang.resolve.BindingContext;
import org.jetbrains.k2js.translate.context.TranslationContext;
import org.jetbrains.k2js.translate.utils.ErrorReportingUtils;

import static org.jetbrains.k2js.translate.general.Translation.translateAsExpression;
import static org.jetbrains.k2js.translate.utils.BindingUtils.getDescriptorForReferenceExpression;
import static org.jetbrains.k2js.translate.utils.PsiUtils.*;

public final class QualifiedExpressionTranslator {

    private QualifiedExpressionTranslator() {
    }

    @NotNull
    public static AccessTranslator getAccessTranslator(@NotNull JetQualifiedExpression expression,
                                                       @NotNull TranslationContext context) {
        JsExpression receiver = translateReceiver(expression, context);
        JetSimpleNameExpression expression1 = getNotNullSimpleNameSelector(expression);
        return PropertyAccessTranslator.newInstance(getPropertyDescriptor(expression1, context), expression1, receiver, CallType.getCallTypeForQualifiedExpression(expression), context
        );
    }

    @NotNull
    private static PropertyDescriptor getPropertyDescriptor(
            @NotNull JetSimpleNameExpression expression,
            @NotNull TranslationContext context
    ) {
        DeclarationDescriptor descriptor =
                getDescriptorForReferenceExpression(context.bindingContext(), expression);
        assert descriptor instanceof PropertyDescriptor : "Must be a property descriptor.";
        return (PropertyDescriptor) descriptor;
    }

    @NotNull
    public static JsExpression translateQualifiedExpression(@NotNull JetQualifiedExpression expression,
                                                            @NotNull TranslationContext context) {
        JsExpression receiver = translateReceiver(expression, context);
        JetExpression selector = getSelector(expression);
        CallType callType = CallType.getCallTypeForQualifiedExpression(expression);
        return dispatchToCorrectTranslator(receiver, selector, callType, context);
    }

    @NotNull
    private static JsExpression dispatchToCorrectTranslator(
            @Nullable JsExpression receiver,
            @NotNull JetExpression expression,
            @NotNull CallType callType,
            @NotNull TranslationContext context
    ) {
        JetSimpleNameExpression selector;
        if (expression instanceof JetQualifiedExpression) {
            selector = getSelectorAsSimpleName((JetQualifiedExpression) expression);
            assert selector != null : "Only names are allowed after the dot";
        }
        else if (expression instanceof JetSimpleNameExpression) {
            selector = (JetSimpleNameExpression) expression;
        }
        else {
            selector = null;
        }

        if (selector != null) {
            DeclarationDescriptor descriptor = getDescriptorForReferenceExpression(context.bindingContext(), selector);
            if (descriptor instanceof PropertyDescriptor) {
                return PropertyAccessTranslator.newInstance((PropertyDescriptor) descriptor, selector, receiver, callType, context).translateAsGet();
            }
        }

        if (expression instanceof JetCallExpression) {
            return invokeCallExpressionTranslator(receiver, expression, callType, context);
        }
        //TODO: never get there
        if (expression instanceof JetSimpleNameExpression) {
            return ReferenceTranslator.translateSimpleName((JetSimpleNameExpression)expression, context);
        }
        throw new AssertionError("Unexpected qualified expression: " + expression.getText());
    }

    @NotNull
    private static JsExpression invokeCallExpressionTranslator(@Nullable JsExpression receiver,
            @NotNull JetExpression selector,
            @NotNull CallType callType,
            @NotNull TranslationContext context) {
        try {
            return CallExpressionTranslator.translate((JetCallExpression) selector, receiver, callType, context);
        } catch (RuntimeException e) {
            throw  ErrorReportingUtils.reportErrorWithLocation(selector, e);
        }
    }

    @Nullable
    private static JsExpression translateReceiver(@NotNull JetQualifiedExpression expression,
                                                  @NotNull TranslationContext context) {
        JetExpression receiverExpression = expression.getReceiverExpression();
        if (isFullQualifierForExpression(receiverExpression, context)) {
            return null;
        }
        return translateAsExpression(receiverExpression, context);
    }

    //TODO: prove correctness
    private static boolean isFullQualifierForExpression(@Nullable JetExpression receiverExpression, @NotNull TranslationContext context) {
        if (receiverExpression == null) {
            return false;
        }
        if (receiverExpression instanceof JetReferenceExpression) {
            DeclarationDescriptor reference = context.bindingContext().get(BindingContext.REFERENCE_TARGET, (JetReferenceExpression)receiverExpression);
            if (reference instanceof NamespaceDescriptor) {
                return true;
            }
        }
        if (receiverExpression instanceof JetQualifiedExpression) {
            return isFullQualifierForExpression(((JetQualifiedExpression)receiverExpression).getSelectorExpression(), context);
        }
        return false;
    }
}
