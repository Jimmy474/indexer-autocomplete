package com.jimmy474.libraryindexerplugin.plugin

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.PlainSyntaxHighlighter
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import org.intellij.lang.annotations.Language
import javax.swing.Icon

class LibraryIndexColorSettingsPage : ColorSettingsPage {

    private val descriptors = arrayOf(
        AttributesDescriptor("Macro prefix", LibraryIndexColors.MACRO_PREFIX),
        AttributesDescriptor("Default text", LibraryIndexColors.MACRO_TEXT),
        AttributesDescriptor("Class name", LibraryIndexColors.MACRO_CLASS),
        AttributesDescriptor("Method (Instance)", LibraryIndexColors.MACRO_METHOD),
        AttributesDescriptor("Method (Static)", LibraryIndexColors.MACRO_METHOD_STATIC),
        AttributesDescriptor("Field (Instance)", LibraryIndexColors.MACRO_FIELD),
        AttributesDescriptor("Field (Static)", LibraryIndexColors.MACRO_FIELD_STATIC),
        AttributesDescriptor("Parameters", LibraryIndexColors.MACRO_PARAMETER),
        AttributesDescriptor("Flags", LibraryIndexColors.MACRO_FLAGS),
        AttributesDescriptor("Custom name", LibraryIndexColors.MACRO_CUSTOM_NAME)
    )

    private val tagToKeyMap = mapOf(
        "prefix" to LibraryIndexColors.MACRO_PREFIX,
        "text" to LibraryIndexColors.MACRO_TEXT,
        "class" to LibraryIndexColors.MACRO_CLASS,
        "method" to LibraryIndexColors.MACRO_METHOD,
        "methodStatic" to LibraryIndexColors.MACRO_METHOD_STATIC,
        "field" to LibraryIndexColors.MACRO_FIELD,
        "fieldStatic" to LibraryIndexColors.MACRO_FIELD_STATIC,
        "param" to LibraryIndexColors.MACRO_PARAMETER,
        "flags" to LibraryIndexColors.MACRO_FLAGS,
        "custom" to LibraryIndexColors.MACRO_CUSTOM_NAME
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