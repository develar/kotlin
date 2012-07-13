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
import org.jetbrains.jet.lang.resolve.DescriptorUtils;
import org.jetbrains.k2js.translate.context.TranslationContext;
import org.jetbrains.k2js.translate.general.AbstractTranslator;
import org.jetbrains.k2js.translate.general.Translation;
import org.jetbrains.k2js.translate.utils.JsAstUtils;
import org.jetbrains.k2js.translate.utils.JsDescriptorUtils;
import org.jetbrains.k2js.translate.utils.TranslationUtils;

import java.util.List;

/**
 * @author Pavel.Talanov
 *         <p/>
 *         Genereate code for a single descriptor.
 */
public final class NamespaceTranslator extends AbstractTranslator {
    @NotNull
    private final NamespaceDescriptor descriptor;
    @NotNull
    private final JsName namespaceName;
    @NotNull
    private final ClassDeclarationTranslator classDeclarationTranslator;

    @NotNull
    private final List<JsExpression> initializers = new SmartList<JsExpression>();

    /*package*/ NamespaceTranslator(@NotNull NamespaceDescriptor descriptor,
                                    @NotNull ClassDeclarationTranslator classDeclarationTranslator,
                                    @NotNull TranslationContext context) {
        super(context.newDeclaration(descriptor));
        this.descriptor = descriptor;
        this.namespaceName = context.getNameForDescriptor(descriptor);
        this.classDeclarationTranslator = classDeclarationTranslator;
    }

    @NotNull
    public List<JsExpression> getInitializers() {
        return initializers;
    }

    @NotNull
    private JsPropertyInitializer getDeclarationAsInitializer() {
        addNamespaceInitializer();
        return new JsPropertyInitializer(namespaceName.makeRef(), toDataDescriptor(getNamespaceDeclaration()));
    }

    public void addNamespaceDeclaration(List<JsPropertyInitializer> list) {
        addNamespaceInitializer();

        if (DescriptorUtils.isRootNamespace(descriptor)) {
            list.addAll(getFunctionsAndClasses());
            return;
        }

        JsExpression value = getNamespaceDeclaration();
        if (context().isNotEcma3()) {
            value = JsAstUtils.createDataDescriptor(value, false);
        }

        list.add(new JsPropertyInitializer(namespaceName.makeRef(), value));
    }

    @NotNull
    private JsInvocation getNamespaceDeclaration() {
        List<JsPropertyInitializer> items = getFunctionsAndClasses();
        getNestedNamespaceDeclarations(items);
        return new JsInvocation(context().namer().packageDefinitionMethodReference(),
                                items.isEmpty() ? JsLiteral.NULL : new JsObjectLiteral(items, true));
    }

    private void addNamespaceInitializer() {
        JsFunction initializer = Translation.generateNamespaceInitializerMethod(descriptor, context());
        if (!initializer.getBody().getStatements().isEmpty()) {
            initializers.add(new JsInvocation(new JsNameRef("call", initializer),
                                              TranslationUtils.getQualifiedReference(context(), descriptor)));
        }
    }

    private List<JsPropertyInitializer> getFunctionsAndClasses() {
        return new DeclarationBodyVisitor(classDeclarationTranslator).traverseNamespace(descriptor, context());
    }

    @NotNull
    private List<JsPropertyInitializer> getNestedNamespaceDeclarations(List<JsPropertyInitializer> result) {
        List<NamespaceDescriptor> nestedNamespaces = JsDescriptorUtils.getNestedNamespaces(descriptor, context().bindingContext());
        for (NamespaceDescriptor nestedNamespace : nestedNamespaces) {
            NamespaceTranslator nestedNamespaceTranslator = new NamespaceTranslator(nestedNamespace, classDeclarationTranslator, context());
            result.add(nestedNamespaceTranslator.getDeclarationAsInitializer());

            initializers.addAll(nestedNamespaceTranslator.getInitializers());
        }
        return result;
    }
}
