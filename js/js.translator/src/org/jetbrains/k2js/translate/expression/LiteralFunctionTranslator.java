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
import com.intellij.util.SmartList;
import com.intellij.util.containers.Stack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jet.lang.descriptors.*;
import org.jetbrains.jet.lang.psi.JetClassOrObject;
import org.jetbrains.jet.lang.psi.JetDeclarationWithBody;
import org.jetbrains.jet.lang.resolve.DescriptorUtils;
import org.jetbrains.k2js.translate.context.AliasingContext;
import org.jetbrains.k2js.translate.context.Namer;
import org.jetbrains.k2js.translate.context.TranslationContext;
import org.jetbrains.k2js.translate.context.UsageTracker;
import org.jetbrains.k2js.translate.declaration.ClassTranslator;
import org.jetbrains.k2js.translate.declaration.DefinitionPlace;
import org.jetbrains.k2js.translate.general.AbstractTranslator;

import static org.jetbrains.k2js.translate.expression.FunctionTranslator.addRegularParameters;
import static org.jetbrains.k2js.translate.utils.JsDescriptorUtils.getExpectedReceiverDescriptor;

public class LiteralFunctionTranslator extends AbstractTranslator {
    private final Stack<DefinitionPlace> definitionPlaces = new Stack<DefinitionPlace>();
    private DefinitionPlace definitionPlace;

    public LiteralFunctionTranslator(@NotNull TranslationContext context) {
        super(context);
    }

    public void popDefinitionPlace() {
        definitionPlaces.pop();
        definitionPlace = definitionPlaces.isEmpty() ? null : definitionPlaces.peek();
    }

    public void setDefinitionPlace(@NotNull DefinitionPlace place) {
        definitionPlaces.push(place);
        definitionPlace = place;
    }

    public JsExpression translateFunction(
            @NotNull JetDeclarationWithBody declaration,
            @NotNull FunctionDescriptor descriptor,
            @NotNull TranslationContext outerContext,
            @Nullable LocalNamedFunctionTranslatorHelper namedFunctionTranslatorHelper) {
        JsFunction fun = createFunction();
        AliasingContext aliasingContext;
        DeclarationDescriptor receiverDescriptor = getExpectedReceiverDescriptor(descriptor);
        String receiverName;
        if (receiverDescriptor == null) {
            receiverName = null;
            aliasingContext = null;
        }
        else {
            receiverName = fun.getScope().declareName(Namer.getReceiverParameterName());
            aliasingContext = outerContext.aliasingContext().inner(receiverDescriptor, new JsNameRef(receiverName));
        }

        boolean asInner;
        ClassDescriptor outerClass;
        if (descriptor.getContainingDeclaration() instanceof ConstructorDescriptor) {
            // KT-2388
            asInner = true;
            fun.setName(fun.getScope().declareName(Namer.CALLEE_NAME));
            outerClass = (ClassDescriptor) descriptor.getContainingDeclaration().getContainingDeclaration();
            assert outerClass != null;
            if (receiverDescriptor == null) {
                aliasingContext = outerContext.aliasingContext().notShareableThisAliased(outerClass,
                                                                                         new JsNameRef("o", new JsNameRef(fun.getName())));
            }
        }
        else {
            outerClass = null;
            asInner = DescriptorUtils.isTopLevelDeclaration(descriptor);

            if (aliasingContext == null && namedFunctionTranslatorHelper != null) {
                aliasingContext = namedFunctionTranslatorHelper.createAliasingContext(fun.getScope());
            }
        }

        TranslationContext funContext = outerContext.newFunctionBody(fun, aliasingContext,
                                                                     new UsageTracker(descriptor, outerContext.usageTracker(), outerClass));
        FunctionTranslator.translateBodyAndAdd(fun, descriptor, declaration, funContext);
        if (asInner) {
            addRegularParameters(descriptor, fun.getParameters(), funContext, receiverName);
            if (outerClass != null) {
                UsageTracker usageTracker = funContext.usageTracker();
                assert usageTracker != null;
                if (usageTracker.isUsed()) {
                    return new JsInvocation(Namer.kotlin("assignOwner"), fun, JsLiteral.THIS);
                }
                else {
                    fun.setName(null);
                }
            }
            return fun;
        }
        else {
            JsExpression result = new InnerFunctionTranslator(descriptor, funContext, fun).translate(createReference(fun), outerContext, namedFunctionTranslatorHelper);
            addRegularParameters(descriptor, fun.getParameters(), funContext, receiverName);
            return result;
        }
    }

    private JsNameRef createReference(JsFunction fun) {
        return definitionPlace.createReference(fun);
    }

    private JsFunction createFunction() {
        JsFunction fun = new JsFunction(context().scope(), new JsBlock());
        fun.setParameters(new SmartList<JsParameter>());
        return fun;
    }

    public JsExpression translate(
            @NotNull ClassDescriptor outerClass,
            @NotNull TranslationContext outerClassContext,
            @NotNull JetClassOrObject declaration,
            @NotNull ClassDescriptor descriptor
    ) {
        JsFunction fun = createFunction();
        JsNameRef outerClassRef = new JsNameRef(fun.getScope().declareName(Namer.OUTER_CLASS_NAME));
        UsageTracker usageTracker = new UsageTracker(descriptor, outerClassContext.usageTracker(), outerClass);
        TranslationContext funContext = outerClassContext.newFunctionBody(fun, outerClassContext.aliasingContext().inner(outerClass, outerClassRef),
                                                                          usageTracker);

        JsNameRef classRef = definitionPlace.addInnerClass(new ClassTranslator(declaration, descriptor, fun).translate(funContext, false), descriptor);
        return new InnerObjectTranslator(funContext, fun).translate(classRef, usageTracker.isUsed() ? outerClassRef : null, null);
    }
}
