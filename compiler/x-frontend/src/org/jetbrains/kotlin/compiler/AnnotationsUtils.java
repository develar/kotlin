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

package org.jetbrains.kotlin.compiler;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jet.lang.descriptors.ClassDescriptor;
import org.jetbrains.jet.lang.descriptors.DeclarationDescriptor;
import org.jetbrains.jet.lang.descriptors.annotations.AnnotationDescriptor;
import org.jetbrains.jet.lang.resolve.DescriptorUtils;
import org.jetbrains.jet.lang.resolve.constants.CompileTimeConstant;

public final class AnnotationsUtils {
    private AnnotationsUtils() {
    }

    @Nullable
    public static String getAnnotationStringParameter(@NotNull AnnotationDescriptor annotationDescriptor) {
        //TODO: this is a quick fix for unsupported default args problem
        if (annotationDescriptor.getAllValueArguments().isEmpty()) {
            return null;
        }
        CompileTimeConstant<?> constant = annotationDescriptor.getAllValueArguments().values().iterator().next();
        //TODO: this is a quick fix for unsupported default args problem
        if (constant == null) {
            return null;
        }
        Object value = constant.getValue();
        assert value instanceof String : "Native function annotation should have one String parameter";
        return (String) value;
    }

    @Nullable
    public static AnnotationDescriptor getAnnotationByName(@NotNull DeclarationDescriptor descriptor, @NotNull String fqn) {
        for (AnnotationDescriptor annotationDescriptor : descriptor.getAnnotations()) {
            if (getAnnotationClassFQName(annotationDescriptor).equals(fqn)) {
                return annotationDescriptor;
            }
        }
        return null;
    }

    @NotNull
    private static String getAnnotationClassFQName(@NotNull AnnotationDescriptor annotationDescriptor) {
        DeclarationDescriptor annotationDeclaration =
                annotationDescriptor.getType().getConstructor().getDeclarationDescriptor();
        assert annotationDeclaration != null : "Annotation supposed to have a declaration";
        return DescriptorUtils.getFQName(annotationDeclaration).getFqName();
    }

    public static boolean isNativeObject(@NotNull DeclarationDescriptor descriptor) {
        return isNativeObjectByModule(descriptor) || isNativeByAnnotation(descriptor);
    }

    public static boolean isNativeByAnnotation(DeclarationDescriptor descriptor) {
        return hasAnnotationOrInsideAnnotatedClass(descriptor, PredefinedAnnotation.NATIVE);
    }

    public static boolean isNativeObjectByModule(@NotNull DeclarationDescriptor descriptor) {
        return DescriptorUtils.getModuleDescriptor(descriptor).getName().equals(ModuleInfo.STUBS_MODULE_NAME);
    }

    public static boolean isLibraryObject(@NotNull DeclarationDescriptor descriptor) {
        return hasAnnotationOrInsideAnnotatedClass(descriptor, PredefinedAnnotation.LIBRARY);
    }

    public static boolean isPredefinedObject(@NotNull DeclarationDescriptor descriptor) {
        for (PredefinedAnnotation annotation : PredefinedAnnotation.values()) {
            if (hasAnnotationOrInsideAnnotatedClass(descriptor, annotation)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasAnnotationOrInsideAnnotatedClass(@NotNull DeclarationDescriptor descriptor,
            @NotNull PredefinedAnnotation annotation) {
        return getAnnotationOrInsideAnnotatedClass(descriptor, annotation.getFQName()) != null;
    }

    @Nullable
    public static AnnotationDescriptor getAnnotationOrInsideAnnotatedClass(@NotNull DeclarationDescriptor descriptor, @NotNull String fqn) {
        AnnotationDescriptor annotationDescriptor = getAnnotationByName(descriptor, fqn);
        if (annotationDescriptor != null) {
            return annotationDescriptor;
        }
        ClassDescriptor containingClass = DescriptorUtils.getContainingClass(descriptor);
        return containingClass == null ? null : getAnnotationByName(containingClass, fqn);
    }
}
