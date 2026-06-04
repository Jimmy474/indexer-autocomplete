package com.jimmy474.libraryindexerplugin.plugin.codesnippet

import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.impl.light.LightElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.SearchScope

class RegionLightElement(
    project: Project,
    val file: PsiFile,
    val regionName: String,
    private val originalComment: PsiComment
) : LightElement(PsiManager.getInstance(project), JavaLanguage.INSTANCE), PsiNamedElement {

    override fun getName(): String = regionName
    override fun setName(newName: String): PsiElement = this
    override fun getText(): String = originalComment.text
    override fun isWritable(): Boolean = true
    override fun getContainingFile(): PsiFile = file
    override fun getUseScope(): SearchScope = GlobalSearchScope.allScope(project)
    override fun getNavigationElement(): PsiElement = originalComment

    override fun isEquivalentTo(another: PsiElement?): Boolean {
        return another is RegionLightElement &&
                another.regionName == this.regionName &&
                another.file == this.file
    }

    override fun toString(): String = "Region($regionName)"
}
