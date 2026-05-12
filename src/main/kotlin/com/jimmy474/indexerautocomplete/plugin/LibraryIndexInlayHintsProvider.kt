package com.jimmy474.indexerautocomplete.plugin

import com.intellij.codeInsight.hints.ChangeListener
import com.intellij.codeInsight.hints.FactoryInlayHintsCollector
import com.intellij.codeInsight.hints.ImmediateConfigurable
import com.intellij.codeInsight.hints.InlayHintsCollector
import com.intellij.codeInsight.hints.InlayHintsProvider
import com.intellij.codeInsight.hints.InlayHintsSink
import com.intellij.codeInsight.hints.NoSettings
import com.intellij.codeInsight.hints.SettingsKey
import com.intellij.codeInsight.hints.presentation.InlayPresentation
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethod
import javax.swing.JPanel

@Suppress("UnstableApiUsage")
class LibraryIndexInlayHintsProvider : InlayHintsProvider<NoSettings> {

    override val key: SettingsKey<NoSettings> = SettingsKey("library.index.type.hints")

    override val name: String = "Library Index Return Types"

    override val previewText: String = "@`net.fabricmc.fabric.api.event.Event#register`"

    override fun createSettings(): NoSettings = NoSettings()

    override fun getCollectorFor(
        file: PsiFile,
        editor: Editor,
        settings: NoSettings,
        sink: InlayHintsSink
    ): InlayHintsCollector {
        return LibraryIndexInlayHintsCollector(editor)
    }

    override fun createConfigurable(settings: NoSettings): ImmediateConfigurable {
        return object : ImmediateConfigurable {
            override fun createComponent(listener: ChangeListener) = JPanel()
        }
    }
}

@Suppress("UnstableApiUsage")
class LibraryIndexInlayHintsCollector(editor: Editor) : FactoryInlayHintsCollector(editor) {

    override fun collect(element: PsiElement, editor: Editor, sink: InlayHintsSink): Boolean {
        if (element !is LibraryIndexPsiElement) return true

        val resolvedTarget = element.references.firstOrNull()?.resolve() ?: return true

        val hintText = when (resolvedTarget) {
            is PsiMethod -> ": ${resolvedTarget.returnType?.presentableText ?: "void"}"
            is PsiField -> ": ${resolvedTarget.type.presentableText}"
            is PsiClass -> " (Class)"
            else -> return true
        }

        val hintPresentation: InlayPresentation = factory.roundWithBackground(factory.smallText(hintText))

        val offset = element.textRange.endOffset

        sink.addInlineElement(
            offset = offset,
            relatesToPrecedingText = true,
            presentation = hintPresentation,
            placeAtTheEndOfLine = false
        )

        return true
    }
}

