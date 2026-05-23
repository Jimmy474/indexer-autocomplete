package com.jimmy474.libraryindexerplugin.plugin

import com.intellij.openapi.util.TextRange

data class CodeSnippet(
    val fullRange: TextRange,
    val path: GroupInfo,
    val fileName: GroupInfo? = null,
    val region: GroupInfo? = null
){
    fun toMarkdownReference(): String {
        return buildString {
            append(LibraryIndex.CODE_SNIPPET_PREFIX)
            append(" ")
            append(LibraryIndex.ROOT_SYMBOL)
            append("/")
            append(path.value)
            region?.let { append("${LibraryIndex.REGION_SYMBOL}${it.value}") }
        }
    }
}