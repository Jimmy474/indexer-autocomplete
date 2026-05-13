package com.jimmy474.indexerautocomplete.plugin

import com.intellij.lang.ASTNode
import com.intellij.lang.folding.FoldingBuilderEx
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.editor.Document
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTreeUtil

class LibraryIndexFoldingBuilder : FoldingBuilderEx() {

    override fun buildFoldRegions(root: PsiElement, document: Document, quick: Boolean): Array<FoldingDescriptor> {
        val descriptors = mutableListOf<FoldingDescriptor>()

        val elements = PsiTreeUtil.findChildrenOfType(root, LibraryIndexPsiElement::class.java)
        for (element in elements) {
            descriptors.add(FoldingDescriptor(element.node, element.textRange))
        }

        return descriptors.toTypedArray()
    }

    override fun getPlaceholderText(node: ASTNode): String? {
        val element = node.psi as? LibraryIndexPsiElement ?: return null
        val regex = LibraryIndexCompletionProvider.INDEX_REFERENCE_FULL_REGEX
        val match = regex.find(element.text) ?: return null
        val indexReference = match.toIndexReference()

        val resolvedTargets = element.references.mapNotNull { it.resolve() }
        val resolved = resolvedTargets.firstOrNull { it is PsiMethod || it is PsiField } ?: resolvedTargets.firstOrNull { it is PsiClass }

        return when (resolved) {
            is PsiMethod -> {
                val className = resolved.containingClass?.name ?: ""
                val methodName = resolved.name
                val params = resolved.parameterList.parameters.joinToString(", ") {
                    it.type.presentableText + " " + it.name
                }
                "$className.$methodName(${if(indexReference.fullDisplayFlag) params else ""})"
            }
            is PsiField -> {
                val className = resolved.containingClass?.name ?: ""
                "$className.${resolved.name}"
            }
            is PsiClass -> resolved.name
            else -> {
                element.text.removePrefix("${LibraryIndexCompletionProvider.PREFIX}`").removeSuffix("`")
            }
        }
    }

    override fun isCollapsedByDefault(node: ASTNode): Boolean = true
}