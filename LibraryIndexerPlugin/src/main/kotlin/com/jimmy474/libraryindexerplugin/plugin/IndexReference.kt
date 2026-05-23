package com.jimmy474.libraryindexerplugin.plugin

import com.intellij.openapi.util.TextRange

data class IndexReference(
    val fullRange: TextRange,
    val fqn: GroupInfo,
    val className: GroupInfo?,
    val memberName: GroupInfo?,
    val memberType: MemberType = MemberType.NONE,
    val params: List<GroupInfo>? = null,
    val flags: Flags = Flags(),
    val customName: GroupInfo? = null,
){
    enum class MemberType {
        NONE,
        METHOD,
        FIELD,
    }

    fun toMarkdownReference(): String {
        return buildString {
            append("${LibraryIndex.PREFIX}`")
            append(fqn.value)
            if (memberName != null) {
                append(LibraryIndex.MEMBER_PREFIX).append(memberName.value)
            }
            if(memberType == MemberType.METHOD) {
                append("(").append(params?.joinToString(",") { it.value } ?: "").append(")")
            }
            if(flags.shortName) append(LibraryIndex.SHORT_NAME_SYMBOL.unescapeRegex())
            if(flags.longName) append(LibraryIndex.LONG_NAME_SYMBOL.unescapeRegex())
            if(flags.fullName) append(LibraryIndex.FULL_NAME_SYMBOL.unescapeRegex())

            if(flags.methodReturnType) append(LibraryIndex.METHOD_RETURN_TYPE_SYMBOL.unescapeRegex())
            if(flags.methodOnlyType) append(LibraryIndex.METHOD_ONLY_TYPE_SYMBOL.unescapeRegex())
            if(flags.methodOnlyName) append(LibraryIndex.METHOD_ONLY_NAME_SYMBOL.unescapeRegex())
            if(flags.methodBoth) append(LibraryIndex.METHOD_BOTH_SYMBOL.unescapeRegex())

            if(customName != null) append(LibraryIndex.CUSTOM_NAME_SYMBOL).append(customName.value)

            append("`")
        }
    }
}

data class GroupInfo(
    val value: String,
    val relativeRange: TextRange
)

data class Flags(
    val relativeRange: TextRange = TextRange.EMPTY_RANGE,
    val shortName: Boolean = false,
    val longName: Boolean = false,
    val fullName: Boolean = false,
    val methodWithParams: Boolean = false,
    val methodOnlyType: Boolean = false,
    val methodOnlyName: Boolean = false,
    val methodBoth: Boolean = false,
    val methodReturnType: Boolean = false,
    val isConstructor: Boolean = false,
)

