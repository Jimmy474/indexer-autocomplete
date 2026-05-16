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
    override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
        val libraryElement = parameters.position.parent ?: return
        val projectDir = parameters.position.project.guessProjectDir() ?: return
        val root = projectDir.findChild("library-index-dependency") ?: return
        if (!root.isDirectory) return

        val path = parameters.originalFile.text.substring(libraryElement.textRange.startOffset, parameters.offset).removePrefix("${LibraryIndex.PREFIX}`")
        if (parameterSuggestions(result, root, path)) {
            result.restartCompletionOnAnyPrefixChange()
            return
        }

        val memberType = if(path.contains(LibraryIndex.MEMBER_PREFIX)) LibraryIndex.MEMBER_PREFIX else null
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

    private fun parameterSuggestions(result: CompletionResultSet, root: VirtualFile, path: String): Boolean {
        val openParen = path.lastIndexOf('(')
        if (openParen == -1 || path.lastIndexOf(')') > openParen) return false

        val referenceText = path.substring(0, openParen)
        val currentText = path.substring(openParen + 1)

        val classPath: String
        val methodName: String?
        val constructors: Boolean
        if (referenceText.contains(LibraryIndex.MEMBER_PREFIX)) {
            classPath = referenceText.substringBeforeLast(LibraryIndex.MEMBER_PREFIX)
            methodName = referenceText.substringAfterLast(LibraryIndex.MEMBER_PREFIX).takeIf { it.isNotBlank() } ?: return false
            constructors = false
        } else {
            classPath = referenceText
            methodName = null
            constructors = true
        }

        val classFile = findClassFile(root, classPath) ?: return false
        val content = Json.parseToJsonElement(classFile.readText()).jsonObject
        val overloads = if (constructors) {
            content["constructors"]?.jsonArray ?: emptyList()
        } else {
            content["methods"]?.jsonArray?.filter {
                it.jsonObject["name"]?.jsonPrimitive?.content == methodName
            } ?: emptyList()
        }

        val overloadLookups = overloads.mapNotNull { overload ->
            val name = if (constructors) classPath.substringAfterLast('.') else methodName ?: return@mapNotNull null
            val parameterTypes = parameterTypes(overload)
            if (parameterTypes.isEmpty()) return@mapNotNull null
            OverloadLookup(name, parameterTypes)
        }.distinct()

        val resultSetWithPrefix = result.withPrefixMatcher(PlainPrefixMatcher(currentText, false))
        resultSetWithPrefix.addAllElements(
            overloadLookups.map { overload ->
                LookupElementBuilder.create(overload.paramsText)
                    .withPresentableText(overload.name)
                    .withTailText(overload.tailText, true)
                    .withIcon(PlatformIcons.METHOD_ICON)
                    .withInsertHandler { ctx, _ ->
                        var tailOffset = ctx.tailOffset
                        if (tailOffset >= ctx.document.textLength || ctx.document.charsSequence[tailOffset] != ')') {
                            ctx.document.insertString(tailOffset++, ")")
                        }
                        ctx.editor.caretModel.moveToOffset(tailOffset)
                    }
            }
        )
        return true
    }

    private fun findClassFile(root: VirtualFile, classPath: String): VirtualFile? {
        val parts = classPath.split('.').filter { it.isNotBlank() }
        if (parts.isEmpty()) return null

        var currentDir = root
        for (part in parts.dropLast(1)) {
            currentDir = currentDir.findChild(part) ?: return null
        }
        return currentDir.findChild("${parts.last()}.json")
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
        val methodLookups = methods.groupBy { it.jsonObject["name"]?.jsonPrimitive?.content }.flatMap { (name, overloads) ->
            if (name == null) return@flatMap emptyList()
            overloads.flatMap { overload ->
                val parameterTypes = parameterTypes(overload)
                buildList {
                    if (overloads.size == 1) {
                        add(OverloadLookup(name, emptyList()))
                    }
                    if (parameterTypes.isNotEmpty()) {
                        add(OverloadLookup(name, parameterTypes))
                    }
                }
            }
        }.distinctBy { it.insertText }

        resultSetWithPrefix.addAllElements(
            methodLookups.map { overload ->
                LookupElementBuilder.create(overload.insertText)
                    .withLookupString(overload.name)
                    .withPresentableText(overload.name)
                    .withTailText(overload.tailText, true)
                    .withIcon(PlatformIcons.METHOD_ICON)
                    .withInsertHandler { ctx, _ ->
                        AutoPopupController.getInstance(ctx.project).scheduleAutoPopup(ctx.editor)
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
            val constructors = content["constructors"]?.jsonArray?.isNotEmpty() ?: false
            if(methods || fields){
                specialSymbolsResult.addElement(
                    PrioritizedLookupElement.withPriority(
                        LookupElementBuilder.create(LibraryIndex.MEMBER_PREFIX).withLookupString("$searchPart${LibraryIndex.MEMBER_PREFIX}").withTypeText(LibraryIndex.MEMBER_PREFIX)
                            .withIcon(AllIcons.Nodes.MultipleTypeDefinitions).withPresentableText("members").withInsertHandler { ctx, _ ->
                                AutoPopupController.getInstance(ctx.project).scheduleAutoPopup(ctx.editor)
                            }, 100.0
                    )
                )
            }
            if (constructors) {
                specialSymbolsResult.addElement(
                    PrioritizedLookupElement.withPriority(
                        LookupElementBuilder.create("()")
                            .withTypeText("constructor")
                            .withIcon(PlatformIcons.METHOD_ICON)
                            .withInsertHandler { ctx, _ ->
                                ctx.editor.caretModel.moveToOffset(ctx.selectionEndOffset - 1)
                                AutoPopupController.getInstance(ctx.project).scheduleAutoPopup(ctx.editor)
                            },
                        90.0
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

    private data class OverloadLookup(val name: String, val parameterTypes: List<String>) {
        val paramsText: String = parameterTypes.joinToString(",")
        val insertText: String = "$name(${parameterTypes.joinToString(",")})"
        val tailText: String = "(${parameterTypes.joinToString(","){ simpleTypeName(it) }})"
    }
}
