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

package org.jetbrains.k2js.translate.reference;

import com.google.dart.compiler.backend.js.ast.JsExpression;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jet.lang.descriptors.CallableDescriptor;
import org.jetbrains.jet.lang.resolve.BindingTraceContext;
import org.jetbrains.jet.lang.resolve.DescriptorUtils;
import org.jetbrains.jet.lang.resolve.TemporaryBindingTrace;
import org.jetbrains.jet.lang.resolve.calls.model.ResolvedCall;
import org.jetbrains.jet.lang.resolve.calls.model.ResolvedCallImpl;
import org.jetbrains.jet.lang.resolve.calls.tasks.ExplicitReceiverKind;
import org.jetbrains.jet.lang.resolve.calls.tasks.ResolutionCandidate;
import org.jetbrains.jet.lang.resolve.calls.tasks.TracingStrategy;
import org.jetbrains.k2js.translate.context.TranslationContext;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class CallBuilder implements CallInfo {
    private boolean invokeAsApply;
    private ThreeState isNative = ThreeState.UNSURE;

    @NotNull
    private final TranslationContext context;
    @Nullable
    private JsExpression receiver;
    @NotNull
    private List<JsExpression> args = Collections.emptyList();
    @NotNull
    private CallType callType = CallType.NORMAL;

    private ResolvedCall<?> resolvedCall;
    @Nullable
    private CallableDescriptor descriptor;
    @Nullable
    private JsExpression callee;

    public static CallBuilder build(@NotNull TranslationContext context) {
        return new CallBuilder(context);
    }

    private CallBuilder(@NotNull TranslationContext context) {
        this.context = context;
    }

    @NotNull
    public CallBuilder receiver(@Nullable JsExpression receiver) {
        this.receiver = receiver;
        return this;
    }

    @NotNull
    public CallBuilder args(@NotNull List<JsExpression> args) {
        this.args = args;
        return this;
    }

    @NotNull
    public CallBuilder args(@NotNull JsExpression... args) {
        return args(Arrays.asList(args));
    }

    @NotNull
    public CallBuilder args(@NotNull JsExpression arg) {
        return args(Collections.singletonList(arg));
    }

    @NotNull
    public CallBuilder descriptor(@NotNull CallableDescriptor descriptor) {
        this.descriptor = descriptor;
        return this;
    }

    @NotNull
    public CallBuilder callee(@Nullable JsExpression callee) {
        this.callee = callee;
        return this;
    }

    @NotNull
    public CallBuilder resolvedCall(@NotNull ResolvedCall<?> call) {
        this.resolvedCall = call;
        return this;
    }

    @NotNull
    public CallBuilder type(@NotNull CallType type) {
        this.callType = type;
        return this;
    }

    @NotNull
    public CallBuilder invokeAsApply(boolean value) {
        invokeAsApply = value;
        return this;
    }

    @NotNull
    @Override
    public List<JsExpression> getArguments() {
        return args;
    }

    @NotNull
    @Override
    public ResolvedCall<?> getResolvedCall() {
        return resolvedCall;
    }

    @NotNull
    @Override
    public CallType getCallType() {
        return callType;
    }

    @Override
    public boolean isNative() {
        if (isNative == ThreeState.UNSURE) {
            isNative = context.isNative(resolvedCall.getCandidateDescriptor()) ? ThreeState.YES : ThreeState.NO;
        }
        return isNative == ThreeState.YES;
    }

    @NotNull
    public JsExpression translate() {
        if (resolvedCall == null) {
            assert descriptor != null;
            resolvedCall = ResolvedCallImpl.create(ResolutionCandidate.create(descriptor, DescriptorUtils.safeGetValue(descriptor.getExpectedThisObject()),
                                                                              DescriptorUtils.safeGetValue(descriptor.getReceiverParameter()),
                                                                              ExplicitReceiverKind.THIS_OBJECT, false),
                                                   TemporaryBindingTrace.create(new BindingTraceContext(), "trace to resolve call (in js)"),
                                                   TracingStrategy.EMPTY);
        }
        if (descriptor == null) {
            descriptor = resolvedCall.getCandidateDescriptor().getOriginal();
        }
        CallParametersResolver callParametersResolver = new CallParametersResolver(receiver, callee, descriptor, resolvedCall, context, invokeAsApply);
        return new CallTranslator(this, callParametersResolver, context).translate();
    }
}