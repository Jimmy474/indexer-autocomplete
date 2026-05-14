package com.jimmy474.indexerautocomplete.plugin

import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.*
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

        val regex = LibraryIndex.INDEX_REFERENCE_REGEX
        val match = regex.matchEntire(element.text) ?: return emptyArray()
        val indexReference = match.toIndexReference()
        val fqn = indexReference.fqn.value

        val refs: MutableList<PsiReference> = mutableListOf()
        if(indexReference.memberType != IndexReference.MemberType.NONE && indexReference.memberName != null){
            refs.add(LibrarySourceReference(element, indexReference.memberName.relativeRange, fqn, indexReference.memberType, indexReference.memberName.value))
        }
        if(indexReference.flags.isConstructor){
            refs.add(LibrarySourceReference(element, indexReference.className!!.relativeRange, fqn, IndexReference.MemberType.METHOD, "<init>"))
        }else{
            indexReference.className?.let { refs.add(LibrarySourceReference(element, it.relativeRange, fqn, IndexReference.MemberType.NONE, null)) }
        }

        return refs.toTypedArray()
    }
}


class LibrarySourceReference(element: PsiElement, range: TextRange, val fqn: String, val memberType: IndexReference.MemberType, val memberName: String?) : PsiReferenceBase<PsiElement>(element, range) {
    val isConstructor = memberName == "<init>"

    override fun resolve(): PsiElement? {
        val project = element.project
        val scope = GlobalSearchScope.allScope(project)
        val facade = JavaPsiFacade.getInstance(project)
        val psiClass = facade.findClass(fqn, scope) ?: return null

        if(isConstructor) return psiClass.constructors.firstOrNull()
        if(memberName == null) return psiClass
        return when (memberType) {
            IndexReference.MemberType.METHOD -> psiClass.findMethodsByName(memberName, true).firstOrNull()
            IndexReference.MemberType.FIELD -> psiClass.findFieldByName(memberName, true)
            IndexReference.MemberType.NONE -> null
        }
    }

    override fun isReferenceTo(element: PsiElement): Boolean {
        if (element is PsiClass) {
            return memberType == IndexReference.MemberType.NONE && element.qualifiedName == fqn
        }

        if (element is PsiMethod) {
            if(isConstructor) return element.containingClass?.qualifiedName == fqn
            return memberType == IndexReference.MemberType.METHOD && element.name == memberName && element.containingClass?.qualifiedName == fqn
        }

        if (element is PsiField) {
            return memberType == IndexReference.MemberType.FIELD && element.name == memberName && element.containingClass?.qualifiedName == fqn
        }

        return false
    }
}

