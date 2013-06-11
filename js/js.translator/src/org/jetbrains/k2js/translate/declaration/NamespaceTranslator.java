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
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jet.lang.descriptors.DeclarationDescriptor;
import org.jetbrains.jet.lang.descriptors.NamespaceDescriptor;
import org.jetbrains.jet.lang.psi.JetDeclaration;
import org.jetbrains.jet.lang.psi.JetFile;
import org.jetbrains.jet.lang.resolve.BindingContext;
import org.jetbrains.jet.lang.resolve.BindingContextUtils;
import org.jetbrains.jet.lang.resolve.DescriptorUtils;
import org.jetbrains.k2js.translate.context.Namer;
import org.jetbrains.k2js.translate.context.TranslationContext;
import org.jetbrains.k2js.translate.expression.GenerationPlace;
import org.jetbrains.k2js.translate.utils.JsAstUtils;

import java.util.ArrayList;
import java.util.List;

public final class NamespaceTranslator {
    private NamespaceTranslator() {
    }

    public static void translateFiles(Iterable<JetFile> files, List<JsStatement> result, TranslationContext context) {
        List<JsStatement> initializers = context.isEcma5() ? null : new SmartList<JsStatement>();
        JsObjectLiteral moduleRootMembers = new JsObjectLiteral(true);
        JsVars vars = new JsVars(true);
        result.add(vars);
        vars.add(new JsVars.JsVar(Namer.ROOT_PACKAGE_NAME, moduleRootMembers));
        for (JetFile file : files) {
            NamespaceDescriptor descriptor = BindingContextUtils.getNotNull(context.bindingContext(), BindingContext.FILE_TO_NAMESPACE, file);
            translate(descriptor, file, result, initializers, context);
        }
        if (initializers == null) {
            result.add(new JsInvocation(Namer.kotlin("finalize"), Namer.ROOT_PACKAGE_NAME_REF).asStatement());
        }
        else {
            result.addAll(initializers);
        }
    }

    private static void translate(
            @NotNull NamespaceDescriptor descriptor,
            @NotNull JetFile file,
            @NotNull List<JsStatement> result,
            @Nullable List<JsStatement> ecma3Initializers,
            @NotNull TranslationContext context
    ) {
        final FileDeclarationVisitor visitor = new FileDeclarationVisitor(context);
        context.literalFunctionTranslator().setDefinitionPlace(new NotNullLazyValue<GenerationPlace>() {
            @Override
            @NotNull
            public GenerationPlace compute() {
                return visitor.createGenerationPlace();
            }
        });

        for (JetDeclaration declaration : file.getDeclarations()) {
            DeclarationDescriptor declarationDescriptor =
                    BindingContextUtils.getNotNull(context.bindingContext(), BindingContext.DECLARATION_TO_DESCRIPTOR, declaration);
            if (!context.predefinedAnnotationManager().isNativeNotRecursive(declarationDescriptor)) {
                declaration.accept(visitor);
            }
        }
        context.literalFunctionTranslator().popDefinitionPlace();

        // own package always JsNameRef (only inter-module references may be JsInvocation)
        JsNameRef packageQualifiedName = (JsNameRef) context.getQualifiedReference(descriptor);
        JsExpression initializer;
        if (visitor.finalizeInitializerStatements()) {
            initializer = null;
        }
        else {
            initializer = visitor.getInitializer();
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
            defineArguments.add(new JsStringLiteral(packageQualifiedName.getName()));
        }
        if (context.isEcma5()) {
            defineArguments.add(initializer == null ? JsLiteral.NULL : initializer);
            defineArguments.add(new JsDocComment(JsAstUtils.LENDS_JS_DOC_TAG, packageQualifiedName));
        }
        defineArguments.add(new JsObjectLiteral(visitor.getResult(), true));
        result.add(new JsDocComment("name", packageQualifiedName));
        result.add(new JsInvocation(Namer.DEFINE_PACKAGE, defineArguments).asStatement());
    }
}