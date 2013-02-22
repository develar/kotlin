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

import com.intellij.psi.PsiElement;
import com.intellij.util.SmartList;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jet.lang.psi.*;

import java.util.List;
import java.util.Set;

import static org.jetbrains.k2js.translate.utils.PsiUtils.getBaseExpression;

public final class FindPreviousVisitor extends JetTreeVisitor<JetExpression> {
    private final Set<JetElement> hasDangerous = new THashSet<JetElement>();
    final List<JetExpression> nodesToBeGeneratedBefore = new SmartList<JetExpression>();

    public FindPreviousVisitor(JetExpression rootNode, JetExpression dangerousNode) {
        JetElement node = dangerousNode;
        PsiElement last = rootNode.getParent();
        while (node != last) {
            hasDangerous.add(node);
            node = (JetElement)node.getParent();
        }
    }

    @Override
    public Void visitJetElement(JetElement element, JetExpression dangerousNode) {
        if (dangerousNode == element) {
            return null;
        }
        if (!hasDangerous.contains(element)) {
            addElement(element);
        }
        else {
            acceptChildrenThatAreBeforeTheDangerousNode(element, dangerousNode);
        }
        return null;
    }

    private void addElement(@NotNull JetElement element) {
        if (element instanceof JetExpression) {
            nodesToBeGeneratedBefore.add((JetExpression) element);
        }
    }

    private void acceptChildrenThatAreBeforeTheDangerousNode(@NotNull JetElement element, JetExpression dangerousNode) {
        PsiElement current = element.getFirstChild();
        while (current != null) {
            if (current instanceof JetElement) {
                ((JetElement)current).accept(this, dangerousNode);
                if (hasDangerous.contains(element)) {
                    break;
                }
            }
            current = current.getNextSibling();
        }
    }

    @Override
    public Void visitPrefixExpression(@NotNull JetPrefixExpression expression, JetExpression dangerousNode) {
        if (dangerousNode == expression) {
            return null;
        }
        if (!hasDangerous.contains(expression)) {
            addElement(expression);
            return null;
        }
        else {
            if (hasDangerous.contains(getBaseExpression(expression))) {
                return null;
            }
            else {
                //TODO:
                throw new IllegalStateException();
            }
        }
    }

    @Override
    public Void visitCallExpression(@NotNull JetCallExpression expression, JetExpression dangerousNode) {
        if (dangerousNode == expression) {
            return null;
        }
        if (!hasDangerous.contains(expression)) {
            nodesToBeGeneratedBefore.add(expression);
        }
        else {
            acceptArgumentsThatAreBeforeDangerousNode(expression, dangerousNode);
        }
        return null;
    }

    private void acceptArgumentsThatAreBeforeDangerousNode(@NotNull JetCallExpression expression, JetExpression dangerousNode) {
        for (ValueArgument argument : expression.getValueArguments()) {
            JetExpression argumentExpression = argument.getArgumentExpression();
            assert argumentExpression != null;
            argumentExpression.accept(this, dangerousNode);
            if (hasDangerous.contains(argumentExpression)) {
                break;
            }
        }
    }
}