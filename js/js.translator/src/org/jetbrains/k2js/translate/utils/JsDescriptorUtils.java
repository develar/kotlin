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

package org.jetbrains.k2js.translate.utils;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jet.lang.descriptors.*;
import org.jetbrains.jet.lang.psi.JetExpression;
import org.jetbrains.jet.lang.resolve.BindingContext;
import org.jetbrains.jet.lang.resolve.DescriptorUtils;
import org.jetbrains.jet.lang.resolve.name.Name;
import org.jetbrains.jet.lang.resolve.scopes.receivers.ReceiverValue;
import org.jetbrains.jet.lang.types.JetType;
import org.jetbrains.jet.lang.types.lang.KotlinBuiltIns;
import org.jetbrains.k2js.translate.context.TranslationContext;

public final class JsDescriptorUtils {
    private JsDescriptorUtils() {
    }

    @Nullable
    public static DeclarationDescriptor getExpectedThisDescriptor(@NotNull CallableDescriptor callableDescriptor) {
        ReceiverParameterDescriptor expectedThisObject = callableDescriptor.getExpectedThisObject();
        if (expectedThisObject == null) {
            return null;
        }
        return getDeclarationDescriptorForReceiver(expectedThisObject.getValue());
    }

    @NotNull
    public static DeclarationDescriptor getDeclarationDescriptorForReceiver
            (@NotNull ReceiverValue receiverParameter) {
        DeclarationDescriptor declarationDescriptor =
                receiverParameter.getType().getConstructor().getDeclarationDescriptor();
        //TODO: WHY assert?
        assert declarationDescriptor != null;
        return declarationDescriptor.getOriginal();
    }

    @Nullable
    public static DeclarationDescriptor getExpectedReceiverDescriptor(@NotNull CallableDescriptor callableDescriptor) {
        ReceiverParameterDescriptor receiverParameter = callableDescriptor.getReceiverParameter();
        return receiverParameter == null ? null : getDeclarationDescriptorForReceiver(receiverParameter.getValue());
    }

    private static boolean isDefaultAccessor(@Nullable PropertyAccessorDescriptor accessorDescriptor) {
        return accessorDescriptor == null || accessorDescriptor.isDefault();
    }

    public static boolean isAsPrivate(@NotNull PropertyDescriptor propertyDescriptor) {
        return (propertyDescriptor.getReceiverParameter() != null) ||
               !isDefaultAccessor(propertyDescriptor.getGetter()) ||
               !isDefaultAccessor(propertyDescriptor.getSetter());
    }

    public static boolean isStandardDeclaration(@NotNull DeclarationDescriptor descriptor) {
        NamespaceDescriptor namespace = getContainingNamespace(descriptor);
        if (namespace == null) {
            return false;
        }
        return namespace == KotlinBuiltIns.getInstance().getBuiltInsScope().getContainingDeclaration();
    }

    @Nullable
    public static NamespaceDescriptor getContainingNamespace(@NotNull DeclarationDescriptor descriptor) {
        return DescriptorUtils.getParentOfType(descriptor, NamespaceDescriptor.class);
    }

    @Nullable
    public static Name getNameIfStandardType(@NotNull JetExpression expression, @NotNull TranslationContext context) {
        JetType type = context.bindingContext().get(BindingContext.EXPRESSION_TYPE, expression);
        return type != null ? getNameIfStandardType(type) : null;
    }

    public static boolean isInBuiltInsPackage(@NotNull DeclarationDescriptor descriptor) {
        return descriptor.getContainingDeclaration() == KotlinBuiltIns.getInstance().getBuiltInsPackage();
    }

    @Nullable
    public static Name getNameIfStandardType(@NotNull JetType type) {
        ClassifierDescriptor descriptor = type.getConstructor().getDeclarationDescriptor();
        if (descriptor != null && isInBuiltInsPackage(descriptor)) {
            return descriptor.getName();
        }
        return null;
    }
}
