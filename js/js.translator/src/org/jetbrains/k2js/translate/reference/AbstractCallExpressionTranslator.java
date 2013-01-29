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

import com.google.dart.compiler.backend.js.ast.JsArrayLiteral;
import com.google.dart.compiler.backend.js.ast.JsExpression;
import com.google.dart.compiler.backend.js.ast.JsLiteral;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jet.lang.descriptors.DeclarationDescriptor;
import org.jetbrains.jet.lang.descriptors.ValueParameterDescriptor;
import org.jetbrains.jet.lang.psi.JetCallExpression;
import org.jetbrains.jet.lang.psi.JetExpression;
import org.jetbrains.jet.lang.psi.JetReferenceExpression;
import org.jetbrains.jet.lang.psi.ValueArgument;
import org.jetbrains.jet.lang.resolve.BindingContext;
import org.jetbrains.jet.lang.resolve.BindingContextUtils;
import org.jetbrains.jet.lang.resolve.calls.model.*;
import org.jetbrains.k2js.translate.context.TranslationContext;
import org.jetbrains.k2js.translate.general.AbstractTranslator;
import org.jetbrains.k2js.translate.general.Translation;
import org.jetbrains.k2js.translate.utils.JsAstUtils;

import java.util.Collections;
import java.util.List;

import static org.jetbrains.k2js.translate.utils.BindingUtils.getResolvedCallForCallExpression;

public abstract class AbstractCallExpressionTranslator extends AbstractTranslator {

    @NotNull
    protected final JetCallExpression expression;
    @NotNull
    protected final ResolvedCall<?> resolvedCall;
    @Nullable
    protected final JsExpression receiver;
    @NotNull
    protected final CallType callType;

    protected AbstractCallExpressionTranslator(@NotNull JetCallExpression expression,
            @Nullable JsExpression receiver,
            @NotNull CallType type, @NotNull TranslationContext context) {
        super(context);
        this.expression = expression;
        resolvedCall = getResolvedCallForCallExpression(bindingContext(), expression);
        this.receiver = receiver;
        this.callType = type;
    }

    protected abstract boolean shouldWrapVarargInArray();

    protected boolean translateSingleArgument(@NotNull ResolvedValueArgument argument, @NotNull List<JsExpression> result) {
        if (argument instanceof VarargValueArgument) {
            return translateVarargArgument(argument.getArguments(), result);
        }
        else if (argument instanceof DefaultValueArgument) {
            result.add(JsLiteral.UNDEFINED);
        }
        else {
            ValueArgument valueArgument = ((ExpressionValueArgument) argument).getValueArgument();
            assert valueArgument != null;
            JetExpression argumentExpression = valueArgument.getArgumentExpression();
            assert argumentExpression != null;
            result.add(Translation.translateAsExpression(argumentExpression, context()));
        }
        return false;
    }

    private boolean translateVarargArgument(@NotNull List<ValueArgument> arguments, @NotNull List<JsExpression> result) {
        if (arguments.isEmpty()) {
            if (shouldWrapVarargInArray()) {
                result.add(new JsArrayLiteral(Collections.<JsExpression>emptyList()));
            }
            return false;
        }

        List<JsExpression> list;
        if (shouldWrapVarargInArray()) {
            list = JsAstUtils.newList(arguments.size());
            result.add(new JsArrayLiteral(list));
        }
        else {
            list = result;
            if (arguments.size() == 1) {
                JetExpression expression = arguments.get(0).getArgumentExpression();
                if (expression instanceof JetReferenceExpression) {
                    DeclarationDescriptor referenceTarget = BindingContextUtils.getNotNull(bindingContext(), BindingContext.REFERENCE_TARGET, (JetReferenceExpression) expression);
                    if (referenceTarget instanceof ValueParameterDescriptor &&
                        ((ValueParameterDescriptor) referenceTarget).getVarargElementType() != null) {
                        result.add(Translation.translateAsExpression(expression, context()));
                        return true;
                    }
                }
            }
        }
        for (ValueArgument argument : arguments) {
            JetExpression argumentExpression = argument.getArgumentExpression();
            assert argumentExpression != null;
            list.add(Translation.translateAsExpression(argumentExpression, context()));
        }
        return false;
    }
}
