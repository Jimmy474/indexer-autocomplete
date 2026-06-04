package com.jimmy474.libraryindexerplugin.plugin.libraryindexer

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.jimmy474.libraryindexerplugin.plugin.common.GroupInfo
import com.jimmy474.libraryindexerplugin.plugin.common.addStart
import com.jimmy474.libraryindexerplugin.plugin.common.rangeTo
import com.jimmy474.libraryindexerplugin.plugin.common.toTextRange
import kotlinx.serialization.json.*

fun MatchResult.toIndexReference(): IndexReference {
    val fullRange = this.range.toTextRange()
    val fqn = groups["fqn"] ?: return IndexReference(fullRange = fullRange, fqn = GroupInfo("", TextRange.EMPTY_RANGE), className = null, memberName = null)
    val className = groups["className"]
    val memberName = groups["memberName"]
    val methodFlagGroup = groups["methodFlag"]
    val methodFlag = methodFlagGroup != null
    val isConstructor = groups["members"] == null && methodFlag
    val customName = groups["customName"]
    val methodOnlyType = groups["methodOnlyTypeFlag"] != null
    val methodOnlyName = groups["methodOnlyNameFlag"] != null
    val methodBoth = groups["methodBothFlag"] != null
    val methodReturnType = groups["methodReturnType"] != null

    val memberType = when {
        isConstructor -> IndexReference.MemberType.METHOD
        memberName != null -> if (methodFlag) IndexReference.MemberType.METHOD else IndexReference.MemberType.FIELD
        groups["members"] != null -> IndexReference.MemberType.FIELD
        else -> IndexReference.MemberType.NONE
    }

    val memberNameRange = memberName?.let {
        if (memberType == IndexReference.MemberType.METHOD) {
            it.range.toTextRange()..methodFlagGroup!!.range.toTextRange()
        } else {
            it.range.toTextRange()
        }
    }

    val memberInfo = if (memberName != null && memberNameRange != null) GroupInfo(memberName.value, memberNameRange) else null
    val shortNameFlag = groups["shortNameFlag"] != null
    val longNameFlag = groups["longNameFlag"] != null
    val fullNameFlag = groups["fullNameFlag"] != null

    val paramsGroup = groups["params"]
    val params = when {
        methodFlagGroup == null -> null
        paramsGroup == null -> emptyList()
        else -> splitTopLevelTypes(paramsGroup.value, paramsGroup.range.first)
    }

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
        params = params,
        flags = Flags(
            relativeRange = groups["flags"]?.takeIf { it.value.isNotBlank() }?.range?.toTextRange() ?: TextRange.EMPTY_RANGE,
            methodWithParams = (params?.isNotEmpty() ?: false) && (methodOnlyName || methodOnlyType || methodBoth),
            methodOnlyType = methodOnlyType,
            methodOnlyName = methodOnlyName,
            methodBoth = methodBoth,
            methodReturnType = methodReturnType,
            isConstructor = isConstructor,
            shortName = shortNameFlag,
            longName = longNameFlag,
            fullName = fullNameFlag,
        ),
        customName = customName?.let { GroupInfo(it.value, it.range.toTextRange()) }
    )
}

fun String.unescapeRegex() = this.removePrefix("\\")

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

fun splitTopLevelTypes(text: String, relativeStart: Int = 0): List<GroupInfo> {
    val result = mutableListOf<GroupInfo>()
    var depth = 0
    var tokenStart = 0

    fun addToken(endExclusive: Int) {
        val raw = text.substring(tokenStart, endExclusive)
        val leadingWhitespace = raw.indexOfFirst { !it.isWhitespace() }
        if (leadingWhitespace == -1) return
        val trailingWhitespace = raw.indexOfLast { !it.isWhitespace() }
        val start = tokenStart + leadingWhitespace
        val end = tokenStart + trailingWhitespace + 1
        result.add(GroupInfo(text.substring(start, end), TextRange(relativeStart + start, relativeStart + end)))
    }

    text.forEachIndexed { index, char ->
        when (char) {
            '<' -> depth++
            '>' -> if (depth > 0) depth--
            ',' -> if (depth == 0) {
                addToken(index)
                tokenStart = index + 1
            }
        }
    }
    addToken(text.length)
    return result
}

fun parameterTypes(overload: JsonElement): List<String> {
    return overload.jsonObject["parameters"]?.jsonArray?.mapNotNull {
        it.jsonObject["type"]?.jsonPrimitive?.content
    } ?: emptyList()
}

fun simpleTypeName(type: String): String {
    val builder = StringBuilder()
    val token = StringBuilder()

    fun flushToken() {
        if (token.isNotEmpty()) {
            builder.append(token.toString().substringAfterLast('.'))
            token.clear()
        }
    }

    for (char in type) {
        if (char.isLetterOrDigit() || char == '_' || char == '$' || char == '.') {
            token.append(char)
        } else {
            flushToken()
            builder.append(char)
        }
    }
    flushToken()
    return builder.toString()
}
