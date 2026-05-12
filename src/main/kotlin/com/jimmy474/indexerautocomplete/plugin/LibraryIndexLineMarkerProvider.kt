package com.jimmy474.indexerautocomplete.plugin

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder
import com.intellij.icons.AllIcons
import com.intellij.psi.PsiElement

class LibraryIndexLineMarkerProvider : RelatedItemLineMarkerProvider() {
    override fun collectNavigationMarkers(element: PsiElement, result: MutableCollection<in RelatedItemLineMarkerInfo<*>>) {
        if (element !is LibraryIndexPsiElement) return
        val targetElements = element.references.mapNotNull { it.resolve() }
        if (targetElements.isEmpty()) return

        val builder = NavigationGutterIconBuilder.create(AllIcons.Gutter.ExtAnnotation)
            .setTargets(targetElements)
            .setTooltipText("Navigate to library source")

        val anchor = element.firstChild ?: element
        result.add(builder.createLineMarkerInfo(anchor))
    }
}

