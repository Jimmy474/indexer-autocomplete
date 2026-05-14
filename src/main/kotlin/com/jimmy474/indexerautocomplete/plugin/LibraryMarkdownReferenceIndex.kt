package com.jimmy474.indexerautocomplete.plugin

import com.intellij.util.indexing.*
import com.intellij.util.io.EnumeratorStringDescriptor
import com.intellij.util.io.KeyDescriptor
import org.intellij.plugins.markdown.lang.MarkdownFileType

val INDEX_ID = ID.create<String, Void>("LibraryMarkdownReferenceIndex")

class LibraryMarkdownReferenceIndex : ScalarIndexExtension<String>() {

    override fun getName(): ID<String, Void> = INDEX_ID

    override fun getVersion(): Int = 1

    override fun dependsOnFileContent(): Boolean = true

    override fun getKeyDescriptor(): KeyDescriptor<String> = EnumeratorStringDescriptor.INSTANCE

    override fun getInputFilter(): FileBasedIndex.InputFilter = DefaultFileTypeSpecificInputFilter(MarkdownFileType.INSTANCE)
    override fun getIndexer(): DataIndexer<String, Void, FileContent> {
        return DataIndexer { inputData ->
            val text = inputData.contentAsText
            val map = mutableMapOf<String, Void?>()

            val regex = Regex(LibraryIndex.INDEX_REFERENCE_PATTERN)

            for (match in regex.findAll(text)) {
                val indexReference = match.toIndexReference()

                indexReference.className?.value?.let { map[it] = null }
                indexReference.memberName?.value?.let { map[it] = null }
            }

            map
        }
    }
}
