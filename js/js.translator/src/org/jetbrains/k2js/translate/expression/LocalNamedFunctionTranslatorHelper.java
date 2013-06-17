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
import org.jetbrains.jet.lang.descriptors.CallableDescriptor;
import org.jetbrains.jet.lang.descriptors.FunctionDescriptor;
import org.jetbrains.jet.lang.psi.JetNamedFunction;
import org.jetbrains.k2js.translate.context.AliasingContext;
import org.jetbrains.k2js.translate.context.TranslationContext;
import org.jetbrains.k2js.translate.utils.JsAstUtils;

import static org.jetbrains.k2js.translate.utils.JsAstUtils.assignment;

class LocalNamedFunctionTranslatorHelper {
    private final FunctionDescriptor descriptor;
    private final TranslationContext context;

    private final String name;
    private String referenceName;
    private JsNameRef referenceNameRef;

    public LocalNamedFunctionTranslatorHelper(FunctionDescriptor descriptor, TranslationContext context) {
        this.descriptor = descriptor;
        this.context = context;

        name = context.scope().declareFreshName(descriptor.getName().asString());
        context.aliasingContext().registerAlias(descriptor, new JsNameRef(name));
    }

    public JsExpression transform(CallableDescriptor capturedDescriptor) {
        if (capturedDescriptor != descriptor) {
            return null;
        }

        assert referenceName == null;
        referenceName = createReferenceName(context.scope());
        referenceNameRef = new JsNameRef("r", referenceName);
        return assignment(new JsNameRef(referenceName), JsAstUtils.wrapValue(referenceNameRef, JsLiteral.NULL));
    }

    private String createReferenceName(JsScope funScope) {
        return funScope.declareFreshName(descriptor.getName().asString() + "$ref");
    }

    public AliasingContext createAliasingContext(JsScope funScope) {
        return context.aliasingContext().inner(descriptor, new JsNameRef("r", createReferenceName(funScope)));
    }

    public void createResult(JetNamedFunction expression, JsExpression funValue) {
        JsVar funVar = new JsVar(name, funValue);
        funVar.source(expression);
        if (referenceName == null) {
            context.addStatementToCurrentBlock(new JsVars(funVar));
        }
        else {
            context.addStatementToCurrentBlock(new JsVars(new JsVar(referenceName), funVar));
            context.addStatementToCurrentBlock(assignment(referenceNameRef, new JsNameRef(name)));
        }
    }
}
