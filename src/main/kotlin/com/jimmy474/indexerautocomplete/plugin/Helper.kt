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
    val fqn: List<String>,
    val className: String?,
    val classNameRange: TextRange? = null,
    val memberName: String?,
    val memberNameRange: TextRange? = null,
    val memberType: MemberType = MemberType.NONE,
    val fullDisplayFlag: Boolean = false,
    val isConstructor: Boolean = false,
){
    enum class MemberType {
        NONE,
        METHOD,
        FIELD,
    }
}

fun MatchResult.toIndexReference(): IndexReference {
    val fqn = this.groupValues[1].split(".")
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
    val isConstructor = memberName == null && this.groupValues[5].isNotBlank()
    val memberType = if(isConstructor) IndexReference.MemberType.METHOD else this.groupValues[3].ifBlank{null} ?.let{
        this.groupValues[5].ifBlank{ null }?.let { IndexReference.MemberType.METHOD } ?: IndexReference.MemberType.FIELD
    } ?: IndexReference.MemberType.NONE
    val fullDisplayFlag = this.groupValues[6].isNotBlank()
    return IndexReference(fqn, className, classNameRange, memberName, memberNameRange, memberType, fullDisplayFlag, isConstructor)
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