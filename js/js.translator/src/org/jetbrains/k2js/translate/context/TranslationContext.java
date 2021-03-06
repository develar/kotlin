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

package org.jetbrains.k2js.translate.context;

import com.google.dart.compiler.backend.js.ast.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jet.lang.descriptors.CallableDescriptor;
import org.jetbrains.jet.lang.descriptors.DeclarationDescriptor;
import org.jetbrains.jet.lang.psi.JetExpression;
import org.jetbrains.jet.lang.resolve.BindingContext;
import org.jetbrains.k2js.translate.expression.LiteralFunctionTranslator;
import org.jetbrains.k2js.translate.intrinsic.Intrinsics;
import org.jetbrains.kotlin.compiler.PredefinedAnnotationManager;

import java.util.Map;

import static org.jetbrains.k2js.translate.utils.JsDescriptorUtils.getExpectedReceiverDescriptor;

/**
 * All the info about the state of the translation process.
 */
public class TranslationContext {
    @NotNull
    private final DynamicContext dynamicContext;
    @NotNull
    private final StaticContext staticContext;
    @NotNull
    private final AliasingContext aliasingContext;
    @Nullable
    private final UsageTracker usageTracker;

    @NotNull
    public static TranslationContext rootContext(@NotNull StaticContext staticContext, JsFunction rootFunction) {
        DynamicContext rootDynamicContext =
                DynamicContext.rootContext(rootFunction.getScope(), rootFunction.getBody());
        AliasingContext rootAliasingContext = AliasingContext.getCleanContext();
        return new TranslationContext(staticContext, rootDynamicContext, rootAliasingContext, null);
    }

    public boolean isEcma5() {
        return staticContext.isEcma5();
    }

    private TranslationContext(
            @NotNull StaticContext staticContext,
            @NotNull DynamicContext dynamicContext,
            @NotNull AliasingContext aliasingContext,
            @Nullable UsageTracker usageTracker
    ) {
        this.dynamicContext = dynamicContext;
        this.staticContext = staticContext;
        this.aliasingContext = aliasingContext;
        this.usageTracker = usageTracker;
    }

    private TranslationContext(@NotNull TranslationContext parent, @NotNull AliasingContext aliasingContext) {
        this(parent.staticContext, parent.dynamicContext, aliasingContext, parent.usageTracker);
    }

    private TranslationContext(
            @NotNull TranslationContext parent,
            @NotNull JsFunction fun,
            @NotNull AliasingContext aliasingContext,
            @Nullable UsageTracker usageTracker
    ) {
        this(parent.staticContext, DynamicContext.newContext(fun.getScope(), fun.getBody()), aliasingContext,
             usageTracker == null ? parent.usageTracker : usageTracker);
    }

    @Nullable
    public UsageTracker usageTracker() {
        return usageTracker;
    }

    public DynamicContext dynamicContext() {
        return dynamicContext;
    }

    @NotNull
    public TranslationContext contextWithScope(@NotNull JsFunction fun) {
        return new TranslationContext(this, fun, aliasingContext, null);
    }

    @NotNull
    public TranslationContext newFunctionBody(
            @NotNull JsFunction fun,
            @Nullable AliasingContext aliasingContext,
            @Nullable UsageTracker usageTracker
    ) {
        return new TranslationContext(this, fun, aliasingContext == null ? new AliasingContext(this.aliasingContext) : aliasingContext,
                                      usageTracker);
    }

    @NotNull
    public TranslationContext innerBlock(@NotNull JsBlock block) {
        return new TranslationContext(staticContext, dynamicContext.innerBlock(block), aliasingContext, usageTracker);
    }

    @NotNull
    public TranslationContext innerContextWithThisAliased(@NotNull DeclarationDescriptor correspondingDescriptor, @NotNull JsNameRef alias) {
        return new TranslationContext(this, aliasingContext.inner(correspondingDescriptor, alias));
    }

    @NotNull
    public TranslationContext innerContextWithAliasesForExpressions(@NotNull Map<JetExpression, String> aliases) {
        return new TranslationContext(this, aliasingContext.withExpressionsAliased(aliases));
    }

    @NotNull
    public TranslationContext innerContextWithDescriptorsAliased(@NotNull Map<DeclarationDescriptor, JsExpression> aliases) {
        return new TranslationContext(this, aliasingContext.withDescriptorsAliased(aliases));
    }

    @NotNull
    public BindingContext bindingContext() {
        return staticContext.getBindingContext();
    }

    @NotNull
    public String getName(@NotNull CallableDescriptor descriptor) {
        return staticContext.getName(descriptor);
    }

    // clearing name mappings is not required, but we do it to reset duplicated name counter
    public void clearNameMapping() {
        staticContext.clearNameMapping();
    }

    @NotNull
    public JsNameRef getNameRefForDescriptor(@NotNull DeclarationDescriptor descriptor) {
        return staticContext.getNameRef(descriptor);
    }

    @NotNull
    public JsExpression getQualifiedReference(@NotNull DeclarationDescriptor descriptor) {
        return staticContext.getQualifiedReference(descriptor, this);
    }

    @Nullable
    public JsExpression getQualifierForDescriptor(@NotNull DeclarationDescriptor descriptor) {
        return staticContext.getQualifierForDescriptor(descriptor);
    }

    @NotNull
    public TemporaryVariable declareTemporary(@Nullable JsExpression initExpression) {
        return dynamicContext.declareTemporary(initExpression);
    }

    @NotNull
    public Intrinsics intrinsics() {
        return staticContext.getIntrinsics();
    }

    @NotNull
    public JsScope scope() {
        return dynamicContext.getScope();
    }

    @NotNull
    public AliasingContext aliasingContext() {
        return aliasingContext;
    }

    @NotNull
    public LiteralFunctionTranslator literalFunctionTranslator() {
        return staticContext.getLiteralFunctionTranslator();
    }

    public void addStatementToCurrentBlock(@NotNull JsNode statement) {
        dynamicContext.jsBlock().getStatements().add(statement);
    }

    @Nullable
    public JsExpression getAliasForDescriptor(@NotNull DeclarationDescriptor descriptor) {
        if (usageTracker != null) {
            usageTracker.triggerUsed(descriptor);
        }
        // ClassDescriptor — this or receiver, these aliases cannot be shared and applicable only in current context
        return aliasingContext.getAliasForDescriptor(descriptor, false);
    }

    @NotNull
    public JsExpression getThisObject(@NotNull DeclarationDescriptor descriptor) {
        DeclarationDescriptor effectiveDescriptor;
        if (descriptor instanceof CallableDescriptor) {
            effectiveDescriptor = getExpectedReceiverDescriptor((CallableDescriptor) descriptor);
            assert effectiveDescriptor != null;
        }
        else {
            effectiveDescriptor = descriptor;
        }

        if (usageTracker != null) {
            usageTracker.triggerUsed(effectiveDescriptor);
        }

        JsExpression alias = aliasingContext.getAliasForDescriptor(effectiveDescriptor, false);
        return alias == null ? JsLiteral.THIS : alias;
    }

    public boolean isNative(DeclarationDescriptor descriptor) {
        return staticContext.isFromNativeModule(descriptor) || staticContext.predefinedAnnotationManager.hasNative(descriptor);
    }

    public PredefinedAnnotationManager predefinedAnnotationManager() {
        return staticContext.predefinedAnnotationManager;
    }
}
