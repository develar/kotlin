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

package org.jetbrains.jet.lang.psi.stubs.elements;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jet.lang.psi.*;
import org.jetbrains.jet.plugin.JetLanguage;

/**
 * @author Nikolay Krasko
 */
public abstract class JetStubElementType<StubT extends StubElement, PsiT extends PsiElement> extends IStubElementType<StubT, PsiT> {

    public JetStubElementType(@NotNull @NonNls String debugName) {
        super(debugName, JetLanguage.INSTANCE);
    }

    public abstract PsiT createPsiFromAst(@NotNull ASTNode node);

    @Override
    public String getExternalId() {
        return "jet." + toString();
    }

    @Override
    public boolean shouldCreateStub(ASTNode node) {
        PsiElement psi = node.getPsi();

        if (PsiTreeUtil.getParentOfType(psi, JetFunctionLiteral.class) != null) {
            return false;
        }

        JetBlockExpression blockExpression = PsiTreeUtil.getParentOfType(psi, JetBlockExpression.class);
        @SuppressWarnings("unchecked") JetDeclarationWithBody stubStopElement =
                PsiTreeUtil.getParentOfType(blockExpression, JetFunction.class, JetPropertyAccessor.class);

        if (stubStopElement != null) {
            return false;
        }

        return super.shouldCreateStub(node);
    }
}
