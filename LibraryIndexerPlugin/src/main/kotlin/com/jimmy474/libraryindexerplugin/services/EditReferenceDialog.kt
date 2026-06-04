package com.jimmy474.libraryindexerplugin.services

import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.observable.util.not
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.TextRange
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.EditorTextField
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.fields.ExtendableTextField
import com.intellij.ui.dsl.builder.*
import com.intellij.util.ui.JBUI
import com.jimmy474.libraryindexerplugin.plugin.common.GroupInfo
import com.jimmy474.libraryindexerplugin.plugin.common.IndexerHighlightColors
import com.jimmy474.libraryindexerplugin.plugin.libraryindexer.*
import kotlinx.serialization.json.*
import org.intellij.plugins.markdown.lang.MarkdownFileType
import java.awt.Dimension
import java.awt.Graphics
import java.io.File
import javax.swing.JComponent
import javax.swing.JList

class EditReferenceDialog(
    private val project: Project,
    private val parsedReference: IndexReference
) : DialogWrapper(project) {

    private val fileReader = SingleFileReader(project)
    private val propertyGraph = PropertyGraph()

    private val fqnProp = propertyGraph.property("")

    private val memberProp = propertyGraph.property<MemberDescriptor?>(null)
    private val overloadProp = propertyGraph.property<ParamsList?>(null)
    private val showOverloadProp = propertyGraph.property(false)
    private val renderingModeProp = propertyGraph.property(RenderingMode.Flags)
    private val showCustomNameProp = propertyGraph.property(false)
    private val nameFormatProp = propertyGraph.property(NameFormat.Default)
    private val methodFormatProp = propertyGraph.property(MethodFormat.Default)
    private lateinit var methodSegmentedButton: SegmentedButton<MethodFormat>
    private val showMethodFormatProp = propertyGraph.property(false)
    private val emptyParamsProp = propertyGraph.property(false)
    private val customNameProp = propertyGraph.property("")

    private val previewEditor = object : EditorTextField("", project, MarkdownFileType.INSTANCE) {
        override fun getMinimumSize(): Dimension = Dimension(100, super.preferredSize.height)
        override fun getPreferredSize(): Dimension = Dimension(minOf(300,super.preferredSize.width), super.preferredSize.height)
    }.apply {
        isViewer = true
        font = EditorColorsManager.getInstance().globalScheme.getFont(EditorFontType.PLAIN)
        addSettingsProvider { editor ->
            editor.isOneLineMode = false
            editor.settings.isUseSoftWraps = true
            editor.settings.isAdditionalPageAtBottom = false
            editor.setVerticalScrollbarVisible(false)
            editor.setHorizontalScrollbarVisible(false)
            applySyntaxHighlighting(editor, text)
        }
    }
    private val foldedPreviewProp = propertyGraph.property("")

    private var stateLock = false
    val foldedPreviewLabel = object : JBTextArea(foldedPreviewProp.get()) {

        override fun getMinimumSize(): Dimension = Dimension(100, super.preferredSize.height)
        override fun getPreferredSize(): Dimension = Dimension(minOf(300,super.preferredSize.width), super.preferredSize.height)

        override fun paintComponent(g: Graphics) {
            val textWidth = g.fontMetrics.stringWidth(text)
            val desiredWidth = textWidth + insets.left + insets.right
            val paintWidth = minOf(width, desiredWidth)
            g.color = background
            g.fillRect(0, 0, paintWidth, height)

            g.color = foreground
            g.drawRect(0, 0, paintWidth - 1, height - 1)

            super.paintComponent(g)
        }
    }.apply {
        val scheme = EditorColorsManager.getInstance().globalScheme
        val attributes = scheme.getAttributes(EditorColors.FOLDED_TEXT_ATTRIBUTES)

        background = attributes.backgroundColor ?: JBColor.namedColor("Editor.foldBackground", JBColor(0xF4F4F4, 0x3A3A3A))
        foreground = attributes.foregroundColor ?: JBColor.namedColor("Editor.foreground", JBColor(0x000000, 0xBBBBBB))

        isOpaque = false
        isEditable = false
        isFocusable = false

        lineWrap = true
        wrapStyleWord = false

        font = scheme.getFont(EditorFontType.PLAIN)
        border = JBUI.Borders.empty(1, 6)
    }

    private var currentLoadedType: JsonObject? = null

    private val membersModel = CollectionComboBoxModel<MemberDescriptor>()
    private val overloadsModel = CollectionComboBoxModel<ParamsList>()

    init {
        title = "Edit Library Reference"

        setupListeners()
        applyParsedState()

        init()
    }

    private fun withStateLock(autoUpdatePreview: Boolean = true,block: () -> Unit) {
        try {
            stateLock = true
            block()
        }finally {
            stateLock = false
            if(autoUpdatePreview) updatePreview()
        }
    }

    private fun updatePreview() {
        if(stateLock) return
        val newReference = getNewReference()
        val rawMacroText = getUpdatedReferenceText(newReference)

        previewEditor.text = rawMacroText
        foldedPreviewProp.set(generateFoldedPreview(newReference))

        ApplicationManager.getApplication().invokeLater {
            val editor = previewEditor.editor ?: return@invokeLater
            applySyntaxHighlighting(editor, rawMacroText)
        }
    }

    private fun applySyntaxHighlighting(editor: Editor, rawMacroText: String) {
        val markupModel = editor.markupModel

        markupModel.removeAllHighlighters()

        val match = LibraryIndex.INDEX_REFERENCE_REGEX.matchEntire(rawMacroText) ?: return
        val previewReference = match.toIndexReference()

        val scheme = EditorColorsManager.getInstance().globalScheme
        val startOffset = previewReference.fullRange.startOffset
        val endOffset = previewReference.fullRange.endOffset

        markupModel.addRangeHighlighter(
            startOffset,
            startOffset + 2,
            HighlighterLayer.SYNTAX,
            scheme.getAttributes(IndexerHighlightColors.MACRO_PREFIX),
            HighlighterTargetArea.EXACT_RANGE
        )
        markupModel.addRangeHighlighter(
            endOffset - 1,
            endOffset,
            HighlighterLayer.SYNTAX,
            scheme.getAttributes(IndexerHighlightColors.MACRO_PREFIX),
            HighlighterTargetArea.EXACT_RANGE
        )
        markupModel.addRangeHighlighter(
            startOffset + 2,
            endOffset - 1,
            HighlighterLayer.SYNTAX,
            scheme.getAttributes(IndexerHighlightColors.MACRO_TEXT),
            HighlighterTargetArea.EXACT_RANGE
        )

        previewReference.className?.relativeRange?.let {
            markupModel.addRangeHighlighter(
                it.startOffset,
                it.endOffset,
                HighlighterLayer.SYNTAX,
                scheme.getAttributes(IndexerHighlightColors.MACRO_CLASS),
                HighlighterTargetArea.EXACT_RANGE
            )
        }
        memberProp.get()?.let { descriptor ->
            if (descriptor.isConstructor) return@let
            val key = if (previewReference.memberType == IndexReference.MemberType.METHOD) "methods" else "fields"
            val items = currentLoadedType?.get(key)?.jsonArray
            val member =
                items?.find { it.jsonObject["name"]?.jsonPrimitive?.content == previewReference.memberName?.value }?.jsonObject
            val isStatic =
                member?.get("declaration")?.jsonObject?.get("flags")?.jsonObject?.get("isStatic")?.jsonPrimitive?.boolean
                    ?: false
            val typeHighlighter = when (previewReference.memberType) {
                IndexReference.MemberType.METHOD -> if (isStatic) IndexerHighlightColors.MACRO_METHOD_STATIC else IndexerHighlightColors.MACRO_METHOD
                IndexReference.MemberType.FIELD -> if (isStatic) IndexerHighlightColors.MACRO_FIELD_STATIC else IndexerHighlightColors.MACRO_FIELD
                else -> IndexerHighlightColors.MACRO_TEXT
            }

            previewReference.memberName?.relativeRange?.let {
                markupModel.addRangeHighlighter(
                    it.startOffset,
                    it.endOffset,
                    HighlighterLayer.SYNTAX,
                    scheme.getAttributes(typeHighlighter),
                    HighlighterTargetArea.EXACT_RANGE
                )
            }
        }

        previewReference.params?.forEach {
            markupModel.addRangeHighlighter(
                it.relativeRange.startOffset,
                it.relativeRange.endOffset,
                HighlighterLayer.SYNTAX,
                scheme.getAttributes(IndexerHighlightColors.MACRO_PARAMETER),
                HighlighterTargetArea.EXACT_RANGE
            )
        }

        previewReference.flags.relativeRange.takeIf { !it.isEmpty }?.let {
            markupModel.addRangeHighlighter(
                it.startOffset,
                it.endOffset,
                HighlighterLayer.SYNTAX,
                scheme.getAttributes(IndexerHighlightColors.MACRO_FLAGS),
                HighlighterTargetArea.EXACT_RANGE
            )
        }

        previewReference.customName?.relativeRange?.let {
            markupModel.addRangeHighlighter(
                it.startOffset,
                it.endOffset,
                HighlighterLayer.SYNTAX,
                scheme.getAttributes(IndexerHighlightColors.MACRO_CUSTOM_NAME),
                HighlighterTargetArea.EXACT_RANGE
            )
        }
    }

    private fun setupListeners() {
        fqnProp.afterChange { fqn ->
            withStateLock {
                updateClassOrPackageListener(fqn)
            }
        }

        memberProp.afterChange { item ->
            withStateLock {
                overloadsModel.removeAll()
                overloadProp.set(null)
                emptyParamsProp.set(false)
                val typeIndex = currentLoadedType

                if (item == null || typeIndex == null) {
                    showOverloadProp.set(false)
                    updatePreview()
                    return@withStateLock
                }

                if ((item.isField && !item.isConstructor) || item.name == "<none>") {
                    showOverloadProp.set(false)
                    updatePreview()
                    updateMethodFormatFlag(item)
                    return@withStateLock
                }

                showOverloadProp.set(true)

                val overloads = if (item.isConstructor) {
                    typeIndex["constructors"]!!.jsonArray.mapNotNull { c ->
                        val params = c.jsonObject["parameters"]!!.jsonArray.map { it.jsonObject["type"]!!.jsonPrimitive.content }
                        ParamsList(displayParams = params.map { type -> simpleTypeName(type) }, params = params)
                    }
                } else {
                    typeIndex["methods"]!!.jsonArray
                        .filter { it.jsonObject["name"]!!.jsonPrimitive.content == item.name }
                        .map { m ->
                            val params = m.jsonObject["parameters"]!!.jsonArray.map { it.jsonObject["type"]!!.jsonPrimitive.content }
                            ParamsList(displayParams = params.map { type -> simpleTypeName(type) }, params = params)
                        }
                }

                val distinctOverloads = overloads.distinct()
                overloadsModel.replaceAll(distinctOverloads)

                if (distinctOverloads.isNotEmpty()) {
                    overloadProp.set(distinctOverloads.first())
                    val emptyParams = distinctOverloads.first().params.isEmpty()
                    emptyParamsProp.set(emptyParams)
                    showOverloadProp.set(distinctOverloads.size > 1 || !emptyParams)
                } else {
                    showOverloadProp.set(false)
                }

                updateMethodFormatFlag(item)
            }
        }

        overloadProp.afterChange { updatePreview() }
        renderingModeProp.afterChange {
            showCustomNameProp.set(it == RenderingMode.CustomName)
            updatePreview()
        }
        nameFormatProp.afterChange { updatePreview() }
        methodFormatProp.afterChange { updatePreview() }
        customNameProp.afterChange { updatePreview() }
    }

    private fun updateMethodFormatFlag(item: MemberDescriptor){
        showMethodFormatProp.set(!item.isField && item.name != "<none>")

        if (item.isConstructor && methodFormatProp.get() == MethodFormat.ReturnType) {
            methodFormatProp.set(MethodFormat.Default)
        }

        if(::methodSegmentedButton.isInitialized){
            methodSegmentedButton.update(*MethodFormat.entries.toTypedArray())
        }
    }

    private fun updateClassOrPackageListener(fqn: String) {
        withStateLock{
            membersModel.removeAll()
            overloadsModel.removeAll()

            currentLoadedType = fileReader.loadClassIndex(fqn)

            currentLoadedType?.let{ ci ->
                val members = mutableListOf<MemberDescriptor>()
                members.add(MemberDescriptor("<none>", isField = false, isConstructor = false))

                if (ci["constructors"]!!.jsonArray.isNotEmpty()) {
                    members.add(MemberDescriptor("<init>", isField = false, isConstructor = true))
                }

                ci["methods"]!!.jsonArray.forEach {
                    members.add(MemberDescriptor(it.jsonObject["name"]!!.jsonPrimitive.content, isField = false))
                }

                ci["fields"]!!.jsonArray.forEach {
                    members.add(MemberDescriptor(it.jsonObject["name"]!!.jsonPrimitive.content, isField = true))
                }

                val uniqueMembers = members.distinctBy { "${it.name}_${it.isField}" }.sortedBy { it.name }
                membersModel.replaceAll(uniqueMembers)
                memberProp.set(uniqueMembers.firstOrNull())
            }
        }
    }

    private fun applyParsedState() {
        withStateLock {
            val initialFqn = parsedReference.fqn.value
            fqnProp.set(initialFqn)

            parsedReference.memberName?.let{
                memberProp.set(MemberDescriptor(
                    it.value,
                    isField = parsedReference.memberType == IndexReference.MemberType.FIELD,
                    isConstructor = parsedReference.memberType == IndexReference.MemberType.METHOD && parsedReference.flags.isConstructor
                ))
            }

            if(parsedReference.memberType == IndexReference.MemberType.METHOD){
                showMethodFormatProp.set(true)
            }

            if (parsedReference.params != null) {
                val initialParams = parsedReference.params.map { it.value }
                overloadProp.set(ParamsList(initialParams.map { simpleTypeName(it) }, initialParams))
            }

            when{
                parsedReference.flags.shortName -> nameFormatProp.set(NameFormat.Short)
                parsedReference.flags.longName -> nameFormatProp.set(NameFormat.Long)
                parsedReference.flags.fullName -> nameFormatProp.set(NameFormat.Full)
            }
            when{
                parsedReference.flags.methodReturnType -> methodFormatProp.set(MethodFormat.ReturnType)
                parsedReference.flags.methodOnlyType -> methodFormatProp.set(MethodFormat.Type)
                parsedReference.flags.methodOnlyName -> methodFormatProp.set(MethodFormat.Name)
                parsedReference.flags.methodBoth -> methodFormatProp.set(MethodFormat.Both)
            }
            if (parsedReference.flags.isConstructor) memberProp.set(MemberDescriptor("<init>", isField = false, isConstructor = true))

            if(parsedReference.memberName == null && parsedReference.memberType == IndexReference.MemberType.NONE){
                memberProp.set(MemberDescriptor("<none>", isField = false, isConstructor = false))
            }
            parsedReference.customName?.let {
                customNameProp.set(it.value)
                showCustomNameProp.set(true)
                renderingModeProp.set(RenderingMode.CustomName)
            }
        }
    }

    override fun createCenterPanel(): JComponent {
        return panel {
            row("Class FQN") {
                val customBrowserField = TextFieldWithBrowseButton {
                    val projectRoot = project.guessProjectDir()
                    val targetFolder = projectRoot?.findFileByRelativePath("library-index-dependency")

                    if (targetFolder != null) {
                        val dialog = MinimalFilePickerDialog(project, targetFolder, fqnProp.get())
                        if (dialog.showAndGet()) {
                            val choice = dialog.selectedFqn
                            if (choice != null) {
                                (it.source as? ExtendableTextField)?.let { tf ->
                                    tf.text = choice
                                }
                            }
                        }
                    }
                }

                cell(customBrowserField)
                    .align(AlignX.FILL)
                    .bindText(fqnProp)
                    .applyToComponent {
                        isEditable = false
                    }
            }

            row("Member") {
                comboBox(membersModel)
                    .bindItem(memberProp)
                    .align(AlignX.FILL)
                    .applyToComponent {
                        renderer = object : ColoredListCellRenderer<MemberDescriptor>() {
                            override fun customizeCellRenderer(list: JList<out MemberDescriptor>, value: MemberDescriptor?, index: Int, selected: Boolean, hasFocus: Boolean) {
                                if (value == null) return

                                append(value.name)

                                icon = if(value.name == "<none>"){
                                    getIconFromKind(currentLoadedType?.get("kind")?.jsonPrimitive?.content ?: "")
                                }else if (value.isField) {
                                    AllIcons.Nodes.Field
                                } else if (value.isConstructor) {
                                    AllIcons.Nodes.Constructor
                                } else {
                                    AllIcons.Nodes.Method
                                }
                            }
                        }
                    }
            }

            row("Overload") {
                comboBox(overloadsModel)
                    .bindItem(overloadProp)
                    .align(AlignX.FILL)
                visibleIf(showOverloadProp)
            }

            row("Rendering Mode"){
                segmentedButton(RenderingMode.entries){
                    text = it.name
                    if(it == RenderingMode.CustomName){
                        icon = AllIcons.Nodes. WarningIntroduction
                        toolTipText = "Custom Name Rendering Mode is not recommended, Unless it is an emergency"
                    }
                }.bind(renderingModeProp)
            }

            group("Flags") {
                row("FQN"){
                    segmentedButton(NameFormat.entries){
                        text = if(it == NameFormat.Default) it.name else "${it.name} ( ${it.symbol} )"
                    }.bind(nameFormatProp)
                }
                row("Method") {
                    methodSegmentedButton = segmentedButton(MethodFormat.entries){
                        text = if(it == MethodFormat.Default) it.name else "${it.name} ( ${it.symbol} )"
                        when {
                            it == MethodFormat.ReturnType -> {
                                toolTipText = if (memberProp.get()?.isConstructor == true) "Return Type is not available for constructors" else null
                                enabled = memberProp.get()?.isConstructor != true
                            }
                            it != MethodFormat.Default -> {
                                toolTipText = if (emptyParamsProp.get()) "${it.name} is not available for method with no parameters" else null
                                enabled = !emptyParamsProp.get()
                            }
                            else -> {
                                toolTipText = null
                                enabled = true
                            }
                        }
                    }.bind(methodFormatProp)
                    visibleIf(showMethodFormatProp)
                }

                visibleIf(!showCustomNameProp)
            }

            group("Custom Name ( ${LibraryIndex.CUSTOM_NAME_SYMBOL.unescapeRegex()} )"){
                row{
                    textField()
                        .bindText(customNameProp)
                        .align(AlignX.FILL)
                        .validationOnInput {
                            warning("It is not recommended to use custom names, Unless it is an emergency")
                        }
                }
                visibleIf(showCustomNameProp)
            }

            group("Preview") {
                row("Reference"){
                    cell(previewEditor).align(AlignX.FILL).align(AlignY.FILL).resizableColumn()
                }.resizableRow()
                row("Output"){
                    cell(foldedPreviewLabel).align(AlignX.FILL).align(AlignY.FILL).resizableColumn().bindText(foldedPreviewProp)
                }.resizableRow()
            }

        }
    }

    fun getUpdatedReferenceText(newReference: IndexReference = getNewReference()): String {
        return newReference.toMarkdownReference()
    }

    private fun getNewReference(): IndexReference {
        val isConstructor = memberProp.get()?.isConstructor ?: false
        return IndexReference(
            fullRange = TextRange.EMPTY_RANGE,
            fqn = GroupInfo(fqnProp.get(), TextRange.EMPTY_RANGE),
            className = null,
            memberName = memberProp.get()?.let {
                if (it.isConstructor || it.name == "<none>") null else GroupInfo(
                    it.name,
                    TextRange.EMPTY_RANGE
                )
            },
            memberType = memberProp.get()?.let {
                when {
                    it.name == "<none>" -> IndexReference.MemberType.NONE
                    it.isField -> IndexReference.MemberType.FIELD
                    it.isConstructor -> IndexReference.MemberType.METHOD
                    else -> IndexReference.MemberType.METHOD
                }
            } ?: IndexReference.MemberType.NONE,
            params = overloadProp.get()?.params?.map { GroupInfo(it, TextRange.EMPTY_RANGE) },
            flags = Flags(
                relativeRange = TextRange.EMPTY_RANGE,
                shortName = !showCustomNameProp.get() && nameFormatProp.get() == NameFormat.Short,
                longName = !showCustomNameProp.get() && nameFormatProp.get() == NameFormat.Long,
                fullName = !showCustomNameProp.get() && nameFormatProp.get() == NameFormat.Full,
                methodWithParams = false,
                methodOnlyType = !showCustomNameProp.get() && showMethodFormatProp.get() && methodFormatProp.get() == MethodFormat.Type,
                methodOnlyName = !showCustomNameProp.get() && showMethodFormatProp.get() && methodFormatProp.get() == MethodFormat.Name,
                methodBoth = !showCustomNameProp.get() && showMethodFormatProp.get() && methodFormatProp.get() == MethodFormat.Both,
                methodReturnType = !showCustomNameProp.get() && showMethodFormatProp.get() && !isConstructor && methodFormatProp . get () == MethodFormat.ReturnType,
                isConstructor = memberProp.get()?.isConstructor ?: false,
            ),
            customName = if (showCustomNameProp.get()) customNameProp.get().takeIf { it.isNotBlank() }
                ?.let { GroupInfo(it, TextRange.EMPTY_RANGE) } else null
        )
    }

    private fun generateFoldedPreview(indexReference: IndexReference): String {
        val fqn = fqnProp.get()
        val member = memberProp.get()

        if (fqn.isEmpty()) return ""

        val outerClass = currentLoadedType?.get("nesting")?.jsonObject?.let{
            if(it["isNested"]!!.jsonPrimitive.boolean) simpleTypeName(it["enclosingType"]!!.jsonPrimitive.content) else null
        }
        val targetInfo = if (member == null || member.name == "<none>") {
            TargetInfo(classFqn = indexReference.fqn.value, outerClass = outerClass)
        } else if (member.isField && !member.isConstructor) {
            TargetInfo(classFqn = indexReference.fqn.value, outerClass = outerClass, memberName = indexReference.memberName!!.value, isField = true)
        } else {
            val targetArray = if (member.isConstructor) {
                currentLoadedType?.get("constructors")?.jsonArray
            } else {
                currentLoadedType?.get("methods")?.jsonArray
            }

            val targetMethod = targetArray?.find {
                val currentParams = overloadProp.get()?.params ?: emptyList()
                it.jsonObject["parameters"]!!.jsonArray.map { p -> p.jsonObject["type"]!!.jsonPrimitive.content } == currentParams
            }?.jsonObject

            val returnType = if (!member.isConstructor) targetMethod?.get("returnType")?.jsonPrimitive?.content else null

            val params = targetMethod?.get("parameters")?.jsonArray?.map {
                ParameterData(
                    name = it.jsonObject["name"]!!.jsonPrimitive.content,
                    typeFqn = it.jsonObject["type"]!!.jsonPrimitive.content
                )
            } ?: emptyList()

            TargetInfo(
                classFqn = indexReference.fqn.value,
                outerClass = outerClass,
                memberName = member.name,
                isField = false,
                isConstructor = member.isConstructor,
                returnTypeFqn = returnType,
                parameters = params
            )
        }

        return ReferenceFormatter.formatFoldedText(indexReference, targetInfo, fqn)
    }
}

enum class NameFormat(val symbol: String) {
    Default(""),
    Short(LibraryIndex.SHORT_NAME_SYMBOL.unescapeRegex()),
    Long(LibraryIndex.LONG_NAME_SYMBOL.unescapeRegex()),
    Full(LibraryIndex.FULL_NAME_SYMBOL.unescapeRegex())
}
enum class MethodFormat(val symbol: String) {
    Default(""),
    Type(LibraryIndex.METHOD_ONLY_TYPE_SYMBOL.unescapeRegex()),
    Name(LibraryIndex.METHOD_ONLY_NAME_SYMBOL.unescapeRegex()),
    Both(LibraryIndex.METHOD_BOTH_SYMBOL.unescapeRegex()),
    ReturnType(LibraryIndex.METHOD_RETURN_TYPE_SYMBOL.unescapeRegex())
}

enum class RenderingMode{
    Flags,
    CustomName
}

class SingleFileReader(private val project: Project) {
    private val jsonFormat = Json { ignoreUnknownKeys = true }

    fun loadClassIndex(fqn: String): JsonObject? {
        if (fqn.isBlank()) return null

        val relativePath = fqn.replace('.', '/') + ".json"
        val indexFile = File(project.basePath, "library-index-dependency/$relativePath")

        if (!indexFile.exists() || !indexFile.isFile) return null
        return try {
            val jsonText = indexFile.readText()
            jsonFormat.parseToJsonElement(jsonText).jsonObject
        } catch (_: Exception) {
            null
        }
    }
}

data class MemberDescriptor(
    val name: String,
    val isField: Boolean,
    val isConstructor: Boolean = false
) {
    override fun toString(): String = name
}

data class ParamsList(
    val displayParams: List<String>,
    val params: List<String>,
){
    override fun toString(): String = displayParams.joinToString(", ")
}
