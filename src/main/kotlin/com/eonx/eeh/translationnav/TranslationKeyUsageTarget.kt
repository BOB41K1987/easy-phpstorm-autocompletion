package com.eonx.eeh.translationnav

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.FakePsiElement

/**
 * Wraps a translation-key usage so the reverse Go to Declaration popup (multiple usage sites of
 * the same key) shows a distinguishable "File.php:42" per row instead of repeating the identical
 * quoted key text every occurrence resolves to by default.
 */
class TranslationKeyUsageTarget(private val target: PsiElement) : FakePsiElement() {

    override fun getParent(): PsiElement = target.parent

    override fun getContainingFile(): PsiFile = target.containingFile

    override fun getProject(): Project = target.project

    override fun isValid(): Boolean = target.isValid

    override fun getTextOffset(): Int = target.textOffset

    override fun getPresentableText(): String {
        val file = target.containingFile
        val document = PsiDocumentManager.getInstance(target.project).getDocument(file)
        val line = document?.getLineNumber(target.textOffset)?.plus(1)
        return if (line != null) "${file.name}:$line" else file.name
    }

    override fun getLocationString(): String? = target.containingFile.virtualFile?.parent?.path

    override fun equals(other: Any?): Boolean = other is TranslationKeyUsageTarget && other.target == target

    override fun hashCode(): Int = target.hashCode()
}