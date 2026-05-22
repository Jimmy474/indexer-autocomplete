package com.jimmy474.libraryindexerplugin.plugin

import com.intellij.icons.AllIcons
import com.intellij.ide.structureView.StructureViewExtension
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.editor.Editor
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownHeader
import javax.swing.Icon

class LibraryIndexStructureExtension: StructureViewExtension {
    override fun getType(): Class<out PsiElement> = PsiElement::class.java

    override fun getChildren(parent: PsiElement): Array<StructureViewTreeElement> {
        if (parent.containingFile?.virtualFile?.extension?.lowercase() != "md") return emptyArray()

        val macros = mutableListOf<LibraryIndexPsiElement>()
        var element = when (parent) {
            is MarkdownHeader -> parent.nextSibling
            is PsiFile -> parent.firstChild
            else -> null
        }
        while (element != null) {
            if (element is MarkdownHeader) break
            macros.addAll(PsiTreeUtil.findChildrenOfType(element, LibraryIndexPsiElement::class.java))
            if (element is LibraryIndexPsiElement) macros.add(element)
            element = element.nextSibling
        }

        return macros.map { LibraryIndexTreeElement(it) }.toTypedArray()
    }

    override fun getCurrentEditorElement(editor: Editor, parent: PsiElement): Any? {
        val offset = editor.caretModel.offset
        val file = parent.containingFile ?: return null
        val elementAtCaret = file.findElementAt(offset)
        return PsiTreeUtil.getParentOfType(elementAtCaret, LibraryIndexPsiElement::class.java)
    }

}

class LibraryIndexTreeElement(private val element: LibraryIndexPsiElement) : StructureViewTreeElement, ItemPresentation {
    override fun getValue(): Any = element
    override fun getPresentation(): ItemPresentation = this
    override fun getChildren(): Array<StructureViewTreeElement> = emptyArray()
    override fun navigate(requestFocus: Boolean) {
        if (element.canNavigate()) {
            element.navigate(requestFocus)
        }
    }

    override fun canNavigate(): Boolean = (element as? Navigatable)?.canNavigate() == true
    override fun canNavigateToSource(): Boolean = (element as? Navigatable)?.canNavigateToSource() == true
    override fun getPresentableText(): String {
        return getFoldedStringFromElement(element) ?: "Library Index Macro"
    }

    override fun getLocationString(): String? = null
    override fun getIcon(unused: Boolean): Icon = AllIcons.Gutter.ExtAnnotation
}