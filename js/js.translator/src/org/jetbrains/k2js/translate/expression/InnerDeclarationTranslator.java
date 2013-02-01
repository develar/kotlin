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
import com.intellij.util.containers.OrderedSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jet.lang.descriptors.CallableDescriptor;
import org.jetbrains.jet.lang.descriptors.VariableDescriptor;
import org.jetbrains.k2js.translate.context.TranslationContext;

import java.util.List;

abstract class InnerDeclarationTranslator {
    protected final TranslationContext context;
    protected final JsFunction fun;

    public InnerDeclarationTranslator(
            @NotNull TranslationContext context,
            @NotNull JsFunction fun
    ) {
        this.context = context;
        this.fun = fun;
    }

    public JsExpression translate(@NotNull JsNameRef nameRef, @Nullable JsExpression self, @Nullable LocalNamedFunctionTranslatorHelper namedFunctionTranslatorHelper) {
        //noinspection ConstantConditions
        OrderedSet<CallableDescriptor> captured = context.usageTracker().getCapturedVariables();
        if (captured == null && self == JsLiteral.NULL) {
            return createExpression(nameRef, self);
        }

        JsInvocation invocation = createInvocation(nameRef, self);
        if (captured != null) {
            List<JsExpression> expressions = invocation.getArguments();
            for (CallableDescriptor descriptor : captured) {
                String name;
                JsExpression expression = null;
                if (descriptor instanceof VariableDescriptor) {
                    name = context.getNameForDescriptor((VariableDescriptor) descriptor);
                }
                else {
                    JsNameRef aliasForDescriptor = (JsNameRef) context.getAliasForDescriptor(descriptor);
                    assert aliasForDescriptor != null;
                    name = aliasForDescriptor.getQualifier() instanceof JsNameRef ? ((JsNameRef) aliasForDescriptor.getQualifier())
                            .getName() : aliasForDescriptor.getName();
                    if (namedFunctionTranslatorHelper != null) {
                        expression = namedFunctionTranslatorHelper.transform(descriptor);
                    }
                }
                fun.getParameters().add(new JsParameter(name));
                expressions.add(expression == null ? new JsNameRef(name) : expression);
            }
        }
        return invocation;
    }

    protected abstract JsExpression createExpression(@NotNull JsNameRef nameRef, @Nullable JsExpression self);

    protected abstract JsInvocation createInvocation(@NotNull JsNameRef nameRef, @Nullable JsExpression self);
}
