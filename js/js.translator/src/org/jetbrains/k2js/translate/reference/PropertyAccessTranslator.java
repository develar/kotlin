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

package org.jetbrains.k2js.translate.reference;

import com.google.dart.compiler.backend.js.ast.JsExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jet.lang.descriptors.PropertyDescriptor;
import org.jetbrains.jet.lang.psi.JetPsiUtil;
import org.jetbrains.jet.lang.psi.JetSimpleNameExpression;
import org.jetbrains.jet.lang.resolve.calls.model.ResolvedCall;
import org.jetbrains.k2js.translate.context.TranslationContext;
import org.jetbrains.k2js.translate.general.AbstractTranslator;

import static org.jetbrains.kotlin.compiler.AnnotationsUtils.isNativeObject;
import static org.jetbrains.k2js.translate.utils.BindingUtils.getResolvedCallForProperty;

public abstract class PropertyAccessTranslator extends AbstractTranslator implements AccessTranslator {
    protected final  CallType callType;

    @NotNull
    protected final PropertyDescriptor propertyDescriptor;

    @Nullable
    protected final JsExpression receiver;

    protected PropertyAccessTranslator(
            @NotNull PropertyDescriptor propertyDescriptor,
            CallType callType,
            @Nullable JsExpression receiver,
            @NotNull TranslationContext context
    ) {
        super(context);

        this.propertyDescriptor = propertyDescriptor;
        this.receiver = receiver;
        this.callType = callType;
    }

    @NotNull
    public static PropertyAccessTranslator newInstance(
            @NotNull PropertyDescriptor propertyDescriptor,
            @NotNull JetSimpleNameExpression expression,
            @Nullable JsExpression qualifier,
            @NotNull CallType callType,
            @NotNull TranslationContext context
    ) {
        if (JetPsiUtil.isBackingFieldReference(expression) || isNativeObject(propertyDescriptor)) {
            return new NativePropertyAccessTranslator(propertyDescriptor, callType, qualifier, context);
        }
        else {
            ResolvedCall<?> resolvedCall = getResolvedCallForProperty(context.bindingContext(), expression);
            return new KotlinPropertyAccessTranslator(propertyDescriptor, callType, qualifier, resolvedCall, context);
        }
    }

    @NotNull
    protected abstract JsExpression translateAsGet(@Nullable JsExpression receiver);

    @NotNull
    protected abstract JsExpression translateAsSet(@Nullable JsExpression receiver, @NotNull JsExpression setTo);

    @Override
    @NotNull
    public JsExpression translateAsGet() {
        return translateAsGet(getReceiver());
    }

    @NotNull
    @Override
    public JsExpression translateAsSet(@NotNull JsExpression setTo) {
        return translateAsSet(getReceiver(), setTo);
    }

    @NotNull
    @Override
    public CachedAccessTranslator getCached() {
        return new CachedPropertyAccessTranslator(getReceiver(), this, context());
    }

    @Nullable
    protected JsExpression getReceiver() {
        return receiver;
    }
}