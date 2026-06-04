package com.jimmy474.libraryindexerplugin.plugin.codesnippet

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.readText
import com.intellij.psi.*
import com.jimmy474.libraryindexerplugin.plugin.common.GroupInfo
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownFile

class CodeSnippetInspection : LocalInspectionTool() {

    override fun getDisplayName(): String = "CODE SNIPPET REFERENCE ERRORS"
    override fun getGroupDisplayName(): String = "Markdown"
    override fun getShortName(): String = "CodeSnippet"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (element.containingFile !is MarkdownFile || element !is CodeSnippetPsiElement) return
                inspectCodeSnippetPsiElement(element, holder)
            }
        }
    }

    private fun inspectCodeSnippetPsiElement(element: CodeSnippetPsiElement, holder: ProblemsHolder) {
        val projectDir = element.project.guessProjectDir() ?: return

        val match = CodeSnippetSyntax.REGEX.matchEntire(element.text)
        if(match == null){
            holder.registerProblem(element, "Malformed code snippet reference", ProblemHighlightType.GENERIC_ERROR)
            return
        }

        val codeSnippet = match.toCodeSnippetReference()

        if(codeSnippet.fileName == null){
            holder.registerProblem(element, "Code snippet reference must end with a file", ProblemHighlightType.GENERIC_ERROR, codeSnippet.path.relativeRange)
            return
        }

        val file = projectDir.findFileByRelativePath(codeSnippet.path.value.removePrefix("${CodeSnippetSyntax.ROOT_SYMBOL}/"))

        if(file == null){
            holder.registerProblem(element, "Code snippet reference file not found", ProblemHighlightType.GENERIC_ERROR, codeSnippet.fileName.relativeRange)
            return
        }
        val psiFile = PsiManager.getInstance(element.project).findFile(file)

        if(codeSnippet.regions.isNotEmpty()){
            val content = psiFile?.text ?: file.readText()
            codeSnippet.regions.forEach { region ->
                if(region.value.length == 1){
                    holder.registerProblem(element, "Code snippet region name not provided", ProblemHighlightType.GENERIC_ERROR, region.relativeRange, RemoveSubstringFix(element, region.value, true))
                    return@forEach
                }
                val regionName = region.value.substring(1)
                if(!content.contains("// #region $regionName")){
                    holder.registerProblem(element, "Code snippet region not found in file ${codeSnippet.fileName.value}", ProblemHighlightType.GENERIC_ERROR, region.relativeRange, RemoveSubstringFix(element, region.value, true))
                    return@forEach
                }else if(!content.contains("// #endregion $regionName")){
                    holder.registerProblem(
                        element,
                        "Code snippet region did not end in file ${codeSnippet.fileName.value}, Consider closing the region with\n'// #\u200Bendregion $regionName'",
                        ProblemHighlightType.GENERIC_ERROR,
                        region.relativeRange
                    )
                    return@forEach
                }
            }

            codeSnippet.regions
                .filter { it.value.length > 1 }
                .groupBy { it.value.substring(1) }
                .filterValues { it.size > 1 }
                .forEach { (regionName, regions) ->
                    regions.drop(1).forEach { region ->
                        holder.registerProblem(
                            element,
                            "Code snippet region $regionName is used multiple times",
                            ProblemHighlightType.GENERIC_ERROR,
                            region.relativeRange,
                            RemoveSubstringFix(element, region.value, true, regionName)
                        )
                    }
                }
        }

        val snippet = getSnippet(element)?.content?.let{
            CodeSnippetSyntax.CODE_ANNOTATION_MARKER.findAll(it).map { match -> match.groups["index"]!!.value.substring(1) }.toList()
        } ?: emptyList()

        codeSnippet.annotations.forEach { annotation ->
            if(annotation.index.value !in snippet){
                holder.registerProblem(
                    element,
                    "Code snippet annotation not used and it can be safely removed",
                    ProblemHighlightType.WARNING,
                    annotation.full.relativeRange,
                    RemoveSubstringFix(element, annotation.full.value, true, annotation.index.value)
                )
            }
        }

        val provided = codeSnippet.annotations.map{ it.index.value }
        snippet.forEach {
            if(it !in provided){
                holder.registerProblem(element, "Code snippet annotation $it not provided", ProblemHighlightType.GENERIC_ERROR, AddAnnotationFix(element, it))
            }
        }

        codeSnippet.annotations.groupBy { it.index.value }.filter { it.value.size > 1 }.forEach { (index, annotations) ->
            annotations.forEach { annotation ->
                holder.registerProblem(
                    element,
                    "Code snippet annotation $index is provided multiple times\n${annotation.full.value}",
                    ProblemHighlightType.WARNING,
                    annotation.full.relativeRange,
                    RemoveSubstringFix(element, annotation.full.value, true, index)
                )
            }
        }

        if(element.text != codeSnippet.toMarkdownReference()){
            holder.registerProblem(element, "Code snippet format is not ideal", ProblemHighlightType.WEAK_WARNING, FormatFix(element,codeSnippet))
        }

    }
}

class RemoveSubstringFix(element: PsiElement, val remove: String, val isCodeSnippet: Boolean = false, val customName: String? = null) : LocalQuickFixAndIntentionActionOnPsiElement(element) {
    override fun getFamilyName(): String = if(isCodeSnippet) "CodeSnippetCorrections" else "LibraryIndexCorrections"
    override fun getText(): String = "Remove '${customName ?: remove}'"
    override fun invoke(project: Project, file: PsiFile, editor: Editor?, startElement: PsiElement, endElement: PsiElement) {
        val document = file.viewProvider.document ?: return
        val elementText = startElement.text
        val lastIndex = elementText.lastIndexOf(remove)

        if (lastIndex != -1) {
            val absoluteStart = startElement.textRange.startOffset + lastIndex
            val absoluteEnd = absoluteStart + remove.length
            document.deleteString(absoluteStart, absoluteEnd)
            PsiDocumentManager.getInstance(project).commitDocument(document)
        }
    }
}

class AddAnnotationFix(element: PsiElement, val annotation: String) : LocalQuickFixAndIntentionActionOnPsiElement(element) {
    override fun getFamilyName(): String = "CodeSnippetCorrections"
    override fun getText(): String = "Add annotation '$annotation'"
    override fun invoke(project: Project, file: PsiFile, editor: Editor?, startElement: PsiElement, endElement: PsiElement) {
        val document = file.viewProvider.document ?: return
        val newText = CodeSnippetSyntax.REGEX.matchEntire((startElement as CodeSnippetPsiElement).text)!!.toCodeSnippetReference().let{ snippet ->
            snippet.copy(
                annotations = (snippet.annotations + AnnotationInfo(GroupInfo("", TextRange.EMPTY_RANGE), GroupInfo(annotation, TextRange.EMPTY_RANGE), GroupInfo("annotation text", TextRange.EMPTY_RANGE))).sortedBy { it.index.value }
            )
        }.toMarkdownReference()
        document.replaceString(startElement.textRange.startOffset, startElement.textRange.endOffset, newText)
        PsiDocumentManager.getInstance(project).commitDocument(document)
    }
}

class FormatFix(element: PsiElement, val codeSnippet: CodeSnippet) : LocalQuickFixAndIntentionActionOnPsiElement(element) {
    override fun getFamilyName(): String = "CodeSnippetCorrections"
    override fun getText(): String = "Format code snippet"
    override fun invoke(project: Project, file: PsiFile, editor: Editor?, startElement: PsiElement, endElement: PsiElement) {
        val document = file.viewProvider.document ?: return
        document.replaceString(startElement.textRange.startOffset, startElement.textRange.endOffset, codeSnippet.toMarkdownReference())
        PsiDocumentManager.getInstance(project).commitDocument(document)
    }
}
