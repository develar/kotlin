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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.k2js.translate.context.TranslationContext;

import java.util.List;

class InnerObjectTranslator extends InnerDeclarationTranslator {
    public InnerObjectTranslator(@NotNull TranslationContext context) {
        super(context);
    }

    @Override
    protected JsExpression createExpression(@NotNull JsNameRef nameRef, @Nullable JsExpression self, List<JsParameter> functionParameters) {
        return createInvocation(nameRef, functionParameters, self, 0);
    }

    @Override
    protected HasArguments createInvocation(
            @NotNull JsNameRef nameRef,
            List<JsParameter> functionParameters,
            @Nullable JsExpression self,
            int additionalArgumentCount
    ) {
        if (self == null) {
            return new JsNew(nameRef, createArgumentList(additionalArgumentCount, null));
        }
        else {
            functionParameters.add(new JsParameter(((JsNameRef) self).getName()));
            return new JsNew(nameRef, createArgumentList(additionalArgumentCount, JsLiteral.THIS));
        }
    }
}
