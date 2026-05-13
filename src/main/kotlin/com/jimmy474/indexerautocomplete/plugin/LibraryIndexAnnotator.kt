package com.jimmy474.indexerautocomplete.plugin

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
        val root = projectDir.findChild("library-index-dependency") ?: return

        val regex = LibraryIndexCompletionProvider.INDEX_REFERENCE_FULL_REGEX
        val match = regex.find(text) ?: return

        val indexReference = match.toIndexReference()

        var current: VirtualFile = root
        for ((index, part) in indexReference.fqn.withIndex()) {
            val isLastPart = index == indexReference.fqn.lastIndex
            val next = when {
                isLastPart && indexReference.memberType != IndexReference.MemberType.NONE -> current.findChild("$part.json")
                isLastPart -> current.findChild("$part.json") ?: current.findChild(part)
                else -> current.findChild(part)
            } ?: return
            current = next
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
            val member = items?.find { it.jsonObject["name"]?.jsonPrimitive?.content == indexReference.memberName }?.jsonObject ?: return
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
}