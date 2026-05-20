package com.jimmy474.libraryindexerplugin.plugin

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
            descriptors.add(FoldingDescriptor(element.node, element.textRange, null))
        }

        return descriptors.toTypedArray()
    }

    override fun getPlaceholderText(node: ASTNode): String? {
        val element = node.psi as? LibraryIndexPsiElement ?: return null
        val match = LibraryIndex.INDEX_REFERENCE_REGEX.matchEntire(element.text) ?: return null
        val indexReference = match.toIndexReference()

        val resolvedTargets = element.references.mapNotNull { it.resolve() }
        val resolved = resolvedTargets.firstOrNull { it is PsiMethod || it is PsiField } ?: resolvedTargets.firstOrNull { it is PsiClass }

        val targetInfo = when (resolved) {
            is PsiMethod -> TargetInfo(
                classFqn = resolved.containingClass?.qualifiedName ?: "",
                outerClass = resolved.containingClass?.containingClass?.name,
                memberName = resolved.name,
                isField = false,
                isConstructor = resolved.isConstructor,
                returnTypeFqn = resolved.returnType?.canonicalText,
                parameters = resolved.parameterList.parameters.map { ParameterData(it.name, it.type.canonicalText) }
            )
            is PsiField -> TargetInfo(
                classFqn = resolved.containingClass?.qualifiedName ?: "",
                outerClass = resolved.containingClass?.containingClass?.name,
                memberName = resolved.name,
                isField = true
            )
            is PsiClass -> TargetInfo(
                classFqn = resolved.qualifiedName ?: "",
                outerClass = resolved.containingClass?.name,
            )
            else -> null
        }

        val fallbackText = element.text.removePrefix("${LibraryIndex.PREFIX}`").removeSuffix("`")

        return ReferenceFormatter.formatFoldedText(indexReference, targetInfo, fallbackText)
    }

    override fun isCollapsedByDefault(node: ASTNode): Boolean = true
}

data class TargetInfo(
    val classFqn: String,
    val outerClass: String? = null,
    val memberName: String? = null,
    val isField: Boolean = false,
    val isConstructor: Boolean = false,
    val returnTypeFqn: String? = null,
    val parameters: List<ParameterData> = emptyList()
)

data class ParameterData(
    val name: String,
    val typeFqn: String
)

object ReferenceFormatter {

    fun formatFoldedText(
        reference: IndexReference,
        target: TargetInfo?,
        fallbackText: String
    ): String {
        reference.customName?.value?.let { return it }

        if (target == null) return fallbackText

        val classShortName = target.classFqn.substringAfterLast('.')
        val className = when {
            reference.flags.longName || reference.flags.fullName -> target.classFqn
            reference.flags.shortName -> classShortName
            else -> target.outerClass?.let { "$it.$classShortName" } ?: classShortName
        }

        if (target.memberName == null) return className

        if (target.isField && !target.isConstructor) {
            return if (reference.flags.shortName) target.memberName else "$className.${target.memberName}"
        }

        if (reference.flags.methodReturnType && !target.isConstructor) {
            val retType = target.returnTypeFqn ?: "void"
            return if (reference.flags.longName || reference.flags.fullName) retType else retType.substringAfterLast('.')
        }

        val paramsString = target.parameters.joinToString(", ") { param ->
            val paramType = if(reference.flags.fullName) param.typeFqn else simpleTypeName(param.typeFqn)
            when {
                reference.flags.methodBoth -> "$paramType ${param.name}"
                reference.flags.methodOnlyName -> param.name
                reference.flags.methodOnlyType -> paramType
                else -> ""
            }
        }

        val finalParamsString = if (reference.flags.methodBoth || reference.flags.methodOnlyName || reference.flags.methodOnlyType) paramsString else ""

        return when {
            target.isConstructor -> "$className($finalParamsString)"
            reference.flags.shortName -> "${target.memberName}($finalParamsString)"
            else -> "$className.${target.memberName}($finalParamsString)"
        }
    }
}