package com.jimmy474.indexerautocomplete.plugin

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
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class LibraryIndexInspection : LocalInspectionTool() {

    override fun getDisplayName(): String = "LIBRARY INDEX REFERENCE ERRORS"
    override fun getGroupDisplayName(): String = "Markdown"
    override fun getShortName(): String = "LibraryIndex"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (element !is LibraryIndexPsiElement) return

                val projectDir = element.project.guessProjectDir() ?: return
                val root = projectDir.findChild("library-index-dependency")

                if (root == null || !root.isDirectory) {
                    holder.registerProblem(element, "Library index dependency not found in project", ProblemHighlightType.WARNING)
                    return
                }

                val regex = LibraryIndex.INDEX_REFERENCE_REGEX
                val match = regex.matchEntire(element.text)

                if (match == null) {
                    holder.registerProblem(element, "Malformed library reference syntax", ProblemHighlightType.GENERIC_ERROR)
                    return
                }

                val indexReference = match.toIndexReference()

                if (indexReference.memberType != IndexReference.MemberType.NONE && indexReference.memberName == null && !indexReference.flags.isConstructor) {
                    holder.registerProblem(element, "Reference cannot end with '${LibraryIndex.MEMBER_PREFIX}', Expected a member name", ProblemHighlightType.GENERIC_ERROR, RemoveTrailingPrefixFix(element))
                    return
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

                if (indexReference.memberType != IndexReference.MemberType.NONE && indexReference.memberName != null) {
                    val jsonContent = getCachedJson(current, element.project)
                    val items = jsonContent?.get(if (indexReference.memberType == IndexReference.MemberType.METHOD) "methods" else "fields")?.jsonArray
                    val member = items?.find { it.jsonObject["name"]?.jsonPrimitive?.content == indexReference.memberName.value }?.jsonObject

                    if (member == null) {
                        val label = if (indexReference.memberType == IndexReference.MemberType.METHOD) "Method" else "Field"
                        val availableNames = items?.mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.content } ?: emptyList()

                        val typoFixes = availableNames
                            .sortedBy { levenshtein(it, indexReference.memberName.value) }
                            .take(3)

                        val fixRelativeRange = indexReference.memberName.relativeRange

                        holder.registerProblem(
                            element,
                            "$label not found",
                            ProblemHighlightType.GENERIC_ERROR,
                            *typoFixes.map { ChangeMemberNameFix(element, fixRelativeRange, it) }.toTypedArray()
                        )
                        return
                    }
                }
            }
        }
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

class RemoveTrailingPrefixFix(element: PsiElement) : LocalQuickFixAndIntentionActionOnPsiElement(element) {
    override fun getFamilyName(): String = "LibraryIndexCorrections"
    override fun getText(): String = "Remove trailing '${LibraryIndex.MEMBER_PREFIX}'"
    override fun invoke(project: Project, file: PsiFile, editor: Editor?, startElement: PsiElement, endElement: PsiElement) {
        val document = file.viewProvider.document ?: return

        val elementText = startElement.text
        val prefix = LibraryIndex.MEMBER_PREFIX

        val lastIndex = elementText.lastIndexOf(prefix)
        if (lastIndex != -1) {
            val absoluteStart = startElement.textRange.startOffset + lastIndex
            val absoluteEnd = startElement.textRange.endOffset - 1
            document.deleteString(absoluteStart, absoluteEnd)
            PsiDocumentManager.getInstance(project).commitDocument(document)
        }
    }
}