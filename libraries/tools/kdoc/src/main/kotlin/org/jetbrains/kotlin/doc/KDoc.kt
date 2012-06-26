package org.jetbrains.kotlin.doc

import kotlin.*
import kotlin.util.*

import org.jetbrains.kotlin.doc.templates.*
import org.jetbrains.kotlin.template.TextTemplate
import org.jetbrains.kotlin.doc.model.*

import java.io.File
import java.util.List
import java.util.HashSet
import java.util.Collection

import org.jetbrains.jet.internal.com.intellij.psi.PsiElement
import org.jetbrains.jet.lang.resolve.BindingContext
import org.jetbrains.jet.lang.resolve.BindingContext.*
import org.jetbrains.jet.cli.common.CompilerPlugin
import org.jetbrains.jet.lang.psi.JetFile
import org.jetbrains.jet.lang.descriptors.NamespaceDescriptor
import org.jetbrains.jet.lexer.JetTokens
import org.jetbrains.jet.lang.descriptors.ClassDescriptor
import org.jetbrains.jet.lang.descriptors.CallableDescriptor
import org.jetbrains.jet.lang.types.JetType
import org.jetbrains.jet.lang.descriptors.DeclarationDescriptor
import org.jetbrains.jet.lang.descriptors.ModuleDescriptor
import org.jetbrains.jet.lang.resolve.java.JavaNamespaceDescriptor
import org.jetbrains.jet.lang.descriptors.ValueParameterDescriptor
import org.jetbrains.jet.lang.resolve.scopes.JetScope
import org.jetbrains.jet.lang.resolve.scopes.receivers.ExtensionReceiver
import org.jetbrains.jet.util.slicedmap.WritableSlice
import org.jetbrains.jet.lang.resolve.BindingContextUtils
import org.jetbrains.kotlin.doc.highlighter2.Html2CompilerPlugin

/** Generates the Kotlin Documentation for the model */
class KDoc(protected arguments: KDocArguments) : KModelCompilerPlugin(arguments) {

    protected override fun processModel(model: KModel) {
        val outputDir = File(arguments.apply().docOutputDir)

        // TODO allow this to be configured; maybe we use configuration on the KotlinModule
        // to define what doclets to use?
        val generator = JavadocStyleHtmlDoclet()
        generator.generate(model, outputDir)

        val srcGenerator = Html2CompilerPlugin(arguments)
        srcGenerator.generate(model, outputDir)
    }
}
