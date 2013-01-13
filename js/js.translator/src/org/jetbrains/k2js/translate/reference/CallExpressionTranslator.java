/*
 * Copyright 2010-2012 JetBrains s.r.o.
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
import org.jetbrains.jet.lang.descriptors.CallableDescriptor;
import org.jetbrains.jet.lang.descriptors.ValueParameterDescriptor;
import org.jetbrains.jet.lang.psi.JetCallExpression;
import org.jetbrains.jet.lang.psi.JetExpression;
import org.jetbrains.jet.lang.psi.JetSimpleNameExpression;
import org.jetbrains.jet.lang.resolve.calls.model.DefaultValueArgument;
import org.jetbrains.jet.lang.resolve.calls.model.ResolvedCall;
import org.jetbrains.jet.lang.resolve.calls.model.ResolvedValueArgument;
import org.jetbrains.jet.lang.resolve.calls.model.VariableAsFunctionResolvedCall;
import org.jetbrains.jet.lang.resolve.calls.util.ExpressionAsFunctionDescriptor;
import org.jetbrains.k2js.translate.context.TranslationContext;
import org.jetbrains.k2js.translate.general.Translation;
import org.jetbrains.kotlin.compiler.AnnotationsUtils;
import org.jetbrains.k2js.translate.utils.JsAstUtils;
import org.jetbrains.k2js.translate.utils.PsiUtils;

import java.util.Collections;
import java.util.List;

import static org.jetbrains.k2js.translate.utils.PsiUtils.getCallee;

public final class CallExpressionTranslator extends AbstractCallExpressionTranslator {

    @NotNull
    public static JsExpression translate(@NotNull JetCallExpression expression,
            @Nullable JsExpression receiver,
            @NotNull CallType callType,
            @NotNull TranslationContext context) {
        if (InlinedCallExpressionTranslator.shouldBeInlined(expression, context)) {
            return InlinedCallExpressionTranslator.translate(expression, receiver, callType, context);
        }
        return (new CallExpressionTranslator(expression, receiver, callType, context)).translate();
    }

    private CallExpressionTranslator(@NotNull JetCallExpression expression,
            @Nullable JsExpression receiver,
            @NotNull CallType callType, @NotNull TranslationContext context) {
        super(expression, receiver, callType, context);
    }

    @NotNull
    private JsExpression translate() {
        return CallBuilder.build(context())
                .receiver(receiver)
                .callee(getCalleeExpression())
                .args(translateArguments())
                .resolvedCall(getResolvedCall())
                .type(callType)
                .translate();
    }

    @NotNull
    private ResolvedCall<?> getResolvedCall() {
        if (resolvedCall instanceof VariableAsFunctionResolvedCall) {
            return ((VariableAsFunctionResolvedCall) resolvedCall).getFunctionCall();
        }
        return resolvedCall;
    }

    @Nullable
    private JsExpression getCalleeExpression() {
        CallableDescriptor candidateDescriptor = resolvedCall.getCandidateDescriptor();
        if (candidateDescriptor instanceof ExpressionAsFunctionDescriptor) {
            return Translation.translateAsExpression(getCallee(expression), context());
        }
        if (resolvedCall instanceof VariableAsFunctionResolvedCall) {
            return translateVariableForVariableAsFunctionResolvedCall();
        }
        return null;
    }

    @NotNull
    //TODO: looks hacky and should be modified soon
    private JsExpression translateVariableForVariableAsFunctionResolvedCall() {
        JetExpression callee = PsiUtils.getCallee(expression);
        if (callee instanceof JetSimpleNameExpression) {
            return ReferenceTranslator.getAccessTranslator((JetSimpleNameExpression) callee, receiver, context()).translateAsGet();
        }
        assert receiver != null;
        return Translation.translateAsExpression(callee, context());
    }

    @NotNull
    private List<JsExpression> translateArguments() {
        List<ValueParameterDescriptor> valueParameters = resolvedCall.getResultingDescriptor().getValueParameters();
        if (valueParameters.isEmpty()) {
            return Collections.emptyList();
        }

        List<JsExpression> result = JsAstUtils.newList(valueParameters.size());
        List<ResolvedValueArgument> valueArgumentsByIndex = resolvedCall.getValueArgumentsByIndex();
        for (ValueParameterDescriptor parameterDescriptor : valueParameters) {
            ResolvedValueArgument argument = valueArgumentsByIndex.get(parameterDescriptor.getIndex());
            if (argument instanceof DefaultValueArgument && isNative()) {
                // see http://developer.chrome.com/extensions/windows.html
                // chrome.windows.getLastFocused(optional object getInfo, function callback)
                // getInfo is optional, but we don't need pass undefined, we can just skip parameter
                continue;
            }

            translateSingleArgument(argument, result);
        }
        return result;
    }

    @Override
    public boolean shouldWrapVarargInArray() {
        return !isNative();
    }

    private boolean isNative() {
        return AnnotationsUtils.isNativeObject(resolvedCall.getCandidateDescriptor());
    }
}
