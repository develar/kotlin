package org.jetbrains.kotlin.doc.highlighter2

import org.jetbrains.jet.cli.common.CompilerPlugin
import org.jetbrains.jet.cli.common.CompilerPluginContext
import java.io.File
import java.util.List
import org.jetbrains.jet.internal.com.intellij.psi.PsiFile
import org.jetbrains.jet.internal.com.intellij.openapi.vfs.local.CoreLocalVirtualFile
import org.jetbrains.jet.cli.jvm.K2JVMCompilerArguments
import org.jetbrains.kotlin.doc.KDocArguments
import org.jetbrains.kotlin.template.TextTemplate
import org.jetbrains.kotlin.template.HtmlTemplate
import org.jetbrains.kotlin.template.escapeHtml
import org.jetbrains.jet.internal.com.intellij.psi.PsiElement
import org.jetbrains.jet.internal.com.intellij.psi.impl.source.tree.LeafPsiElement
import org.jetbrains.jet.JetNodeTypes
import org.jetbrains.jet.lexer.JetTokens


class Html2CompilerPlugin(private val compilerArguments: KDocArguments) : CompilerPlugin {

    private val docOutputRoot: File
    {
        val docOutputDir = compilerArguments.docConfig.docOutputDir
        if (docOutputDir.isEmpty()) {
            throw IllegalArgumentException("empty doc output dir")
        }
        docOutputRoot = File(docOutputDir)
    }

    private val srcOutputRoot = File(docOutputRoot, "src")

    private val sourceDirs: List<File> =
        compilerArguments
                .getSourceDirs()
                .orEmpty()
                .requireNoNulls()
                .map { path -> File(path).getCanonicalFile()!! }

    private val sourceDirPaths: List<String> = sourceDirs.map { d -> d.getPath()!! }

    private fun fileToWrite(psiFile: PsiFile): String {
        val file = File((psiFile.getVirtualFile() as CoreLocalVirtualFile).getPath()).getCanonicalFile()!!
        val filePath = file.getPath()!!
        for (sourceDirPath in sourceDirPaths) {
            if (filePath.startsWith(sourceDirPath) && filePath.length() > sourceDirPath.length()) {
                val relativePath = filePath.substring(sourceDirPath.length + 1)
                return relativePath
            }
        }
        throw Exception("$file is not a child of any source roots $sourceDirPaths")
    }

    override fun processFiles(context: CompilerPluginContext) {
        srcOutputRoot.mkdirs()

        val css = javaClass<Html2CompilerPlugin>().getClassLoader()!!.getResourceAsStream(
                "org/jetbrains/kotlin/doc/highlighter2/hightlight.css")!!

        File(srcOutputRoot, "highlight.css").write { outputStream ->
            css.copyTo(outputStream)
            #()
        }

        for (file in context.getFiles().requireNoNulls()) {
            processFile(file)
        }
    }

    private fun processFile(psiFile: PsiFile) {
        val relativePath = fileToWrite(psiFile)
        val htmlFile = File(srcOutputRoot, relativePath.replaceFirst("\\.kt$", "") + ".html")

        println("Generating $htmlFile")
        htmlFile.getParentFile()!!.mkdirs()

        // see http://maven.apache.org/jxr/xref/index.html

        val template = object : HtmlTemplate() {
            override fun render() {
                html {
                    head {
                        title("${psiFile.getName()} xref")
                        linkCssStylesheet("highlight.css")
                    }
                    body {
                        table(style = "white-space: pre; font-size: 10pt; font-family: Monaco, monospace") {
                            tr {
                                td(style = "margin-right: 1.5em; text-align: right") {
                                    val text = psiFile.getText()!!
                                    val lineCount =
                                        text.count { c -> c == '\n' } + (if (text.endsWith('\n')) 0 else 1)

                                    for (i in 1..lineCount) {
                                        val label = "$i"
                                        a(name = "$label", href = "#$label") {
                                            print(i)
                                        }
                                        println()
                                    }
                                }

                                td {
                                    fun classForPsi(psi: PsiElement): String? = when (psi) {
                                        is LeafPsiElement -> {
                                            val elementType = psi.getElementType()
                                            if (elementType == JetTokens.WHITE_SPACE)
                                                null
                                            else if (elementType == JetTokens.DOC_COMMENT)
                                                "hl-comment"
                                            else if (JetTokens.KEYWORDS.contains(elementType))
                                                "hl-keyword"
                                            else
                                                // TODO
                                                elementType.toString()
                                        }
                                        // TODO
                                        else -> psi.getClass()!!.getName()
                                    }

                                    for (t in splitPsi(psiFile)) {
                                        val text = t._1
                                        val psi = t._2
                                        span(className = classForPsi(psi)) {
                                            print(text.escapeHtml())
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        template.renderTo(htmlFile)
    }

}
