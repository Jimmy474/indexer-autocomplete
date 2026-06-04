package com.jimmy474.libraryindexerplugin.plugin.libraryindexer

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.jimmy474.libraryindexerplugin.plugin.common.IndexerHighlightColors
import com.jimmy474.libraryindexerplugin.plugin.common.plus
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownFile

class LibraryIndexAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (element.containingFile !is MarkdownFile) return
        if (element is LibraryIndexPsiElement) annotateLibraryIndexPsiElement(element, holder)
    }

    private fun annotateLibraryIndexPsiElement(element: LibraryIndexPsiElement, holder: AnnotationHolder) {
        val text = element.text
        val startOffset = element.textRange.startOffset
        val endOffset = element.textRange.endOffset

        holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
            .range(TextRange(startOffset, startOffset + 2))
            .textAttributes(IndexerHighlightColors.MACRO_PREFIX)
            .create()

        holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
            .range(TextRange(endOffset - 1, endOffset))
            .textAttributes(IndexerHighlightColors.MACRO_PREFIX)
            .create()

        holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
            .range(TextRange(startOffset + 2, endOffset - 1))
            .textAttributes(IndexerHighlightColors.MACRO_TEXT)
            .create()


        val project = element.project
        val projectDir = project.guessProjectDir() ?: return
        val root = projectDir.findChild("library-index-dependency") ?: return

        val regex = LibraryIndex.INDEX_REFERENCE_REGEX
        val match = regex.matchEntire(text) ?: return

        val indexReference = match.toIndexReference()

        var current: VirtualFile = root
        val parts = indexReference.fqn.value.split(".")
        for ((index, part) in parts.withIndex()) {
            val isLastPart = index == parts.lastIndex
            val next = when {
                isLastPart && indexReference.memberType != IndexReference.MemberType.NONE -> current.findChild("$part.json")
                isLastPart -> current.findChild("$part.json") ?: current.findChild(part)
                else -> current.findChild(part)
            } ?: return
            current = next
        }

        if (!current.isDirectory) {
            indexReference.className?.relativeRange?.let {
                holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                    .range(it + startOffset)
                    .textAttributes(IndexerHighlightColors.MACRO_CLASS)
                    .create()
            }
        }

        if (indexReference.memberType != IndexReference.MemberType.NONE && !indexReference.flags.isConstructor) {
            val jsonContent = getCachedJson(current, project)
            val key = if (indexReference.memberType == IndexReference.MemberType.METHOD) "methods" else "fields"
            val items = jsonContent?.get(key)?.jsonArray
            val member =
                items?.find { it.jsonObject["name"]?.jsonPrimitive?.content == indexReference.memberName?.value }?.jsonObject
                    ?: return
            val isStatic =
                member["declaration"]?.jsonObject?.get("flags")?.jsonObject?.get("isStatic")?.jsonPrimitive?.boolean
                    ?: false
            val typeHighlighter = when (indexReference.memberType) {
                IndexReference.MemberType.METHOD -> if (isStatic) IndexerHighlightColors.MACRO_METHOD_STATIC else IndexerHighlightColors.MACRO_METHOD
                IndexReference.MemberType.FIELD -> if (isStatic) IndexerHighlightColors.MACRO_FIELD_STATIC else IndexerHighlightColors.MACRO_FIELD
            }

            indexReference.memberName?.relativeRange?.let {
                holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                    .range(it + startOffset)
                    .textAttributes(typeHighlighter)
                    .create()
            }
        }

        indexReference.params?.forEach {
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(it.relativeRange + startOffset)
                .textAttributes(IndexerHighlightColors.MACRO_PARAMETER)
                .create()
        }

        indexReference.flags.relativeRange.takeIf { !it.isEmpty }?.let {
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(it + startOffset)
                .textAttributes(IndexerHighlightColors.MACRO_FLAGS)
                .create()
        }

        indexReference.customName?.relativeRange?.takeIf { !it.isEmpty }?.let {
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(it + startOffset)
                .textAttributes(IndexerHighlightColors.MACRO_CUSTOM_NAME)
                .create()
        }
    }
}
