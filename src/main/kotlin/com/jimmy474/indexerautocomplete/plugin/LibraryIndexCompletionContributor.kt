package com.jimmy474.indexerautocomplete.plugin

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.AutoCompletionPolicy
import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.readText
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.util.PlatformIcons
import com.intellij.util.ProcessingContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class LibraryIndexCompletionContributor : CompletionContributor() {
    init {
        extend(CompletionType.BASIC, psiElement().inside(LibraryIndexPsiElement::class.java), LibraryIndexCompletionProvider())
    }
}

class LibraryIndexCompletionProvider : CompletionProvider<CompletionParameters>() {

    companion object {
        const val PREFIX = "@"
        const val METHODS_PREFIX = "#"
        const val FIELDS_PREFIX = "%"
        const val VALID_ID = "[a-zA-Z_$][a-zA-Z0-9_$]*"

        val INDEX_REFERENCE_PATH_REGEX = Regex("($VALID_ID(\\.$VALID_ID)*)(\\.|(([$METHODS_PREFIX$FIELDS_PREFIX])($VALID_ID)?))?")

        val INDEX_REFERENCE_FULL_REGEX = Regex("^$PREFIX`$INDEX_REFERENCE_PATH_REGEX`$")
    }

    override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
        val libraryElement = parameters.position.parent ?: return
        val projectDir = parameters.position.project.guessProjectDir() ?: return
        val root = projectDir.findChild("library-index-dependency") ?: return
        if (!root.isDirectory) return

        val path = parameters.originalFile.text.substring(libraryElement.textRange.startOffset, parameters.offset).removePrefix("${PREFIX}`").trim()
        val memberType = when {
            path.contains(METHODS_PREFIX) -> METHODS_PREFIX
            path.contains(FIELDS_PREFIX) -> FIELDS_PREFIX
            else -> null
        }
        val memberName = memberType?.let { path.substringAfterLast(it) }
        val pathText = memberType?.let { path.substringBeforeLast(it) } ?: path
        val parts = pathText.split('.')
        val navigationParts = parts.dropLast(1)
        val searchPart = parts.last()

        var currentDir = root
        for (part in navigationParts) {
            if (part.isEmpty()) continue
            currentDir = currentDir.findChild(part) ?: return
        }

        if (memberType != null) {
            currentDir = currentDir.findChild("$searchPart.json") ?: return
            fileSuggestions(result, currentDir, memberType, memberName)
        } else {
            folderSuggestions(result, searchPart, currentDir)
        }
        result.restartCompletionOnAnyPrefixChange()
    }

    private fun fileSuggestions(result: CompletionResultSet, currentDir: VirtualFile, type: String, typeName: String?) {
        val resultSetWithPrefix = result.withPrefixMatcher(PlainPrefixMatcher(typeName ?: "", true))
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

        currentDir.children.find{ it.nameWithoutExtension == searchPart && !it.isDirectory }?.let {
            val content = Json.parseToJsonElement(it.readText()).jsonObject
            val methods = content["methods"]?.jsonArray?.isNotEmpty() ?: false
            val fields = content["fields"]?.jsonArray?.isNotEmpty() ?: false
            if(methods){
                specialSymbolsResult.addElement(
                    PrioritizedLookupElement.withPriority(
                        LookupElementBuilder.create(METHODS_PREFIX).withLookupString("$searchPart$METHODS_PREFIX").withTypeText(METHODS_PREFIX)
                            .withIcon(PlatformIcons.METHOD_ICON).withPresentableText("methods").withInsertHandler { ctx, _ ->
                                AutoPopupController.getInstance(ctx.project).scheduleAutoPopup(ctx.editor)
                            }, 100.0
                    )
                )
            }
            if(fields){
                specialSymbolsResult.addElement(
                    PrioritizedLookupElement.withPriority(
                        LookupElementBuilder.create(FIELDS_PREFIX).withLookupString("$searchPart$FIELDS_PREFIX").withTypeText(FIELDS_PREFIX)
                            .withIcon(PlatformIcons.FIELD_ICON).withPresentableText("fields").withInsertHandler { ctx, _ ->
                                AutoPopupController.getInstance(ctx.project).scheduleAutoPopup(ctx.editor)
                            }, 100.0
                    )
                )
            }
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