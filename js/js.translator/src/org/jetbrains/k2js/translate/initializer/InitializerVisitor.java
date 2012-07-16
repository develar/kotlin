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

package org.jetbrains.k2js.translate.initializer;

import com.google.dart.compiler.backend.js.ast.JsExpression;
import com.google.dart.compiler.backend.js.ast.JsStatement;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jet.lang.descriptors.PropertyDescriptor;
import org.jetbrains.jet.lang.psi.*;
import org.jetbrains.k2js.translate.context.TranslationContext;
import org.jetbrains.k2js.translate.declaration.ClassTranslator;
import org.jetbrains.k2js.translate.general.Translation;
import org.jetbrains.k2js.translate.general.TranslatorVisitor;

import java.util.List;

import static org.jetbrains.k2js.translate.general.Translation.translateAsStatement;
import static org.jetbrains.k2js.translate.initializer.InitializerUtils.generateInitializerForProperty;
import static org.jetbrains.k2js.translate.utils.BindingUtils.getPropertyDescriptor;
import static org.jetbrains.k2js.translate.utils.BindingUtils.getPropertyDescriptorForObjectDeclaration;
import static org.jetbrains.k2js.translate.utils.PsiUtils.getObjectDeclarationForName;

/**
 * @author Pavel Talanov
 */
public final class InitializerVisitor extends TranslatorVisitor<Void> {
    private final List<JsStatement> result = new SmartList<JsStatement>();

    public List<JsStatement> getResult() {
        return result;
    }

    @Override
    public final Void visitProperty(@NotNull JetProperty property, @NotNull TranslationContext context) {
        JetExpression initializer = property.getInitializer();
        if (initializer != null) {
            result.add(generateInitializerForProperty(context, getPropertyDescriptor(context.bindingContext(), property),
                                                      Translation.translateAsExpression(initializer, context)));
        }

        return null;
    }

    @Override
    public Void visitAnonymousInitializer(@NotNull JetClassInitializer initializer,
            @NotNull TranslationContext context) {
        result.add(translateAsStatement(initializer.getBody(), context));
        return null;
    }

    @Override
    // Not interested in other types of declarations, they do not contain initializers.
    public Void visitDeclaration(@NotNull JetDeclaration expression, @NotNull TranslationContext context) {
        return null;
    }

    @Override
    public final Void visitObjectDeclarationName(@NotNull JetObjectDeclarationName objectName, @NotNull TranslationContext context) {
        PropertyDescriptor propertyDescriptor = getPropertyDescriptorForObjectDeclaration(context.bindingContext(), objectName);
        JetObjectDeclaration objectDeclaration = getObjectDeclarationForName(objectName);
        JsExpression objectValue = ClassTranslator.generateClassCreationExpression(objectDeclaration, context);
        result.add(generateInitializerForProperty(context, propertyDescriptor, objectValue));
        return null;
    }

    @NotNull
    private List<JsStatement> generateInitializerStatements(@NotNull List<JetDeclaration> declarations,
            @NotNull TranslationContext context) {
        for (JetDeclaration declaration : declarations) {
            declaration.accept(this, context);
        }
        return result;
    }

    @NotNull
    public final List<JsStatement> traverseClass(@NotNull JetClassOrObject expression, @NotNull TranslationContext context) {
        return generateInitializerStatements(expression.getDeclarations(), context);
    }
}
