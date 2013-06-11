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
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jet.lang.descriptors.ClassDescriptor;
import org.jetbrains.jet.lang.descriptors.ClassKind;
import org.jetbrains.jet.lang.psi.JetClassOrObject;
import org.jetbrains.jet.lang.psi.JetObjectDeclaration;
import org.jetbrains.jet.lang.resolve.DescriptorUtils;
import org.jetbrains.jet.lang.types.JetType;
import org.jetbrains.k2js.translate.context.Namer;
import org.jetbrains.k2js.translate.context.TranslationContext;
import org.jetbrains.k2js.translate.expression.GenerationPlace;
import org.jetbrains.k2js.translate.initializer.ClassInitializerTranslator;
import org.jetbrains.k2js.translate.utils.JsAstUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.jetbrains.jet.lang.resolve.DescriptorUtils.getClassDescriptorForType;
import static org.jetbrains.jet.lang.resolve.DescriptorUtils.isNotAny;
import static org.jetbrains.k2js.translate.utils.BindingUtils.getClassDescriptor;

/**
 * Generates a definition of a single class
 */
public final class ClassTranslator {
    private ClassTranslator() {
    }

    public static JsExpression translateObjectDeclaration(@NotNull JetObjectDeclaration declaration, @NotNull TranslationContext context) {
        return translateObjectDeclaration(declaration, getClassDescriptor(context.bindingContext(), declaration), context);
    }

    @NotNull
    public static JsExpression translateObjectDeclaration(
            @NotNull JetObjectDeclaration objectDeclaration,
            @NotNull ClassDescriptor descriptor,
            @NotNull TranslationContext context
    ) {
        ClassDescriptor containingClass = DescriptorUtils.getContainingClass(descriptor);
        if (containingClass == null) {
            return translate(objectDeclaration, descriptor, context, context);
        }
        return context.literalFunctionTranslator().translate(containingClass, context, objectDeclaration, descriptor);
    }

    @NotNull
    private static JsExpression classCreateInvocation(@NotNull ClassDescriptor descriptor) {
        switch (descriptor.getKind()) {
            case TRAIT:
                return Namer.CREATE_TRAIT;
            case OBJECT:
                return Namer.CREATE_OBJECT;

            default:
                return Namer.CREATE_CLASS;
        }
    }

    @NotNull
    public static JsInvocation translate(@NotNull JetClassOrObject declaration, @NotNull ClassDescriptor descriptor, @NotNull TranslationContext declarationContext, @NotNull TranslationContext containingContext) {
        JsFunction closure = JsAstUtils.createFunctionWithEmptyBody(declarationContext.scope());
        TranslationContext context = declarationContext.contextWithScope(closure);
        JsInvocation createInvocation = new JsInvocation(classCreateInvocation(descriptor));

        addSuperclassReferences(descriptor, createInvocation, context);
        addClassOwnDeclarations(declaration, descriptor, createInvocation.getArguments(), context, containingContext, closure);

        closure.getBody().getStatements().add(new JsReturn(createInvocation));

        return new JsInvocation(new JsInvocation(new JsNameRef(""), closure), Collections.<JsExpression>emptyList());
    }

    private static void addClassOwnDeclarations(
            @NotNull JetClassOrObject declaration,
            @NotNull ClassDescriptor descriptor,
            @NotNull List<JsExpression> invocationArguments,
            @NotNull TranslationContext declarationContext,
            @NotNull TranslationContext containingContext,
            final JsFunction closure
    ) {
        List<JsPropertyInitializer> properties = new SmartList<JsPropertyInitializer>();
        JsExpression qualifiedReference;
        qualifiedReference = descriptor.getKind().isObject() ? null : declarationContext.getQualifiedReference(descriptor);
        declarationContext.literalFunctionTranslator().setDefinitionPlace(new NotNullLazyValue<GenerationPlace>() {
            @Override
            @NotNull
            public GenerationPlace compute() {
                return new ClosureBackedGenerationPlace(closure.getBody().getStatements());
            }
        });

        DeclarationBodyVisitor visitor = new DeclarationBodyVisitor(properties, declarationContext);
        JsFunction initializer = visitor.getInitializer();
        boolean isTrait = descriptor.getKind().equals(ClassKind.TRAIT);
        if (!isTrait) {
            // must be before visitor.traverseContainer - visitAnonymousInitializer will add anonymous initializer, but this statement can use constructor properties,
            // so, we should process constructor properties first of all
            ClassInitializerTranslator initializerTranslator = new ClassInitializerTranslator(declaration, descriptor);
            initializer.setParameters(
                    initializerTranslator.translate(visitor.getInitializerContext(), initializer, properties, declarationContext));
        }
        visitor.traverseContainer(declaration);
        if (!isTrait) {
            if (containingContext.isEcma5()) {
                invocationArguments.add(initializer.getBody().getStatements().isEmpty() ? JsLiteral.NULL : initializer);
            }
            else {
                properties.add(new JsPropertyInitializer(Namer.INITIALIZE_METHOD_NAME, initializer));
            }
        }

        declarationContext.literalFunctionTranslator().popDefinitionPlace();

        if (!properties.isEmpty()) {
            if (properties.isEmpty()) {
                invocationArguments.add(JsLiteral.NULL);
            }
            else {
                if (qualifiedReference != null) {
                    // about "prototype" — see http://code.google.com/p/jsdoc-toolkit/wiki/TagLends
                    invocationArguments.add(new JsDocComment(JsAstUtils.LENDS_JS_DOC_TAG, new JsNameRef("prototype", qualifiedReference)));
                }
                invocationArguments.add(new JsObjectLiteral(properties, true));
            }
        }
    }

    private static void addSuperclassReferences(ClassDescriptor descriptor, @NotNull JsInvocation jsClassDeclaration, TranslationContext context) {
        List<JsExpression> superClassReferences = getSupertypesNameReferences(descriptor, context);
        if (superClassReferences.isEmpty()) {
            if (!descriptor.getKind().equals(ClassKind.TRAIT) || context.isEcma5()) {
                jsClassDeclaration.getArguments().add(JsLiteral.NULL);
            }
            return;
        }

        List<JsExpression> expressions;
        if (superClassReferences.size() > 1) {
            JsArrayLiteral arrayLiteral = new JsArrayLiteral();
            jsClassDeclaration.getArguments().add(arrayLiteral);
            expressions = arrayLiteral.getExpressions();
        }
        else {
            expressions = jsClassDeclaration.getArguments();
        }

        for (JsExpression superClassReference : superClassReferences) {
            expressions.add(superClassReference);
        }
    }

    @NotNull
    private static List<JsExpression> getSupertypesNameReferences(ClassDescriptor descriptor, TranslationContext context) {
        Collection<JetType> supertypes = descriptor.getTypeConstructor().getSupertypes();
        if (supertypes.isEmpty()) {
            return Collections.emptyList();
        }

        JsExpression base = null;
        List<JsExpression> list = null;
        for (JetType type : supertypes) {
            ClassDescriptor result = getClassDescriptorForType(type);
            if (isNotAny(result) && !context.isNative(result)) {
                switch (result.getKind()) {
                    case CLASS:
                        base = getClassReference(result, context);
                        break;
                    case TRAIT:
                        if (list == null) {
                            list = new ArrayList<JsExpression>();
                        }
                        list.add(getClassReference(result, context));
                        break;

                    default:
                        throw new UnsupportedOperationException("unsupported super class kind " + result.getKind().name());
                }
            }
        }

        if (list == null) {
            return base == null ? Collections.<JsExpression>emptyList() : Collections.singletonList(base);
        }
        else if (base != null) {
            list.add(0, base);
        }
        return list;
    }

    @NotNull
    private static JsExpression getClassReference(@NotNull ClassDescriptor superClassDescriptor, TranslationContext context) {
        return context.getQualifiedReference(superClassDescriptor);
    }
}