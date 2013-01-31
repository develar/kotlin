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
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jet.lang.descriptors.*;
import org.jetbrains.jet.lang.psi.*;
import org.jetbrains.jet.lang.resolve.BindingContext;
import org.jetbrains.jet.lang.resolve.BindingContextUtils;
import org.jetbrains.k2js.translate.context.TranslationContext;
import org.jetbrains.k2js.translate.intrinsic.functions.factories.TopLevelFIF;
import org.jetbrains.k2js.translate.utils.JsAstUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.jetbrains.k2js.translate.general.Translation.translateAsBlock;
import static org.jetbrains.k2js.translate.general.Translation.translateAsStatement;

public final class TryTranslator {
    private TryTranslator() {
    }

    @NotNull
    public static JsTry translate(@NotNull JetTryExpression expression, @NotNull TranslationContext context) {
        return new JsTry(translateAsBlock(expression.getTryBlock(), context),
                         translateCatches(expression, context),
                         translateFinallyBlock(expression, context));
    }


    @Nullable
    private static JsBlock translateFinallyBlock(JetTryExpression expression, TranslationContext context) {
        JetFinallySection finallyBlock = expression.getFinallyBlock();
        return finallyBlock != null ? translateAsBlock(finallyBlock.getFinalExpression(), context) : null;
    }

    private static VariableDescriptor getCatchParameterDescriptor(JetCatchClause clause, TranslationContext context) {
        JetParameter catchParameter = clause.getCatchParameter();
        assert catchParameter != null;
        return BindingContextUtils.getNotNull(context.bindingContext(), BindingContext.VALUE_PARAMETER, catchParameter);
    }

    @NotNull
    private static List<JsCatch> translateCatches(JetTryExpression expression, TranslationContext outerContext) {
        List<JetCatchClause> clauses = expression.getCatchClauses();
        if (clauses.isEmpty()) {
            return Collections.emptyList();
        }

        String catchIdent = outerContext.scope().declareFreshName("e");
        JsNameRef catchIdentRef = new JsNameRef(catchIdent);
        JsCatch jsCatch = new JsCatch(catchIdent);
        if (clauses.size() == 1) {
            JetCatchClause clause = clauses.get(0);
            TranslationContext context = outerContext.innerContextWithDescriptorsAliased(
                    Collections.<DeclarationDescriptor, JsExpression>singletonMap(getCatchParameterDescriptor(clause, outerContext), catchIdentRef));
            JetExpression catchBody = clause.getCatchBody();
            assert catchBody != null;
            jsCatch.setBody(translateAsBlock(catchBody, context));
        }
        else if (clauses.size() > 1) {
            JsIf prevIf = null;
            THashMap<DeclarationDescriptor, JsExpression> aliasingMap = new THashMap<DeclarationDescriptor, JsExpression>(clauses.size());
            TranslationContext context = outerContext.innerContextWithDescriptorsAliased(aliasingMap);
            for (JetCatchClause clause : clauses) {
                JetExpression catchBody = clause.getCatchBody();
                assert catchBody != null;

                VariableDescriptor descriptor = getCatchParameterDescriptor(clause, outerContext);
                aliasingMap.put(descriptor, catchIdentRef);
                ClassifierDescriptor classDescriptor = descriptor.getType().getConstructor().getDeclarationDescriptor();
                String errorName = null;
                if (classDescriptor instanceof ClassDescriptor) {
                    Collection<ConstructorDescriptor> constructors = ((ClassDescriptor) classDescriptor).getConstructors();
                    for (ConstructorDescriptor constructor : constructors) {
                        if (constructor.isPrimary() && TopLevelFIF.JAVA_EXCEPTION_PATTERN.apply(constructor)) {
                            errorName = classDescriptor.getName().getName();
                            break;
                        }
                    }
                }

                JsIf ifStatement = new JsIf();
                if (errorName != null) {
                    ifStatement.setIfExpression(JsAstUtils.equality(new JsNameRef("name", catchIdentRef),
                                                                    context.program().getStringLiteral(errorName)));
                }
                else {
                    // todo is check
                    throw new UnsupportedOperationException("catch clause translator");
                }

                ifStatement.setThenStatement(translateAsStatement(catchBody, context));
                if (prevIf == null) {
                    prevIf = ifStatement;
                    jsCatch.setBody(new JsBlock(prevIf));
                }
                else {
                    prevIf.setElseStatement(ifStatement);
                    prevIf = ifStatement;
                }
            }
        }
        return Collections.singletonList(jsCatch);
    }
}
