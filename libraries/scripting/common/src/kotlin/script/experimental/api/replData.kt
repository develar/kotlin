/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package kotlin.script.experimental.api

import java.io.Serializable
import kotlin.script.experimental.host.ScriptingHostConfigurationKeys
import kotlin.script.experimental.util.PropertiesCollection

// Warning: during the transition to the new REPL infrastructure, should be kept in sync with REPL_CODE_LINE_FIRST_NO/REPL_CODE_LINE_FIRST_GEN
const val REPL_SNIPPET_FIRST_NO = 1
const val REPL_SNIPPET_FIRST_GEN = 1

interface ReplSnippetId : Serializable, Comparable<ReplSnippetId> {
    val no: Int
    val generation: Int
}

data class ReplSnippetIdImpl(override val no: Int, override val generation: Int, private val codeHash: Int) : ReplSnippetId, Serializable {

    constructor(no: Int, generation: Int, code: SourceCode) : this(no, generation, code.text.hashCode())

    override fun compareTo(other: ReplSnippetId): Int = (other as? ReplSnippetIdImpl)?.let { otherId ->
        no.compareTo(otherId.no).takeIf { it != 0 }
            ?: generation.compareTo(otherId.generation).takeIf { it != 0 }
            ?: codeHash.compareTo(otherId.codeHash)
    } ?: -1

    companion object {
        private val serialVersionUID: Long = 1L
    }
}

interface ReplScriptingHostConfigurationKeys

open class ReplScriptingHostConfigurationBuilder : PropertiesCollection.Builder(),
    ReplScriptingHostConfigurationKeys {
    companion object : ReplScriptingHostConfigurationKeys
}

val ScriptingHostConfigurationKeys.repl
    get() = ReplScriptingHostConfigurationBuilder()


interface ReplScriptCompilationConfigurationKeys

open class ReplScriptCompilationConfigurationBuilder : PropertiesCollection.Builder(),
    ReplScriptCompilationConfigurationKeys {
    companion object : ReplScriptCompilationConfigurationKeys
}

val ScriptCompilationConfigurationKeys.repl
    get() = ReplScriptCompilationConfigurationBuilder()


/**
 * The prefix of the name of the generated script class field to assign the snipped results to, empty means disabled
 * see also ScriptCompilationConfigurationKeys.resultField
 */
val ReplScriptCompilationConfigurationKeys.resultFieldPrefix by PropertiesCollection.key<String>("res")

typealias MakeSnippetIdentifier = (ScriptCompilationConfiguration, ReplSnippetId) -> String

/**
 * The REPL snippet class identifier generation function
 */
val ReplScriptCompilationConfigurationKeys.makeSnippetIdentifier by PropertiesCollection.key<MakeSnippetIdentifier>(
    { _, snippetId ->
        makeDefaultSnippetIdentifier(snippetId)
    })

fun makeDefaultSnippetIdentifier(snippetId: ReplSnippetId) =
    "Line_${snippetId.no}${if (snippetId.generation > REPL_SNIPPET_FIRST_GEN) "_gen_${snippetId.generation}" else ""}"


