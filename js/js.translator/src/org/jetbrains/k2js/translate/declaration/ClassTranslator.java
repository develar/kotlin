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
import org.jetbrains.jet.lang.descriptors.*;
import org.jetbrains.jet.lang.psi.JetClassOrObject;
import org.jetbrains.jet.lang.psi.JetObjectDeclaration;
import org.jetbrains.jet.lang.resolve.BindingContext;
import org.jetbrains.jet.lang.resolve.DescriptorUtils;
import org.jetbrains.jet.lang.types.JetType;
import org.jetbrains.k2js.translate.context.Namer;
import org.jetbrains.k2js.translate.context.TranslationContext;
import org.jetbrains.k2js.translate.expression.GenerationPlace;
import org.jetbrains.k2js.translate.general.AbstractTranslator;
import org.jetbrains.k2js.translate.initializer.ClassInitializerTranslator;
import org.jetbrains.k2js.translate.utils.JsAstUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.jetbrains.jet.lang.resolve.DescriptorUtils.getClassDescriptorForType;
import static org.jetbrains.jet.lang.resolve.DescriptorUtils.isNotAny;
import static org.jetbrains.k2js.translate.expression.LiteralFunctionTranslator.createPlace;
import static org.jetbrains.k2js.translate.utils.BindingUtils.getClassDescriptor;

/**
 * Generates a definition of a single class
 */
public final class ClassTranslator extends AbstractTranslator {
    @NotNull
    private final JetClassOrObject classDeclaration;

    @NotNull
    private final ClassDescriptor descriptor;

    @NotNull
    public static JsInvocation generateClassCreation(@NotNull JetClassOrObject classDeclaration, @NotNull TranslationContext context) {
        return new ClassTranslator(classDeclaration, context).translate(context);
    }

    @NotNull
    public static JsExpression generateObjectLiteral(
            @NotNull JetObjectDeclaration objectDeclaration,
            @NotNull TranslationContext context
    ) {
        return new ClassTranslator(objectDeclaration, context).translateObjectLiteralExpression();
    }

    @NotNull
    public static JsExpression generateObjectLiteral(
            @NotNull JetObjectDeclaration objectDeclaration,
            @NotNull ClassDescriptor descriptor,
            @NotNull TranslationContext context
    ) {
        return new ClassTranslator(objectDeclaration, descriptor, context).translateObjectLiteralExpression();
    }

    private ClassTranslator(
            @NotNull JetClassOrObject classDeclaration,
            @NotNull TranslationContext context
    ) {
        this(classDeclaration, getClassDescriptor(context.bindingContext(), classDeclaration), context);
    }

    ClassTranslator(@NotNull JetClassOrObject classDeclaration,
            @NotNull ClassDescriptor descriptor,
            @NotNull TranslationContext context) {
        super(context);
        this.descriptor = descriptor;
        this.classDeclaration = classDeclaration;
    }

    @NotNull
    private JsExpression translateObjectLiteralExpression() {
        ClassDescriptor containingClass = DescriptorUtils.getContainingClass(descriptor);
        if (containingClass == null) {
            return translate(context());
        }
        return context().literalFunctionTranslator().translate(containingClass, context(), classDeclaration, descriptor, this);
    }

    @NotNull
    private JsExpression classCreateInvocation(@NotNull ClassDescriptor descriptor) {
        switch (descriptor.getKind()) {
            case TRAIT:
                return context().namer().traitCreationMethodReference();
            case OBJECT:
                return context().namer().objectCreationMethodReference();

            default:
                return context().namer().classCreationMethodReference();
        }
    }

    @NotNull
    public JsInvocation translate(@NotNull TranslationContext declarationContext) {
        JsFunction closure = JsAstUtils.createFunctionWithEmptyBody(declarationContext.scope());
        TranslationContext context = declarationContext.contextWithScope(closure);
        JsInvocation createInvocation = new JsInvocation(classCreateInvocation(descriptor));

        boolean isTopLevelDeclaration = context() == declarationContext;

        addSuperclassReferences(descriptor, createInvocation, context);
        addClassOwnDeclarations(createInvocation.getArguments(), context, closure, isTopLevelDeclaration);

        closure.getBody().getStatements().add(new JsReturn(createInvocation));

        return new JsInvocation(new JsInvocation(new JsNameRef(""), closure), Collections.<JsExpression>emptyList());
    }

    private void addClassOwnDeclarations(
            @NotNull List<JsExpression> invocationArguments,
            @NotNull TranslationContext declarationContext,
            final JsFunction closure,
            boolean isTopLevelDeclaration
    ) {
        final List<JsPropertyInitializer> properties = new SmartList<JsPropertyInitializer>();
        JsExpression qualifiedReference;
        if (!isTopLevelDeclaration) {
            qualifiedReference = null;
        }
        else if (descriptor.getKind().isObject()) {
            qualifiedReference = null;
            declarationContext.literalFunctionTranslator().setDefinitionPlace(new NotNullLazyValue<GenerationPlace>() {
                @Override
                @NotNull
                public GenerationPlace compute() {
                    return createPlace(properties, context().getThisObject(descriptor));
                }
            });
        }
        else {
            qualifiedReference = declarationContext.getQualifiedReference(descriptor);
            declarationContext.literalFunctionTranslator().setDefinitionPlace(new NotNullLazyValue<GenerationPlace>() {
                @Override
                @NotNull
                public GenerationPlace compute() {
                    return new ClosureBackedGenerationPlace(closure.getBody().getStatements());
                }
            });
        }

        if (!descriptor.getKind().equals(ClassKind.TRAIT)) {
            JsFunction initializer = ClassInitializerTranslator.translateInitializerFunction(classDeclaration, descriptor, declarationContext);
            if (context().isEcma5()) {
                invocationArguments.add(initializer.getBody().getStatements().isEmpty() ? JsLiteral.NULL : initializer);
            }
            else {
                properties.add(new JsPropertyInitializer(Namer.initializeMethodReference(), initializer));
            }
        }

        translatePropertiesAsConstructorParameters(descriptor, declarationContext, properties);
        new DeclarationBodyVisitor(properties, declarationContext).traverseContainer(classDeclaration);

        if (isTopLevelDeclaration) {
            declarationContext.literalFunctionTranslator().popDefinitionPlace();
        }

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

    private static void translatePropertiesAsConstructorParameters(
            @NotNull ClassDescriptor descriptor,
            @NotNull TranslationContext context,
            @NotNull List<JsPropertyInitializer> result
    ) {
        ConstructorDescriptor primaryConstructor = descriptor.getUnsubstitutedPrimaryConstructor();
        if (primaryConstructor == null || primaryConstructor.getValueParameters().isEmpty()) {
            return;
        }

        for (ValueParameterDescriptor parameter : primaryConstructor.getValueParameters()) {
            PropertyDescriptor property =
                    context.bindingContext().get(BindingContext.VALUE_PARAMETER_AS_PROPERTY, parameter);
            if (property != null) {
                PropertyTranslator.translateAccessors(property, result, context);
            }
        }
    }
}
