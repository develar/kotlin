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
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jet.lang.descriptors.FunctionDescriptor;
import org.jetbrains.jet.lang.descriptors.VariableDescriptor;
import org.jetbrains.jet.lang.psi.*;
import org.jetbrains.jet.lang.resolve.BindingContext;
import org.jetbrains.jet.lang.resolve.constants.BooleanValue;
import org.jetbrains.jet.lang.resolve.constants.CompileTimeConstant;
import org.jetbrains.jet.lang.resolve.constants.NullValue;
import org.jetbrains.jet.lexer.JetTokens;
import org.jetbrains.k2js.translate.context.TemporaryVariable;
import org.jetbrains.k2js.translate.context.TranslationContext;
import org.jetbrains.k2js.translate.declaration.ClassTranslator;
import org.jetbrains.k2js.translate.expression.foreach.ForTranslator;
import org.jetbrains.k2js.translate.general.Translation;
import org.jetbrains.k2js.translate.operation.BinaryOperationTranslator;
import org.jetbrains.k2js.translate.operation.UnaryOperationTranslator;
import org.jetbrains.k2js.translate.reference.*;
import org.jetbrains.k2js.translate.utils.BindingUtils;
import org.jetbrains.k2js.translate.utils.JsAstUtils;
import org.jetbrains.k2js.translate.utils.mutator.AssignToExpressionMutator;

import java.util.List;

import static org.jetbrains.jet.lang.resolve.BindingContextUtils.getNotNull;
import static org.jetbrains.k2js.translate.general.Translation.translateAsExpression;
import static org.jetbrains.k2js.translate.utils.BindingUtils.getDescriptorForReferenceExpression;
import static org.jetbrains.k2js.translate.utils.BindingUtils.getFunctionDescriptor;
import static org.jetbrains.k2js.translate.utils.ErrorReportingUtils.message;
import static org.jetbrains.k2js.translate.utils.JsAstUtils.convertToExpression;
import static org.jetbrains.k2js.translate.utils.JsAstUtils.newVar;
import static org.jetbrains.k2js.translate.utils.PsiUtils.getBaseExpression;
import static org.jetbrains.k2js.translate.utils.TranslationUtils.sure;
import static org.jetbrains.k2js.translate.utils.mutator.LastExpressionMutator.mutateLastExpression;

public final class ExpressionVisitor extends JetVisitor<JsNode, TranslationContext> {
    @Override
    @NotNull
    public JsNode visitConstantExpression(@NotNull JetConstantExpression expression, @NotNull TranslationContext context) {
        CompileTimeConstant<?> compileTimeValue = getNotNull(context.bindingContext(), BindingContext.COMPILE_TIME_VALUE, expression);
        if (compileTimeValue == NullValue.NULL) {
            return JsLiteral.NULL;
        }
        else if (compileTimeValue == BooleanValue.FALSE) {
            return JsLiteral.FALSE;
        }
        else if (compileTimeValue == BooleanValue.TRUE) {
            return JsLiteral.TRUE;
        }

        Object value = compileTimeValue.getValue();
        if (value instanceof CharSequence) {
            return JsStringLiteral.unescaped((CharSequence) value);
        }
        else if (value instanceof Character) {
            return new JsStringLiteral(value.toString());
        }
        else if (value instanceof Number) {
            return new JsNumberLiteral((Number) value);
        }
        else {
            throw new AssertionError(message(expression, "Unsupported constant expression"));
        }
    }

    @Override
    @NotNull
    public JsNode visitBlockExpression(@NotNull JetBlockExpression jetBlock, @NotNull TranslationContext context) {
        List<JetElement> statements = jetBlock.getStatements();
        JsBlock jsBlock = new JsBlock();
        TranslationContext blockContext = context.innerBlock(jsBlock);
        for (JetElement statement : statements) {
            if (statement instanceof JetWhenExpression) {
                new WhenTranslator((JetWhenExpression) statement, blockContext, true).translate(jsBlock.getStatements());
            }
            else {
                assert statement instanceof JetExpression : "Elements in JetBlockExpression " +
                                                            "should be of type JetExpression";
                JsNode jsNode = statement.accept(this, blockContext);
                if (jsNode != null) {
                    jsBlock.getStatements().add(jsNode);
                }
            }
        }
        return jsBlock;
    }

    @Override
    @NotNull
    public JsNode visitReturnExpression(@NotNull JetReturnExpression returnExpression,
            @NotNull TranslationContext context) {
        JetExpression returned = returnExpression.getReturnedExpression();
        return new JsReturn(returned != null ? translateAsExpression(returned, context) : null).source(returnExpression);
    }

    @Override
    @NotNull
    public JsNode visitParenthesizedExpression(@NotNull JetParenthesizedExpression expression, @NotNull TranslationContext context) {
        JetExpression expressionInside = expression.getExpression();
        return expressionInside != null ? expressionInside.accept(this, context) : JsStatement.EMPTY;
    }

    @Override
    @NotNull
    public JsNode visitBinaryExpression(@NotNull JetBinaryExpression expression, @NotNull TranslationContext context) {
        return BinaryOperationTranslator.translate(expression, context).source(expression);
    }

    @Override
    @NotNull
    // assume it is a local variable declaration
    public JsNode visitProperty(@NotNull JetProperty expression, @NotNull TranslationContext context) {
        VariableDescriptor descriptor = getNotNull(context.bindingContext(), BindingContext.VARIABLE, expression);
        JsExpression initializer = translateInitializerForProperty(expression, context);
        String name = context.getName(descriptor);
        if (descriptor.isVar() && context.bindingContext().get(BindingContext.CAPTURED_IN_CLOSURE, descriptor) != null) {
            // well, wrap it
            JsNameRef alias = new JsNameRef("v", new JsNameRef(name));
            initializer = JsAstUtils.wrapValue(alias, initializer == null ? JsLiteral.NULL : initializer);
            context.aliasingContext().registerAlias(descriptor, alias);
        }

        return newVar(name, initializer).source(expression);
    }

    @Nullable
    private static JsExpression translateInitializerForProperty(
            @NotNull JetProperty declaration,
            @NotNull TranslationContext context
    ) {
        JetExpression initializer = declaration.getInitializer();
        return initializer == null ? null : Translation.translateAsExpression(initializer, context);
    }

    @Override
    @NotNull
    public JsNode visitCallExpression(@NotNull JetCallExpression expression,
            @NotNull TranslationContext context) {
        return CallExpressionTranslator.translate(expression, null, CallType.NORMAL, context).source(expression);
    }

    @Override
    @NotNull
    public JsNode visitIfExpression(@NotNull JetIfExpression expression, @NotNull TranslationContext context) {
        JsExpression testExpression = translateConditionExpression(expression.getCondition(), context);
        JetExpression thenExpression = expression.getThen();
        JetExpression elseExpression = expression.getElse();
        assert thenExpression != null;
        JsNode thenNode = thenExpression.accept(this, context);
        JsNode elseNode = elseExpression == null ? null : elseExpression.accept(this, context);

        boolean isKotlinStatement = BindingUtils.isStatement(context.bindingContext(), expression);
        boolean canBeJsExpression = thenNode instanceof JsExpression && elseNode instanceof JsExpression;
        if (!isKotlinStatement && canBeJsExpression) {
            return new JsConditional(testExpression, convertToExpression(thenNode), convertToExpression(elseNode)).source(expression);
        }
        else {
            JsIf ifStatement = new JsIf(testExpression, thenNode, elseNode == null ? null : elseNode);
            ifStatement.setSource(expression);
            if (isKotlinStatement) {
                return ifStatement;
            }

            TemporaryVariable result = context.declareTemporary(null);
            AssignToExpressionMutator saveResultToTemporaryMutator = new AssignToExpressionMutator(result.reference());
            context.addStatementToCurrentBlock(mutateLastExpression(ifStatement, saveResultToTemporaryMutator));
            return result.reference();
        }
    }

    @Override
    @NotNull
    public JsExpression visitSimpleNameExpression(@NotNull JetSimpleNameExpression expression,
            @NotNull TranslationContext context) {
        return ReferenceTranslator.translateSimpleName(expression, context).source(expression);
    }

    @NotNull
    private JsNode translateNullableExpressionAsNotNullStatement(@Nullable JetExpression nullableExpression,
            @NotNull TranslationContext context) {
        if (nullableExpression == null) {
            return JsStatement.EMPTY;
        }
        return nullableExpression.accept(this, context);
    }

    private JsExpression translateConditionExpression(@Nullable JetExpression expression, @NotNull TranslationContext context) {
        return expression == null ? null : (JsExpression) expression.accept(this, context);
    }

    @Override
    @NotNull
    public JsNode visitWhileExpression(@NotNull JetWhileExpression expression, @NotNull TranslationContext context) {
        return createWhile(new JsWhile(), expression, context);
    }

    @Override
    @NotNull
    public JsNode visitDoWhileExpression(@NotNull JetDoWhileExpression expression, @NotNull TranslationContext context) {
        return createWhile(new JsDoWhile(), expression, context);
    }

    private JsNode createWhile(@NotNull JsWhile result, @NotNull JetWhileExpressionBase expression, @NotNull TranslationContext context) {
        result.setCondition(translateConditionExpression(expression.getCondition(), context));
        result.setBody(translateNullableExpressionAsNotNullStatement(expression.getBody(), context));
        return result.source(expression);
    }

    @Override
    @NotNull
    public JsNode visitStringTemplateExpression(@NotNull JetStringTemplateExpression expression,
            @NotNull TranslationContext context) {
        StringTemplateVisitor entryVisitor = new StringTemplateVisitor(expression, context);
        expression.acceptChildren(entryVisitor);
        return entryVisitor.prepareResult(expression);
    }

    @Override
    @NotNull
    public JsNode visitDotQualifiedExpression(@NotNull JetDotQualifiedExpression expression,
            @NotNull TranslationContext context) {
        return QualifiedExpressionTranslator.translateQualifiedExpression(expression, context);
    }

    @Override
    @NotNull
    public JsNode visitPrefixExpression(
            @NotNull JetPrefixExpression expression,
            @NotNull TranslationContext context
    ) {
        JetSimpleNameExpression operationReference = expression.getOperationReference();
        IElementType operationToken = operationReference.getReferencedNameElementType();
        if (JetTokens.LABELS.contains(operationToken)) {
            JetExpression baseExpression = expression.getBaseExpression();
            assert baseExpression != null;
            return new JsLabel(context.scope().declareName(getReferencedName(operationReference)),
                               baseExpression.accept(this, context)).source(expression);
        }
        else {
            return UnaryOperationTranslator.translate(expression, operationToken, context).source(expression);
        }
    }

    @Override
    @NotNull
    public JsNode visitPostfixExpression(@NotNull JetPostfixExpression expression,
            @NotNull TranslationContext context) {
        IElementType operationToken = expression.getOperationReference().getReferencedNameElementType();
        if (operationToken == JetTokens.EXCLEXCL) {
            return sure(translateAsExpression(getBaseExpression(expression), context), context).source(expression);
        }
        else {
            return UnaryOperationTranslator.translate(expression, operationToken, context).source(expression);
        }
    }

    @Override
    @NotNull
    public JsNode visitIsExpression(@NotNull JetIsExpression expression, @NotNull TranslationContext context) {
        return Translation.patternTranslator(context).translateIsExpression(expression);
    }

    @Override
    @NotNull
    public JsNode visitSafeQualifiedExpression(@NotNull JetSafeQualifiedExpression expression, @NotNull TranslationContext context) {
        return QualifiedExpressionTranslator.translateQualifiedExpression(expression, context).source(expression);
    }

    @Override
    @NotNull
    public JsNode visitWhenExpression(@NotNull JetWhenExpression expression, @NotNull TranslationContext context) {
        // voidResult is never null if WhenTranslator from visitWhenExpression - true only if from visitBlockExpression, otherwise false
        // its result used for dangerous translator or binary expression
        return new WhenTranslator(expression, context, false).translate();
    }

    @Override
    @NotNull
    public JsNode visitBinaryWithTypeRHSExpression(@NotNull JetBinaryExpressionWithTypeRHS expression, @NotNull TranslationContext context) {
        JsExpression jsExpression = Translation.translateAsExpression(expression.getLeft(), context);
        //noinspection ConstantConditions
        if (expression.getOperationReference().getReferencedNameElementType() != JetTokens.AS_KEYWORD ||
            getNotNull(context.bindingContext(), BindingContext.TYPE, expression.getRight()).isNullable()) {
            return jsExpression.source(expression);
        }

        // KT-2670
        // we actually do not care for types in js
        return sure(jsExpression, context).source(expression);
    }

    private static String getReferencedName(JetSimpleNameExpression expression) {
        String name = expression.getReferencedName();
        return name.charAt(0) == '@' ? name.substring(1) + '$' : name;
    }

    @Nullable
    private static String getTargetLabel(JetLabelQualifiedExpression expression, TranslationContext context) {
        JetSimpleNameExpression labelElement = expression.getTargetLabel();
        return labelElement == null ? null : context.scope().findName(getReferencedName(labelElement));
    }

    @Override
    @NotNull
    public JsNode visitBreakExpression(@NotNull JetBreakExpression expression,
            @NotNull TranslationContext context) {
        return new JsBreak(getTargetLabel(expression, context)).source(expression);
    }

    @Override
    @NotNull
    public JsNode visitContinueExpression(@NotNull JetContinueExpression expression,
            @NotNull TranslationContext context) {
        return new JsContinue(getTargetLabel(expression, context)).source(expression);
    }

    @Override
    @NotNull
    public JsNode visitFunctionLiteralExpression(@NotNull JetFunctionLiteralExpression expression, @NotNull TranslationContext context) {
        JetFunctionLiteral functionLiteral = expression.getFunctionLiteral();
        FunctionDescriptor descriptor = getFunctionDescriptor(context.bindingContext(), functionLiteral);
        return context.literalFunctionTranslator().translateFunction(functionLiteral, descriptor, context, null);
    }

    @Override
    public JsNode visitNamedFunction(@NotNull JetNamedFunction expression, @NotNull TranslationContext context) {
        FunctionDescriptor descriptor = getFunctionDescriptor(context.bindingContext(), expression);
        LocalNamedFunctionTranslatorHelper translatorHelper = new LocalNamedFunctionTranslatorHelper(descriptor, context);
        JsExpression alias = context.literalFunctionTranslator().translateFunction(expression, descriptor, context, translatorHelper);
        translatorHelper.createResult(expression, alias);
        return null;
    }

    @Override
    @NotNull
    public JsNode visitThisExpression(@NotNull JetThisExpression expression,
                                      @NotNull TranslationContext context) {
        return context.getThisObject(getDescriptorForReferenceExpression(context.bindingContext(), expression.getInstanceReference()))
                .source(expression);
    }

    @Override
    @NotNull
    public JsNode visitArrayAccessExpression(@NotNull JetArrayAccessExpression expression,
            @NotNull TranslationContext context) {
        return AccessTranslationUtils.translateAsGet(expression, context);
    }

    @Override
    @NotNull
    public JsNode visitForExpression(@NotNull JetForExpression expression,
            @NotNull TranslationContext context) {
        return ForTranslator.translate(expression, context).source(expression);
    }

    @Override
    @NotNull
    public JsNode visitTryExpression(@NotNull JetTryExpression expression,
            @NotNull TranslationContext context) {
        return TryTranslator.translate(expression, context).source(expression);
    }

    @Override
    @NotNull
    public JsNode visitThrowExpression(@NotNull JetThrowExpression expression,
            @NotNull TranslationContext context) {
        JetExpression thrownExpression = expression.getThrownExpression();
        assert thrownExpression != null : "Thrown expression must not be null";
        return new JsThrow(translateAsExpression(thrownExpression, context)).source(expression);
    }

    @Override
    @NotNull
    public JsNode visitObjectLiteralExpression(@NotNull JetObjectLiteralExpression expression, @NotNull TranslationContext context) {
        return ClassTranslator.translateObjectDeclaration(expression.getObjectDeclaration(), context).source(expression);
    }

    @Override
    @NotNull
    public JsNode visitObjectDeclaration(@NotNull JetObjectDeclaration expression, @NotNull TranslationContext context) {
        JetObjectDeclarationName nameAsDeclaration = expression.getNameAsDeclaration();
        assert nameAsDeclaration != null;
        VariableDescriptor descriptor = (VariableDescriptor) getNotNull(context.bindingContext(),
                                                                        BindingContext.DECLARATION_TO_DESCRIPTOR,
                                                                        nameAsDeclaration);
        return newVar(context.getName(descriptor), ClassTranslator.translateObjectDeclaration(expression, context)).source(expression);
    }
}
