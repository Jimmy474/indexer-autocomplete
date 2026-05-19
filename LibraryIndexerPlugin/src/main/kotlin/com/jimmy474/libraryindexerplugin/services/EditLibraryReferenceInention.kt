package com.jimmy474.libraryindexerplugin.services

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jimmy474.libraryindexerplugin.plugin.LibraryIndex
import com.jimmy474.libraryindexerplugin.plugin.LibraryIndexPsiElement
import com.jimmy474.libraryindexerplugin.plugin.toIndexReference

class EditLibraryReferenceIntention : PsiElementBaseIntentionAction() {

    override fun getText(): String = "Edit library reference..."

    override fun getFamilyName(): String = "Library Index actions"

    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        val indexElement = PsiTreeUtil.getParentOfType(element, LibraryIndexPsiElement::class.java, false)
        return indexElement != null
    }

    override fun startInWriteAction(): Boolean = false

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        if (editor == null) return
        val indexElement = PsiTreeUtil.getParentOfType(element, LibraryIndexPsiElement::class.java, false) ?: return
        val currentText = indexElement.text
        val regex = LibraryIndex.INDEX_REFERENCE_REGEX
        val match = regex.matchEntire(currentText) ?: return
        val indexReference = match.toIndexReference()

        ApplicationManager.getApplication().invokeLater {

            val dialog = EditReferenceDialog(project, indexReference)
            if (dialog.showAndGet()) {
                val newReferenceText = dialog.getUpdatedReferenceText()
                WriteCommandAction.runWriteCommandAction(project, "Edit Library Reference", null, {
                    editor.document.replaceString(
                        indexElement.textRange.startOffset,
                        indexElement.textRange.endOffset,
                        newReferenceText
                    )
                })
            }
        }
    }
}

