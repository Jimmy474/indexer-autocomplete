package com.jimmy474.libraryindexerplugin.plugin.codesnippet

import com.intellij.openapi.util.TextRange
import com.jimmy474.libraryindexerplugin.plugin.common.GroupInfo
import com.jimmy474.libraryindexerplugin.plugin.common.plus
import com.jimmy474.libraryindexerplugin.plugin.common.toTextRange
import org.intellij.lang.annotations.Language

object CodeSnippetSyntax {
    const val PREFIX = "<<<"
    const val INCLUDE_SYMBOL = "#"
    const val EXCLUDE_SYMBOL = "!"
    const val REGION_SYMBOL = "[$INCLUDE_SYMBOL$EXCLUDE_SYMBOL]"
    const val ROOT_SYMBOL = "@"
    const val ANNOTATION_SYMBOL = "@"
    @Language("RegExp")
    const val VALID_ID = """[^/\\$INCLUDE_SYMBOL$EXCLUDE_SYMBOL\s]+"""

    @Language("RegExp")
    const val ANNOTATION = """\r?\n\s*(?<index>\d+)\.\s*(?<text>[\s\S]*?)(?=\r?\n\s*(?:\d+\.|$PREFIX)|\z)"""

    const val ANNOTATIONS = """(?<annotations>$ANNOTATION+)"""

    val CODE_ANNOTATION_MARKER = Regex("""/\*\s*(?<index>$ANNOTATION_SYMBOL\d+)\s*\*/""")

    @Language("RegExp")
    const val REGION = """$REGION_SYMBOL(?<region>[a-zA-Z_][a-zA-Z0-9_]*)?"""

    const val REGIONS = """(?<regions>(?:$REGION)*)"""

    @Language("RegExp")
    const val PATTERN = """$PREFIX\s+(?<path>$ROOT_SYMBOL/(?:$VALID_ID/)*(?<fileName>$VALID_ID))$REGIONS$ANNOTATIONS?\s*(?<closingMarker>$PREFIX)"""

    val REGEX = Regex("^$PATTERN$", RegexOption.MULTILINE)
    val REGION_REGEX = Regex("""//\s+${REGION_SYMBOL}(?<endMarker>end)?region\s+(?<region>[a-zA-Z_][a-zA-Z0-9_]*)""")
}

fun MatchResult.toCodeSnippetReference(): CodeSnippet{
    val fullRange = this.range.toTextRange()
    val closingMarker = groups["closingMarker"]!!.let { GroupInfo(it.value, it.range.toTextRange()) }
    val path = groups["path"]?.let { GroupInfo(it.value, it.range.toTextRange()) } ?: return CodeSnippet(fullRange, closingMarker, GroupInfo("", TextRange.EMPTY_RANGE))
    val fileName = groups["fileName"]?.let { GroupInfo(it.value, it.range.toTextRange()) }
    val regions = groups["regions"]?.let {
        val offset = it.range.first
        buildList {
            CodeSnippetSyntax.REGION.toRegex().findAll(it.value).forEach { region -> add(GroupInfo(region.value, region.range.toTextRange() + offset)) }
        }
    } ?: emptyList()
    val annotations = groups["annotations"]?.let {
        val offset = it.range.first
        buildList {
            CodeSnippetSyntax.ANNOTATION.toRegex().findAll(it.value).forEach { annotation ->
                add(AnnotationInfo(
                    GroupInfo(annotation.value, annotation.range.toTextRange() + offset),
                    GroupInfo(annotation.groups["index"]!!.value, annotation.groups["index"]!!.range.toTextRange() + offset),
                    annotation.groups["text"]?.let { text -> GroupInfo(text.value, text.range.toTextRange() + offset) },
                ))
            }
        }
    } ?: emptyList()
    return CodeSnippet(fullRange, closingMarker, path, fileName, regions, annotations)
}
