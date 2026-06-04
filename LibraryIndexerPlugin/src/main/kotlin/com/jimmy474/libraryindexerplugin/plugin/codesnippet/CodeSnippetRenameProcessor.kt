package com.jimmy474.libraryindexerplugin.plugin.codesnippet

import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.refactoring.rename.RenamePsiElementProcessor

class CodeSnippetRenameProcessor : RenamePsiElementProcessor() {

    override fun canProcessElement(element: PsiElement): Boolean {
        return element is PsiComment && getRegionName(element.text) != null
    }

    override fun substituteElementToRename(element: PsiElement, editor: Editor?): PsiElement? {
        return if (element is PsiComment) {
            val regionName = getRegionName(element.text) ?: return element
            RegionLightElement(element.project, element.containingFile, regionName, element)
        } else null
    }

    private fun getRegionName(text: String): String? {
        val trimmed = text.trim()
        if (trimmed.startsWith("// #region ")) return trimmed.substringAfter("// #region ").trim()
        if (trimmed.startsWith("// #endregion ")) return trimmed.substringAfter("// #endregion ").trim()
        return null
    }
}
