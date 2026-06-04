package com.jimmy474.libraryindexerplugin.plugin.codesnippet

import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiReference
import com.intellij.psi.impl.cache.CacheManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.UsageSearchContext
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.Processor
import kotlin.experimental.or

class CodeSnippetFileReferenceSearcher : QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters>(true) {

    override fun processQuery(
        queryParameters: ReferencesSearch.SearchParameters,
        consumer: Processor<in PsiReference>
    ) {
        val target = queryParameters.elementToSearch

        if (target !is PsiFile) return

        val project = target.project
        val fileName = target.virtualFile.nameWithoutExtension

        val filesWithWord = CacheManager.getInstance(project).getFilesWithWord(
            fileName,
            UsageSearchContext.IN_STRINGS or UsageSearchContext.IN_PLAIN_TEXT,
            GlobalSearchScope.allScope(project),
            true
        )

        for (file in filesWithWord) {
            val customElements = PsiTreeUtil.findChildrenOfType(file, CodeSnippetPsiElement::class.java)

            for (element in customElements) {
                for (ref in element.references) {
                    if (ref is CodeSnippetReference && ref.isReferenceTo(target)) {
                        consumer.process(ref)
                    }
                }
            }
        }
    }
}
