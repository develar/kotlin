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
import org.jetbrains.jet.lang.descriptors.*;
import org.jetbrains.jet.lang.psi.*;
import org.jetbrains.k2js.translate.context.TranslationContext;
import org.jetbrains.k2js.translate.expression.FunctionTranslator;
import org.jetbrains.k2js.translate.general.Translation;
import org.jetbrains.k2js.translate.general.TranslatorVisitor;
import org.jetbrains.k2js.translate.utils.BindingUtils;
import org.jetbrains.k2js.translate.utils.JsAstUtils;

import java.util.Collections;
import java.util.List;

import static org.jetbrains.k2js.translate.expression.FunctionTranslator.createDefaultValueGetterName;
import static org.jetbrains.k2js.translate.general.Translation.translate;
import static org.jetbrains.k2js.translate.general.Translation.translateAsExpression;
import static org.jetbrains.k2js.translate.initializer.InitializerUtils.generateInitializerForProperty;
import static org.jetbrains.k2js.translate.utils.BindingUtils.getClassDescriptor;
import static org.jetbrains.k2js.translate.utils.BindingUtils.getFunctionDescriptor;
import static org.jetbrains.k2js.translate.utils.JsAstUtils.assignment;
import static org.jetbrains.k2js.translate.utils.JsAstUtils.createDataDescriptor;
import static org.jetbrains.k2js.translate.utils.JsAstUtils.createPropertyDataDescriptor;

public class DeclarationBodyVisitor extends TranslatorVisitor {
    private final JsFunction initializer;

    protected final List<JsPropertyInitializer> result;
    protected final List<JsNode> initializerStatements;

    protected final TranslationContext initializerContext;

    public DeclarationBodyVisitor(TranslationContext context) {
        this(new SmartList<JsPropertyInitializer>(), context);
    }

    public DeclarationBodyVisitor(List<JsPropertyInitializer> result, TranslationContext context) {
        super(context);
        this.result = result;
        initializerStatements = new SmartList<JsNode>();

        initializer = new JsFunction(context.scope(), new JsBlock(initializerStatements));
        initializerContext = context.newFunctionBody(initializer, null, null);
    }

    public JsFunction getInitializer() {
        return initializer;
    }

    public TranslationContext getInitializerContext() {
        return initializerContext;
    }

    @NotNull
    public List<JsPropertyInitializer> getResult() {
        return result;
    }

    @Override
    public void visitClass(@NotNull JetClass declaration) {
        ClassDescriptor descriptor = getClassDescriptor(context.bindingContext(), declaration);
        if (context.predefinedAnnotationManager().hasOptionsArg(descriptor)) {
            return;
        }

        JsExpression value = ClassTranslator.translate(declaration, descriptor, context);
        result.add(new JsPropertyInitializer(descriptor.getName().asString(), value));
    }

    @Override
    public void visitObjectDeclaration(@NotNull JetObjectDeclaration declaration) {
        ClassDescriptor descriptor = getClassDescriptor(context.bindingContext(), declaration);
        JsExpression value = ClassTranslator.translateObjectDeclaration(declaration, descriptor, context);
        JsExpression expression;
        if (asEcma5PropertyDescriptor()) {
            boolean enumerable = !(descriptor.getContainingDeclaration() instanceof NamespaceDescriptor);
            expression = JsAstUtils.defineProperty(descriptor.getName().asString(), createDataDescriptor(value, false, enumerable));
        }
        else {
            expression = assignment(new JsNameRef(descriptor.getName().asString(), JsLiteral.THIS), value);
        }
        initializerStatements.add(expression);
    }

    protected boolean asEcma5PropertyDescriptor() {
        return context.isEcma5();
    }

    @Override
    public void visitNamedFunction(@NotNull JetNamedFunction expression) {
        FunctionDescriptor descriptor = getFunctionDescriptor(context.bindingContext(), expression);
        if (descriptor.getModality().isOverridable()) {
            for (ValueParameterDescriptor valueParameter : descriptor.getValueParameters()) {
                if (!valueParameter.hasDefaultValue() || !valueParameter.declaresDefaultValue()) {
                    continue;
                }

                JetExpression parameter = FunctionTranslator.getDefaultValue(context.bindingContext(), valueParameter);
                JsFunction fun = new JsFunction(context.scope(), new JsBlock(new JsReturn(translateAsExpression(parameter, context))));
                fun.setParameters(Collections.<JsParameter>emptyList());
                defineFunction(createDefaultValueGetterName(descriptor, valueParameter, context),
                               asEcma5PropertyDescriptor() ? createDataDescriptor(fun, false, false) : fun);
            }

            if (descriptor.getModality() == Modality.ABSTRACT) {
                return;
            }
        }

        JsFunction value = FunctionTranslator.translate(expression, descriptor, context);
        String name = context.getNameRefForDescriptor(descriptor).getName();
        if (asEcma5PropertyDescriptor()) {
            defineFunction(name, createPropertyDataDescriptor(descriptor, descriptor.getModality().isOverridable(), value, context));
        }
        else {
            defineFunction(name, value);
        }
    }

    protected void defineFunction(String name, JsExpression value) {
        result.add(new JsPropertyInitializer(name, value));
    }

    @Override
    public void visitProperty(@NotNull JetProperty expression) {
        PropertyDescriptor propertyDescriptor = BindingUtils.getPropertyDescriptor(context.bindingContext(), expression);
        PropertyTranslator.translateAccessors(propertyDescriptor, expression, result, context);

        JetExpression initializer = expression.getInitializer();
        if (initializer != null) {
            visitPropertyWithInitializer(propertyDescriptor, Translation.translateAsExpression(initializer, initializerContext));
        }
    }

    protected void visitPropertyWithInitializer(PropertyDescriptor descriptor, JsExpression value) {
        initializerStatements.add(generateInitializerForProperty(initializerContext, descriptor, value));
    }

    @Override
    public void visitAnonymousInitializer(@NotNull JetClassInitializer expression) {
        initializerStatements.add(translate(expression.getBody(), initializerContext));
    }
}