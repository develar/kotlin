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
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jet.lang.descriptors.NamespaceDescriptor;
import org.jetbrains.jet.lang.descriptors.NamespaceDescriptorParent;
import org.jetbrains.jet.lang.psi.JetClass;
import org.jetbrains.jet.lang.psi.JetClassInitializer;
import org.jetbrains.jet.lang.psi.JetDeclaration;
import org.jetbrains.jet.lang.psi.JetFile;
import org.jetbrains.jet.lang.resolve.DescriptorUtils;
import org.jetbrains.k2js.translate.context.TranslationContext;
import org.jetbrains.k2js.translate.general.AbstractTranslator;
import org.jetbrains.k2js.translate.initializer.InitializerVisitor;
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
    private final JsName namespaceName;
    @NotNull
    private final ClassDeclarationTranslator classDeclarationTranslator;

    @NotNull
    private final List<JsExpression> initializers;
    private final Map<NamespaceDescriptorParent, JsObjectLiteral> descriptorToDeclarationPlace;

    private final FileDeclarationVisitor visitor;

    /*package*/ NamespaceTranslator(@NotNull NamespaceDescriptor descriptor,
            @NotNull ClassDeclarationTranslator classDeclarationTranslator,
            @NotNull TranslationContext context,
            @NotNull List<JsExpression> initializers,
            Map<NamespaceDescriptorParent, JsObjectLiteral> descriptorToDeclarationPlace) {
        super(context.newDeclaration(descriptor));
        this.descriptor = descriptor;
        this.initializers = initializers;
        this.descriptorToDeclarationPlace = descriptorToDeclarationPlace;
        this.namespaceName = context.getNameForDescriptor(descriptor);
        this.classDeclarationTranslator = classDeclarationTranslator;

        visitor = new FileDeclarationVisitor();
    }

    //@NotNull
    //private JsPropertyInitializer getDeclarationAsInitializer() {
    //    addNamespaceInitializer();
    //    return new JsPropertyInitializer(namespaceName.makeRef(), toDataDescriptor(getNamespaceDeclaration()));
    //}

    public void addNamespaceDeclaration(List<JsPropertyInitializer> list) {
        List<JsStatement> initializerStatements = visitor.initializerVisitor.getResult();
        if (!initializerStatements.isEmpty()) {
            visitor.initializer.getBody().getStatements().addAll(initializerStatements);
            initializers.add(new JsInvocation(new JsNameRef("call", visitor.initializer),
                                              TranslationUtils.getQualifiedReference(context(), descriptor)));
        }

        if (DescriptorUtils.isRootNamespace(descriptor)) {
            list.addAll(visitor.getResult());
            return;
        }

        list.add(new JsPropertyInitializer(namespaceName.makeRef(), toDataDescriptor(getNamespaceDeclaration())));
    }

    @NotNull
    private JsInvocation getNamespaceDeclaration() {
        JsObjectLiteral objectLiteral = descriptorToDeclarationPlace.get(descriptor);
        if (objectLiteral == null) {
            objectLiteral = new JsObjectLiteral();
            descriptorToDeclarationPlace.put(descriptor, objectLiteral);
            addPlaceToParent(objectLiteral, descriptor.getContainingDeclaration());
        }

        List<JsPropertyInitializer> items = visitor.getResult();
        return new JsInvocation(context().namer().packageDefinitionMethodReference(),
                                items.isEmpty() ? JsLiteral.NULL : new JsObjectLiteral(items, true));
    }

    private void addPlaceToParent(
            NamespaceDescriptorParent parent, JsObjectLiteral child, JsObjectLiteral root) {
        JsObjectLiteral place = descriptorToDeclarationPlace.get(parent);
        if (place != null) {
            place.getPropertyInitializers().add(new JsPropertyInitializer(namespaceName.makeRef(), toDataDescriptor(getNamespaceDeclaration())));
            return;
        }

        place = new JsObjectLiteral();
        place.getPropertyInitializers().add(child);
    }


    public void translate(JetFile file) {
        for (JetDeclaration declaration : file.getDeclarations()) {
            declaration.accept(visitor, context());
        }
    }

    private class FileDeclarationVisitor extends DeclarationBodyVisitor {
        private final JsFunction initializer;
        private final TranslationContext initializerContext;
        private InitializerVisitor initializerVisitor = new InitializerVisitor();

        private FileDeclarationVisitor() {
            initializer = JsAstUtils.createFunctionWithEmptyBody(context().scope());
            initializerContext = context().contextWithScope(initializer);
        }

        @Override
        public Void visitClass(@NotNull JetClass expression, @NotNull TranslationContext context) {
            result.add(classDeclarationTranslator.translateAndGetRef(expression));
            return null;
        }

        @Override
        public Void visitAnonymousInitializer(@NotNull JetClassInitializer expression, @NotNull TranslationContext context) {
            initializerVisitor = new InitializerVisitor();
            expression.accept(initializerVisitor, initializerContext);
            return null;
        }
    }
}
