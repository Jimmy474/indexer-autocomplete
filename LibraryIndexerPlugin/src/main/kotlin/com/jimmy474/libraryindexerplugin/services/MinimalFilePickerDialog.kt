package com.jimmy474.libraryindexerplugin.services

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.readText
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.SearchTextField
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.AlignY
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.treeStructure.Tree
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.awt.Dimension
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JTree
import javax.swing.event.DocumentEvent
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath
import javax.swing.tree.TreeSelectionModel

class MinimalFilePickerDialog(
    project: Project,
    rootFolder: VirtualFile,
    private val initial: String
) : DialogWrapper(project, true) {

    private val manifestEntries: List<ManifestEntry>
    private val tree = Tree()

    var selectedFqn: String? = null
        private set

    init {
        title = "Select Class or Package"

        val jsonText = rootFolder.findChild("manifest.json")?.readText()
        val jsonArray = jsonText?.let { Json.parseToJsonElement(it).jsonObject["entries"]?.jsonArray }

        manifestEntries = jsonArray?.mapNotNull {
            val obj = it.jsonObject
            ManifestEntry(
                packageName = obj["packageName"]?.jsonPrimitive?.content ?: "",
                name = obj["name"]?.jsonPrimitive?.content ?: "",
                qualifiedName = obj["qualifiedName"]?.jsonPrimitive?.content ?: "",
                kind = obj["kind"]?.jsonPrimitive?.content ?: "CLASS"
            )
        } ?: emptyList()

        init()
    }

    override fun createCenterPanel(): JComponent {
        val rootNode = buildTree(manifestEntries, "")
        val treeModel = DefaultTreeModel(rootNode)

        tree.model = treeModel
        tree.selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
        tree.isRootVisible = false

        tree.cellRenderer = object : ColoredTreeCellRenderer() {
            override fun customizeCellRenderer(
                tree: JTree, value: Any?, selected: Boolean,
                expanded: Boolean, leaf: Boolean, row: Int, hasFocus: Boolean
            ) {
                val node = value as? CustomManifestNode ?: return
                append(node.nodeName, SimpleTextAttributes.REGULAR_ATTRIBUTES)

                icon = getIconFromKind(node.kind)
            }
        }

        if (initial.isNotBlank()) {
            selectInitialField(rootNode, initial)
        }

        val searchField = SearchTextField().apply {
            addDocumentListener(object : DocumentAdapter() {
                override fun textChanged(e: DocumentEvent) {
                    val filterText = text
                    val newRoot = buildTree(manifestEntries, filterText)
                    treeModel.setRoot(newRoot)
                    treeModel.reload()

                    if (filterText.isNotBlank()) {
                        expandAll(tree, TreePath(newRoot))
                    }
                }
            })
        }

        return panel {
            row {
                cell(searchField).align(AlignX.FILL)
            }
            row {
                scrollCell(tree)
                    .align(AlignX.FILL)
                    .align(AlignY.FILL)
            }.resizableRow()
        }.apply {
            preferredSize = Dimension(350, 450)
        }
    }

    override fun doOKAction() {
        val selectedNode = tree.lastSelectedPathComponent as? CustomManifestNode
        selectedFqn = selectedNode?.fqn
        super.doOKAction()
    }

    private fun buildTree(entries: List<ManifestEntry>, filterText: String): CustomManifestNode {
        val root = CustomManifestNode("Root", "", "ROOT")

        val filtered = if (filterText.isBlank()) entries else entries.filter {
            it.qualifiedName.contains(filterText)
        }

        for (entry in filtered) {
            val parts = entry.qualifiedName.split('.')
            var current = root
            var currentFqn = ""

            for (i in parts.indices) {
                val part = parts[i]
                currentFqn = if (currentFqn.isEmpty()) part else "$currentFqn.$part"
                val isLeaf = (i == parts.size - 1)

                var childNode = (0 until current.childCount)
                    .map { current.getChildAt(it) as CustomManifestNode }
                    .find { it.nodeName == part }

                if (childNode == null) {
                    val kind = if (isLeaf) entry.kind else "PACKAGE"
                    childNode = CustomManifestNode(part, currentFqn, kind)
                    current.add(childNode)
                } else if (isLeaf) {
                    childNode.kind = entry.kind
                }

                current = childNode
            }
        }
        return root
    }

    private fun selectInitialField(rootNode: CustomManifestNode, targetFqn: String) {
        val parts = targetFqn.split('.')
        var current = rootNode
        val path = mutableListOf<Any>(rootNode)
        var currentFqn = ""

        for (part in parts) {
            currentFqn = if (currentFqn.isEmpty()) part else "$currentFqn.$part"
            val child = (0 until current.childCount)
                .map { current.getChildAt(it) as CustomManifestNode }
                .find { it.fqn == currentFqn }

            if (child != null) {
                current = child
                path.add(child)
            } else {
                break
            }
        }

        val treePath = TreePath(path.toTypedArray())
        tree.expandPath(treePath)
        tree.selectionPath = treePath
        tree.scrollPathToVisible(treePath)
    }

    private fun expandAll(tree: JTree, parent: TreePath) {
        val node = parent.lastPathComponent as DefaultMutableTreeNode
        if (node.childCount >= 0) {
            for (e in node.children()) {
                val path = parent.pathByAddingChild(e)
                expandAll(tree, path)
            }
        }
        tree.expandPath(parent)
    }
}

fun getIconFromKind(kind: String): Icon? = when (kind) {
    "PACKAGE" -> AllIcons.Nodes.Package
    "INTERFACE" -> AllIcons.Nodes.Interface
    "ENUM" -> AllIcons.Nodes.Enum
    "ANNOTATION" -> AllIcons.Nodes.Annotationtype
    "RECORD" -> AllIcons.Nodes.Record
    else -> AllIcons.Nodes.Class
}

data class ManifestEntry(
    val packageName: String,
    val name: String,
    val qualifiedName: String,
    val kind: String
)

class CustomManifestNode(
    val nodeName: String,
    val fqn: String,
    var kind: String
) : DefaultMutableTreeNode(nodeName)