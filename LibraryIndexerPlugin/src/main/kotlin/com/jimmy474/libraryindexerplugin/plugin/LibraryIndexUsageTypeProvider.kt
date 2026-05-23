package com.jimmy474.libraryindexerplugin.plugin

import com.intellij.psi.PsiElement
import com.intellij.usages.impl.rules.UsageType
import com.intellij.usages.impl.rules.UsageTypeProvider

val LIBRARY_USAGE_TYPE = UsageType { "Library Index" }
class LibraryIndexUsageTypeProvider: UsageTypeProvider {
    override fun getUsageType(element: PsiElement): UsageType? {
        return if(element !is LibraryIndexPsiElement) null else LIBRARY_USAGE_TYPE
    }
}

