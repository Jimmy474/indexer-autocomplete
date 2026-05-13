package com.jimmy474.indexerautocomplete.plugin

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import kotlinx.serialization.json.*
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownFile

object LibraryIndexColors {
    val MACRO_PREFIX = TextAttributesKey.createTextAttributesKey(
        "LIBRARY_INDEX_PREFIX", DefaultLanguageHighlighterColors.VALID_STRING_ESCAPE
    )
    val MACRO_TEXT = TextAttributesKey.createTextAttributesKey(
        "LIBRARY_INDEX_TEXT", DefaultLanguageHighlighterColors.STRING
    )

    val MACRO_METHOD = TextAttributesKey.createTextAttributesKey(
        "LIBRARY_INDEX_METHOD", DefaultLanguageHighlighterColors.INSTANCE_METHOD
    )

    val MACRO_METHOD_STATIC = TextAttributesKey.createTextAttributesKey(
        "LIBRARY_INDEX_METHOD_STATIC", DefaultLanguageHighlighterColors.STATIC_METHOD
    )

    val MACRO_FIELD = TextAttributesKey.createTextAttributesKey(
        "LIBRARY_INDEX_FIELD", DefaultLanguageHighlighterColors.INSTANCE_FIELD
    )

    val MACRO_FIELD_STATIC = TextAttributesKey.createTextAttributesKey(
        "LIBRARY_INDEX_FIELD_STATIC", DefaultLanguageHighlighterColors.STATIC_FIELD
    )

    val MACRO_CLASS = TextAttributesKey.createTextAttributesKey(
        "LIBRARY_INDEX_CLASS", DefaultLanguageHighlighterColors.KEYWORD
    )
}

class LibraryIndexAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (element.containingFile !is MarkdownFile) return
        if (element !is LibraryIndexPsiElement) return

        val text = element.text
        val startOffset = element.textRange.startOffset
        val endOffset = element.textRange.endOffset

        holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
            .range(TextRange(startOffset, startOffset + 2))
            .textAttributes(LibraryIndexColors.MACRO_PREFIX)
            .create()

        holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
            .range(TextRange(endOffset - 1, endOffset))
            .textAttributes(LibraryIndexColors.MACRO_PREFIX)
            .create()

        holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
            .range(TextRange(startOffset + 2, endOffset - 1))
            .textAttributes(LibraryIndexColors.MACRO_TEXT)
            .create()


        val project = element.project
        val projectDir = project.guessProjectDir() ?: return
        val root = projectDir.findChild("library-index-dependency")

        if (root == null || !root.isDirectory) {
            holder.newAnnotation(HighlightSeverity.WARNING, "Library index dependency not found in project").range(element.textRange).create()
            return
        }

        val regex = LibraryIndexCompletionProvider.INDEX_REFERENCE_FULL_REGEX
        val match = regex.find(text)

        if (match == null) {
            holder.newAnnotation(HighlightSeverity.ERROR, "Malformed library reference syntax").range(element.textRange).create()
            return
        }

        val indexReference = match.toIndexReference()

        if (indexReference.memberType != IndexReference.MemberType.NONE && indexReference.memberName.isNullOrBlank()) {
            holder.newAnnotation(HighlightSeverity.ERROR, "Reference cannot end with '${LibraryIndexCompletionProvider.MEMBER_PREFIX}', Expected a member name")
                .range(TextRange(endOffset - 2, endOffset - 1))
                .withFix(RemoveTrailingPrefixFix(element))
                .create()
            return
        }

        var currentPartAbsoluteStart = startOffset + 2
        var current: VirtualFile = root
        for ((index, part) in indexReference.fqn.withIndex()) {
            val isLastPart = index == indexReference.fqn.lastIndex
            val partAbsoluteRange = TextRange(currentPartAbsoluteStart, currentPartAbsoluteStart + part.length)
            val next = if (isLastPart && indexReference.memberType != IndexReference.MemberType.NONE) {
                current.findChild("$part.json")
            } else if (isLastPart) {
                current.findChild("$part.json") ?: current.findChild(part)
            } else {
                current.findChild(part)
            }

            if (next == null) {
                val annotationBuilder = holder.newAnnotation(HighlightSeverity.ERROR, "Package or Class '$part' not found").range(partAbsoluteRange)
                val availableNames = current.children
                    .filter { it.isDirectory || it.extension == "json" }
                    .map { it.nameWithoutExtension to it.isDirectory }
                    .distinct()

                val typoFixes = availableNames
                    .sortedBy { levenshtein(it.first, part) }
                    .take(3)

                for (suggestedName in typoFixes) {
                    annotationBuilder.withFix(ChangeClassOrPackageNameFix(element, partAbsoluteRange, suggestedName))
                }

                annotationBuilder.create()
                return
            }
            current = next
            currentPartAbsoluteStart += part.length + 1
        }

        if(!current.isDirectory){
            indexReference.classNameRange?.let {
                holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                    .range(TextRange(startOffset + it.startOffset, startOffset + it.endOffset))
                    .textAttributes(LibraryIndexColors.MACRO_CLASS)
                    .create()
            }
        }

        if (indexReference.memberType != IndexReference.MemberType.NONE) {
            val jsonContent = getCachedJson(current, project)
            val key = if (indexReference.memberType == IndexReference.MemberType.METHOD) "methods" else "fields"
            val items = jsonContent?.get(key)?.jsonArray
            val member = items?.find { it.jsonObject["name"]?.jsonPrimitive?.content == indexReference.memberName }?.jsonObject

            if (member == null) {
                val label = if (indexReference.memberType == IndexReference.MemberType.METHOD) "Method" else "Field"
                val annotationBuilder = holder.newAnnotation(HighlightSeverity.ERROR, "$label '${indexReference.memberName}' not found in ${current.nameWithoutExtension} Class").range(element.textRange)
                val availableNames = items?.mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.content } ?: emptyList()

                val typoFixes = availableNames
                    .sortedBy { levenshtein(it, indexReference.memberName!!) }
                    .take(3)

                indexReference.memberNameRange?.let { memberRange ->
                    val absoluteRange = TextRange(startOffset + memberRange.startOffset, startOffset + memberRange.endOffset)

                    for (suggestedName in typoFixes) {
                        annotationBuilder.withFix(ChangeMemberNameFix(element, absoluteRange, suggestedName))
                    }
                }

                annotationBuilder.create()
                return
            }

            val isStatic = member["declaration"]?.jsonObject?.get("flags")?.jsonObject?.get("isStatic")?.jsonPrimitive?.boolean ?: false


            val typeHighlighter = when (indexReference.memberType) {
                IndexReference.MemberType.METHOD -> if(isStatic) LibraryIndexColors.MACRO_METHOD_STATIC else LibraryIndexColors.MACRO_METHOD
                IndexReference.MemberType.FIELD -> if(isStatic) LibraryIndexColors.MACRO_FIELD_STATIC else LibraryIndexColors.MACRO_FIELD
                else -> LibraryIndexColors.MACRO_TEXT
            }
            indexReference.memberNameRange?.let {
                var additionalOffset = if(indexReference.memberType == IndexReference.MemberType.METHOD) 2 else 0
                if(indexReference.fullDisplayFlag) additionalOffset += 3
                holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                    .range(TextRange(startOffset + it.startOffset, startOffset + it.endOffset + additionalOffset))
                    .textAttributes(typeHighlighter)
                    .create()
            }
        }
    }

    private fun getCachedJson(file: VirtualFile, project: Project): JsonObject? {
        val psiFile = PsiManager.getInstance(project).findFile(file) ?: return null

        return CachedValuesManager.getCachedValue(psiFile) {
            val jsonText = psiFile.text
            val jsonObject = try {
                Json.parseToJsonElement(jsonText).jsonObject
            } catch (_: Exception) {
                null
            }
            CachedValueProvider.Result.create(jsonObject, psiFile)
        }
    }
}

class ChangeMemberNameFix(
    element: PsiElement,
    private val absoluteRangeToReplace: TextRange,
    private val suggestedName: String,
) : LocalQuickFixAndIntentionActionOnPsiElement(element) {
    override fun getFamilyName(): String = "LibraryIndexCorrections"
    override fun getText(): String = "Change Member name to '$suggestedName'"
    override fun invoke(project: Project, file: PsiFile, editor: Editor?, startElement: PsiElement, endElement: PsiElement) {
        val document = file.viewProvider.document ?: return
        document.replaceString(absoluteRangeToReplace.startOffset, absoluteRangeToReplace.endOffset, suggestedName)
        PsiDocumentManager.getInstance(project).commitDocument(document)
    }
}

class ChangeClassOrPackageNameFix(
    element: PsiElement,
    private val absoluteRangeToReplace: TextRange,
    private val suggestedName: Pair<String, Boolean>,
) : LocalQuickFixAndIntentionActionOnPsiElement(element) {
    override fun getFamilyName(): String = "LibraryIndexCorrections"
    override fun getText(): String = "Change ${if(suggestedName.second) "Package" else "Class"} name to '${suggestedName.first}'"
    override fun invoke(project: Project, file: PsiFile, editor: Editor?, startElement: PsiElement, endElement: PsiElement) {
        val document = file.viewProvider.document ?: return
        val replacement = "${suggestedName.first}${if (suggestedName.second) "." else ""}"
        document.replaceString(absoluteRangeToReplace.startOffset, absoluteRangeToReplace.endOffset, replacement)
        editor?.let {
            it.caretModel.moveToOffset(absoluteRangeToReplace.startOffset + replacement.length)
            if(suggestedName.second){
                AutoPopupController.getInstance(project).scheduleAutoPopup(it)
            }
        }
        PsiDocumentManager.getInstance(project).commitDocument(document)
    }
}

class RemoveTrailingPrefixFix(element: PsiElement) : LocalQuickFixAndIntentionActionOnPsiElement(element) {
    override fun getFamilyName(): String = "LibraryIndexCorrections"
    override fun getText(): String = "Remove trailing '${LibraryIndexCompletionProvider.MEMBER_PREFIX}'"
    override fun invoke(project: Project, file: PsiFile, editor: Editor?, startElement: PsiElement, endElement: PsiElement) {
        val document = file.viewProvider.document ?: return
        val elementText = startElement.text
        val prefix = LibraryIndexCompletionProvider.MEMBER_PREFIX

        val lastIndex = elementText.lastIndexOf(prefix)
        if (lastIndex != -1) {
            val absoluteStart = startElement.textRange.startOffset + lastIndex
            val absoluteEnd = startElement.textRange.endOffset - 1
            document.deleteString(absoluteStart, absoluteEnd)
            PsiDocumentManager.getInstance(project).commitDocument(document)
        }
    }
}