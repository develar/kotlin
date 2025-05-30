/*
 * Copyright 2010-2025 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.arguments.dsl.types

import kotlinx.serialization.Serializable
import org.jetbrains.kotlin.arguments.dsl.base.KotlinReleaseVersion
import org.jetbrains.kotlin.arguments.dsl.base.ReleaseDependent

@Serializable
sealed interface KotlinArgumentValueType<T : Any> {
    val isNullable: ReleaseDependent<Boolean>
    val defaultValue: ReleaseDependent<T?>

    fun stringRepresentation(value: T?): String?
}

@Serializable
class BooleanType(
    override val isNullable: ReleaseDependent<Boolean> = ReleaseDependent(true),
    override val defaultValue: ReleaseDependent<Boolean?> = ReleaseDependent(null),
) : KotlinArgumentValueType<Boolean> {
    override fun stringRepresentation(value: Boolean?): String? {
        return value?.toString()
    }
}

@Serializable
class KotlinVersionType(
    override val isNullable: ReleaseDependent<Boolean> = ReleaseDependent(true),
    override val defaultValue: ReleaseDependent<KotlinVersion?> = ReleaseDependent(
        KotlinVersion.v2_0,
        KotlinReleaseVersion.v1_4_0..KotlinReleaseVersion.v1_9_20 to KotlinVersion.v1_9
    )
) : KotlinArgumentValueType<KotlinVersion> {
    override fun stringRepresentation(value: KotlinVersion?): String? {
        return value?.versionName?.valueOrNullStringLiteral
    }
}

@Serializable
class KotlinJvmTargetType(
    override val isNullable: ReleaseDependent<Boolean> = ReleaseDependent(true),
    override val defaultValue: ReleaseDependent<JvmTarget?> = ReleaseDependent(
        JvmTarget.jvm1_8,
        KotlinReleaseVersion.v1_0_0..KotlinReleaseVersion.v1_9_20 to JvmTarget.jvm1_6
    )
) : KotlinArgumentValueType<JvmTarget> {
    override fun stringRepresentation(value: JvmTarget?): String? {
        return value?.targetName?.valueOrNullStringLiteral
    }
}

@Serializable
class StringType(
    override val isNullable: ReleaseDependent<Boolean> = ReleaseDependent(true),
    override val defaultValue: ReleaseDependent<String?> = ReleaseDependent(null),
) : KotlinArgumentValueType<String> {
    override fun stringRepresentation(value: String?): String? {
        if (value == null) return null
        return "\"$value\""
    }
}

@Serializable
class IntType(
    override val isNullable: ReleaseDependent<Boolean> = ReleaseDependent(false),
    override val defaultValue: ReleaseDependent<Int?> = ReleaseDependent(null),
) : KotlinArgumentValueType<Int> {
    override fun stringRepresentation(value: Int?): String {
        return "\"$value\""
    }
}

@Serializable
class StringArrayType(
    override val defaultValue: ReleaseDependent<Array<String>?> = ReleaseDependent(null),
) : KotlinArgumentValueType<Array<String>> {
    override val isNullable: ReleaseDependent<Boolean> = ReleaseDependent(true)

    override fun stringRepresentation(value: Array<String>?): String {
        if (value == null) return "null"
        return value.joinToString(separator = ", ", prefix = "arrayOf(", postfix = ")") { "\"$it\""}
    }
}

@Serializable
class KotlinExplicitApiModeType(
    override val isNullable: ReleaseDependent<Boolean> = ReleaseDependent(false),
    override val defaultValue: ReleaseDependent<ExplicitApiMode?> = ReleaseDependent(ExplicitApiMode.disable),
) : KotlinArgumentValueType<ExplicitApiMode> {
    override fun stringRepresentation(value: ExplicitApiMode?): String {
        return value?.modeName.valueOrNullStringLiteral
    }
}

@Serializable
class ReturnValueCheckerModeType(
    override val isNullable: ReleaseDependent<Boolean> = ReleaseDependent(false),
    override val defaultValue: ReleaseDependent<ReturnValueCheckerMode?> = ReleaseDependent(ReturnValueCheckerMode.disabled),
) : KotlinArgumentValueType<ReturnValueCheckerMode> {
    override fun stringRepresentation(value: ReturnValueCheckerMode?): String {
        return value?.modeState.valueOrNullStringLiteral
    }
}

private val String?.valueOrNullStringLiteral: String
    get() = "\"${this}\""
