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

import com.google.dart.compiler.backend.js.ast.*;
import com.google.dart.compiler.backend.js.ast.JsVar;
import com.intellij.psi.FileViewProvider;
import com.intellij.util.SmartList;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jet.cli.common.messages.CompilerMessageLocation;
import org.jetbrains.jet.lang.descriptors.FunctionDescriptor;
import org.jetbrains.jet.lang.diagnostics.DiagnosticUtils;
import org.jetbrains.jet.lang.diagnostics.DiagnosticUtils.LineAndColumn;
import org.jetbrains.jet.lang.psi.JetExpression;
import org.jetbrains.jet.lang.psi.JetFile;
import org.jetbrains.jet.lang.psi.JetNamedFunction;
import org.jetbrains.jet.lang.resolve.BindingContext;
import org.jetbrains.k2js.config.Config;
import org.jetbrains.k2js.facade.MainCallParameters;
import org.jetbrains.k2js.translate.context.Namer;
import org.jetbrains.k2js.translate.context.StaticContext;
import org.jetbrains.k2js.translate.context.TranslationContext;
import org.jetbrains.k2js.translate.declaration.NamespaceTranslator;
import org.jetbrains.k2js.translate.expression.ExpressionVisitor;
import org.jetbrains.k2js.translate.expression.PatternTranslator;
import org.jetbrains.k2js.translate.reference.CallBuilder;
import org.jetbrains.k2js.translate.test.JSTestGenerator;
import org.jetbrains.k2js.translate.test.JSTester;
import org.jetbrains.k2js.translate.utils.dangerous.FindDangerousVisitor;
import org.jetbrains.kotlin.compiler.ModuleInfo;
import org.jetbrains.kotlin.compiler.TranslationException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.jetbrains.jet.plugin.JetMainDetector.getMainFunction;
import static org.jetbrains.k2js.translate.utils.BindingUtils.getFunctionDescriptor;
import static org.jetbrains.k2js.translate.utils.JsAstUtils.toStringLiteralList;

/**
 * This class provides a interface which all translators use to interact with each other.
 * Goal is to simplify interaction between translators.
 */
public final class Translation {

    private Translation() {
    }

    @NotNull
    public static PatternTranslator patternTranslator(@NotNull TranslationContext context) {
        return PatternTranslator.newInstance(context);
    }

    @NotNull
    public static JsNode translateExpression(@NotNull JetExpression expression, @NotNull TranslationContext context) {
        String aliasForExpression = context.aliasingContext().getAliasForExpression(expression);
        if (aliasForExpression != null) {
            return new JsNameRef(aliasForExpression);
        }

        try {
            List<JetExpression> nodesToBeGeneratedBefore = FindDangerousVisitor.collect(expression, context);
            if (nodesToBeGeneratedBefore.isEmpty()) {
                return doTranslateExpression(expression, context);
            }
            else {
                return translateDangerous(expression, context, nodesToBeGeneratedBefore);
            }
        }
        catch (TranslationException e) {
            if (e.getLocation() == null) {
                e.setLocation(getCompilerMessageLocation(expression));
            }
            throw e;
        }
        catch (Throwable e) {
            throw new TranslationException(e, getCompilerMessageLocation(expression));
        }
    }

    private static JsNode translateDangerous(JetExpression rootExpression, TranslationContext context, List<JetExpression> nodesToBeGeneratedBefore) {
        Map<JetExpression, String> aliasesForExpressions = new THashMap<JetExpression, String>(nodesToBeGeneratedBefore.size());
        List<JsVar> vars = new ArrayList<JsVar>(nodesToBeGeneratedBefore.size());
        for (JetExpression expression : nodesToBeGeneratedBefore) {
            JsExpression translatedExpression = translateAsExpression(expression, context);
            JsVar alias = context.dynamicContext().createTemporaryVar(translatedExpression);
            vars.add(alias);
            aliasesForExpressions.put(expression, alias.getName());
        }
        context.addStatementToCurrentBlock(new JsVars(vars, true));
        return doTranslateExpression(rootExpression, context.innerContextWithAliasesForExpressions(aliasesForExpressions));
    }

    @NotNull
    private static JsNode doTranslateExpression(JetExpression expression, TranslationContext context) {
        return expression.accept(new ExpressionVisitor(), context);
    }

    public static CompilerMessageLocation getCompilerMessageLocation(JetExpression expression) {
        FileViewProvider viewProvider = expression.getContainingFile().getViewProvider();
        LineAndColumn location = DiagnosticUtils.offsetToLineAndColumn(viewProvider.getDocument(), expression.getNode().getStartOffset());
        return CompilerMessageLocation.create(viewProvider.getVirtualFile().getPath(), location.getLine(), location.getColumn());
    }

    @NotNull
    public static JsExpression translateAsExpression(@NotNull JetExpression expression, @NotNull TranslationContext context) {
        return (JsExpression) translateExpression(expression, context);
    }

    @NotNull
    public static JsNode translate(@NotNull JetExpression expression, @NotNull TranslationContext context) {
        return translateExpression(expression, context);
    }

    @NotNull
    public static JsBlock translateAsBlock(@NotNull JetExpression expression, @NotNull TranslationContext context) {
        JsNode result = translateExpression(expression, context);
        return result instanceof JsBlock ? (JsBlock) result : new JsBlock(result);
    }

    @NotNull
    public static JsProgram generateAst(
            @NotNull BindingContext bindingContext, @NotNull Collection<JetFile> files,
            @NotNull MainCallParameters mainCallParameters,
            @NotNull Config config
    ) {
        StaticContext staticContext = new StaticContext(bindingContext, config);
        JsFunction definitionFunction = generateDefinitionFunction(staticContext, files, config, mainCallParameters);
        JsProgram program = staticContext.getProgram();
        program.getStatements().add(generateDefineModuleInvocation(config, definitionFunction));
        return program;
    }

    private static JsInvocation generateDefineModuleInvocation(Config config, JsFunction definitionFunction) {
        List<JsExpression> moduleDependencies = new SmartList<JsExpression>();
        for (ModuleInfo module : config.getModule().getDependencies()) {
            if (!config.getModule().isDependencyProvided(module)) {
                moduleDependencies.add(new JsStringLiteral(module.getName()));
            }
        }

        return new JsInvocation(new JsNameRef("defineModule", Namer.KOTLIN_OBJECT_NAME_REF),
                                new JsStringLiteral(config.getModule().getName()),
                                moduleDependencies.isEmpty() ? JsLiteral.NULL : new JsArrayLiteral(moduleDependencies),
                                definitionFunction);
    }

    private static JsFunction generateDefinitionFunction(
            StaticContext staticContext,
            Collection<JetFile> files,
            Config config,
            MainCallParameters mainCallParameters
    ) {
        List<JsNode> statements = new ArrayList<JsNode>();
        statements.add(new JsStringLiteral("use strict"));

        JsFunction definitionFunction = new JsFunction(null, new JsBlock(statements));
        TranslationContext context = TranslationContext.rootContext(staticContext, definitionFunction);
        staticContext.initTranslators(context);
        NamespaceTranslator.translateFiles(files, statements, context);

        if (mainCallParameters.shouldBeGenerated()) {
            JsNode statement = generateCallToMain(context, files, mainCallParameters.arguments());
            if (statement != null) {
                statements.add(statement);
            }
        }
        mayBeGenerateTests(files, config, definitionFunction.getBody(), context);
        statements.add(new JsReturn(Namer.ROOT_PACKAGE_NAME_REF));
        return definitionFunction;
    }

    private static void mayBeGenerateTests(
            @NotNull Collection<JetFile> files,
            @NotNull Config config,
            @NotNull JsBlock rootBlock,
            @NotNull TranslationContext context
    ) {
        JSTester tester = config.getTester();
        if (tester != null) {
            tester.initialize(context, rootBlock);
            JSTestGenerator.generateTestCalls(context, files, tester);
            tester.deinitialize();
        }
    }

    @Nullable
    private static JsNode generateCallToMain(@NotNull TranslationContext context, @NotNull Collection<JetFile> files,
            @NotNull List<String> arguments) {
        JetNamedFunction mainFunction = getMainFunction(files);
        if (mainFunction == null) {
            return null;
        }
        FunctionDescriptor functionDescriptor = getFunctionDescriptor(context.bindingContext(), mainFunction);
        return CallBuilder.build(context).args(new JsArrayLiteral(toStringLiteralList(arguments))).
                descriptor(functionDescriptor).translate();
    }
}
