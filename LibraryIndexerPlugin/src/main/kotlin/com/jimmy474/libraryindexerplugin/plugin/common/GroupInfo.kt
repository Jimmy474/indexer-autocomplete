package com.jimmy474.libraryindexerplugin.plugin.common

import com.intellij.openapi.util.TextRange

data class GroupInfo(
    val value: String,
    val relativeRange: TextRange
)
