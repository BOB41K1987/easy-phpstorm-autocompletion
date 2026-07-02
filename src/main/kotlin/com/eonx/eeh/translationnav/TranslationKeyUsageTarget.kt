package com.eonx.eeh.translationnav

import com.intellij.navigation.NavigationItem
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.FakePsiElement
import javax.swing.Icon

/**
 * Wraps a translation-key usage so the reverse Go to Declaration popup (multiple usage sites of
 * the same key) shows a distinguishable "File.php:42" per row instead of repeating the identical
 * quoted key text every occurrence resolves to by default.
 *
 * Explicitly implements [NavigationItem]: `FakePsiElement` satisfies its methods structurally
 * (`getName`/`getPresentation`) but doesn't declare the interface itself, and platform utilities
 * that build hover/quick-doc/popup presentations gate on `element is NavigationItem` before using
 * `getPresentation()` — without it they fall back to a generic dump of this synthetic element.
 */
class TranslationKeyUsageTarget(private val target: PsiElement) : FakePsiElement(), NavigationItem {

    override fun getParent(): PsiElement = target.parent

    override fun getName(): String = presentableText

    override fun getContainingFile(): PsiFile = target.containingFile

    override fun getProject(): Project = target.project

    override fun isValid(): Boolean = target.isValid

    override fun getTextOffset(): Int = target.textOffset

    override fun getTextRange(): TextRange = target.textRange

    override fun getText(): String = target.text

    // Quick Documentation / Ctrl+hover resolve through this to decide what to render, so route
    // them to the real usage instead of falling back to a debug dump of this synthetic element.
    override fun getNavigationElement(): PsiElement = target

    override fun getOriginalElement(): PsiElement = target

    override fun getPresentableText(): String {
        val file = target.containingFile
        val document = PsiDocumentManager.getInstance(target.project).getDocument(file)
        val line = document?.getLineNumber(target.textOffset)?.plus(1)
        return if (line != null) "${file.name}:$line" else file.name
    }

    override fun getLocationString(): String? {
        val dir = target.containingFile.virtualFile?.parent ?: return null
        val root = ProjectRootManager.getInstance(target.project).contentRoots
            .firstOrNull { VfsUtilCore.isAncestor(it, dir, false) }
            ?: return dir.path
        return VfsUtilCore.getRelativePath(dir, root)?.ifEmpty { null }
    }

    override fun getIcon(open: Boolean): Icon = PluginIcons.EONX

    override fun equals(other: Any?): Boolean = other is TranslationKeyUsageTarget && other.target == target

    override fun hashCode(): Int = target.hashCode()
}