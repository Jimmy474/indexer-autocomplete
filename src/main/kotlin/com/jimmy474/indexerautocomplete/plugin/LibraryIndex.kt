package com.jimmy474.indexerautocomplete.plugin

object LibraryIndex{
    /*
    * Hardcoded references to these symbols are present in "src/main/resources/inspectionDescriptions/LibraryIndex.html" remember to update that there.
    */

    const val PREFIX = "@"
    const val MEMBER_PREFIX ="#"
    const val VALID_ID = """[a-zA-Z_$][a-zA-Z0-9_$]*"""
    const val GENERIC = """<[^()]+>"""
    const val PARAM_FQN = """$VALID_ID(\.$VALID_ID)*($GENERIC)?"""
    const val METHOD_FLAG = """\((?<params>$PARAM_FQN(,$PARAM_FQN)*)?\)"""
    const val SHORT_NAME_SYMBOL = "\\-"
    const val FULL_NAME_SYMBOL = "\\+"
    const val METHOD_ONLY_TYPE_SYMBOL = "\\."
    const val METHOD_ONLY_NAME_SYMBOL = ","
    const val METHOD_BOTH_SYMBOL = ";"
    const val MEMBER_PATTERN = """(?<members>$MEMBER_PREFIX(?<memberName>$VALID_ID))"""
    const val FLAGS_PATTERN = """(?<flags>((?<methodOnlyTypeFlag>$METHOD_ONLY_TYPE_SYMBOL)|(?<methodOnlyNameFlag>$METHOD_ONLY_NAME_SYMBOL)|(?<methodBothFlag>$METHOD_BOTH_SYMBOL))?((?<shortNameFlag>$SHORT_NAME_SYMBOL)|(?<fullNameFlag>$FULL_NAME_SYMBOL))?)"""

    const val CUSTOM_NAME_SYMBOL = "\\|"
    const val CUSTOM_NAME_PATTERN = """($CUSTOM_NAME_SYMBOL(?<customName>.*))"""
    const val INDEX_REFERENCE_PATH_PATTERN = """(?<fqn>$VALID_ID(?<className>\.$VALID_ID)*)($MEMBER_PATTERN?(?<methodFlag>$METHOD_FLAG)?)?($FLAGS_PATTERN|$CUSTOM_NAME_PATTERN)?"""

    const val INDEX_REFERENCE_PATTERN = "$PREFIX`$INDEX_REFERENCE_PATH_PATTERN`"
    val INDEX_REFERENCE_REGEX = Regex("^$INDEX_REFERENCE_PATTERN$")
}