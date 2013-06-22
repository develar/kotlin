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

import com.google.dart.compiler.backend.js.ast.JsNameRef;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jet.lang.descriptors.DeclarationDescriptor;
import org.jetbrains.jet.lang.descriptors.NamespaceDescriptor;
import org.jetbrains.jet.lang.resolve.DescriptorUtils;

/**
 * Encapsulates different types of constants and naming conventions.
 */
public final class Namer {
    private static final JsNameRef NEW_KOTLIN_PACKAGE_REF = new JsNameRef("kotlin");

    public static final String CALLEE_NAME = "$fun";
    public static final String OUTER_CLASS_NAME = "$outer";

    private static final String SETTER_PREFIX = "set_";
    private static final String GETTER_PREFIX = "get_";
    private static final String BACKING_FIELD_PREFIX = "$";

    public static final String KOTLIN_OBJECT_NAME = "Kotlin";
    public static final JsNameRef KOTLIN_OBJECT_NAME_REF = new JsNameRef(KOTLIN_OBJECT_NAME);
    private static final String RECEIVER_PARAMETER_NAME = "$receiver";
    public static final JsNameRef THROW_NPE_FUN_NAME_REF = new JsNameRef("throwNPE", KOTLIN_OBJECT_NAME_REF);
    public static final JsNameRef NEW_EXCEPTION_FUN_NAME_REF = new JsNameRef("newException", NEW_KOTLIN_PACKAGE_REF);

    public static final String ROOT_PACKAGE_NAME = "_";
    public static final JsNameRef ROOT_PACKAGE_NAME_REF = new JsNameRef(ROOT_PACKAGE_NAME);

    public static final JsNameRef IS_TYPE_FUN_NAME_REF = new JsNameRef("isType", NEW_KOTLIN_PACKAGE_REF);

    public static final JsNameRef DEFINE_PACKAGE = new JsNameRef("p", KOTLIN_OBJECT_NAME_REF);

    private Namer() {
    }

    @NotNull
    public static String getReceiverParameterName() {
        return RECEIVER_PARAMETER_NAME;
    }

    @NotNull
    public static String getNameForAccessor(@NotNull String propertyName, boolean isGetter) {
        String prefix = isGetter ? GETTER_PREFIX : SETTER_PREFIX;
        return prefix + propertyName;
    }

    @NotNull
    public static String getKotlinBackingFieldName(@NotNull String propertyName) {
        return BACKING_FIELD_PREFIX + propertyName;
    }

    @NotNull
    public static JsNameRef kotlin(@NotNull String name) {
        return new JsNameRef(name, KOTLIN_OBJECT_NAME_REF);
    }

    @NotNull
    static String generateNamespaceName(DeclarationDescriptor descriptor) {
        if (DescriptorUtils.isRootNamespace((NamespaceDescriptor) descriptor)) {
            return ROOT_PACKAGE_NAME;
        }
        else {
            return descriptor.getName().asString();
        }
    }
}