package com.jimmy474.indexerautocomplete.plugin

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VirtualFile

fun Project.getIndexFileFromPath(path: String): VirtualFile?{
    val root = this.guessProjectDir()?.findChild("library-index-dependency") ?: return null
    if(!root.isDirectory) return null
    return root.findChild("${path.replace('.', '/')}.json")
}