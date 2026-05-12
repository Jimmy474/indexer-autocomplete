package com.jimmy474.indexerautocomplete.plugin

import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.ProcessingContext
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownFile

class LibraryReferenceContributor: PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar){
        registrar.registerReferenceProvider(PlatformPatterns.psiElement(LibraryIndexPsiElement::class.java),LibraryReferenceProvider())
    }
}

class LibraryReferenceProvider: PsiReferenceProvider(){
    override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
        if (element.containingFile !is MarkdownFile) return emptyArray()
        if (element !is LibraryIndexPsiElement) return emptyArray()

        val regex = LibraryIndexCompletionProvider.INDEX_REFERENCE_FULL_REGEX
        val match = regex.find(element.text) ?: return emptyArray()
        val fqn = match.groupValues[1]
        val memberType = match.groupValues[5].ifBlank { null }
        val memberName = match.groupValues[6].ifBlank { null }

        val refs: MutableList<PsiReference> = mutableListOf()
        if(memberType != null && memberName != null){
            refs.add(LibrarySourceReference(element, TextRange(match.groups[6]!!.range.first, match.groups[6]!!.range.last+1), fqn, memberType, memberName))
        }
        refs.add(LibrarySourceReference(element, TextRange(match.groups[1]!!.range.first, match.groups[1]!!.range.last+1), fqn, null, null))

        return refs.toTypedArray()
    }
}


class LibrarySourceReference(element: PsiElement, range: TextRange, val fqn: String, val memberType: String?, val memberName: String?) : PsiReferenceBase<PsiElement>(element, range) {
    override fun resolve(): PsiElement? {
        val project = element.project
        val scope = GlobalSearchScope.allScope(project)
        val facade = JavaPsiFacade.getInstance(project)
        val psiClass = facade.findClass(fqn, scope) ?: return null

        if(memberName == null) return psiClass
        return when (memberType) {
            LibraryIndexCompletionProvider.METHODS_PREFIX -> psiClass.findMethodsByName(memberName, true).firstOrNull()
            LibraryIndexCompletionProvider.FIELDS_PREFIX -> psiClass.findFieldByName(memberName, true)
            else -> null
        }
    }
}
