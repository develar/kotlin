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

package org.jetbrains.k2js.translate.general;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jet.lang.psi.JetDeclaration;
import org.jetbrains.jet.lang.psi.JetDeclarationContainer;
import org.jetbrains.jet.lang.psi.JetElement;
import org.jetbrains.jet.lang.psi.JetVisitorVoid;
import org.jetbrains.k2js.translate.context.TranslationContext;

public abstract class TranslatorVisitor extends JetVisitorVoid {
    protected final TranslationContext context;

    public TranslatorVisitor(TranslationContext context) {
        this.context = context;
    }

    @Override
    public void visitJetElement(JetElement expression) {
        throw new UnsupportedOperationException("Unsupported expression encountered:" + expression.toString());
    }

    public final void traverseContainer(@NotNull JetDeclarationContainer jetClass) {
        for (JetDeclaration declaration : jetClass.getDeclarations()) {
            declaration.accept(this);
        }
    }
}
