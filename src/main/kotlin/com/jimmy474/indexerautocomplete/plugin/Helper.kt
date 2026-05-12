package com.jimmy474.indexerautocomplete.plugin

import com.intellij.openapi.util.TextRange

data class IndexReference(
    val packages: List<String>,
    val className: String?,
    val classNameRange: TextRange? = null,
    val memberName: String?,
    val memberNameRange: TextRange? = null,
    val memberType: MemberType = MemberType.NONE,
    val fullDisplayFlag: Boolean = false
){
    enum class MemberType {
        NONE,
        METHOD,
        FIELD,
    }
}

fun MatchResult.toIndexReference(): IndexReference {
    val packages = this.groupValues[1].split(".")
    val className = this.groupValues[2].ifBlank { null }?.removePrefix(".")
    val classNameRange = this.groupValues[2].ifBlank { null }?.let {
        TextRange(
            this.groups[2]!!.range.first + 1,
            this.groups[2]!!.range.last + 1
        )
    }
    val memberName = this.groupValues[4].ifBlank { null }
    val memberNameRange = this.groupValues[4].ifBlank { null }?.let {
        TextRange(
            this.groups[4]!!.range.first,
            this.groups[4]!!.range.last + 1
        )
    }
    val memberType = this.groupValues[3].ifBlank{null} ?.let{
        this.groupValues[5].ifBlank{ null }?.let { IndexReference.MemberType.METHOD } ?: IndexReference.MemberType.FIELD
    } ?: IndexReference.MemberType.NONE
    val fullDisplayFlag = this.groupValues[6].isNotBlank()
    return IndexReference(packages, className, classNameRange, memberName, memberNameRange, memberType, fullDisplayFlag)
}