package com.jimmy474.libraryindexerplugin.plugin

import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.ProcessingContext
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownFile

class LibraryReferenceContributor: PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar){
        registrar.registerReferenceProvider(PlatformPatterns.psiElement(LibraryIndexPsiElement::class.java),LibraryReferenceProvider())
        registrar.registerReferenceProvider(PlatformPatterns.psiElement(CodeSnippetPsiElement::class.java),CodeSnippetReferenceProvider())
    }
}

class LibraryReferenceProvider: PsiReferenceProvider(){
    override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
        if (element.containingFile !is MarkdownFile) return emptyArray()
        if (element !is LibraryIndexPsiElement) return emptyArray()

        val regex = LibraryIndex.INDEX_REFERENCE_REGEX
        val match = regex.matchEntire(element.text) ?: return emptyArray()
        val indexReference = match.toIndexReference()
        val fqn = indexReference.fqn.value

        val refs: MutableList<PsiReference> = mutableListOf()
        if(indexReference.memberType != IndexReference.MemberType.NONE && indexReference.memberName != null){
            refs.add(LibrarySourceReference(element, indexReference.memberName.relativeRange, fqn, indexReference.memberType, indexReference.memberName.value, indexReference.params?.map { it.value }))
        }
        if(indexReference.flags.isConstructor){
            refs.add(LibrarySourceReference(element, indexReference.className!!.relativeRange, fqn, IndexReference.MemberType.METHOD, "<init>", indexReference.params?.map { it.value }))
        }else{
            indexReference.className?.let { refs.add(LibrarySourceReference(element, it.relativeRange, fqn, IndexReference.MemberType.NONE, null)) }
        }

        return refs.toTypedArray()
    }
}

class LibrarySourceReference(
    element: LibraryIndexPsiElement,
    range: TextRange,
    val fqn: String,
    val memberType: IndexReference.MemberType,
    val memberName: String?,
    private val parameterTypes: List<String>? = null,
) : PsiReferenceBase<PsiElement>(element, range) {
    val isConstructor = memberName == "<init>"
    var singleOverload = true

    override fun resolve(): PsiElement? {
        val project = element.project
        val scope = GlobalSearchScope.allScope(project)
        val facade = JavaPsiFacade.getInstance(project)
        val psiClass = facade.findClass(fqn, scope) ?: return null

        if(isConstructor) {
            singleOverload = psiClass.constructors.size == 1
            return psiClass.constructors.firstOrNull { psiClass.constructors.size == 1 || parametersMatch(it, parameterTypes) }
        }
        if(memberName == null) return psiClass
        return when (memberType) {
            IndexReference.MemberType.METHOD -> {
                val methods = psiClass.findMethodsByName(memberName, false)
                singleOverload = methods.size == 1
                methods.firstOrNull { methods.size == 1 || parametersMatch(it, parameterTypes) }
            }
            IndexReference.MemberType.FIELD -> psiClass.findFieldByName(memberName, true)
            IndexReference.MemberType.NONE -> null
        }
    }

    override fun isReferenceTo(element: PsiElement): Boolean {
        if (element is PsiClass) {
            return memberType == IndexReference.MemberType.NONE && element.qualifiedName == fqn
        }

        if (element is PsiMethod) {
            return memberType == IndexReference.MemberType.METHOD && element.name == memberName && element.containingClass?.qualifiedName == fqn && (singleOverload || parametersMatch(element, parameterTypes))
        }

        if (element is PsiField) {
            return memberType == IndexReference.MemberType.FIELD && element.name == memberName && element.containingClass?.qualifiedName == fqn
        }

        return false
    }

    private fun parametersMatch(method: PsiMethod, expectedTypes: List<String>?): Boolean {
        if (expectedTypes == null) return true
        val actualTypes = method.parameterList.parameters.map { it.type }
        if (actualTypes.size != expectedTypes.size) return false
        return actualTypes.zip(expectedTypes).all { (actual, expected) ->
            actual.canonicalText == expected || actual.presentableText == expected || actual.canonicalText.endsWith(expected)
        }
    }
}

class CodeSnippetReferenceProvider: PsiReferenceProvider(){
    override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
        if (element.containingFile !is MarkdownFile) return emptyArray()
        if (element !is CodeSnippetPsiElement) return emptyArray()

        val match = LibraryIndex.CODE_SNIPPET_REGEX.matchEntire(element.text) ?: return emptyArray()
        val codeSnippet = match.toCodeSnippetReference()
        return buildList {
            val path = codeSnippet.path.value.removePrefix("${LibraryIndex.ROOT_SYMBOL}/")
            codeSnippet.fileName?.let { add(CodeSnippetReference(element, it.relativeRange, path)) }
            codeSnippet.region?.let { add(CodeSnippetReference(element, it.relativeRange, path, it.value)) }
        }.toTypedArray()
    }
}

class CodeSnippetReference(element: CodeSnippetPsiElement, range: TextRange, private val path: String, private val region: String? = null) : PsiReferenceBase<PsiElement>(element, range){
    override fun resolve(): PsiElement? {
        val project = element.project
        val virtualFile = project.guessProjectDir()?.findFileByRelativePath(path) ?: return null
        val psiFile = PsiManager.getInstance(project).findFile(virtualFile) ?: return null
        return region?.let{
            if(virtualFile.extension != "java") return@let null
            val fileText = psiFile.text
            val targetText = "// #region $region"

            val offset = fileText.indexOf(targetText)
            if (offset == -1) return@let null

            val elementAtOffset = psiFile.findElementAt(offset)
            return@let elementAtOffset
        } ?: psiFile
    }

}