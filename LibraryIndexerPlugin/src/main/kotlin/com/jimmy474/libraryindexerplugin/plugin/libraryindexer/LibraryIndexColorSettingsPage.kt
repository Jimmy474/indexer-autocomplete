package com.jimmy474.libraryindexerplugin.plugin.libraryindexer

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.PlainSyntaxHighlighter
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import com.jimmy474.libraryindexerplugin.plugin.common.IndexerHighlightColors
import org.intellij.lang.annotations.Language
import javax.swing.Icon

class LibraryIndexColorSettingsPage : ColorSettingsPage {

    private val descriptors = arrayOf(
        AttributesDescriptor("Macro prefix", IndexerHighlightColors.MACRO_PREFIX),
        AttributesDescriptor("Default text", IndexerHighlightColors.MACRO_TEXT),
        AttributesDescriptor("Class name", IndexerHighlightColors.MACRO_CLASS),
        AttributesDescriptor("Method (Instance)", IndexerHighlightColors.MACRO_METHOD),
        AttributesDescriptor("Method (Static)", IndexerHighlightColors.MACRO_METHOD_STATIC),
        AttributesDescriptor("Field (Instance)", IndexerHighlightColors.MACRO_FIELD),
        AttributesDescriptor("Field (Static)", IndexerHighlightColors.MACRO_FIELD_STATIC),
        AttributesDescriptor("Parameters", IndexerHighlightColors.MACRO_PARAMETER),
        AttributesDescriptor("Flags", IndexerHighlightColors.MACRO_FLAGS),
        AttributesDescriptor("Custom name", IndexerHighlightColors.MACRO_CUSTOM_NAME)
    )

    private val tagToKeyMap = mapOf(
        "prefix" to IndexerHighlightColors.MACRO_PREFIX,
        "text" to IndexerHighlightColors.MACRO_TEXT,
        "class" to IndexerHighlightColors.MACRO_CLASS,
        "method" to IndexerHighlightColors.MACRO_METHOD,
        "methodStatic" to IndexerHighlightColors.MACRO_METHOD_STATIC,
        "field" to IndexerHighlightColors.MACRO_FIELD,
        "fieldStatic" to IndexerHighlightColors.MACRO_FIELD_STATIC,
        "param" to IndexerHighlightColors.MACRO_PARAMETER,
        "flags" to IndexerHighlightColors.MACRO_FLAGS,
        "custom" to IndexerHighlightColors.MACRO_CUSTOM_NAME
    )

    override fun getAttributeDescriptors(): Array<AttributesDescriptor> = descriptors

    override fun getColorDescriptors(): Array<ColorDescriptor> = ColorDescriptor.EMPTY_ARRAY

    override fun getDisplayName(): String = "Library Indexer"

    override fun getIcon(): Icon? = null

    override fun getHighlighter(): SyntaxHighlighter = PlainSyntaxHighlighter()

    override fun getAdditionalHighlightingTagToDescriptorMap(): Map<String, TextAttributesKey> = tagToKeyMap

    @Language("XML")
    override fun getDemoText(): String = """
        <prefix>@`</prefix><text>package1.package2.</text><class>class</class><text>#</text><field>field</field><flags>+</flags><prefix>`</prefix>
        <prefix>@`</prefix><text>package1.package2.</text><class>class</class><text>#</text><fieldStatic>staticField</fieldStatic><flags>-</flags><prefix>`</prefix>
        <prefix>@`</prefix><text>package1.package2.</text><class>class</class><text>#</text><method>method(</method><param>param1 name</param><text>,</text><param>param2 name</param><method>)</method><flags>+></flags><prefix>`</prefix>
        <prefix>@`</prefix><text>package1.package2.</text><class>class</class><text>#</text><methodStatic>staticMethod(</methodStatic><param>param1 name</param><methodStatic>)</methodStatic><flags>*></flags><prefix>`</prefix>
        <prefix>@`</prefix><text>package1.package2.</text><class>class</class><text>|</text><custom>custom name</custom><prefix>`</prefix>
    """.trimIndent()
}
