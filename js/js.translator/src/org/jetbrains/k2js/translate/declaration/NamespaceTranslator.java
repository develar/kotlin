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
import com.intellij.openapi.util.Trinity;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jet.lang.descriptors.NamespaceDescriptor;
import org.jetbrains.jet.lang.descriptors.PropertyDescriptor;
import org.jetbrains.jet.lang.psi.*;
import org.jetbrains.jet.lang.resolve.BindingContext;
import org.jetbrains.jet.lang.resolve.BindingContextUtils;
import org.jetbrains.jet.lang.resolve.DescriptorUtils;
import org.jetbrains.k2js.translate.LabelGenerator;
import org.jetbrains.k2js.translate.context.Namer;
import org.jetbrains.k2js.translate.context.TranslationContext;
import org.jetbrains.k2js.translate.general.Translation;
import org.jetbrains.k2js.translate.initializer.InitializerUtils;
import org.jetbrains.k2js.translate.initializer.InitializerVisitor;
import org.jetbrains.k2js.translate.utils.JsAstUtils;
import org.jetbrains.kotlin.compiler.AnnotationsUtils;

import java.util.ArrayList;
import java.util.List;

import static org.jetbrains.k2js.translate.expression.LiteralFunctionTranslator.createPlace;
import static org.jetbrains.k2js.translate.initializer.InitializerUtils.generateInitializerForProperty;
import static org.jetbrains.k2js.translate.utils.BindingUtils.getPropertyDescriptor;

public final class NamespaceTranslator {
    private NamespaceTranslator() {
    }

    public static void translateFiles(Iterable<JetFile> files, List<JsStatement> result, TranslationContext context) {
        List<JsStatement> initializers = context.isEcma5() ? null : new SmartList<JsStatement>();
        JsObjectLiteral moduleRootMembers = new JsObjectLiteral(true);
        JsVars vars = new JsVars(true);
        result.add(vars);
        vars.add(context.classDeclarationTranslator().getDeclaration());
        vars.add(new JsVars.JsVar(Namer.ROOT_PACKAGE_NAME, moduleRootMembers));
        for (JetFile file : files) {
            NamespaceDescriptor descriptor = BindingContextUtils.getNotNull(context.bindingContext(), BindingContext.FILE_TO_NAMESPACE, file);
            translate(descriptor, file, result, initializers, context);
        }
        context.classDeclarationTranslator().generateDeclarations();

        if (initializers == null) {
            result.add(new JsInvocation(Namer.kotlin("finalize"), Namer.ROOT_PACKAGE_NAME_REF).asStatement());
        }
        else {
            result.addAll(initializers);
        }
    }

    private static void translate(
            @NotNull final NamespaceDescriptor descriptor,
            @NotNull JetFile file,
            @NotNull List<JsStatement> result,
            @Nullable List<JsStatement> ecma3Initializers,
            @NotNull TranslationContext context
    ) {
        final FileDeclarationVisitor visitor = new FileDeclarationVisitor(context);
        // own package always JsNameRef (only inter-module references may be JsInvocation)
        final JsNameRef packageQualifiedName = (JsNameRef) context.getQualifiedReference(descriptor);
        context.literalFunctionTranslator().setDefinitionPlace(
                new NotNullLazyValue<Trinity<List<JsPropertyInitializer>, LabelGenerator, JsExpression>>() {
                    @Override
                    @NotNull
                    public Trinity<List<JsPropertyInitializer>, LabelGenerator, JsExpression> compute() {
                        return createPlace(visitor.getResult(), packageQualifiedName);
                    }
                });

        for (JetDeclaration declaration : file.getDeclarations()) {
            if (!AnnotationsUtils.isPredefinedObject(
                    BindingContextUtils.getNotNull(context.bindingContext(), BindingContext.DECLARATION_TO_DESCRIPTOR, declaration))) {
                declaration.accept(visitor, context);
            }
        }
        context.literalFunctionTranslator().setDefinitionPlace(null);

        JsExpression initializer;
        if (visitor.initializerStatements.isEmpty()) {
            initializer = null;
        }
        else {
            initializer = visitor.initializer;
            if (ecma3Initializers != null) {
                ecma3Initializers.add(
                        new JsInvocation(new JsNameRef("call", initializer), packageQualifiedName).asStatement());
            }
        }

        List<JsExpression> defineArguments = new ArrayList<JsExpression>();
        defineArguments.add(Namer.ROOT_PACKAGE_NAME_REF);
        if (DescriptorUtils.isRootNamespace(descriptor)) {
            defineArguments.add(JsLiteral.NULL);
        }
        else {
            defineArguments.add(context.program().getStringLiteral(packageQualifiedName.getName()));
        }
        if (context.isEcma5()) {
            defineArguments.add(initializer == null ? JsLiteral.NULL : initializer);
            defineArguments.add(new JsDocComment(JsAstUtils.LENDS_JS_DOC_TAG, packageQualifiedName));
        }
        defineArguments.add(new JsObjectLiteral(visitor.getResult(), true));
        result.add(new JsDocComment("name", packageQualifiedName));
        result.add(new JsInvocation(context.namer().packageDefinitionMethodReference(), defineArguments).asStatement());
    }

    private static final class FileDeclarationVisitor extends DeclarationBodyVisitor {
        private final JsFunction initializer;
        private final TranslationContext initializerContext;
        private final List<JsStatement> initializerStatements;
        private final InitializerVisitor initializerVisitor;

        private FileDeclarationVisitor(TranslationContext context) {
            initializer = JsAstUtils.createFunctionWithEmptyBody(context.scope());
            initializerContext = context.contextWithScope(initializer);
            initializerStatements = initializer.getBody().getStatements();
            initializerVisitor = new InitializerVisitor(initializerStatements);
        }

        @Override
        public Void visitClass(@NotNull JetClass declaration, @NotNull TranslationContext context) {
            JsPropertyInitializer entry = context.classDeclarationTranslator().translate(declaration, context);
            if (entry != null) {
                result.add(entry);
            }
            return null;
        }

        @Override
        public Void visitObjectDeclaration(@NotNull JetObjectDeclaration declaration, @NotNull TranslationContext context) {
            InitializerUtils.generate(declaration, initializerStatements, context);
            return null;
        }

        @Override
        public Void visitProperty(@NotNull JetProperty property, @NotNull TranslationContext context) {
            super.visitProperty(property, context);
            JetExpression initializer = property.getInitializer();
            if (initializer != null) {
                JsExpression value = Translation.translateAsExpression(initializer, initializerContext);
                PropertyDescriptor propertyDescriptor = getPropertyDescriptor(context.bindingContext(), property);
                if (value instanceof JsLiteral) {
                    result.add(new JsPropertyInitializer(context.getNameRefForDescriptor(propertyDescriptor),
                                                         context.isEcma5() ? JsAstUtils
                                                                 .createPropertyDataDescriptor(propertyDescriptor, value) : value));
                }
                else {
                    initializerStatements.add(generateInitializerForProperty(context, propertyDescriptor, value));
                }
            }
            return null;
        }

        @Override
        public Void visitAnonymousInitializer(@NotNull JetClassInitializer expression, @NotNull TranslationContext context) {
            expression.accept(initializerVisitor, initializerContext);
            return null;
        }
    }
}
