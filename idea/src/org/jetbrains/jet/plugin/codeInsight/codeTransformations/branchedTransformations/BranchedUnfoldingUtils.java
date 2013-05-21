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

package org.jetbrains.jet.plugin.codeInsight.codeTransformations.branchedTransformations;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jet.lang.psi.*;

public class BranchedUnfoldingUtils {
    private BranchedUnfoldingUtils() {
    }

    private static JetExpression getOutermostLastBlockElement(@Nullable JetExpression expression) {
        return (JetExpression) JetPsiUtil.getOutermostLastBlockElement(expression, JetPsiUtil.ANY_JET_ELEMENT);
    }

    @Nullable
    public static UnfoldableKind getUnfoldableExpressionKind(@Nullable JetExpression root) {
        if (root == null) return null;

        if (JetPsiUtil.isAssignment(root)) {
            JetBinaryExpression assignment = (JetBinaryExpression)root;

            assertNotNull(assignment.getLeft());

            JetExpression rhs = assignment.getRight();
            if (rhs instanceof JetIfExpression) return UnfoldableKind.ASSIGNMENT_TO_IF;
            if (rhs instanceof JetWhenExpression && JetPsiUtil.checkWhenExpressionHasSingleElse((JetWhenExpression) rhs)) {
                return UnfoldableKind.ASSIGNMENT_TO_WHEN;
            }
        } else if (root instanceof JetReturnExpression) {
            JetExpression resultExpr = ((JetReturnExpression)root).getReturnedExpression();

            if (resultExpr instanceof JetIfExpression) return UnfoldableKind.RETURN_TO_IF;
            if (resultExpr instanceof JetWhenExpression && JetPsiUtil.checkWhenExpressionHasSingleElse((JetWhenExpression) resultExpr)) {
                return UnfoldableKind.RETURN_TO_WHEN;
            }
        }

        return null;
    }

    public static final String UNFOLD_WITHOUT_CHECK = "Expression must be checked before unfolding";

    private static void assertNotNull(JetExpression expression) {
        assert expression != null : UNFOLD_WITHOUT_CHECK;
    }

    public static void unfoldAssignmentToIf(@NotNull JetBinaryExpression assignment, @NotNull Editor editor) {
        Project project = assignment.getProject();
        String op = assignment.getOperationReference().getText();
        JetExpression lhs = assignment.getLeft();
        JetIfExpression ifExpression = (JetIfExpression)assignment.getRight();

        assertNotNull(ifExpression);

        //noinspection ConstantConditions
        JetIfExpression newIfExpression = (JetIfExpression) ifExpression.copy();

        JetExpression thenExpr = getOutermostLastBlockElement(newIfExpression.getThen());
        JetExpression elseExpr = getOutermostLastBlockElement(newIfExpression.getElse());

        assertNotNull(thenExpr);
        assertNotNull(elseExpr);

        //noinspection ConstantConditions
        thenExpr.replace(JetPsiFactory.createBinaryExpression(project, lhs, op, thenExpr));
        elseExpr.replace(JetPsiFactory.createBinaryExpression(project, lhs, op, elseExpr));

        PsiElement resultElement = assignment.replace(newIfExpression);

        editor.getCaretModel().moveToOffset(resultElement.getTextOffset());
    }

    public static void unfoldAssignmentToWhen(@NotNull JetBinaryExpression assignment, @NotNull Editor editor) {
        Project project = assignment.getProject();
        String op = assignment.getOperationReference().getText();
        JetExpression lhs = assignment.getLeft();
        JetWhenExpression whenExpression = (JetWhenExpression)assignment.getRight();

        assertNotNull(whenExpression);

        //noinspection ConstantConditions
        JetWhenExpression newWhenExpression = (JetWhenExpression) whenExpression.copy();

        for (JetWhenEntry entry : newWhenExpression.getEntries()) {
            JetExpression currExpr = getOutermostLastBlockElement(entry.getExpression());

            assertNotNull(currExpr);

            //noinspection ConstantConditions
            currExpr.replace(JetPsiFactory.createBinaryExpression(project, lhs, op, currExpr));
        }

        PsiElement resultElement = assignment.replace(newWhenExpression);

        editor.getCaretModel().moveToOffset(resultElement.getTextOffset());
    }

    public static void unfoldReturnToIf(@NotNull JetReturnExpression returnExpression) {
        Project project = returnExpression.getProject();
        JetIfExpression ifExpression = (JetIfExpression)returnExpression.getReturnedExpression();

        assertNotNull(ifExpression);

        //noinspection ConstantConditions
        JetIfExpression newIfExpression = (JetIfExpression) ifExpression.copy();

        JetExpression thenExpr = getOutermostLastBlockElement(newIfExpression.getThen());
        JetExpression elseExpr = getOutermostLastBlockElement(newIfExpression.getElse());

        assertNotNull(thenExpr);
        assertNotNull(elseExpr);

        thenExpr.replace(JetPsiFactory.createReturn(project, thenExpr));
        elseExpr.replace(JetPsiFactory.createReturn(project, elseExpr));

        returnExpression.replace(newIfExpression);
    }

    public static void unfoldReturnToWhen(@NotNull JetReturnExpression returnExpression) {
        Project project = returnExpression.getProject();
        JetWhenExpression whenExpression = (JetWhenExpression)returnExpression.getReturnedExpression();

        assertNotNull(whenExpression);

        //noinspection ConstantConditions
        JetWhenExpression newWhenExpression = (JetWhenExpression) whenExpression.copy();

        for (JetWhenEntry entry : newWhenExpression.getEntries()) {
            JetExpression currExpr = getOutermostLastBlockElement(entry.getExpression());

            assertNotNull(currExpr);

            currExpr.replace(JetPsiFactory.createReturn(project, currExpr));
        }

        returnExpression.replace(newWhenExpression);
    }
}
