package com.jimmy474.libraryindexerplugin.plugin.codesnippet

import com.intellij.openapi.util.TextRange
import com.jimmy474.libraryindexerplugin.plugin.common.GroupInfo

data class CodeSnippet(
    val fullRange: TextRange,
    val closingMarker: GroupInfo,
    val path: GroupInfo,
    val fileName: GroupInfo? = null,
    val regions: List<GroupInfo> = emptyList(),
    val annotations: List<AnnotationInfo> = emptyList(),
){
    fun toMarkdownReference(): String {
        return buildString {
            append(CodeSnippetSyntax.PREFIX)
            append(" ")
            append(path.value)
            regions.sortedWith(
                compareBy<GroupInfo> { if (it.value.startsWith(CodeSnippetSyntax.INCLUDE_SYMBOL)) 0 else 1 }
                    .thenBy { it.value.substring(1) }
            ).forEach { append(it.value) }
            if(annotations.isNotEmpty()){
                appendLine()
                appendLine(annotations.formatList())
            }else append(" ")
            append(CodeSnippetSyntax.PREFIX)
        }
    }

}

data class AnnotationInfo(
    val full: GroupInfo,
    val index: GroupInfo,
    val text: GroupInfo? = null,
)

fun List<AnnotationInfo>.formatList(): String {
    val items = sortedBy { it.index.value.toInt() }
    if (items.isEmpty()) return ""

    val maxIndexWidth = items.last().index.value.length
    val indentSize = maxIndexWidth + 1

    return items.joinToString("\n") { item ->
        val lines = item.text?.value?.lines() ?: listOf("")
        val paddedIndex = "${item.index.value}.".padStart(indentSize)
        val firstLine = "$paddedIndex ${lines.first()}"
        if (lines.size > 1) {
            val remaining = lines.drop(1).joinToString("\n"){"\t$it"}.replaceIndent(" ".repeat(indentSize*2 - 1))
            "\t$firstLine\n$remaining"
        } else {
            "\t$firstLine"
        }
    }
}
