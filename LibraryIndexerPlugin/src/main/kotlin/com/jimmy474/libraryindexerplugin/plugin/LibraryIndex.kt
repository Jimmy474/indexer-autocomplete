package com.jimmy474.libraryindexerplugin.plugin

import org.intellij.lang.annotations.Language

object LibraryIndex{
    /*
    * Hardcoded references to these symbols are present in "src/main/resources/inspectionDescriptions/LibraryIndex.html" remember to update that there.
    */

    @Language("RegExp")
    const val PREFIX = "@"
    @Language("RegExp")
    const val MEMBER_PREFIX ="#"
    @Language("RegExp")
    const val VALID_ID = """[a-zA-Z_$][a-zA-Z0-9_$]*"""
    @Language("RegExp")
    const val GENERIC = """<[^()]+>"""
    @Language("RegExp")
    const val PARAM_FQN = """$VALID_ID(\.$VALID_ID)*($GENERIC)?"""
    @Language("RegExp")
    const val METHOD_FLAG = """\((?<params>$PARAM_FQN(,$PARAM_FQN)*)?\)"""
    @Language("RegExp")
    const val SHORT_NAME_SYMBOL = "-"
    @Language("RegExp")
    const val LONG_NAME_SYMBOL = "\\+"
    @Language("RegExp")
    const val FULL_NAME_SYMBOL = "\\*"
    @Language("RegExp")
    const val METHOD_ONLY_TYPE_SYMBOL = ":"
    @Language("RegExp")
    const val METHOD_ONLY_NAME_SYMBOL = ","
    @Language("RegExp")
    const val METHOD_BOTH_SYMBOL = ";"

    @Language("RegExp")
    const val METHOD_RETURN_TYPE_SYMBOL = ">"
    @Language("RegExp")
    const val MEMBER_PATTERN = """(?<members>$MEMBER_PREFIX(?<memberName>$VALID_ID))"""

    @Language("RegExp")
    const val METHOD_FLAGS = """((?<methodOnlyTypeFlag>$METHOD_ONLY_TYPE_SYMBOL)|(?<methodOnlyNameFlag>$METHOD_ONLY_NAME_SYMBOL)|(?<methodBothFlag>$METHOD_BOTH_SYMBOL)|(?<methodReturnType>$METHOD_RETURN_TYPE_SYMBOL))"""
    @Language("RegExp")
    const val FLAGS_PATTERN = """(?<flags>((?<shortNameFlag>$SHORT_NAME_SYMBOL)|(?<longNameFlag>$LONG_NAME_SYMBOL)|(?<fullNameFlag>$FULL_NAME_SYMBOL))?$METHOD_FLAGS?)"""

    @Language("RegExp")
    const val CUSTOM_NAME_SYMBOL = "\\|"
    @Language("RegExp")
    const val CUSTOM_NAME_PATTERN = """($CUSTOM_NAME_SYMBOL(?<customName>.*))"""
    @Language("RegExp")
    const val INDEX_REFERENCE_PATH_PATTERN = """(?<fqn>$VALID_ID(?<className>\.$VALID_ID)*)($MEMBER_PATTERN?(?<methodFlag>$METHOD_FLAG)?)?($FLAGS_PATTERN|$CUSTOM_NAME_PATTERN)?"""

    @Language("RegExp")
    const val INDEX_REFERENCE_PATTERN = "$PREFIX`$INDEX_REFERENCE_PATH_PATTERN`"
    val INDEX_REFERENCE_REGEX = Regex("^$INDEX_REFERENCE_PATTERN$")
}