package com.jimmy474.libraryindexerplugin.plugin.common

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey

object IndexerHighlightColors {
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

    val MACRO_PARAMETER = TextAttributesKey.createTextAttributesKey(
        "LIBRARY_INDEX_PARAMETER", DefaultLanguageHighlighterColors.PARAMETER
    )

    val MACRO_FLAGS = TextAttributesKey.createTextAttributesKey(
        "LIBRARY_INDEX_FLAGS", DefaultLanguageHighlighterColors.VALID_STRING_ESCAPE
    )

    val MACRO_CUSTOM_NAME = TextAttributesKey.createTextAttributesKey(
        "LIBRARY_INDEX_CUSTOM_NAME", DefaultLanguageHighlighterColors.CONSTANT
    )
}
