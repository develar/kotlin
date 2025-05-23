/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

// This file was generated automatically. See compiler/fir/tree/tree-generator/Readme.md.
// DO NOT MODIFY IT MANUALLY.

@file:Suppress("DuplicatedCode", "unused")

package org.jetbrains.kotlin.fir.expressions.builder

import kotlin.contracts.*
import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.fir.builder.FirBuilderDsl
import org.jetbrains.kotlin.fir.expressions.FirArgumentList
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.impl.FirArgumentListImpl

@FirBuilderDsl
class FirArgumentListBuilder {
    var source: KtSourceElement? = null
    val arguments: MutableList<FirExpression> = mutableListOf()

    fun build(): FirArgumentList {
        return FirArgumentListImpl(
            source,
            arguments,
        )
    }

}

@OptIn(ExperimentalContracts::class)
inline fun buildArgumentList(init: FirArgumentListBuilder.() -> Unit = {}): FirArgumentList {
    contract {
        callsInPlace(init, InvocationKind.EXACTLY_ONCE)
    }
    return FirArgumentListBuilder().apply(init).build()
}

@OptIn(ExperimentalContracts::class)
inline fun buildArgumentListCopy(original: FirArgumentList, init: FirArgumentListBuilder.() -> Unit = {}): FirArgumentList {
    contract {
        callsInPlace(init, InvocationKind.EXACTLY_ONCE)
    }
    val copyBuilder = FirArgumentListBuilder()
    copyBuilder.source = original.source
    copyBuilder.arguments.addAll(original.arguments)
    return copyBuilder.apply(init).build()
}
