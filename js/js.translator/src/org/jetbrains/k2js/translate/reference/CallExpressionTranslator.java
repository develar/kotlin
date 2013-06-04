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
import com.google.dart.compiler.backend.js.ast.JsObjectLiteral;
import com.google.dart.compiler.backend.js.ast.JsPropertyInitializer;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jet.lang.descriptors.CallableDescriptor;
import org.jetbrains.jet.lang.descriptors.ConstructorDescriptor;
import org.jetbrains.jet.lang.descriptors.ValueParameterDescriptor;
import org.jetbrains.jet.lang.psi.JetCallExpression;
import org.jetbrains.jet.lang.psi.JetExpression;
import org.jetbrains.jet.lang.psi.JetSimpleNameExpression;
import org.jetbrains.jet.lang.resolve.calls.model.DefaultValueArgument;
import org.jetbrains.jet.lang.resolve.calls.model.ResolvedCall;
import org.jetbrains.jet.lang.resolve.calls.model.ResolvedValueArgument;
import org.jetbrains.jet.lang.resolve.calls.model.VariableAsFunctionResolvedCall;
import org.jetbrains.jet.lang.resolve.calls.util.ExpressionAsFunctionDescriptor;
import org.jetbrains.jet.lang.types.lang.KotlinBuiltIns;
import org.jetbrains.k2js.translate.context.TranslationContext;
import org.jetbrains.k2js.translate.general.Translation;
import org.jetbrains.k2js.translate.utils.JsAstUtils;
import org.jetbrains.k2js.translate.utils.PsiUtils;
import org.jetbrains.kotlin.compiler.PredefinedAnnotationManager;

import java.util.List;

import static org.jetbrains.k2js.translate.utils.PsiUtils.getCallee;

public final class CallExpressionTranslator extends AbstractCallExpressionTranslator {
    private boolean isNative;

    @NotNull
    public static JsExpression translate(
            @NotNull JetCallExpression expression,
            @Nullable JsExpression receiver,
            @NotNull CallType callType,
            @NotNull TranslationContext context
    ) {
        if (InlinedCallExpressionTranslator.shouldBeInlined(expression, context)) {
            return InlinedCallExpressionTranslator.translate(expression, receiver, callType, context);
        }
        return new CallExpressionTranslator(expression, receiver, callType, context).translate();
    }

    private CallExpressionTranslator(
            @NotNull JetCallExpression expression,
            @Nullable JsExpression receiver,
            @NotNull CallType callType, @NotNull TranslationContext context
    ) {
        super(expression, receiver, callType, context);
    }

    @NotNull
    private JsExpression translate() {
        CallBuilder callBuilder = CallBuilder.build(context())
                .receiver(receiver)
                .callee(getCalleeExpression())
                .resolvedCall(getResolvedCall())
                .type(callType);
        isNative = callBuilder.isNative();

        List<JsPropertyInitializer> optionsConstructor = translateArguments(callBuilder);
        if (optionsConstructor == null) {
            return callBuilder.translate();
        }
        else {
            return new JsObjectLiteral(optionsConstructor);
        }
    }

    private List<JsPropertyInitializer> translateArguments(CallBuilder callBuilder) {
        List<ValueParameterDescriptor> valueParameters = resolvedCall.getResultingDescriptor().getValueParameters();
        if (valueParameters.isEmpty()) {
            return null;
        }

        PredefinedAnnotationManager annotationManager = context().predefinedAnnotationManager();
        boolean funOptionsArg;
        boolean forceAnyOptionsObject;
        if (isNative) {
            CallableDescriptor descriptor = resolvedCall.getCandidateDescriptor();
            if (descriptor instanceof ConstructorDescriptor) {
                funOptionsArg = annotationManager.hasOptionsArg(((ConstructorDescriptor) descriptor).getContainingDeclaration());
                forceAnyOptionsObject = funOptionsArg;
            }
            else {
                funOptionsArg = annotationManager.hasOptionsArg(descriptor);
                forceAnyOptionsObject = false;
            }
        }
        else {
            funOptionsArg = false;
            forceAnyOptionsObject = false;
        }

        List<JsPropertyInitializer> optionsObject = funOptionsArg ? new SmartList<JsPropertyInitializer>() : null;

        List<JsExpression> result = forceAnyOptionsObject ? null : JsAstUtils.<JsExpression>newList(valueParameters.size());
        List<ResolvedValueArgument> valueArgumentsByIndex = resolvedCall.getValueArgumentsByIndex();
        for (ValueParameterDescriptor parameterDescriptor : valueParameters) {
            ResolvedValueArgument argument = valueArgumentsByIndex.get(parameterDescriptor.getIndex());
            if (argument instanceof DefaultValueArgument && isNative) {
                // see http://developer.chrome.com/extensions/windows.html
                // chrome.windows.getLastFocused(optional object getInfo, function callback)
                // getInfo is optional, but we don't need pass undefined, we can just skip parameter
                continue;
            }

            boolean forceOptionsObject;
            if (forceAnyOptionsObject) {
                forceOptionsObject = true;
            }
            else if (funOptionsArg) {
                if (parameterDescriptor.hasDefaultValue()) {
                    // last function is not property of options object
                    forceOptionsObject = valueArgumentsByIndex.size() != (parameterDescriptor.getIndex() + 1) ||
                                         !KotlinBuiltIns.getInstance().isFunctionType(parameterDescriptor.getType());
                }
                else {
                    forceOptionsObject = parameterDescriptor.hasDefaultValue() || annotationManager.hasOptionsArg(parameterDescriptor);
                }
            }
            else {
                forceOptionsObject = isNative && annotationManager.hasOptionsArg(parameterDescriptor);
                if (forceOptionsObject && optionsObject == null) {
                    optionsObject = new SmartList<JsPropertyInitializer>();
                }
            }

            if (translateSingleArgument(argument, result, optionsObject, forceOptionsObject ? parameterDescriptor : null)) {
                callBuilder.invokeAsApply(true);
            }
        }

        if (forceAnyOptionsObject) {
            return optionsObject;
        }
        else {
            callBuilder.args(result);
            return null;
        }
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

    @Override
    public boolean shouldWrapVarargInArray() {
        return !isNative;
    }
}
