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

package org.jetbrains.k2js.translate.expression.foreach;

import com.google.dart.compiler.backend.js.ast.*;
import com.google.dart.compiler.backend.js.ast.JsVars.JsVar;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jet.lang.psi.JetExpression;
import org.jetbrains.jet.lang.psi.JetForExpression;
import org.jetbrains.jet.lang.types.JetType;
import org.jetbrains.k2js.translate.context.TranslationContext;
import org.jetbrains.k2js.translate.general.Translation;
import org.jetbrains.k2js.translate.utils.BindingUtils;

import static org.jetbrains.jet.lang.resolve.DescriptorUtils.getClassDescriptorForType;
import static org.jetbrains.k2js.translate.utils.JsAstUtils.addAssign;
import static org.jetbrains.k2js.translate.utils.JsAstUtils.lessThanEq;
import static org.jetbrains.k2js.translate.utils.PsiUtils.getLoopRange;

public final class RangeForTranslator extends ForTranslator {
    @NotNull
    public static JsStatement doTranslate(@NotNull JetForExpression expression,
                                          @NotNull TranslationContext context) {
        return (new RangeForTranslator(expression, context).translate());
    }

    public static boolean isApplicable(@NotNull JetForExpression expression,
                                       @NotNull TranslationContext context) {
        JetExpression loopRange = getLoopRange(expression);
        JetType rangeType = BindingUtils.getTypeForExpression(context.bindingContext(), loopRange);
        //TODO: better check
        //TODO: long range?
        return getClassDescriptorForType(rangeType).getName().asString().equals("IntRange");
    }

    @NotNull
    private final Pair<JsVar, JsExpression> rangeExpression;

    private RangeForTranslator(@NotNull JetForExpression forExpression, @NotNull TranslationContext context) {
        super(forExpression, context);
        rangeExpression = context().dynamicContext().createTemporary(Translation.translateAsExpression(getLoopRange(expression), context));
    }

    @NotNull
    private JsStatement translate() {
        Pair<JsVar, JsExpression> increment = context().dynamicContext().createTemporary(callFunction("get_increment"));
        Pair<JsVar, JsExpression> end = context().dynamicContext().createTemporary(callFunction("get_end"));
        return new JsFor(new JsVars(rangeExpression.first, new JsVar(parameterName, callFunction("get_start")), end.first, increment.first),
                         lessThanEq(new JsNameRef(parameterName), end.second),
                         addAssign(new JsNameRef(parameterName), increment.second),
                         translateOriginalBodyExpression());
    }

    @NotNull
    private JsExpression callFunction(@NotNull String funName) {
        return new JsInvocation(new JsNameRef(funName, rangeExpression.second));
    }
}
