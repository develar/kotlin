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

package org.jetbrains.k2js.translate.utils.dangerous;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jet.lang.psi.*;
import org.jetbrains.k2js.translate.context.TranslationContext;
import org.jetbrains.k2js.translate.reference.InlinedCallExpressionTranslator;

import java.util.Collections;
import java.util.List;

import static org.jetbrains.k2js.translate.utils.BindingUtils.isStatement;

/**
 * This module uses a metaphor for naming.
 * <p/>
 * Dangerous are the nodes that can be expressions in Kotlin but can't be expressions in JavaScript.
 * These are: when, if, inlined functions.
 * The issue with them is that we have to translate them to a list of statements. And also all the expressions which must be computed before
 * the dangerous expressions.
 * RootNode is a node which contains such an expression. For example, it may be a statement expression belongs to.
 */
public final class FindDangerousVisitor extends JetTreeVisitor<TranslationContext> {
    private JetExpression dangerousNode;

    @NotNull
    public static List<JetExpression> collect(@NotNull JetExpression expression, @NotNull TranslationContext context) {
        if (cannotContainDangerousElements(expression)) {
            return Collections.emptyList();
        }

        FindDangerousVisitor visitor = new FindDangerousVisitor();
        expression.accept(visitor, context);
        if (visitor.dangerousNode == null) {
            return Collections.emptyList();
        }

        FindPreviousVisitor previousVisitor = new FindPreviousVisitor(expression, visitor.dangerousNode);
        expression.accept(previousVisitor, visitor.dangerousNode);
        return previousVisitor.nodesToBeGeneratedBefore;
    }

    private static boolean cannotContainDangerousElements(JetExpression element) {
        return element instanceof JetBlockExpression || element instanceof JetFunctionLiteralExpression || element instanceof JetConstantExpression;
    }

    @Override
    public Void visitDeclaration(JetDeclaration dcl, TranslationContext context) {
        return null;
    }

    @Override
    public Void visitJetElement(JetElement element, TranslationContext context) {
        if (dangerousNode != null) {
            return null;
        }
        return super.visitJetElement(element, context);
    }

    @Override
    public Void visitWhenExpression(JetWhenExpression expression, TranslationContext context) {
        if (expressionFound(expression, context)) {
            return null;
        }
        return super.visitWhenExpression(expression, context);
    }

    @Override
    public Void visitIfExpression(JetIfExpression expression, TranslationContext context) {
        if (expressionFound(expression, context)) {
            return null;
        }
        return super.visitIfExpression(expression, context);
    }

    @Override
    public Void visitBlockExpression(JetBlockExpression expression, TranslationContext context) {
        if (isStatement(context.bindingContext(), expression)) {
            return null;
        }
        else {
            return super.visitBlockExpression(expression, context);
        }
    }

    @Override
    public Void visitCallExpression(JetCallExpression expression, TranslationContext context) {
        if (InlinedCallExpressionTranslator.shouldBeInlined(expression, context)) {
            if (expressionFound(expression, context)) {
                return null;
            }
        }
        return super.visitCallExpression(expression, context);
    }

    private boolean expressionFound(@NotNull JetExpression expression, TranslationContext context) {
        if (dangerousNode != null) {
            return true;
        }
        if (!isStatement(context.bindingContext(), expression)) {
            dangerousNode = expression;
            return true;
        }
        return false;
    }
}
