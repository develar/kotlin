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
import com.google.dart.compiler.backend.js.ast.JsVar;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jet.lang.psi.JetExpression;
import org.jetbrains.jet.lang.psi.JetForExpression;
import org.jetbrains.jet.lang.types.JetType;
import org.jetbrains.k2js.translate.context.TranslationContext;
import org.jetbrains.k2js.translate.utils.BindingUtils;

import static org.jetbrains.jet.lang.resolve.DescriptorUtils.getClassDescriptorForType;
import static org.jetbrains.k2js.translate.general.Translation.translateAsExpression;
import static org.jetbrains.k2js.translate.utils.JsAstUtils.addAssign;
import static org.jetbrains.k2js.translate.utils.JsAstUtils.lessThanEq;
import static org.jetbrains.k2js.translate.utils.PsiUtils.getLoopRange;
import static org.jetbrains.k2js.translate.utils.TranslationUtils.createTemporaryIfNeed;

public final class RangeForTranslator extends ForTranslator {
    public static boolean isApplicable(@NotNull JetExpression loopRange, @NotNull TranslationContext context) {
        JetType rangeType = BindingUtils.getTypeForExpression(context.bindingContext(), loopRange);
        //TODO: better check
        //TODO: long range?
        return getClassDescriptorForType(rangeType).getName().asString().equals("IntRange");
    }

    RangeForTranslator(@NotNull JetForExpression forExpression, @NotNull TranslationContext context) {
        super(forExpression, context);
    }

    @NotNull
    JsStatement translate() {
        Pair<JsVar, JsExpression> rangeExpression =
                createTemporaryIfNeed(translateAsExpression(getLoopRange(expression), context()), context());

        JsExpression incrementExpression;
        if (context().isEcma5()) {
            incrementExpression = new JsNameRef("increment", rangeExpression.second);
        }
        else {
            incrementExpression = new JsInvocation(new JsNameRef("get_increment", rangeExpression.second));
        }
        Pair<JsVar, JsExpression> increment = context().dynamicContext().createTemporary(incrementExpression);

        Pair<JsVar, JsExpression> end = context().dynamicContext().createTemporary(new JsNameRef("end", rangeExpression.second));
        JsVars initVars = new JsVars(rangeExpression.first,
                                     new JsVar(parameterName, new JsNameRef("start", rangeExpression.second)),
                                     end.first,
                                     increment.first);
        return new JsFor(initVars,
                         lessThanEq(new JsNameRef(parameterName), end.second),
                         addAssign(new JsNameRef(parameterName), increment.second),
                         translateOriginalBodyExpression());
    }
}
