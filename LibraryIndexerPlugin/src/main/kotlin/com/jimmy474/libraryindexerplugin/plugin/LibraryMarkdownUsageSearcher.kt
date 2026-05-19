package com.jimmy474.libraryindexerplugin.plugin

import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.MethodReferencesSearch
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.util.Processor
import com.intellij.util.indexing.FileBasedIndex

interface LibraryMarkdownSearcherBase {
    fun search(targetElement: PsiElement, targetName: String, consumer: Processor<in PsiReference>) {
        val project = targetElement.project
        val projectScope = GlobalSearchScope.projectScope(project)

        val matchingFiles = FileBasedIndex.getInstance().getContainingFiles(INDEX_ID, targetName, projectScope)

        val psiManager = PsiManager.getInstance(project)

        for (vFile in matchingFiles) {
            val psiFile = psiManager.findFile(vFile) ?: continue

            val libraryElements = SyntaxTraverser.psiTraverser(psiFile).filter(LibraryIndexPsiElement::class.java)

            for (hostElement in libraryElements) {
                if (!hostElement.text.contains(targetName)) continue

                for (ref in hostElement.references) {
                    if (ref.isReferenceTo(targetElement)) {
                        if (!consumer.process(ref)) return
                    }
                }
            }
        }
    }
}

class LibraryMarkdownUsageSearcher : QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters>(true), LibraryMarkdownSearcherBase {
    override fun processQuery(queryParameters: ReferencesSearch.SearchParameters, consumer: Processor<in PsiReference>) {
        val targetElement = queryParameters.elementToSearch
        if (targetElement !is PsiClass && targetElement !is PsiMethod && targetElement !is PsiField) return

        val targetName = (targetElement as? PsiNamedElement)?.name ?: return
        search(targetElement, targetName, consumer)
    }
}

class LibraryMarkdownMethodUsageSearcher : QueryExecutorBase<PsiReference, MethodReferencesSearch.SearchParameters>(true), LibraryMarkdownSearcherBase {

    override fun processQuery(queryParameters: MethodReferencesSearch.SearchParameters, consumer: Processor<in PsiReference>) {
        val targetMethod = queryParameters.method
        val targetName = targetMethod.name
        search(targetMethod, targetName, consumer)
    }
}

