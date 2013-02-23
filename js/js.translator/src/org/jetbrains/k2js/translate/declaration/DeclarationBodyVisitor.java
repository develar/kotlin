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

package org.jetbrains.k2js.translate.declaration;

import com.google.dart.compiler.backend.js.ast.*;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jet.lang.descriptors.FunctionDescriptor;
import org.jetbrains.jet.lang.descriptors.Modality;
import org.jetbrains.jet.lang.descriptors.PropertyDescriptor;
import org.jetbrains.jet.lang.descriptors.ValueParameterDescriptor;
import org.jetbrains.jet.lang.psi.*;
import org.jetbrains.k2js.translate.context.TranslationContext;
import org.jetbrains.k2js.translate.expression.FunctionTranslator;
import org.jetbrains.k2js.translate.general.TranslatorVisitor;
import org.jetbrains.k2js.translate.utils.BindingUtils;
import org.jetbrains.k2js.translate.utils.JsAstUtils;

import java.util.Collections;
import java.util.List;

import static org.jetbrains.k2js.translate.expression.FunctionTranslator.createDefaultValueGetterName;
import static org.jetbrains.k2js.translate.general.Translation.translateAsExpression;
import static org.jetbrains.k2js.translate.utils.BindingUtils.getFunctionDescriptor;

public class DeclarationBodyVisitor extends TranslatorVisitor<Void> {
    protected final List<JsPropertyInitializer> result;

    public DeclarationBodyVisitor() {
        this(new SmartList<JsPropertyInitializer>());
    }

    public DeclarationBodyVisitor(List<JsPropertyInitializer> result) {
        this.result = result;
    }

    @NotNull
    public List<JsPropertyInitializer> getResult() {
        return result;
    }

    @Override
    public Void visitClass(@NotNull JetClass expression, @NotNull TranslationContext context) {
        return null;
    }

    @Override
    public Void visitObjectDeclaration(@NotNull JetObjectDeclaration declaration, @NotNull TranslationContext context) {
        // parsed it in initializer visitor => no additional actions are needed
        return null;
    }

    @Override
    public Void visitNamedFunction(@NotNull JetNamedFunction expression, @NotNull TranslationContext context) {
        FunctionDescriptor descriptor = getFunctionDescriptor(context.bindingContext(), expression);
        if (descriptor.getModality().isOverridable()) {
            for (ValueParameterDescriptor valueParameter : descriptor.getValueParameters()) {
                if (!valueParameter.hasDefaultValue() || !valueParameter.declaresDefaultValue()) {
                    continue;
                }

                JetExpression parameter = FunctionTranslator.getDefaultValue(context.bindingContext(), valueParameter);
                JsFunction fun = new JsFunction(context.scope(), new JsBlock(new JsReturn(translateAsExpression(parameter, context))));
                fun.setParameters(Collections.<JsParameter>emptyList());
                result.add(new JsPropertyInitializer(createDefaultValueGetterName(descriptor, valueParameter, context), context.isEcma5() ? JsAstUtils.createDataDescriptor(fun, false, false) : fun));
            }

            if (descriptor.getModality() == Modality.ABSTRACT) {
                return null;
            }
        }

        JsPropertyInitializer methodAsPropertyInitializer = FunctionTranslator.translate(false, expression, descriptor, context);
        if (context.isEcma5()) {
            JsExpression methodBodyExpression = methodAsPropertyInitializer.getValueExpr();
            methodAsPropertyInitializer.setValueExpr(JsAstUtils.createPropertyDataDescriptor(descriptor, descriptor.getModality().isOverridable(), methodBodyExpression));
        }
        result.add(methodAsPropertyInitializer);
        return null;
    }

    @Override
    public Void visitProperty(@NotNull JetProperty expression, @NotNull TranslationContext context) {
        PropertyDescriptor propertyDescriptor = BindingUtils.getPropertyDescriptor(context.bindingContext(), expression);
        PropertyTranslator.translateAccessors(propertyDescriptor, expression, result, context);
        return null;
    }

    @Override
    public Void visitAnonymousInitializer(@NotNull JetClassInitializer expression, @NotNull TranslationContext context) {
        // parsed it in initializer visitor => no additional actions are needed
        return null;
    }
}
