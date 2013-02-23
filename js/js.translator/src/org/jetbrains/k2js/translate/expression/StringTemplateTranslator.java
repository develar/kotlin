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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jet.lang.psi.*;
import org.jetbrains.jet.lang.resolve.BindingContext;
import org.jetbrains.jet.lang.resolve.name.Name;
import org.jetbrains.jet.lang.types.JetType;
import org.jetbrains.k2js.translate.context.TranslationContext;
import org.jetbrains.k2js.translate.general.Translation;
import org.jetbrains.k2js.translate.intrinsic.functions.factories.TopLevelFIF;
import org.jetbrains.k2js.translate.intrinsic.functions.patterns.NamePredicate;

import java.util.Collections;

import static org.jetbrains.k2js.translate.utils.ErrorReportingUtils.message;
import static org.jetbrains.k2js.translate.utils.JsAstUtils.sum;
import static org.jetbrains.k2js.translate.utils.JsDescriptorUtils.getNameIfStandardType;

final class StringTemplateTranslator extends JetVisitorVoid {
    private JsExpression resultingExpression;
    private final TranslationContext context;
    private final boolean hasOnlyOneExpressionEntry;

    private StringTemplateTranslator(TranslationContext context, boolean hasOnlyOneExpressionEntry) {
        this.context = context;
        this.hasOnlyOneExpressionEntry = hasOnlyOneExpressionEntry;
    }

    @NotNull
    public static JsExpression translate(@NotNull JetStringTemplateExpression expression, @NotNull TranslationContext context) {
        JetStringTemplateEntry[] expressionEntries = expression.getEntries();
        assert expressionEntries.length != 0 : message(expression, "String template must have one or more entries.");
        StringTemplateTranslator entryVisitor = new StringTemplateTranslator(context, expressionEntries.length == 1);
        for (JetStringTemplateEntry entry : expressionEntries) {
            entry.accept(entryVisitor);
        }
        return entryVisitor.resultingExpression;
    }

    private void append(@NotNull JsExpression expression) {
        if (resultingExpression == null) {
            resultingExpression = expression;
        }
        else {
            resultingExpression = sum(resultingExpression, expression);
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

        JetType type = context.bindingContext().get(BindingContext.EXPRESSION_TYPE, entryExpression);
        if (type == null || type.isNullable()) {
            append(TopLevelFIF.STRINGIFY.apply((JsExpression) null, Collections.singletonList(translatedExpression), context));
        }
        else if (mustCallToString(type)) {
            append(new JsInvocation(new JsNameRef("toString", translatedExpression)));
        }
        else {
            append(translatedExpression);
        }
    }

    private boolean mustCallToString(@NotNull JetType type) {
        Name typeName = getNameIfStandardType(type);
        if (typeName != null) {
            if (typeName.getName().equals("String")) {
                return false;
            }
            else if (NamePredicate.PRIMITIVE_NUMBERS.apply(typeName)) {
                return resultingExpression == null;
            }
        }
        return hasOnlyOneExpressionEntry;
    }

    @Override
    public void visitLiteralStringTemplateEntry(@NotNull JetLiteralStringTemplateEntry entry) {
        appendText(entry.getText());
    }

    @Override
    public void visitEscapeStringTemplateEntry(@NotNull JetEscapeStringTemplateEntry entry) {
        appendText(entry.getUnescapedValue());
    }

    private void appendText(@NotNull String text) {
        append(new JsStringLiteral(text));
    }
}