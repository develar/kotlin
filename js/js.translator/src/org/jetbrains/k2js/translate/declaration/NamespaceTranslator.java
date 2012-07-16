/*
 * Copyright 2010-2012 JetBrains s.r.o.
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
import org.jetbrains.jet.lang.descriptors.NamespaceDescriptor;
import org.jetbrains.jet.lang.descriptors.NamespaceDescriptorParent;
import org.jetbrains.jet.lang.psi.*;
import org.jetbrains.k2js.translate.context.TranslationContext;
import org.jetbrains.k2js.translate.general.AbstractTranslator;
import org.jetbrains.k2js.translate.initializer.InitializerVisitor;
import org.jetbrains.k2js.translate.utils.AnnotationsUtils;
import org.jetbrains.k2js.translate.utils.BindingUtils;
import org.jetbrains.k2js.translate.utils.JsAstUtils;
import org.jetbrains.k2js.translate.utils.TranslationUtils;

import java.util.List;
import java.util.Map;

/**
 * @author Pavel.Talanov
 */
final class NamespaceTranslator extends AbstractTranslator {
    @NotNull
    private final NamespaceDescriptor descriptor;
    @NotNull
    private final ClassDeclarationTranslator classDeclarationTranslator;

    private final FileDeclarationVisitor visitor;

    NamespaceTranslator(@NotNull NamespaceDescriptor descriptor,
            @NotNull ClassDeclarationTranslator classDeclarationTranslator,
            @NotNull TranslationContext context) {
        super(context.newDeclaration(descriptor));
        this.descriptor = descriptor;
        this.classDeclarationTranslator = classDeclarationTranslator;

        visitor = new FileDeclarationVisitor();
    }

    private void addToParent(Map<NamespaceDescriptorParent, JsObjectLiteral> descriptorToDeclarationPlace,
            NamespaceDescriptorParent descriptor,
            JsPropertyInitializer entry) {
        JsObjectLiteral parentPlace = descriptorToDeclarationPlace.get(descriptor);
        if (parentPlace != null) {
            parentPlace.getPropertyInitializers().add(entry);
            return;
        }

        while (true) {
            JsObjectLiteral place = new JsObjectLiteral(new SmartList<JsPropertyInitializer>(entry), true);
            entry = new JsPropertyInitializer(context().getNameForDescriptor(descriptor).makeRef(), toDataDescriptor(new JsInvocation(context().namer().packageDefinitionMethodReference(), place)));

            descriptorToDeclarationPlace.put(descriptor, place);

            descriptor = (NamespaceDescriptorParent) descriptor.getContainingDeclaration();
            assert descriptor != null;
            if ((parentPlace = descriptorToDeclarationPlace.get(descriptor)) != null) {
                parentPlace.getPropertyInitializers().add(entry);
                return;
            }
        }
    }

    public void translate(JetFile file) {
        for (JetDeclaration declaration : file.getDeclarations()) {
            if (AnnotationsUtils.isNativeObject(BindingUtils.getDescriptorForElement(bindingContext(), declaration))) {
                continue;
            }

            declaration.accept(visitor, context());
        }
    }

    public void add(@NotNull Map<NamespaceDescriptorParent, JsObjectLiteral> descriptorToDeclarationPlace,
            @NotNull List<JsExpression> initializers) {
        List<JsStatement> initializerStatements = visitor.initializerVisitor.getResult();
        if (!initializerStatements.isEmpty()) {
            visitor.initializer.getBody().getStatements().addAll(initializerStatements);
            initializers.add(new JsInvocation(new JsNameRef("call", visitor.initializer),
                                              TranslationUtils.getQualifiedReference(context(), descriptor)));
        }

        JsObjectLiteral place = descriptorToDeclarationPlace.get(descriptor);
        if (place == null) {
            place = new JsObjectLiteral(visitor.getResult(), true);
            descriptorToDeclarationPlace.put(descriptor, place);
            addToParent(descriptorToDeclarationPlace, descriptor.getContainingDeclaration(),
                        new JsPropertyInitializer(context().getNameForDescriptor(descriptor).makeRef(), toDataDescriptor(
                                new JsInvocation(context().namer().packageDefinitionMethodReference(), place))));
        }
        else {
            place.getPropertyInitializers().addAll(visitor.getResult());
        }
    }

    private class FileDeclarationVisitor extends DeclarationBodyVisitor {
        private final JsFunction initializer;
        private final TranslationContext initializerContext;
        private final InitializerVisitor initializerVisitor = new InitializerVisitor();

        private FileDeclarationVisitor() {
            initializer = JsAstUtils.createFunctionWithEmptyBody(context().scope());
            initializerContext = context().contextWithScope(initializer);
        }

        @Override
        public Void visitClass(@NotNull JetClass expression, @NotNull TranslationContext context) {
            result.add(classDeclarationTranslator.translate(expression));
            return null;
        }

        @Override
        public Void visitProperty(@NotNull JetProperty property, @NotNull TranslationContext context) {
            super.visitProperty(property, context);
            property.accept(initializerVisitor, initializerContext);
            return null;
        }

        @Override
        public Void visitAnonymousInitializer(@NotNull JetClassInitializer expression, @NotNull TranslationContext context) {
            expression.accept(initializerVisitor, initializerContext);
            return null;
        }

        @Override
        public Void visitObjectDeclaration(@NotNull JetObjectDeclaration expression,
                @NotNull TranslationContext context) {
            result.add(classDeclarationTranslator.translate(expression));
            return null;
        }
    }
}
