package com.jimmy474.libraryindexerplugin.plugin

import com.intellij.psi.PsiElement
import com.intellij.usages.impl.rules.UsageType
import com.intellij.usages.impl.rules.UsageTypeProvider

val CODE_SNIPPET_USAGE_TYPE = UsageType { "Code Snippet" }

class CodeSnippetUsageTypeProvider: UsageTypeProvider {
    override fun getUsageType(element: PsiElement): UsageType? {
        return if(element !is CodeSnippetPsiElement) null else CODE_SNIPPET_USAGE_TYPE
    }
}