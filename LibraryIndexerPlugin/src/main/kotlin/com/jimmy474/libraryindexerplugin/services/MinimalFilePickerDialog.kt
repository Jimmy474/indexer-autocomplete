package com.jimmy474.libraryindexerplugin.services

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.SimpleTree
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JTree
import javax.swing.event.TreeExpansionEvent
import javax.swing.event.TreeWillExpandListener
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath
import javax.swing.tree.TreeSelectionModel

class MinimalFilePickerDialog(
    project: Project,
    private val rootFolder: VirtualFile,
    private val initial: String
) : DialogWrapper(project, true) {

    private val tree = SimpleTree()
    var selectedFile: VirtualFile? = null
        private set

    init {
        title = "Select Class or Package"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val rootNode = CustomFileNode(rootFolder,false)
        val treeModel = DefaultTreeModel(rootNode)
        tree.model = treeModel

        rootNode.loadChildren(treeModel)
        tree.selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
        tree.isRootVisible = false

        tree.addTreeWillExpandListener(object : TreeWillExpandListener {
            override fun treeWillExpand(event: TreeExpansionEvent) {
                val node = event.path.lastPathComponent as? CustomFileNode
                node?.loadChildren(treeModel)
            }

            override fun treeWillCollapse(event: TreeExpansionEvent?) {}
        })

        tree.cellRenderer = object : ColoredTreeCellRenderer() {
            override fun customizeCellRenderer(
                tree: JTree, value: Any?, selected: Boolean,
                expanded: Boolean, leaf: Boolean, row: Int, hasFocus: Boolean
            ) {
                val node = value as? CustomFileNode ?: return
                val file = node.virtualFile

                append(file.nameWithoutExtension, SimpleTextAttributes.REGULAR_ATTRIBUTES)
                icon = if(node.isResource){
                    if(file.isDirectory) AllIcons.Nodes.Folder else AllIcons.FileTypes.Any_type
                }else{
                    if (file.isDirectory) AllIcons.Nodes.Package else AllIcons.Nodes.Class
                }
            }
        }

        if (initial.isNotBlank()) {
            selectInitialField(rootNode, treeModel)
        }

        val scrollPane = JBScrollPane(tree)
        scrollPane.preferredSize = Dimension(350, 400)
        return scrollPane
    }

    override fun doOKAction() {
        val selectedNode = tree.lastSelectedPathComponent as? CustomFileNode
        selectedFile = selectedNode?.virtualFile
        super.doOKAction()
    }

    private fun selectInitialField(rootNode: CustomFileNode, treeModel: DefaultTreeModel) {
        val targetParts = initial.split(".")

        var currentNode = rootNode
        val pathNodes = mutableListOf<Any>(rootNode)

        for (part in targetParts) {
            currentNode.loadChildren(treeModel)
            val matchedChildren = (0 until currentNode.childCount).map { currentNode.getChildAt(it) as CustomFileNode }
            val file = matchedChildren.firstOrNull { it.virtualFile.nameWithoutExtension == part && !it.virtualFile.isDirectory }
            val matchedChild = file ?: matchedChildren.firstOrNull { it.virtualFile.nameWithoutExtension == part && it.virtualFile.isDirectory }

            if (matchedChild != null) {
                currentNode = matchedChild
                pathNodes.add(matchedChild)
            } else {
                return
            }
        }

        val treePath = TreePath(pathNodes.toTypedArray())
        tree.expandPath(treePath)
        tree.selectionPath = treePath

        tree.scrollPathToVisible(treePath)
    }
}

class CustomFileNode(val virtualFile: VirtualFile, isInsideResourceRoot: Boolean) : DefaultMutableTreeNode(virtualFile) {
    private var isLoaded = false

    override fun isLeaf(): Boolean = !virtualFile.isDirectory
    val isResource = isInsideResourceRoot || (virtualFile.isDirectory && virtualFile.name == "_resources")

    fun loadChildren(model: DefaultTreeModel) {
        if (isLoaded || !virtualFile.isDirectory) return

        val childrenFiles = virtualFile.children ?: return
        val filteredChildren = childrenFiles.filter { !it.name.startsWith(".") || it.name == "manifest.json" }

        for (child in filteredChildren) {
            val childNode = CustomFileNode(child, isResource)
            this.add(childNode)
        }

        isLoaded = true
        model.nodeStructureChanged(this)
    }
}