package com.jimmy474.indexerautocomplete.plugin

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownFile

class LibraryIndexAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (element.containingFile !is MarkdownFile) return
        if (element !is LibraryIndexPsiElement) return

        val regex = LibraryIndexCompletionProvider.INDEX_REFERENCE_FULL_REGEX
        val match = regex.find(element.text) ?: return
        val project = element.project
        val projectDir = project.guessProjectDir() ?: return
        val root = projectDir.findChild("library-index-dependency") ?: return

        val pathText = match.groupValues[1]
        val type = match.groupValues[5]
        val typeName = match.groupValues[6]

        if(pathText.endsWith('.')){
            holder.newAnnotation(HighlightSeverity.ERROR, "Path segment cannot end with '.'")
                .range(element.textRange).create()
            return
        }

        if(type.isNotEmpty() && typeName.isEmpty()){
            holder.newAnnotation(HighlightSeverity.ERROR, "Path segment cannot end with '$type'")
                .range(element.textRange).create()
            return
        }

        var current: VirtualFile = root
        val parts = pathText.split(".").filter { it.isNotEmpty() }

        for (part in parts) {
            val next = current.findChild(part) ?: current.findChild("$part.json")
            if (next == null) {
                holder.newAnnotation(HighlightSeverity.ERROR, "Path segment '$part' not found")
                    .range(element.textRange).create()
                return
            }
            current = next
        }

        if (type.isNotEmpty()) {
            if (current.isDirectory || current.extension != "json") {
                holder.newAnnotation(HighlightSeverity.ERROR, "Cannot use $type on a directory")
                    .range(element.textRange).create()
                return
            }

            val jsonContent = getCachedJson(current, project)
            val key = if (type == LibraryIndexCompletionProvider.METHODS_PREFIX) "methods" else "fields"
            val items = jsonContent?.get(key)?.jsonArray
            val exists = items?.any { it.jsonObject["name"]?.jsonPrimitive?.content == typeName } ?: false

            if (!exists) {
                val label = if (type == LibraryIndexCompletionProvider.METHODS_PREFIX) "Method" else "Field"
                holder.newAnnotation(HighlightSeverity.ERROR, "$label '$typeName' not found in ${current.name}")
                    .range(element.textRange).create()
                return
            }
        }
    }

    private fun getCachedJson(file: VirtualFile, project: Project): JsonObject? {
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
}