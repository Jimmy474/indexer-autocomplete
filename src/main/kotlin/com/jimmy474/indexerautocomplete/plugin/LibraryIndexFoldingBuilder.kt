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
        val regex = LibraryIndex.INDEX_REFERENCE_REGEX
        val match = regex.matchEntire(element.text) ?: return null
        val indexReference = match.toIndexReference()

        val resolvedTargets = element.references.mapNotNull { it.resolve() }
        val resolved = resolvedTargets.firstOrNull { it is PsiMethod || it is PsiField } ?: resolvedTargets.firstOrNull { it is PsiClass }

        val className = when (resolved) {
            is PsiMethod, is PsiField -> resolved.containingClass!!
            is PsiClass -> resolved
            else -> null
        }?.let{
            if(indexReference.flags.fullName) it.qualifiedName else it.name
        } ?: ""

        return when (resolved) {
            is PsiMethod -> {
                val methodName = resolved.name
                val params = resolved.parameterList.parameters.joinToString(", ") {
                    it.type.presentableText + " " + it.name
                }
                val paramsString = if (indexReference.flags.methodWithParams) params else ""
                when{
                    indexReference.flags.isConstructor ->"$className($paramsString)"
                    indexReference.flags.shortName -> "$methodName($paramsString)"
                    else -> "$className.$methodName($paramsString)"
                }
            }
            is PsiField -> {
                if(indexReference.flags.shortName) resolved.name else "$className.${resolved.name}"
            }
            is PsiClass -> className
            else -> {
                element.text.removePrefix("${LibraryIndex.PREFIX}`").removeSuffix("`")
            }
        }
    }

    override fun isCollapsedByDefault(node: ASTNode): Boolean = true
}