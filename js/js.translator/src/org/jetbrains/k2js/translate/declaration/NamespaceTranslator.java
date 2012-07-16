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
import com.intellij.util.Processor;
import com.intellij.util.SmartList;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jet.lang.descriptors.*;
import org.jetbrains.jet.lang.psi.*;
import org.jetbrains.jet.lang.resolve.BindingContext;
import org.jetbrains.k2js.translate.context.TranslationContext;
import org.jetbrains.k2js.translate.general.AbstractTranslator;
import org.jetbrains.k2js.translate.general.Translation;
import org.jetbrains.k2js.translate.initializer.InitializerUtils;
import org.jetbrains.k2js.translate.initializer.InitializerVisitor;
import org.jetbrains.k2js.translate.utils.AnnotationsUtils;
import org.jetbrains.k2js.translate.utils.BindingUtils;
import org.jetbrains.k2js.translate.utils.JsAstUtils;
import org.jetbrains.k2js.translate.utils.TranslationUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.jetbrains.k2js.translate.initializer.InitializerUtils.generateInitializerForProperty;
import static org.jetbrains.k2js.translate.utils.BindingUtils.getClassDescriptor;
import static org.jetbrains.k2js.translate.utils.BindingUtils.getPropertyDescriptor;

/**
 * @author Pavel.Talanov
 */
final class NamespaceTranslator extends AbstractTranslator implements Comparable<NamespaceTranslator> {
    @NotNull
    private final NamespaceDescriptor descriptor;
    @NotNull
    private final ClassDeclarationTranslator classDeclarationTranslator;

    private final FileDeclarationVisitor visitor;

    private final Set<NamespaceDescriptor> usedNamespaces = new THashSet<NamespaceDescriptor>();

    NamespaceTranslator(@NotNull NamespaceDescriptor descriptor,
            @NotNull ClassDeclarationTranslator classDeclarationTranslator,
            @NotNull TranslationContext context) {
        super(context.newDeclaration(descriptor));
        this.descriptor = descriptor;
        this.classDeclarationTranslator = classDeclarationTranslator;

        visitor = new FileDeclarationVisitor();
    }

    @Override
    public int compareTo(NamespaceTranslator o) {
        return usedNamespaces.contains(o.descriptor) ? 1 : (o.usedNamespaces.contains(descriptor) ? -1 : 0);
    }

    public void translate(JetFile file) {
        for (JetDeclaration declaration : file.getDeclarations()) {
            if (!AnnotationsUtils.isNativeObject(BindingUtils.getDescriptorForElement(bindingContext(), declaration))) {
                declaration.accept(visitor, context());
            }
        }
    }

    public void add(@NotNull Map<NamespaceDescriptor, JsObjectLiteral> descriptorToDeclarationPlace,
            @NotNull List<JsExpression> initializers) {
        if (!visitor.initializerStatements.isEmpty()) {
            visitor.initializer.getBody().getStatements().addAll(visitor.initializerStatements);
            initializers.add(new JsInvocation(new JsNameRef("call", visitor.initializer),
                                              TranslationUtils.getQualifiedReference(context(), descriptor)));
        }

        JsObjectLiteral place = descriptorToDeclarationPlace.get(descriptor);
        if (place == null) {
            place = new JsObjectLiteral(visitor.getResult(), true);
            descriptorToDeclarationPlace.put(descriptor, place);
            addToParent((NamespaceDescriptor) descriptor.getContainingDeclaration(), getEntry(descriptor, place),
                        descriptorToDeclarationPlace);
        }
        else {
            place.getPropertyInitializers().addAll(visitor.getResult());
        }
    }

    private JsPropertyInitializer getEntry(NamespaceDescriptor descriptor, JsObjectLiteral place) {
        return new JsPropertyInitializer(context().getNameForDescriptor(descriptor).makeRef(),
                                         toDataDescriptor(new JsInvocation(context().namer().packageDefinitionMethodReference(), place)));
    }

    private static boolean addEntryIfParentExists(NamespaceDescriptor parentDescriptor,
            JsPropertyInitializer entry,
            Map<NamespaceDescriptor, JsObjectLiteral> descriptorToDeclarationPlace) {
        JsObjectLiteral parentPlace = descriptorToDeclarationPlace.get(parentDescriptor);
        if (parentPlace != null) {
            parentPlace.getPropertyInitializers().add(entry);
            return true;
        }
        return false;
    }

    private void addToParent(NamespaceDescriptor parentDescriptor,
            JsPropertyInitializer entry,
            Map<NamespaceDescriptor, JsObjectLiteral> descriptorToDeclarationPlace) {
        while (!addEntryIfParentExists(parentDescriptor, entry, descriptorToDeclarationPlace)) {
            JsObjectLiteral place = new JsObjectLiteral(new SmartList<JsPropertyInitializer>(entry), true);
            entry = getEntry(parentDescriptor, place);

            descriptorToDeclarationPlace.put(parentDescriptor, place);
            parentDescriptor = (NamespaceDescriptor) parentDescriptor.getContainingDeclaration();
        }
    }

    private class FileDeclarationVisitor extends DeclarationBodyVisitor {
        private final JsFunction initializer;
        private final TranslationContext initializerContext;
        private final List<JsStatement> initializerStatements = new SmartList<JsStatement>();
        private final InitializerVisitor initializerVisitor = new InitializerVisitor(initializerStatements);
        private final OwnNamespaceRefTracer ownNamespaceRefTracer;

        private FileDeclarationVisitor() {
            initializer = JsAstUtils.createFunctionWithEmptyBody(context().scope());
            ownNamespaceRefTracer = new OwnNamespaceRefTracer();
            initializerContext = context().contextWithScope(initializer).nameTracer(ownNamespaceRefTracer);
        }

        @Override
        public Void visitClass(@NotNull JetClass expression, @NotNull TranslationContext context) {
            JsPropertyInitializer value = classDeclarationTranslator.translate(expression);
            result.add(value);
            return null;
        }

        @Override
        public Void visitObjectDeclaration(@NotNull JetObjectDeclaration declaration, @NotNull TranslationContext context) {
            ownNamespaceRefTracer.hasReference = false;
            ClassDescriptor descriptor = getClassDescriptor(context.bindingContext(), declaration);
            JsExpression value = ClassTranslator.generateClassCreation(declaration, descriptor, initializerContext);
            String name = descriptor.getName().getName();

            if (ownNamespaceRefTracer.hasReference) {
                initializerStatements.add(InitializerUtils.create(name, value, context));
            }
            else {
                result.add(new JsPropertyInitializer(context.program().getStringLiteral(name), toDataDescriptor(value)));
            }
            return null;
        }

        @Override
        public Void visitProperty(@NotNull JetProperty property, @NotNull TranslationContext context) {
            super.visitProperty(property, context);
            JetExpression initializer = property.getInitializer();
            if (initializer != null) {
                ownNamespaceRefTracer.hasReference = false;
                JsExpression value = Translation.translateAsExpression(initializer, initializerContext);
                PropertyDescriptor propertyDescriptor = getPropertyDescriptor(context.bindingContext(), property);
                if (!ownNamespaceRefTracer.hasReference) {
                    result.add(new JsPropertyInitializer(context.getNameForDescriptor(propertyDescriptor).makeRef(),
                                                         context().isEcma5() ? JsAstUtils
                                                                 .createPropertyDataDescriptor(propertyDescriptor, value, context) : value));
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

        private class OwnNamespaceRefTracer implements Processor<DeclarationDescriptor> {
            private boolean hasReference;

            @Override
            public boolean process(DeclarationDescriptor declarationDescriptor) {
                NamespaceDescriptor namespaceDescriptor;
                if (declarationDescriptor instanceof NamespaceDescriptor) {
                    namespaceDescriptor = (NamespaceDescriptor) declarationDescriptor;
                }
                else if (declarationDescriptor.getContainingDeclaration() instanceof NamespaceDescriptor) {
                    namespaceDescriptor = (NamespaceDescriptor) declarationDescriptor.getContainingDeclaration();
                }
                else if (declarationDescriptor instanceof ConstructorDescriptor) {
                    DeclarationDescriptor declaration =
                            ((ConstructorDescriptor) declarationDescriptor).getContainingDeclaration().getContainingDeclaration();
                    if (declaration instanceof NamespaceDescriptor) {
                        namespaceDescriptor = (NamespaceDescriptor) declaration;
                    }
                    else {
                        return true;
                    }
                }
                else {
                    return true;
                }

                if (namespaceDescriptor == descriptor) {
                    hasReference = true;
                }
                else if (context().bindingContext().get(BindingContext.NAMESPACE_IS_SRC, namespaceDescriptor) == Boolean.TRUE) {
                    usedNamespaces.add(namespaceDescriptor);
                    hasReference = true;
                }

                return true;
            }
        }
    }
}
