package com.jimmy474.libraryindexerplugin.plugin.codesnippet

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.jimmy474.libraryindexerplugin.plugin.common.IndexerHighlightColors
import com.jimmy474.libraryindexerplugin.plugin.common.plus
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownFile

class CodeSnippetAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (element.containingFile !is MarkdownFile || element !is CodeSnippetPsiElement) return

        val startOffset = element.textRange.startOffset
        val endOffset = element.textRange.endOffset

        val match = CodeSnippetSyntax.REGEX.matchEntire(element.text) ?: return
        val codeSnippet = match.toCodeSnippetReference()

        holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
            .range(TextRange(startOffset, startOffset + 3))
            .textAttributes(IndexerHighlightColors.MACRO_PREFIX)
            .create()

        codeSnippet.closingMarker.let{
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(it.relativeRange + startOffset)
                .textAttributes(IndexerHighlightColors.MACRO_PREFIX)
                .create()
        }

        holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
            .range(TextRange(startOffset + 4, endOffset))
            .textAttributes(IndexerHighlightColors.MACRO_TEXT)
            .create()

        codeSnippet.fileName?.relativeRange?.let {
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(it + startOffset)
                .textAttributes(IndexerHighlightColors.MACRO_CLASS)
                .create()
        }

        codeSnippet.regions.forEach {
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(it.relativeRange + startOffset)
                .textAttributes(if(it.value.startsWith(CodeSnippetSyntax.INCLUDE_SYMBOL)) IndexerHighlightColors.MACRO_FIELD else IndexerHighlightColors.MACRO_METHOD)
                .create()
        }

        codeSnippet.annotations.forEach { annotation ->
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(annotation.index.relativeRange + startOffset)
                .textAttributes(IndexerHighlightColors.MACRO_FLAGS)
                .create()
            annotation.text?.let {
                holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                    .range(it.relativeRange + startOffset)
                    .textAttributes(IndexerHighlightColors.MACRO_PARAMETER)
                    .create()
            }
        }
    }
}
