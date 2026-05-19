package com.jimmy474.libraryindexerplugin.services

import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.TextRange
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.fields.ExtendableTextField
import com.intellij.ui.dsl.builder.*
import com.intellij.util.ui.JBUI
import com.jimmy474.libraryindexerplugin.plugin.*
import kotlinx.serialization.json.*
import java.awt.Font
import java.awt.Graphics
import java.io.File
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.SwingConstants

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
    private val nameFormatProp = propertyGraph.property(NameFormat.DEFAULT)
    private val methodFormatProp = propertyGraph.property(MethodFormat.DEFAULT)
    private val customNameProp = propertyGraph.property("")

    private val previewTextProp = propertyGraph.property("")
    private val foldedPreviewProp = propertyGraph.property("")

    private var stateLock = false
    val foldedPreviewLabel = object : JBLabel(foldedPreviewProp.get()) {
        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            g.color = foreground
            g.drawRect(0, 0, width - 1, height - 1)
        }
    }.apply {
        val scheme = EditorColorsManager.getInstance().globalScheme
        val attributes = scheme.getAttributes(EditorColors.FOLDED_TEXT_ATTRIBUTES)

        background = attributes.backgroundColor ?: JBColor.namedColor("Editor.foldBackground", JBColor(0xF4F4F4, 0x3A3A3A))
        foreground = attributes.foregroundColor ?: JBColor.namedColor("Editor.foreground", JBColor(0x000000, 0xBBBBBB))

        isOpaque = true
        horizontalAlignment = SwingConstants.CENTER
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
        previewTextProp.set(getUpdatedReferenceText(newReference))
        foldedPreviewProp.set(generateFoldedPreview(newReference))
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
                val typeIndex = currentLoadedType

                if (item == null || typeIndex == null) {
                    showOverloadProp.set(false)
                    updatePreview()
                    return@withStateLock
                }

                if ((item.isField && !item.isConstructor) || item.name == "<none>") {
                    showOverloadProp.set(false)
                    updatePreview()
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
                    showOverloadProp.set(distinctOverloads.size > 1 || distinctOverloads.firstOrNull()?.params?.isNotEmpty() ?: false)
                } else {
                    showOverloadProp.set(false)
                }

            }
        }

        overloadProp.afterChange { updatePreview() }
        nameFormatProp.afterChange { updatePreview() }
        methodFormatProp.afterChange { updatePreview() }
        customNameProp.afterChange { updatePreview() }
    }

    private fun updateClassOrPackageListener(fqn: String) {
        withStateLock{
            membersModel.removeAll()
            overloadsModel.removeAll()

            val typeIndex = fileReader.loadClassIndex(fqn)
            currentLoadedType = typeIndex

            if (typeIndex != null) {
                val members = mutableListOf<MemberDescriptor>()
                members.add(MemberDescriptor("<none>", isField = false, isConstructor = false))

                if (typeIndex["constructors"]!!.jsonArray.isNotEmpty()) {
                    members.add(MemberDescriptor("<init>", isField = false, isConstructor = true))
                }

                typeIndex["methods"]!!.jsonArray.forEach {
                    members.add(MemberDescriptor(it.jsonObject["name"]!!.jsonPrimitive.content, isField = false))
                }

                typeIndex["fields"]!!.jsonArray.forEach {
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

            if (parsedReference.params != null) {
                val initialParams = parsedReference.params.map { it.value }
                overloadProp.set(ParamsList(initialParams.map { simpleTypeName(it) }, initialParams))
            }

            when{
                parsedReference.flags.shortName -> nameFormatProp.set(NameFormat.SHORT)
                parsedReference.flags.fullName -> nameFormatProp.set(NameFormat.FULL)
            }
            when{
                parsedReference.flags.methodReturnType -> methodFormatProp.set(MethodFormat.RETURN_TYPE)
                parsedReference.flags.methodOnlyType -> methodFormatProp.set(MethodFormat.TYPE_ONLY)
                parsedReference.flags.methodOnlyName -> methodFormatProp.set(MethodFormat.NAME_ONLY)
                parsedReference.flags.methodBoth -> methodFormatProp.set(MethodFormat.BOTH)
            }
            if (parsedReference.flags.isConstructor) memberProp.set(MemberDescriptor("<init>", isField = false, isConstructor = true))

            if(parsedReference.memberName == null && parsedReference.memberType == IndexReference.MemberType.NONE){
                memberProp.set(MemberDescriptor("<none>", isField = false, isConstructor = false))
            }
            parsedReference.customName?.let { customNameProp.set(it.value) }
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
                            val choice = dialog.selectedFile
                            if (choice != null) {
                                (it.source as? ExtendableTextField)?.let { tf ->
                                    tf.text = choice.path.removePrefix(targetFolder.path).removePrefix("/").replace('/', '.').removeSuffix(".json")
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
                                    AllIcons.Nodes.Class
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
            }.visibleIf(showOverloadProp)

            group("Formatting Flags") {
                buttonsGroup("Class output") {
                    row {
                        radioButton("Default", NameFormat.DEFAULT).actionListener { _, _ -> nameFormatProp.set(NameFormat.DEFAULT) }
                        radioButton("Short ( ${NameFormat.SHORT.symbol} )", NameFormat.SHORT).actionListener { _, _ -> nameFormatProp.set(NameFormat.SHORT) }
                        radioButton("Full ( ${NameFormat.FULL.symbol} )", NameFormat.FULL).actionListener { _, _ -> nameFormatProp.set(NameFormat.FULL) }
                    }
                }.bind({ nameFormatProp.get() }, { nameFormatProp.set(it) })

                buttonsGroup("Method output") {
                    row {
                        radioButton("Default", MethodFormat.DEFAULT).actionListener { _, _ -> methodFormatProp.set(MethodFormat.DEFAULT) }
                        radioButton("Type ( ${MethodFormat.TYPE_ONLY.symbol} )", MethodFormat.TYPE_ONLY).actionListener { _, _ -> methodFormatProp.set(MethodFormat.TYPE_ONLY) }
                        radioButton("Name ( ${MethodFormat.NAME_ONLY.symbol} )", MethodFormat.NAME_ONLY).actionListener { _, _ -> methodFormatProp.set(MethodFormat.NAME_ONLY) }
                        radioButton("Both ( ${MethodFormat.BOTH.symbol} )", MethodFormat.BOTH).actionListener { _, _ -> methodFormatProp.set(MethodFormat.BOTH) }
                        radioButton("Return ( ${MethodFormat.RETURN_TYPE.symbol} )", MethodFormat.RETURN_TYPE).actionListener { _, _ -> methodFormatProp.set(MethodFormat.RETURN_TYPE) }
                    }
                }.bind({ methodFormatProp.get() }, { methodFormatProp.set(it) }).visibleIf(showOverloadProp)

                row("Custom Name ( ${LibraryIndex.CUSTOM_NAME_SYMBOL.unescapeRegex()} )") {
                    textField()
                        .bindText(customNameProp)
                        .align(AlignX.FILL)
                        .comment("It is not recommended to use custom names, Unless it is an emergency")
                }
            }

            separator()

            row("Preview") {
                textField()
                    .bindText(previewTextProp)
                    .align(AlignX.FILL)
                    .applyToComponent {
                        isEditable = false
                        font = font.deriveFont(Font.BOLD)
                    }
            }

            row("Folded Preview"){}
            row{
                cell(foldedPreviewLabel).bindText(foldedPreviewProp)
            }

        }
    }

    fun getUpdatedReferenceText(newReference: IndexReference = getNewReference()): String {
        return newReference.toMarkdownReference()
    }

    private fun getNewReference(): IndexReference = IndexReference(
        fullRange = TextRange.EMPTY_RANGE,
        fqn = GroupInfo(fqnProp.get(), TextRange.EMPTY_RANGE),
        className = null,
        memberName = memberProp.get()?.let { if (it.isConstructor || it.name == "<none>") null else GroupInfo(it.name, TextRange.EMPTY_RANGE) },
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
            shortName = nameFormatProp.get() == NameFormat.SHORT,
            fullName = nameFormatProp.get() == NameFormat.FULL,
            methodWithParams = false,
            methodOnlyType = methodFormatProp.get() == MethodFormat.TYPE_ONLY,
            methodOnlyName = methodFormatProp.get() == MethodFormat.NAME_ONLY,
            methodBoth = methodFormatProp.get() == MethodFormat.BOTH,
            methodReturnType = methodFormatProp.get() == MethodFormat.RETURN_TYPE,
            isConstructor = memberProp.get()?.isConstructor ?: false,
        ),
        customName = customNameProp.get().takeIf { it.isNotBlank() }?.let { GroupInfo(it, TextRange.EMPTY_RANGE) }
    )

    private fun generateFoldedPreview(indexReference: IndexReference): String {
        val fqn = fqnProp.get()
        val member = memberProp.get()

        if (fqn.isEmpty()) return ""

        val targetInfo = if (member == null || member.name == "<none>") {
            TargetInfo(classFqn = indexReference.fqn.value)
        } else if (member.isField && !member.isConstructor) {
            TargetInfo(classFqn = indexReference.fqn.value, memberName = indexReference.memberName!!.value, isField = true)
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

            val returnType = if (!member.isConstructor) targetMethod?.get("type")?.jsonPrimitive?.content else null

            val params = targetMethod?.get("parameters")?.jsonArray?.map {
                ParameterData(
                    name = it.jsonObject["name"]!!.jsonPrimitive.content,
                    typeFqn = it.jsonObject["type"]!!.jsonPrimitive.content
                )
            } ?: emptyList()

            TargetInfo(
                classFqn = indexReference.fqn.value,
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
    DEFAULT(""),
    SHORT(LibraryIndex.SHORT_NAME_SYMBOL.unescapeRegex()),
    FULL(LibraryIndex.FULL_NAME_SYMBOL.unescapeRegex())
}
enum class MethodFormat(val symbol: String) {
    DEFAULT(""),
    TYPE_ONLY(LibraryIndex.METHOD_ONLY_TYPE_SYMBOL.unescapeRegex()),
    NAME_ONLY(LibraryIndex.METHOD_ONLY_NAME_SYMBOL.unescapeRegex()),
    BOTH(LibraryIndex.METHOD_BOTH_SYMBOL.unescapeRegex()),
    RETURN_TYPE(LibraryIndex.METHOD_RETURN_TYPE_SYMBOL.unescapeRegex())
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