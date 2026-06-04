package com.jimmy474.libraryindexerplugin.plugin.codesnippet

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.AutoCompletionPolicy
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.readText
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.PlatformIcons
import com.intellij.util.ProcessingContext

class CodeSnippetCompletionContributor : CompletionContributor() {
    init {
        extend(CompletionType.BASIC, psiElement().inside(CodeSnippetPsiElement::class.java), CodeSnippetCompletionProvider())
    }
}

private class CodeSnippetCompletionProvider : CompletionProvider<CompletionParameters>() {
    override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
        val snippetElement = PsiTreeUtil.getParentOfType(parameters.position, CodeSnippetPsiElement::class.java, false) ?: return
        val projectDir = parameters.position.project.guessProjectDir() ?: return
        val textBeforeCaret = parameters.originalFile.text.substring(snippetElement.textRange.startOffset, parameters.offset)
        if (textBeforeCaret.contains('\n') || textBeforeCaret.contains('\r')) return

        val snippetText = textBeforeCaret.removePrefix(CodeSnippetSyntax.PREFIX).trimStart()

        when {
            snippetText.isBlank() || snippetText == CodeSnippetSyntax.ROOT_SYMBOL -> rootSuggestion(result, snippetText)
            snippetText.startsWith("${CodeSnippetSyntax.ROOT_SYMBOL}/") && snippetText.hasRegionMarker() ->
                regionSuggestions(result, projectDir, parameters.position.project, snippetText)
            snippetText.startsWith("${CodeSnippetSyntax.ROOT_SYMBOL}/") ->
                pathSuggestions(result, projectDir, parameters.position.project, snippetText)
        }

        result.restartCompletionOnAnyPrefixChange()
    }

    private fun rootSuggestion(result: CompletionResultSet, prefix: String) {
        result.withPrefixMatcher(PlainPrefixMatcher(prefix, true)).addElement(
            LookupElementBuilder.create("${CodeSnippetSyntax.ROOT_SYMBOL}/")
                .withIcon(PlatformIcons.FOLDER_ICON)
                .withTypeText("project root")
                .withInsertHandler { ctx, _ ->
                    AutoPopupController.getInstance(ctx.project).scheduleAutoPopup(ctx.editor)
                }
        )
    }

    private fun pathSuggestions(result: CompletionResultSet, projectDir: VirtualFile, project: Project, snippetText: String) {
        val relativePath = snippetText.removePrefix("${CodeSnippetSyntax.ROOT_SYMBOL}/")
        val parts = relativePath.split('/')
        val navigationParts = parts.dropLast(1).filter { it.isNotBlank() }
        val searchPart = parts.lastOrNull().orEmpty()

        var currentDir = projectDir
        for (part in navigationParts) {
            currentDir = currentDir.findChild(part)?.takeIf { it.isDirectory } ?: return
        }

        val resultSetWithPrefix = result.withPrefixMatcher(PlainPrefixMatcher(searchPart, true))
        currentDir.findChild(searchPart)
            ?.takeIf { !it.isDirectory && findRegionNames(it, project).isNotEmpty() }
            ?.let {
                addRegionMarkerSuggestion(result, searchPart, CodeSnippetSyntax.INCLUDE_SYMBOL, "include region")
                addRegionMarkerSuggestion(result, searchPart, CodeSnippetSyntax.EXCLUDE_SYMBOL, "exclude region")
            }

        currentDir.children
            .filter { it.isDirectory || !it.fileType.isBinary }
            .forEach { child ->
                val lookupElement = LookupElementBuilder.create(child, child.name)
                    .withIcon(if (child.isDirectory) PlatformIcons.FOLDER_ICON else FileTypeRegistry.getInstance().getFileTypeByFileName(child.name).icon)
                    .withInsertHandler { ctx, item ->
                        val selected = item.`object` as? VirtualFile ?: return@withInsertHandler
                        if (selected.isDirectory) {
                            ctx.document.insertString(ctx.selectionEndOffset, "/")
                            ctx.editor.caretModel.moveToOffset(ctx.selectionEndOffset)
                        }
                        AutoPopupController.getInstance(ctx.project).scheduleAutoPopup(ctx.editor)
                    }
                    .withAutoCompletionPolicy(AutoCompletionPolicy.NEVER_AUTOCOMPLETE)

                resultSetWithPrefix.addElement(lookupElement)
            }
    }

    private fun regionSuggestions(result: CompletionResultSet, projectDir: VirtualFile, project: Project, snippetText: String) {
        val markerIndex = snippetText.indexOfLast { it.isRegionMarker() }
        if (markerIndex == -1) return

        val relativePath = snippetText
            .removePrefix("${CodeSnippetSyntax.ROOT_SYMBOL}/")
            .substringBeforeRegionMarker()
        val regionPrefix = snippetText.substring(markerIndex + 1)
        val marker = snippetText[markerIndex]
        val file = projectDir.findFileByRelativePath(relativePath)?.takeIf { !it.isDirectory } ?: return

        val resultSetWithPrefix = result.withPrefixMatcher(PlainPrefixMatcher(regionPrefix, true))
        val regionNames = findRegionNames(file, project)
        val usedRegionNames = snippetText.regionNamesBefore(markerIndex)
        regionNames.filterNot { it in usedRegionNames }.forEach { region ->
            resultSetWithPrefix.addElement(
                LookupElementBuilder.create(region)
                    .withIcon(PlatformIcons.PROPERTY_ICON)
                    .withTypeText(if (marker == CodeSnippetSyntax.INCLUDE_SYMBOL.single()) "include region" else "exclude region")
                    .withInsertHandler { ctx, _ ->
                        AutoPopupController.getInstance(ctx.project).scheduleAutoPopup(ctx.editor)
                    }
            )
        }

        if (regionPrefix in regionNames && regionPrefix !in usedRegionNames) {
            addRegionMarkerSuggestion(result, regionPrefix, CodeSnippetSyntax.INCLUDE_SYMBOL, "include region")
            addRegionMarkerSuggestion(result, regionPrefix, CodeSnippetSyntax.EXCLUDE_SYMBOL, "exclude region")
        }
    }

    private fun addRegionMarkerSuggestion(result: CompletionResultSet, searchPart: String, symbol: String, typeText: String) {
        result.withPrefixMatcher(PlainPrefixMatcher("", true)).addElement(
            LookupElementBuilder.create(symbol)
                .withLookupString("$searchPart$symbol")
                .withIcon(PlatformIcons.PROPERTY_ICON)
                .withTypeText(typeText)
                .withInsertHandler { ctx, _ ->
                    AutoPopupController.getInstance(ctx.project).scheduleAutoPopup(ctx.editor)
                }
        )
    }
}

private fun String.hasRegionMarker(): Boolean = any { it.isRegionMarker() }

private fun String.substringBeforeRegionMarker(): String {
    val markerIndex = indexOfFirst { it.isRegionMarker() }
    return if (markerIndex == -1) this else substring(0, markerIndex)
}

private fun Char.isRegionMarker(): Boolean {
    return this == CodeSnippetSyntax.INCLUDE_SYMBOL.single() || this == CodeSnippetSyntax.EXCLUDE_SYMBOL.single()
}

private fun String.regionNamesBefore(lastMarkerIndex: Int): Set<String> {
    return CodeSnippetSyntax.REGION.toRegex().findAll(substring(0, lastMarkerIndex))
        .mapNotNull { it.groups["region"]?.value }
        .toSet()
}

fun findRegionNames(file: VirtualFile, project: Project): List<String> {
    if (file.isDirectory || file.fileType.isBinary) return emptyList()
    val content = PsiManager.getInstance(project).findFile(file)?.text ?: file.readText()
    return CodeSnippetSyntax.REGION_REGEX.findAll(content)
        .filter { it.groups["endMarker"] == null }
        .mapNotNull { it.groups["region"]?.value }
        .distinct()
        .toList()
}
