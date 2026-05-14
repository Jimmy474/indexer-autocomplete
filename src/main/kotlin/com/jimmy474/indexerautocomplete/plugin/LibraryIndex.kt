package com.jimmy474.indexerautocomplete.plugin

object LibraryIndex{
    /*
    * Hardcoded references to these symbols are present in "src/main/resources/inspectionDescriptions/LibraryIndex.html" remember to update that there.
    */

    const val PREFIX = "@"
    const val MEMBER_PREFIX ="#"
    const val VALID_ID = """[a-zA-Z_$][a-zA-Z0-9_$]*"""
    const val METHOD_FLAG_PARAMS_REQUIRED_DOTS = 3
    const val METHOD_FLAG = """\((?<methodWithParamsFlag>\.{$METHOD_FLAG_PARAMS_REQUIRED_DOTS})?\)"""
    const val SHORT_NAME_SYMBOL = "\\-"
    const val FULL_NAME_SYMBOL = "\\+"
    const val MEMBER_PATTERN = """(?<members>$MEMBER_PREFIX(?<memberName>$VALID_ID))"""
    const val FLAGS_PATTERN = """(?<flags>((?<shortNameFlag>$SHORT_NAME_SYMBOL)|(?<fullNameFlag>$FULL_NAME_SYMBOL))?)"""
    val INDEX_REFERENCE_PATH_REGEX = Regex("""(?<fqn>$VALID_ID(?<className>\.$VALID_ID)*)($MEMBER_PATTERN?(?<methodFlag>$METHOD_FLAG)?)?$FLAGS_PATTERN?""")

    val INDEX_REFERENCE_PATTERN = "$PREFIX`$INDEX_REFERENCE_PATH_REGEX`"
    val INDEX_REFERENCE_REGEX = Regex("^$INDEX_REFERENCE_PATTERN$")
}