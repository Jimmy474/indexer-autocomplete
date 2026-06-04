package com.jimmy474.libraryindexerplugin.plugin.codesnippet

import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.util.PsiTreeUtil

class CodeSnippetReference(
    element: CodeSnippetPsiElement,
    range: TextRange,
    private val path: String,
    private val region: String? = null
) : PsiReferenceBase<PsiElement>(element, range){
    override fun resolve(): PsiElement? {
        val project = element.project
        val virtualFile = project.guessProjectDir()?.findFileByRelativePath(path) ?: return null
        val psiFile = PsiManager.getInstance(project).findFile(virtualFile) ?: return null
        return region?.let{
            val fileText = psiFile.text
            val targetText = "// #region $region"

            val offset = fileText.indexOf(targetText)
            if (offset == -1) return@let null

            val elementAtOffset = psiFile.findElementAt(offset)
            return@let elementAtOffset
        } ?: psiFile
    }

    override fun bindToElement(newElement: PsiElement): PsiElement {
        if (newElement is PsiFile) {
            val projectDir = newElement.project.guessProjectDir() ?: return super.bindToElement(newElement)
            val newRelativePath = VfsUtilCore.getRelativePath(newElement.virtualFile, projectDir) ?: return super.bindToElement(newElement)
            return handleElementRename(newRelativePath)
        }
        return super.bindToElement(newElement)
    }

    override fun handleElementRename(newElementName: String): PsiElement {
        val oldText = element.text
        val newText = oldText.replaceRange(rangeInElement.startOffset, rangeInElement.endOffset, newElementName)

        val dummyFile = PsiFileFactory.getInstance(element.project).createFileFromText("dummy.md", element.language, newText)

        val newElement = PsiTreeUtil.findChildOfType(dummyFile, CodeSnippetPsiElement::class.java)
        return if (newElement != null) {
            element.replace(newElement)
        } else {
            element
        }
    }

    override fun isReferenceTo(element: PsiElement): Boolean {
        if (element is PsiFile) {
            val targetFile = element.virtualFile ?: return false
            val projectDir = element.project.guessProjectDir() ?: return false

            val targetRelativePath = VfsUtilCore.getRelativePath(targetFile, projectDir)
            return targetRelativePath == this.path
        }

        if (element is RegionLightElement && this.region != null) {
            if (element.regionName != this.region) return false

            val targetFile = element.file.virtualFile ?: return false
            val projectDir = element.project.guessProjectDir() ?: return false

            val targetRelativePath = VfsUtilCore.getRelativePath(targetFile, projectDir)
            return targetRelativePath == this.path
        }

        return super.isReferenceTo(element)
    }
}
