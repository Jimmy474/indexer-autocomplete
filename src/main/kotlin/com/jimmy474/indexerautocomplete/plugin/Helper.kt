package com.jimmy474.indexerautocomplete.plugin

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

data class IndexReference(
    val fullRange: TextRange,
    val fqn: GroupInfo,
    val className: GroupInfo?,
    val memberName: GroupInfo?,
    val memberType: MemberType = MemberType.NONE,
    val flags: Flags = Flags()
){
    enum class MemberType {
        NONE,
        METHOD,
        FIELD,
    }
}

data class GroupInfo(
    val value: String,
    val relativeRange: TextRange
)

data class Flags(
    val relativeRange: TextRange = TextRange.EMPTY_RANGE,
    val shortName: Boolean = false,
    val fullName: Boolean = false,
    val methodWithParams: Boolean = false,
    val isConstructor: Boolean = false,
)

fun MatchResult.toIndexReference(): IndexReference {
    val fullRange = this.range.toTextRange()
    val fqn = groups["fqn"] ?: return IndexReference(fullRange = fullRange, fqn = GroupInfo("", TextRange.EMPTY_RANGE), className = null, memberName = null)
    val className = groups["className"]
    val memberName = groups["memberName"]
    val methodFlagGroup = groups["methodFlag"]
    val methodFlag = methodFlagGroup != null
    val isConstructor = groups["members"] == null && methodFlag

    val memberType = when {
        isConstructor -> IndexReference.MemberType.METHOD
        memberName != null -> if (methodFlag) IndexReference.MemberType.METHOD else IndexReference.MemberType.FIELD
        else -> IndexReference.MemberType.NONE
    }

    val memberNameRange =
        memberName?.let {
            if (memberType == IndexReference.MemberType.METHOD) {
                it.range.toTextRange()..methodFlagGroup!!.range.toTextRange()
            } else {
                it.range.toTextRange()
            }
        }

    val memberInfo = if (memberName != null && memberNameRange != null) GroupInfo(memberName.value, memberNameRange) else null
    val methodWithParamsFlag = groups["methodWithParamsFlag"] != null
    val shortNameFlag = groups["shortNameFlag"] != null
    val fullNameFlag = groups["fullNameFlag"] != null

    return IndexReference(
        fullRange = fullRange,
        fqn = GroupInfo(fqn.value, fqn.range.toTextRange()),
        className = className?.let {
            var relativeRange = it.range.toTextRange().addStart(1)
            if(isConstructor) relativeRange = relativeRange..methodFlagGroup.range.toTextRange()
            GroupInfo(it.value.substring(1), relativeRange)
        },
        memberName = memberInfo,
        memberType = memberType,
        flags = Flags(
            relativeRange = groups["flags"]?.takeIf { it.value.isNotBlank() }?.range?.toTextRange() ?: TextRange.EMPTY_RANGE,
            methodWithParams = methodWithParamsFlag,
            isConstructor = isConstructor,
            shortName = shortNameFlag,
            fullName = fullNameFlag
        )
    )
}

fun getCachedJson(file: VirtualFile, project: Project): JsonObject? {
    val psiFile = PsiManager.getInstance(project).findFile(file) ?: return null

    return CachedValuesManager.getCachedValue(psiFile) {
        val jsonText = psiFile.text
        val jsonObject = try {
            Json.parseToJsonElement(jsonText).jsonObject
        } catch (_: Exception) {
            null
        }
        CachedValueProvider.Result.create(jsonObject, psiFile)
    }
}

fun getPackageOrClassFixSuggestions(current: VirtualFile, part: String): List<Pair<String, Boolean>>{
    val availableNames = current.children
        .filter { it.isDirectory || it.extension == "json" }
        .map { it.nameWithoutExtension to it.isDirectory }
        .distinct()

    return availableNames.sortedBy { levenshtein(it.first, part) }.take(3)
}

fun levenshtein(lhs: CharSequence, rhs: CharSequence): Int {
    if (lhs == rhs) return 0
    if (lhs.isEmpty()) return rhs.length
    if (rhs.isEmpty()) return lhs.length

    val lhsLength = lhs.length + 1
    val rhsLength = rhs.length + 1
    var cost = IntArray(lhsLength) { it }
    var newCost = IntArray(lhsLength) { 0 }

    for (i in 1 until rhsLength) {
        newCost[0] = i
        for (j in 1 until lhsLength) {
            val match = if (lhs[j - 1] == rhs[i - 1]) 0 else 1
            val costReplace = cost[j - 1] + match
            val costInsert = cost[j] + 1
            val costDelete = newCost[j - 1] + 1
            newCost[j] = minOf(costInsert, costDelete, costReplace)
        }
        val swap = cost
        cost = newCost
        newCost = swap
    }
    return cost[lhsLength - 1]
}

fun IntRange.toTextRange() = TextRange(first, last + 1)

operator fun TextRange.plus(other: TextRange) = TextRange(startOffset + other.startOffset, endOffset + other.endOffset)
operator fun TextRange.plus(other: IntRange) = TextRange(startOffset + other.first, endOffset + other.last)
operator fun TextRange.plus(other: Int) = TextRange(startOffset + other, endOffset + other)

operator fun TextRange.minus(other: TextRange) = TextRange(startOffset - other.startOffset, endOffset - other.endOffset)
operator fun TextRange.minus(other: IntRange) = TextRange(startOffset - other.first, endOffset - other.last)
operator fun TextRange.minus(other: Int) = TextRange(startOffset - other, endOffset - other)

operator fun TextRange.times(other: TextRange) = TextRange(startOffset * other.startOffset, endOffset * other.endOffset)
operator fun TextRange.times(other: IntRange) = TextRange(startOffset * other.first, endOffset * other.last)
operator fun TextRange.times(other: Int) = TextRange(startOffset * other, endOffset * other)

operator fun TextRange.div(other: TextRange) = TextRange(startOffset / other.startOffset, endOffset / other.endOffset)
operator fun TextRange.div(other: IntRange) = TextRange(startOffset / other.first, endOffset / other.last)
operator fun TextRange.div(other: Int) = TextRange(startOffset / other, endOffset / other)

operator fun TextRange.rangeTo(other: TextRange): TextRange = TextRange(minOf(startOffset, other.startOffset), maxOf(endOffset, other.endOffset))
fun TextRange.addStart(other: Int) = TextRange(startOffset + other, endOffset)
fun TextRange.addEnd(other: Int) = TextRange(startOffset, endOffset + other)
