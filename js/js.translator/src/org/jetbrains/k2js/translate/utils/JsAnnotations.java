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
import org.jetbrains.jet.lang.descriptors.ClassDescriptor;
import org.jetbrains.jet.lang.descriptors.ClassKind;
import org.jetbrains.jet.lang.descriptors.DeclarationDescriptor;
import org.jetbrains.jet.lang.resolve.DescriptorUtils;
import org.jetbrains.kotlin.compiler.AnnotationsUtils;

public final class JsAnnotations {
    private static final String ENUMERABLE = "js.enumerable";

    private JsAnnotations() {
    }

    public static boolean isEnumerable(@NotNull DeclarationDescriptor descriptor) {
        if (AnnotationsUtils.getAnnotationByName(descriptor, ENUMERABLE) != null) {
            return true;
        }
        ClassDescriptor containingClass = DescriptorUtils.getContainingClass(descriptor);
        return containingClass != null &&
               ((containingClass.getKind().equals(ClassKind.OBJECT) && containingClass.getName().isSpecial()) ||
                AnnotationsUtils.getAnnotationByName(containingClass, ENUMERABLE) != null);
    }
}
