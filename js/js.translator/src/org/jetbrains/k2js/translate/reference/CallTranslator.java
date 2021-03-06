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

import com.google.dart.compiler.backend.js.ast.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jet.lang.descriptors.*;
import org.jetbrains.jet.lang.resolve.calls.model.ResolvedCall;
import org.jetbrains.jet.lang.resolve.calls.model.VariableAsFunctionResolvedCall;
import org.jetbrains.jet.lang.resolve.calls.util.ExpressionAsFunctionDescriptor;
import org.jetbrains.k2js.translate.context.TranslationContext;
import org.jetbrains.k2js.translate.general.AbstractTranslator;
import org.jetbrains.k2js.translate.intrinsic.functions.basic.FunctionIntrinsic;
import org.jetbrains.k2js.translate.utils.ErrorReportingUtils;
import org.jetbrains.k2js.translate.utils.TranslationUtils;

import static org.jetbrains.k2js.translate.utils.BindingUtils.isObjectDeclaration;
import static org.jetbrains.k2js.translate.utils.JsAstUtils.assignment;
import static org.jetbrains.k2js.translate.utils.JsAstUtils.setQualifier;

//TODO: write tests on calling backing fields as functions
public final class CallTranslator extends AbstractTranslator {
    @NotNull
    private final CallInfo callInfo;
    @NotNull
    private final CallableDescriptor descriptor;
    @NotNull
    private final CallParameters callParameters;

    CallTranslator(
            @NotNull CallInfo callInfo,
            @NotNull CallParameters callParameters,
            @NotNull TranslationContext context
    ) {
        super(context);

        this.callInfo = callInfo;
        this.callParameters = callParameters;
        descriptor = callParameters.getDescriptor();
    }

    @NotNull
    public ResolvedCall<? extends CallableDescriptor> getResolvedCall() {
        return callInfo.getResolvedCall();
    }

    @NotNull
    public CallParameters getParameters() {
        return callParameters;
    }

    @NotNull
    JsExpression translate() {
        JsExpression result = intrinsicInvocation();
        if (result != null) {
            return result;
        }
        if ((descriptor instanceof ConstructorDescriptor)) {
            return createConstructorCallExpression(ReferenceTranslator.translateAsFQReference(descriptor, context()));
        }
        if (callInfo.getResolvedCall().getReceiverArgument().exists()) {
            if (callInfo.isNative()) {
                return methodCall(callParameters.getReceiver());
            }
            return extensionFunctionCall(!(descriptor instanceof ExpressionAsFunctionDescriptor));
        }
        return methodCall(isExpressionAsFunction() ? null : getThisObjectOrQualifier());
    }

    private boolean isExpressionAsFunction() {
        return descriptor instanceof ExpressionAsFunctionDescriptor ||
               callInfo.getResolvedCall() instanceof VariableAsFunctionResolvedCall;
    }

    @Nullable
    private JsExpression intrinsicInvocation() {
        if (descriptor instanceof FunctionDescriptor) {
            try {
                FunctionIntrinsic intrinsic = context().intrinsics().getFunctionIntrinsics().getIntrinsic((FunctionDescriptor) descriptor);
                if (intrinsic != null) {
                    return intrinsic.apply(this, callInfo.getArguments(), context());
                }
            }
            catch (RuntimeException e) {
                throw ErrorReportingUtils.reportErrorWithLocation(e, descriptor, bindingContext());
            }
        }
        return null;
    }

    @NotNull
    public HasArguments createConstructorCallExpression(@NotNull JsExpression constructorReference) {
        return new JsNew(constructorReference, callInfo.getArguments());
    }

    @NotNull
    public JsExpression explicitInvokeCall() {
        return callInfo.getCallType().constructCall(callParameters.getThisObject(), new CallType.CallConstructor() {
            @NotNull
            @Override
            public JsExpression construct(@Nullable JsExpression receiver) {
                return new JsInvocation(receiver, callInfo.getArguments());
            }
        }, context());
    }

    @NotNull
    public JsExpression extensionFunctionCall(final boolean useThis) {
        return callInfo.getCallType().constructCall(callParameters.getReceiver(), new CallType.CallConstructor() {
            @NotNull
            @Override
            public JsExpression construct(@Nullable JsExpression receiver) {
                assert receiver != null : "Could not be null for extensions";
                JsExpression functionReference = callParameters.getFunctionReference();
                if (useThis) {
                    setQualifier(functionReference, getThisObjectOrQualifier());
                }
                return new JsInvocation(functionReference, TranslationUtils.generateInvocationArguments(receiver, callInfo.getArguments()));
            }
        }, context());
    }

    @NotNull
    private JsExpression methodCall(@Nullable JsExpression receiver) {
        return callInfo.getCallType().constructCall(receiver, new CallType.CallConstructor() {
            @NotNull
            @Override
            public JsExpression construct(@Nullable JsExpression receiver) {
                JsExpression qualifiedCallee = callParameters.getFunctionReference();
                if (receiver != null) {
                    setQualifier(qualifiedCallee, receiver);
                }

                if (isDirectPropertyAccess()) {
                    return directPropertyAccess(qualifiedCallee);
                }
                if (callParameters.invokeAsApply()) {
                    return new JsInvocation(new JsNameRef("apply", qualifiedCallee), TranslationUtils
                            .generateInvocationArguments(receiver == null ? JsLiteral.NULL : receiver, callInfo.getArguments()));
                }
                return new JsInvocation(qualifiedCallee, callInfo.getArguments());
            }
        }, context());
    }

    @NotNull
    private JsExpression directPropertyAccess(@NotNull JsExpression callee) {
        if (descriptor instanceof PropertyGetterDescriptor) {
            assert callInfo.getArguments().isEmpty();
            return callee;
        }
        else {
            assert descriptor instanceof PropertySetterDescriptor;
            assert callInfo.getArguments().size() == 1;
            return assignment(callee, callInfo.getArguments().get(0));
        }
    }

    private boolean isDirectPropertyAccess() {
        return descriptor instanceof PropertyAccessorDescriptor &&
               (context().isEcma5() || isObjectAccessor((PropertyAccessorDescriptor) descriptor));
    }

    private boolean isObjectAccessor(@NotNull PropertyAccessorDescriptor propertyAccessorDescriptor) {
        PropertyDescriptor correspondingProperty = propertyAccessorDescriptor.getCorrespondingProperty();
        return isObjectDeclaration(bindingContext(), correspondingProperty);
    }

    @Nullable
    private JsExpression getThisObjectOrQualifier() {
        JsExpression thisObject = callParameters.getThisObject();
        if (thisObject != null) {
            return thisObject;
        }
        return context().getQualifierForDescriptor(descriptor);
    }
}
