package com.jimmy474.indexerautocomplete.plugin

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.AutoCompletionPolicy
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.readText
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.patterns.PlatformPatterns.psiFile
import com.intellij.util.PlatformIcons
import com.intellij.util.ProcessingContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownFile

class MyCompletionContributor : CompletionContributor() {

    init {
        extend(CompletionType.BASIC, psiElement().inFile(psiFile(MarkdownFile::class.java)), MyCompletionProvider())
    }
}

class MyCompletionProvider : CompletionProvider<CompletionParameters>() {

    companion object {
        const val PREFIX = "@"
        const val METHODS_PREFIX = "#"
        const val FIELDS_PREFIX = "$"
        val INLINE_CODE_REGEX = Regex("""${PREFIX}`([a-zA-Z0-9_.-]*)(([$METHODS_PREFIX$FIELDS_PREFIX])([a-zA-Z0-9_.-]*))?`""")
    }

    override fun addCompletions(
        parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet
    ) {
        val offset = parameters.offset
        val fileText = parameters.originalFile.text

        val searchStart = maxOf(0, offset - 200)
        val recentText = fileText.substring(searchStart, minOf(offset + 200,fileText.length))
        val match = INLINE_CODE_REGEX.findAll(recentText).lastOrNull() ?: return
        val pathText = match.groupValues[1]
        val type = match.groupValues[3].ifBlank { null }
        val typeName = match.groupValues[4]

        val projectDir = parameters.position.project.guessProjectDir() ?: return
        val root = projectDir.findChild("library-index-dependency") ?: return
        if (!root.isDirectory) return

        val parts = pathText.split('.')
        val navigationParts = parts.dropLast(1)
        val searchPart = parts.last()

        var currentDir = root
        for (part in navigationParts) {
            if (part.isEmpty()) continue
            currentDir = currentDir.findChild(part) ?: return
        }

        if (type != null) {
            currentDir = currentDir.findChild("$searchPart.json") ?: return
            fileSuggestions(result, currentDir, type, typeName)
        } else {
            folderSuggestions(result, searchPart, currentDir)
        }
        result.restartCompletionOnAnyPrefixChange()
    }

    private fun fileSuggestions(result: CompletionResultSet, currentDir: VirtualFile, type: String, typeName: String) {
        val resultSetWithPrefix = result.withPrefixMatcher(PlainPrefixMatcher(typeName, true))
        val content = Json.parseToJsonElement(currentDir.readText()).jsonObject
        val typeContent = content[if (type == METHODS_PREFIX) "methods" else "fields"]?.jsonArray ?: return
        resultSetWithPrefix.addAllElements(
            typeContent.map { element ->
                element.jsonObject["name"]?.let {
                    LookupElementBuilder.create(it.jsonPrimitive.content)
                        .withIcon(if (type == METHODS_PREFIX) PlatformIcons.METHOD_ICON else PlatformIcons.FIELD_ICON)
                        .withInsertHandler { ctx, _ ->
                            AutoPopupController.getInstance(ctx.project).scheduleAutoPopup(ctx.editor)
                        }
                }
            }
        )
    }

    private fun folderSuggestions(result: CompletionResultSet, searchPart: String, currentDir: VirtualFile) {
        val resultSetWithPrefix = result.withPrefixMatcher(PlainPrefixMatcher(searchPart, true))
        val specialSymbolsResult = result.withPrefixMatcher(PlainPrefixMatcher("", true))

        if (currentDir.children.any { it.nameWithoutExtension == searchPart && !it.isDirectory }) {
            specialSymbolsResult.addElement(
                PrioritizedLookupElement.withPriority(
                    LookupElementBuilder.create("#").withLookupString("$searchPart#").withTypeText("#")
                        .withIcon(PlatformIcons.METHOD_ICON).withPresentableText("methods").withInsertHandler { ctx, _ ->
                            AutoPopupController.getInstance(ctx.project).scheduleAutoPopup(ctx.editor)
                        }, 100.0
                )
            )
            specialSymbolsResult.addElement(
                PrioritizedLookupElement.withPriority(
                    LookupElementBuilder.create("$").withLookupString("$searchPart$").withTypeText("$")
                        .withIcon(PlatformIcons.FIELD_ICON).withPresentableText("fields").withInsertHandler { ctx, _ ->
                            AutoPopupController.getInstance(ctx.project).scheduleAutoPopup(ctx.editor)
                        }, 100.0
                )
            )
        }

        currentDir.children.forEach { child ->
            val name = if (child.isDirectory) child.name else child.nameWithoutExtension
            if (name == searchPart && !child.isDirectory) return@forEach
            val icon = if (child.isDirectory) {
                PlatformIcons.FOLDER_ICON
            } else {
                FileTypeRegistry.getInstance().getFileTypeByFileName(child.name).icon
            }

            val lookupElement = LookupElementBuilder.create(child, name).withIcon(icon).withInsertHandler { ctx, item ->
                val child = item.`object` as? VirtualFile ?: return@withInsertHandler
                if (child.isDirectory) {
                    val document = ctx.document
                    document.insertString(ctx.selectionEndOffset, ".")
                    ctx.editor.caretModel.moveToOffset(ctx.selectionEndOffset)
                }
                AutoPopupController.getInstance(ctx.project).scheduleAutoPopup(ctx.editor)
            }.withAutoCompletionPolicy(AutoCompletionPolicy.NEVER_AUTOCOMPLETE)

            resultSetWithPrefix.addElement(lookupElement)
        }
    }
}