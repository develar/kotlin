package org.jetbrains.kotlin.js.stubGenerator

import java.io.File
import java.util.HashSet

fun main(args: Array<String>) {
    generateJs(File(args[0]))
}

private fun generateJs(stubDir: File) {
    val jsGeneratedDir = File("js.libraries/generated")
    val ignoredEsClasses = HashSet<String>()
    ignoredEsClasses.add("Object")
    ignoredEsClasses.add("Array")
    ignoredEsClasses.add("String")
    ignoredEsClasses.add("Function")
    ignoredEsClasses.add("JSON")
    ignoredEsClasses.add("document")
    ignoredEsClasses.add("Global")
    ignoredEsClasses.add("Boolean")
    ignoredEsClasses.add("Arguments")
    ignoredEsClasses.add("Number")
    ignoredEsClasses.add("RegExp")
    ignoredEsClasses.add("Error")

    val ecma5Generator = JavaScriptStubGenerator("js")
    ecma5Generator.generate(File(stubDir, "EcmaScript.xml"), ignoredEsClasses)
    ecma5Generator.generate(File(stubDir, "EcmaScript5.xml"), ignoredEsClasses)
    ecma5Generator.writeTo(File(jsGeneratedDir, "ecmaScript5.kt"))

    val domGenerator = JavaScriptStubGenerator("org.w3c.dom")
    domGenerator.generate(File(stubDir, "DOMCore.xml"), ignoredEsClasses)
    domGenerator.generate(File(stubDir, "DOMEvents.xml"))
    domGenerator.generate(File(stubDir, "DOMTraversalAndRange.xml"))
    domGenerator.writeTo(File(jsGeneratedDir, "dom.kt"))

    val htmlGenerator = JavaScriptStubGenerator("html")
    htmlGenerator.append("\n\nimport org.w3c.dom.*")
    htmlGenerator.generate(File(stubDir, "DHTML.xml"), ignoredEsClasses)
    htmlGenerator.generate(File(stubDir, "AJAX.xml"))
    htmlGenerator.generate(File(stubDir, "HTML5.xml"))
    htmlGenerator.writeTo(File(jsGeneratedDir, "html5.kt"))
}