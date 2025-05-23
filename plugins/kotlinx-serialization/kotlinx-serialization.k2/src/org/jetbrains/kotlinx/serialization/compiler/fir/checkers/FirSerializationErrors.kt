/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlinx.serialization.compiler.fir.checkers

import org.jetbrains.kotlin.diagnostics.*
import org.jetbrains.kotlin.diagnostics.rendering.BaseDiagnosticRendererFactory
import org.jetbrains.kotlin.diagnostics.KtDiagnosticsContainer
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtElement

object FirSerializationErrors : KtDiagnosticsContainer() {
    val INLINE_CLASSES_NOT_SUPPORTED by error2<KtElement, String, String>()

    val PLUGIN_IS_NOT_ENABLED by warning0<KtElement>()
    val ANONYMOUS_OBJECTS_NOT_SUPPORTED by error0<KtElement>()
    val INNER_CLASSES_NOT_SUPPORTED by error0<KtElement>()

    val EXPLICIT_SERIALIZABLE_IS_REQUIRED by warning0<KtElement>()

    val COMPANION_OBJECT_AS_CUSTOM_SERIALIZER_DEPRECATED by error1<KtElement, FirRegularClassSymbol>()
    val COMPANION_OBJECT_SERIALIZER_INSIDE_OTHER_SERIALIZABLE_CLASS by error2<KtElement, ConeKotlinType, ConeKotlinType>()
    val COMPANION_OBJECT_SERIALIZER_INSIDE_NON_SERIALIZABLE_CLASS by warning2<KtElement, ConeKotlinType, ConeKotlinType>()

    val COMPANION_OBJECT_IS_SERIALIZABLE_INSIDE_SERIALIZABLE_CLASS by warning1<KtElement, FirRegularClassSymbol>()

    val SERIALIZABLE_ANNOTATION_IGNORED by error0<KtAnnotationEntry>()
    val NON_SERIALIZABLE_PARENT_MUST_HAVE_NOARG_CTOR by error0<KtAnnotationEntry>()
    val PRIMARY_CONSTRUCTOR_PARAMETER_IS_NOT_A_PROPERTY by error0<KtAnnotationEntry>()
    val DUPLICATE_SERIAL_NAME by error1<KtAnnotationEntry, String>()
    val DUPLICATE_SERIAL_NAME_ENUM by error3<KtElement, FirClassSymbol<*>, String, String>()
    val SERIALIZER_NOT_FOUND by error1<KtElement, ConeKotlinType>()
    val SERIALIZER_NULLABILITY_INCOMPATIBLE by error2<KtElement, ConeKotlinType, ConeKotlinType>()
    val SERIALIZER_TYPE_INCOMPATIBLE by warning3<KtElement, ConeKotlinType, ConeKotlinType, ConeKotlinType>()
    val ABSTRACT_SERIALIZER_TYPE by error2<KtElement, ConeKotlinType, ConeKotlinType>()
    val LOCAL_SERIALIZER_USAGE by error1<KtElement, ConeKotlinType>()
    val CUSTOM_SERIALIZER_PARAM_ILLEGAL_COUNT by error3<KtElement, ConeKotlinType, ConeKotlinType, String>()
    val CUSTOM_SERIALIZER_PARAM_ILLEGAL_TYPE by error3<KtElement, ConeKotlinType, ConeKotlinType, String>()

    val GENERIC_ARRAY_ELEMENT_NOT_SUPPORTED by error0<KtElement>()
    val TRANSIENT_MISSING_INITIALIZER by error0<KtElement>()

    val TRANSIENT_IS_REDUNDANT by warning0<KtElement>()
    val INCORRECT_TRANSIENT by warning0<KtElement>()

    val REQUIRED_KOTLIN_TOO_HIGH by error3<KtAnnotationEntry, String, String, String>()
    val PROVIDED_RUNTIME_TOO_LOW by error3<KtAnnotationEntry, String, String, String>()

    val INCONSISTENT_INHERITABLE_SERIALINFO by error2<KtElement, ConeKotlinType, ConeKotlinType>()
    val META_SERIALIZABLE_NOT_APPLICABLE by error0<KtElement>()
    val INHERITABLE_SERIALINFO_CANT_BE_REPEATABLE by error0<KtElement>()

    val EXTERNAL_SERIALIZER_USELESS by warning1<KtElement, FirClassSymbol<*>>()
    val EXTERNAL_CLASS_NOT_SERIALIZABLE by error2<KtElement, FirClassSymbol<*>, ConeKotlinType>()
    val EXTERNAL_CLASS_IN_ANOTHER_MODULE by error2<KtElement, FirClassSymbol<*>, ConeKotlinType>()
    val EXTERNAL_SERIALIZER_NO_SUITABLE_CONSTRUCTOR by error3<KtElement, FirClassSymbol<*>, ConeKotlinType, String>()

    val KEEP_SERIALIZER_ANNOTATION_USELESS by error0<KtElement>()
    val KEEP_SERIALIZER_ANNOTATION_ON_POLYMORPHIC by error0<KtElement>()

    val PROTOBUF_PROTO_NUM_DUPLICATED by warning2<KtAnnotationEntry, String, String>()

    val JSON_FORMAT_REDUNDANT_DEFAULT by warning0<KtElement>()
    val JSON_FORMAT_REDUNDANT by warning0<KtElement>()

    override fun getRendererFactory(): BaseDiagnosticRendererFactory = KtDefaultErrorMessagesSerialization
}
