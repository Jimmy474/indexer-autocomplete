package com.jimmy474.indexerautocomplete.plugin

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.AutoCompletionPolicy
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
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
        const val MEMBER_PREFIX = "#"
        const val VALID_ID = "[a-zA-Z_$][a-zA-Z0-9_$]*"

        val INDEX_REFERENCE_PATH_REGEX = Regex("($VALID_ID(\\.$VALID_ID)*)($MEMBER_PREFIX($VALID_ID)?(\\((\\.{3})?\\))?)?")

        val INDEX_REFERENCE_PREFIXED_REGEX = Regex("$PREFIX`$INDEX_REFERENCE_PATH_REGEX`")
        val INDEX_REFERENCE_FULL_REGEX = Regex("^$INDEX_REFERENCE_PREFIXED_REGEX$")
    }

    override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
        val libraryElement = parameters.position.parent ?: return
        val projectDir = parameters.position.project.guessProjectDir() ?: return
        val root = projectDir.findChild("library-index-dependency") ?: return
        if (!root.isDirectory) return

        val path = parameters.originalFile.text.substring(libraryElement.textRange.startOffset, parameters.offset).removePrefix("${PREFIX}`").trim()
        val memberType = if(path.contains(MEMBER_PREFIX)) MEMBER_PREFIX else null
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
            fileSuggestions(result, currentDir, memberName)
        } else {
            folderSuggestions(result, searchPart, currentDir)
        }
        result.restartCompletionOnAnyPrefixChange()
    }

    private fun fileSuggestions(result: CompletionResultSet, currentDir: VirtualFile, typeName: String?) {
        val resultSetWithPrefix = result.withPrefixMatcher(PlainPrefixMatcher(typeName ?: "", true))
        val content = Json.parseToJsonElement(currentDir.readText()).jsonObject
        val methods = content["methods"]?.jsonArray ?: emptyList()
        val fields = content["fields"]?.jsonArray ?: emptyList()
        resultSetWithPrefix.addAllElements(
            fields.map { element ->
                element.jsonObject["name"]?.let {
                    LookupElementBuilder.create(it.jsonPrimitive.content)
                        .withIcon(PlatformIcons.FIELD_ICON)
                        .withInsertHandler { ctx, _ ->
                            AutoPopupController.getInstance(ctx.project).scheduleAutoPopup(ctx.editor)
                        }
                }
            }
        )
        resultSetWithPrefix.addAllElements(
            methods.map { element ->
                element.jsonObject["name"]?.let {
                    LookupElementBuilder.create(it.jsonPrimitive.content)
                        .withIcon(PlatformIcons.METHOD_ICON)
                        .withInsertHandler { ctx, _ ->
                            ctx.document.insertString(ctx.selectionEndOffset, "()")
                            ctx.editor.caretModel.moveToOffset(ctx.selectionEndOffset)
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
            if(methods || fields){
                specialSymbolsResult.addElement(
                    PrioritizedLookupElement.withPriority(
                        LookupElementBuilder.create(MEMBER_PREFIX).withLookupString("$searchPart$MEMBER_PREFIX").withTypeText(MEMBER_PREFIX)
                            .withIcon(AllIcons.Nodes.MultipleTypeDefinitions).withPresentableText("members").withInsertHandler { ctx, _ ->
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
                    ctx.document.insertString(ctx.selectionEndOffset, ".")
                    ctx.editor.caretModel.moveToOffset(ctx.selectionEndOffset)
                }
                AutoPopupController.getInstance(ctx.project).scheduleAutoPopup(ctx.editor)
            }.withAutoCompletionPolicy(AutoCompletionPolicy.NEVER_AUTOCOMPLETE)

            resultSetWithPrefix.addElement(lookupElement)
        }
    }
}