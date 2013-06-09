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

import com.google.dart.compiler.backend.js.ast.JsExpression;
import com.google.dart.compiler.backend.js.ast.JsNameRef;
import com.google.dart.compiler.backend.js.ast.JsScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jet.lang.descriptors.DeclarationDescriptor;
import org.jetbrains.jet.lang.descriptors.NamespaceDescriptor;
import org.jetbrains.jet.lang.resolve.DescriptorUtils;

/**
 * Encapsulates different types of constants and naming conventions.
 */
public final class Namer {
    public static final String CALLEE_NAME = "$fun";
    public static final String OUTER_CLASS_NAME = "$outer";

    private static final String INITIALIZE_METHOD_NAME = "initialize";
    private static final String CLASS_OBJECT_NAME = "createClass";
    private static final String TRAIT_OBJECT_NAME = "createTrait";
    private static final String OBJECT_OBJECT_NAME = "createObject";
    private static final String SETTER_PREFIX = "set_";
    private static final String GETTER_PREFIX = "get_";
    private static final String BACKING_FIELD_PREFIX = "$";
    private static final String SUPER_METHOD_NAME = "super_init";

    public static final String KOTLIN_OBJECT_NAME = "Kotlin";
    public static final JsNameRef KOTLIN_OBJECT_NAME_REF = new JsNameRef(KOTLIN_OBJECT_NAME);
    private static final String RECEIVER_PARAMETER_NAME = "$receiver";
    public static final JsNameRef THROW_NPE_FUN_NAME_REF = new JsNameRef("throwNPE", KOTLIN_OBJECT_NAME_REF);
    public static final JsNameRef NEW_EXCEPTION_FUN_NAME_REF = new JsNameRef("newException", KOTLIN_OBJECT_NAME_REF);

    public static final String ROOT_PACKAGE_NAME = "_";
    public static final JsNameRef ROOT_PACKAGE_NAME_REF = new JsNameRef(ROOT_PACKAGE_NAME);

    @NotNull
    public static String getReceiverParameterName() {
        return RECEIVER_PARAMETER_NAME;
    }

    @NotNull
    public static String getRootNamespaceName() {
        return ROOT_PACKAGE_NAME;
    }

    @NotNull
    public static JsNameRef initializeMethodReference() {
        return new JsNameRef(INITIALIZE_METHOD_NAME);
    }

    @NotNull
    public static String superMethodName() {
        return SUPER_METHOD_NAME;
    }

    @NotNull
    public static String getNameForAccessor(@NotNull String propertyName, boolean isGetter) {
        return getNameWithPrefix(propertyName, isGetter ? GETTER_PREFIX : SETTER_PREFIX);
    }

    @NotNull
    public static String getKotlinBackingFieldName(@NotNull String propertyName) {
        return getNameWithPrefix(propertyName, BACKING_FIELD_PREFIX);
    }

    @NotNull
    private static String getNameWithPrefix(@NotNull String name, @NotNull String prefix) {
        return prefix + name;
    }

    public static Namer newInstance(@NotNull JsScope rootScope) {
        return new Namer(rootScope);
    }

    @NotNull
    private final String className;
    @NotNull
    private final String traitName;
    @NotNull
    private final JsNameRef definePackage;
    @NotNull
    private final String objectName;

    @NotNull
    private final String isTypeName;

    private Namer(@NotNull JsScope rootScope) {
        rootScope.declareName("Kotlin");
        JsScope kotlinScope = new JsScope(rootScope, "Kotlin standard object");
        traitName = kotlinScope.declareName(TRAIT_OBJECT_NAME);

        definePackage = kotlin("p");

        className = kotlinScope.declareName(CLASS_OBJECT_NAME);
        objectName = kotlinScope.declareName(OBJECT_OBJECT_NAME);

        isTypeName = kotlinScope.declareName("isType");
    }

    @NotNull
    public JsExpression classCreationMethodReference() {
        return kotlin(className);
    }

    @NotNull
    public JsExpression traitCreationMethodReference() {
        return kotlin(traitName);
    }

    @NotNull
    public JsExpression packageDefinitionMethodReference() {
        return definePackage;
    }

    @NotNull
    public JsExpression objectCreationMethodReference() {
        return kotlin(objectName);
    }

    @NotNull
    public static JsNameRef kotlin(@NotNull String name) {
        return new JsNameRef(name, KOTLIN_OBJECT_NAME_REF);
    }

    @NotNull
    public JsExpression isOperationReference() {
        return kotlin(isTypeName);
    }

    @NotNull
    static String generateNamespaceName(DeclarationDescriptor descriptor) {
        if (DescriptorUtils.isRootNamespace((NamespaceDescriptor) descriptor)) {
            return getRootNamespaceName();
        }
        else {
            return descriptor.getName().asString();
        }
    }
}