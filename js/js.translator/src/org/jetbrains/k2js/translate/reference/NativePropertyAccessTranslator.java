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
import com.google.dart.compiler.backend.js.ast.JsNameRef;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jet.lang.descriptors.DeclarationDescriptor;
import org.jetbrains.jet.lang.descriptors.PropertyDescriptor;
import org.jetbrains.k2js.translate.context.TranslationContext;

import static org.jetbrains.k2js.translate.utils.JsAstUtils.assignment;
import static org.jetbrains.k2js.translate.utils.JsDescriptorUtils.getExpectedThisDescriptor;

/**
 * For native apis that use .property notation for access.
 */
public final class NativePropertyAccessTranslator extends PropertyAccessTranslator {
    NativePropertyAccessTranslator(
            @NotNull PropertyDescriptor descriptor,
            CallType callType, @Nullable JsExpression receiver,
            @NotNull TranslationContext context
    ) {
        super(descriptor, callType, receiver, context);
    }

    @NotNull
    @Override
    protected JsExpression translateAsGet(@Nullable JsExpression receiver) {
        return callType.constructCall(receiver, new CallType.CallConstructor() {
            @NotNull
            @Override
            public JsExpression construct(@Nullable JsExpression receiver) {
                JsNameRef ref = context().getNameRefForDescriptor(propertyDescriptor);
                if (receiver != null) {
                    ref.setQualifier(receiver);
                }
                return ref;
            }
        }, context());
    }

    @Override
    @NotNull
    protected JsExpression translateAsSet(@Nullable JsExpression receiver, @NotNull final JsExpression setTo) {
        return callType.constructCall(receiver, new CallType.CallConstructor() {
            @NotNull
            @Override
            public JsExpression construct(@Nullable JsExpression receiver) {
                return assignment(translateAsGet(receiver), setTo);
            }
        }, context());
    }

    @Nullable
    @Override
    protected JsExpression getReceiver() {
        if (receiver != null) {
            return receiver;
        }

        assert propertyDescriptor.getReceiverParameter() == null : "Can't have native extension properties.";
        DeclarationDescriptor expectedThisDescriptor = getExpectedThisDescriptor(propertyDescriptor);
        if (expectedThisDescriptor == null) {
            return null;
        }
        return context().getThisObject(expectedThisDescriptor);
    }
}
