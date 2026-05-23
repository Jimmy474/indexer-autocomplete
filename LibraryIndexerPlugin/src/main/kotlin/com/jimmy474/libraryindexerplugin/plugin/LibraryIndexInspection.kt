package com.jimmy474.libraryindexerplugin.plugin

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.readText
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownFile

class LibraryIndexInspection : LocalInspectionTool() {

    override fun getDisplayName(): String = "LIBRARY INDEX REFERENCE ERRORS"
    override fun getGroupDisplayName(): String = "Markdown"
    override fun getShortName(): String = "LibraryIndex"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (element.containingFile !is MarkdownFile) return
                when (element) {
                    is LibraryIndexPsiElement -> inspectLibraryReferencePsiElement(element, holder)
                    is CodeSnippetPsiElement -> inspectCodeSnippetPsiElement(element, holder)
                }
            }
        }
    }

    private fun inspectLibraryReferencePsiElement(element: LibraryIndexPsiElement, holder: ProblemsHolder) {
        val projectDir = element.project.guessProjectDir() ?: return
        val root = projectDir.findChild("library-index-dependency")

        if (root == null || !root.isDirectory) {
            holder.registerProblem(
                element,
                "Library index dependency not found in project",
                ProblemHighlightType.WARNING
            )
            return
        }

        val regex = LibraryIndex.INDEX_REFERENCE_REGEX
        val match = regex.matchEntire(element.text)

        if (match == null) {
            holder.registerProblem(element, "Malformed library reference syntax", ProblemHighlightType.GENERIC_ERROR)
            return
        }

        val indexReference = match.toIndexReference()

        indexReference.customName?.let {
            holder.registerProblem(
                element,
                "It is not recommended to use custom names, Unless it is an emergency",
                ProblemHighlightType.WARNING
            )
            return
        }

        if (indexReference.memberType != IndexReference.MemberType.NONE && indexReference.memberName == null && !indexReference.flags.isConstructor) {
            holder.registerProblem(
                element,
                "Reference cannot end with '${LibraryIndex.MEMBER_PREFIX}', Expected a member name",
                ProblemHighlightType.GENERIC_ERROR,
                RemoveTrailingSuffixFix(element, LibraryIndex.MEMBER_PREFIX)
            )
            return
        }

        if (indexReference.memberType != IndexReference.MemberType.METHOD) {
            when {
                indexReference.flags.methodBoth -> holder.registerProblem(
                    element,
                    "Method both flag is only allowed for method or constructor reference",
                    ProblemHighlightType.GENERIC_ERROR,
                    RemoveTrailingSuffixFix(element, LibraryIndex.METHOD_BOTH_SYMBOL)
                )

                indexReference.flags.methodOnlyName -> holder.registerProblem(
                    element,
                    "Method only name flag is only allowed for method or constructor reference",
                    ProblemHighlightType.GENERIC_ERROR,
                    RemoveTrailingSuffixFix(element, LibraryIndex.METHOD_ONLY_NAME_SYMBOL)
                )

                indexReference.flags.methodOnlyType -> holder.registerProblem(
                    element,
                    "Method only type flag is only allowed for method or constructor reference",
                    ProblemHighlightType.GENERIC_ERROR,
                    RemoveTrailingSuffixFix(element, LibraryIndex.METHOD_ONLY_TYPE_SYMBOL)
                )

                indexReference.flags.methodReturnType -> holder.registerProblem(
                    element,
                    "Method return type flag is only allowed for method reference",
                    ProblemHighlightType.GENERIC_ERROR,
                    RemoveTrailingSuffixFix(element, LibraryIndex.METHOD_RETURN_TYPE_SYMBOL)
                )
            }
        } else if (indexReference.flags.isConstructor && indexReference.flags.methodReturnType) {
            holder.registerProblem(
                element,
                "Method return type flag is only allowed for method reference",
                ProblemHighlightType.GENERIC_ERROR,
                RemoveTrailingSuffixFix(element, LibraryIndex.METHOD_RETURN_TYPE_SYMBOL)
            )
        }

        var currentPartRelativeStart = 2
        var current: VirtualFile = root

        val parts = indexReference.fqn.value.split(".")
        for ((index, part) in parts.withIndex()) {
            val isLastPart = index == parts.lastIndex
            val partRelativeRange = TextRange(currentPartRelativeStart, currentPartRelativeStart + part.length)

            val next = if (isLastPart && indexReference.memberType != IndexReference.MemberType.NONE) {
                current.findChild("$part.json")
            } else if (isLastPart) {
                current.findChild("$part.json") ?: current.findChild(part)
            } else {
                current.findChild(part)
            }

            if (next == null) {
                val suggestions = getPackageOrClassFixSuggestions(current, part)
                holder.registerProblem(
                    element,
                    "Package or Class '$part' not found",
                    ProblemHighlightType.GENERIC_ERROR,
                    *suggestions.map { ChangeClassOrPackageNameFix(element, partRelativeRange, it) }.toTypedArray()
                )
                return
            }
            current = next
            currentPartRelativeStart += part.length + 1
        }

        if (indexReference.memberType == IndexReference.MemberType.FIELD && indexReference.memberName != null) {
            val jsonContent = getCachedJson(current, element.project)!!
            val fields = jsonContent["fields"]!!.jsonArray
            val field =
                fields.find { it.jsonObject["name"]!!.jsonPrimitive.content == indexReference.memberName.value }?.jsonObject
            if (field == null) {
                incorrectMemberNameProblem("Field", fields, indexReference.memberName, element, holder)
                return
            }
            if (indexReference.flags.methodBoth || indexReference.flags.methodOnlyName || indexReference.flags.methodOnlyType) {
                val suffix = when {
                    indexReference.flags.methodBoth -> LibraryIndex.METHOD_BOTH_SYMBOL.removePrefix("\\")
                    indexReference.flags.methodOnlyName -> LibraryIndex.METHOD_ONLY_NAME_SYMBOL.removePrefix("\\")
                    indexReference.flags.methodOnlyType -> LibraryIndex.METHOD_ONLY_TYPE_SYMBOL.removePrefix("\\")
                    else -> ""
                }
                holder.registerProblem(
                    element,
                    "Method only flags are not allowed for field references",
                    ProblemHighlightType.GENERIC_ERROR,
                    RemoveTrailingSuffixFix(element, suffix)
                )
            }
        }

        if (indexReference.memberType == IndexReference.MemberType.METHOD) {
            val isConstructor = indexReference.flags.isConstructor
            val paramFQNs = indexReference.params
            val jsonContent = getCachedJson(current, element.project) ?: return
            val methodName = if (isConstructor) indexReference.className!!.value else indexReference.memberName!!.value
            val overloadsArray = jsonContent[if (isConstructor) "constructors" else "methods"]?.jsonArray ?: return
            val label = if (isConstructor) "Constructor" else "Method"

            if (!isConstructor && overloadsArray.none { it.jsonObject["name"]!!.jsonPrimitive.content == methodName }) {
                incorrectMemberNameProblem("Method", overloadsArray, indexReference.memberName!!, element, holder)
                return
            }

            val overloads = if (isConstructor) {
                overloadsArray.toList()
            } else {
                overloadsArray.filter { it.jsonObject["name"]!!.jsonPrimitive.content == methodName }
            }

            if (paramFQNs == null) {
                return
            }

            if (paramFQNs.isEmpty() && overloads.size == 1) return

            if (paramFQNs.isEmpty() && overloads.size > 1) {
                holder.registerProblem(
                    element,
                    "Ambiguous reference for $label overload, to reference $label with multiple overloads you must provide the types of parameters in parenthesis",
                    ProblemHighlightType.GENERIC_ERROR
                )
                return
            }

            val overload = overloads.find { overload ->
                val types =
                    overload.jsonObject["parameters"]?.jsonArray?.map { it.jsonObject["type"]!!.jsonPrimitive.content }
                        ?: emptyList()
                types == paramFQNs.map { it.value }
            }?.jsonObject

            if (overload == null) {
                holder.registerProblem(
                    element,
                    "$label overload with given types not found",
                    ProblemHighlightType.GENERIC_ERROR
                )
            }
        }
    }

    private fun inspectCodeSnippetPsiElement(element: CodeSnippetPsiElement, holder: ProblemsHolder) {
        val projectDir = element.project.guessProjectDir() ?: return

        val match = LibraryIndex.CODE_SNIPPET_REGEX.matchEntire(element.text)
        if(match == null){
            holder.registerProblem(element, "Malformed code snippet reference", ProblemHighlightType.GENERIC_ERROR)
            return
        }

        val codeSnippet = match.toCodeSnippetReference()

        if(codeSnippet.fileName == null){
            holder.registerProblem(element, "Code snippet reference must end with a file", ProblemHighlightType.GENERIC_ERROR)
            return
        }

        val file = projectDir.findFileByRelativePath(codeSnippet.path.value.removePrefix("${LibraryIndex.ROOT_SYMBOL}/"))
        if(file == null){
            holder.registerProblem(element, "Code snippet reference file not found", ProblemHighlightType.GENERIC_ERROR)
            return
        }

        codeSnippet.region?.let {
            if(file.extension != "java"){
                holder.registerProblem(
                    element,
                    "Code snippet region is only allowed for java files",
                    ProblemHighlightType.GENERIC_ERROR,
                    RemoveTrailingSuffixFix(element, "${LibraryIndex.REGION_SYMBOL}${codeSnippet.region.value}")
                )
                return
            }
            val content = file.readText()
            if(!content.contains("// #region ${codeSnippet.region.value}")){
                holder.registerProblem(element, "Code snippet region not found in file ${codeSnippet.fileName.value}", ProblemHighlightType.GENERIC_ERROR, RemoveTrailingSuffixFix(element, "${LibraryIndex.REGION_SYMBOL}${codeSnippet.region.value}"))
                return
            }else if(!content.contains("// #endregion ${codeSnippet.region.value}")){
                holder.registerProblem(
                    element,
                    "Code snippet region did not end in file ${codeSnippet.fileName.value}, Consider closing the region with\n'// #\u200Bendregion ${codeSnippet.region.value}'",
                    ProblemHighlightType.GENERIC_ERROR
                )
                return
            }
        }

        if(element.text.endsWith(LibraryIndex.REGION_SYMBOL)){
            holder.registerProblem(element, "Code snippet region name not provided", ProblemHighlightType.GENERIC_ERROR, RemoveTrailingSuffixFix(element, LibraryIndex.REGION_SYMBOL))
            return
        }
    }


    fun incorrectMemberNameProblem(label: String,items: JsonArray?, memberInfo: GroupInfo, element: PsiElement, holder: ProblemsHolder){
        val availableNames = items?.mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.content } ?: emptyList()
        val typoFixes = availableNames.sortedBy { levenshtein(it, memberInfo.value) }.take(3)
        val fixRelativeRange = memberInfo.relativeRange

        holder.registerProblem(element, "$label '${memberInfo.value}' not found", ProblemHighlightType.GENERIC_ERROR, *typoFixes.map { ChangeMemberNameFix(element, fixRelativeRange, it) }.toTypedArray())
    }
}

class ChangeMemberNameFix(
    element: PsiElement,
    private val relativeRangeToReplace: TextRange,
    private val suggestedName: String,
) : LocalQuickFixAndIntentionActionOnPsiElement(element) {
    override fun getFamilyName(): String = "LibraryIndexCorrections"
    override fun getText(): String = "Change Member name to '$suggestedName'"
    override fun invoke(project: Project, file: PsiFile, editor: Editor?, startElement: PsiElement, endElement: PsiElement) {
        val document = file.viewProvider.document ?: return

        val elementStartOffset = startElement.textRange.startOffset
        val absoluteStart = elementStartOffset + relativeRangeToReplace.startOffset
        val absoluteEnd = elementStartOffset + relativeRangeToReplace.endOffset

        document.replaceString(absoluteStart, absoluteEnd, suggestedName)
        PsiDocumentManager.getInstance(project).commitDocument(document)
    }
}

class ChangeClassOrPackageNameFix(
    element: PsiElement,
    private val relativeRangeToReplace: TextRange,
    private val suggestedName: Pair<String, Boolean>,
) : LocalQuickFixAndIntentionActionOnPsiElement(element) {
    override fun getFamilyName(): String = "LibraryIndexCorrections"
    override fun getText(): String = "Change ${if(suggestedName.second) "Package" else "Class"} name to '${suggestedName.first}'"
    override fun invoke(project: Project, file: PsiFile, editor: Editor?, startElement: PsiElement, endElement: PsiElement) {
        val document = file.viewProvider.document ?: return
        val replacement = "${suggestedName.first}${if (suggestedName.second) "." else ""}"

        val elementStartOffset = startElement.textRange.startOffset
        val absoluteStart = elementStartOffset + relativeRangeToReplace.startOffset
        val absoluteEnd = elementStartOffset + relativeRangeToReplace.endOffset

        document.replaceString(absoluteStart, absoluteEnd, replacement)

        editor?.let {
            it.caretModel.moveToOffset(absoluteStart + replacement.length)
            if(suggestedName.second){
                AutoPopupController.getInstance(project).scheduleAutoPopup(it)
            }
        }
        PsiDocumentManager.getInstance(project).commitDocument(document)
    }
}

class RemoveTrailingSuffixFix(element: PsiElement, suffix: String) : LocalQuickFixAndIntentionActionOnPsiElement(element) {
    private val unescapedSuffix = suffix.removePrefix("\\")
    override fun getFamilyName(): String = "LibraryIndexCorrections"
    override fun getText(): String = "Remove '$unescapedSuffix'"
    override fun invoke(project: Project, file: PsiFile, editor: Editor?, startElement: PsiElement, endElement: PsiElement) {
        val document = file.viewProvider.document ?: return
        val elementText = startElement.text
        val lastIndex = elementText.lastIndexOf(unescapedSuffix)

        if (lastIndex != -1) {
            val absoluteStart = startElement.textRange.startOffset + lastIndex
            val absoluteEnd = absoluteStart + unescapedSuffix.length
            document.deleteString(absoluteStart, absoluteEnd)
            PsiDocumentManager.getInstance(project).commitDocument(document)
        }
    }
}
