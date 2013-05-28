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
import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jet.lang.psi.*;
import org.jetbrains.jet.lang.resolve.BindingContext;
import org.jetbrains.jet.lang.resolve.name.Name;
import org.jetbrains.jet.lang.types.JetType;
import org.jetbrains.jet.lexer.JetTokens;
import org.jetbrains.k2js.translate.context.TranslationContext;
import org.jetbrains.k2js.translate.general.Translation;
import org.jetbrains.k2js.translate.intrinsic.functions.factories.TopLevelFIF;
import org.jetbrains.k2js.translate.intrinsic.functions.patterns.NamePredicate;

import java.util.Collections;

import static org.jetbrains.k2js.translate.utils.JsAstUtils.sum;
import static org.jetbrains.k2js.translate.utils.JsDescriptorUtils.getNameIfStandardType;

final class StringTemplateVisitor extends JetVisitorVoid {
    @Nullable
    private JsExpression result;
    private final TranslationContext context;
    private final char quote;

    private JsExpression notStringifiedExpression;

    StringTemplateVisitor(JetStringTemplateExpression expression, TranslationContext context) {
        this.context = context;

        ASTNode quoteNode = expression.getFirstChild().getNode();
        assert quoteNode.getElementType() == JetTokens.OPEN_QUOTE;
        CharSequence quoteChars = quoteNode.getChars();
        this.quote = quoteChars.length() == 1 ? quoteChars.charAt(0) : JsStringLiteral.UNESCAPED;
    }

    @NotNull
    JsExpression prepareResult(JetStringTemplateExpression expression) {
        if (result == null) {
            return JsStringLiteral.EMPTY;
        }
        else if (notStringifiedExpression == result) {
            // it is only one expression in string template, we must call toString() explicitly
            return toStringInvocation(notStringifiedExpression).source(expression);
        }
        else {
            return result.source(expression);
        }
    }

    private void append(@NotNull JsExpression expression) {
        if (result == null) {
            result = expression;
        }
        else {
            result = sum(result, expression);
        }
    }

    @Override
    public void visitStringTemplateEntryWithExpression(@NotNull JetStringTemplateEntryWithExpression entry) {
        JetExpression entryExpression = entry.getExpression();
        assert entryExpression != null :
                "JetStringTemplateEntryWithExpression must have not null entry expression.";
        JsExpression translatedExpression = Translation.translateAsExpression(entryExpression, context);
        if (translatedExpression instanceof JsNumberLiteral) {
            append(new JsStringLiteral(translatedExpression.toString()));
            return;
        }
        else if (translatedExpression instanceof JsStringLiteral) {
            append(translatedExpression);
            return;
        }

        JetType type = context.bindingContext().get(BindingContext.EXPRESSION_TYPE, entryExpression);
        if (type == null || type.isNullable()) {
            append(TopLevelFIF.STRINGIFY.apply((JsExpression) null, Collections.singletonList(translatedExpression), context));
        }
        else if (mustCallToString(type, translatedExpression)) {
            append(toStringInvocation(translatedExpression));
        }
        else {
            append(translatedExpression);
        }
    }

    private static JsInvocation toStringInvocation(JsExpression translatedExpression) {
        return new JsInvocation(new JsNameRef("toString", translatedExpression));
    }

    @Override
    public void visitStringTemplateEntry(@NotNull JetStringTemplateEntry entry) {
        append(new JsStringLiteral(entry.getNode().getChars(), quote));
    }

    private boolean mustCallToString(@NotNull JetType type, JsExpression translatedExpression) {
        Name typeName = getNameIfStandardType(type);
        if (typeName != null) {
            if (typeName.asString().equals("String")) {
                return false;
            }
            else if (NamePredicate.PRIMITIVE_NUMBERS.apply(typeName)) {
                return result == null;
            }
        }

        notStringifiedExpression = translatedExpression;
        return false;
    }
}