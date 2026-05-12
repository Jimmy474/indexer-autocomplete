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
        val indexReference = match.toIndexReference()
        val fqn = indexReference.packages.joinToString(".")

        val refs: MutableList<PsiReference> = mutableListOf()
        if(indexReference.memberType != IndexReference.MemberType.NONE && indexReference.memberName != null){
            refs.add(LibrarySourceReference(element, indexReference.memberNameRange!!, fqn, indexReference.memberType, indexReference.memberName))
        }
        refs.add(LibrarySourceReference(element, indexReference.classNameRange!!, fqn, IndexReference.MemberType.NONE, null))

        return refs.toTypedArray()
    }
}


class LibrarySourceReference(element: PsiElement, range: TextRange, val fqn: String, val memberType: IndexReference.MemberType, val memberName: String?) : PsiReferenceBase<PsiElement>(element, range) {
    override fun resolve(): PsiElement? {
        val project = element.project
        val scope = GlobalSearchScope.allScope(project)
        val facade = JavaPsiFacade.getInstance(project)
        val psiClass = facade.findClass(fqn, scope) ?: return null

        if(memberName == null) return psiClass
        return when (memberType) {
            IndexReference.MemberType.METHOD -> psiClass.findMethodsByName(memberName, true).firstOrNull()
            IndexReference.MemberType.FIELD -> psiClass.findFieldByName(memberName, true)
            IndexReference.MemberType.NONE -> null
        }
    }
}
