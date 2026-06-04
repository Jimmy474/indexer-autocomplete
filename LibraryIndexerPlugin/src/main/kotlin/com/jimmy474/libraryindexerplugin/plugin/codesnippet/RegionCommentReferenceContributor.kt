package com.jimmy474.libraryindexerplugin.plugin.codesnippet

import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.util.ProcessingContext

class RegionCommentReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(PsiComment::class.java),
            RegionCommentReferenceProvider()
        )
    }
}

class RegionCommentReferenceProvider : PsiReferenceProvider() {
    private val regionRegex = Regex("//\\s*#(end)?region\\s+(\\w+)")

    override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
        val comment = element as? PsiComment ?: return emptyArray()
        val match = regionRegex.find(comment.text) ?: return emptyArray()

        val nameGroup = match.groups[2] ?: return emptyArray()

        val range = TextRange(nameGroup.range.first, nameGroup.range.last + 1)
        return arrayOf(RegionCommentReference(comment, range, nameGroup.value))
    }
}

class RegionCommentReference(
    element: PsiComment,
    range: TextRange,
    private val regionName: String
) : PsiReferenceBase<PsiComment>(element, range) {

    override fun resolve(): PsiElement {
        return RegionLightElement(element.project, element.containingFile, regionName, element)
    }

    override fun handleElementRename(newElementName: String): PsiElement {
        val newText = element.text.replaceRange(rangeInElement.startOffset, rangeInElement.endOffset, newElementName)
        val factory = JavaPsiFacade.getElementFactory(element.project)
        val newComment = factory.createCommentFromText(newText, null)
        return element.replace(newComment)
    }
}
