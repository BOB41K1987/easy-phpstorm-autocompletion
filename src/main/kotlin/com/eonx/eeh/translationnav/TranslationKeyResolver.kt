package com.eonx.eeh.translationnav

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.PhpFile
import com.jetbrains.php.lang.psi.elements.ArrayCreationExpression
import com.jetbrains.php.lang.psi.elements.ArrayHashElement
import com.jetbrains.php.lang.psi.elements.PhpReturn
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

/**
 * Resolves a dotted translation key (e.g. "exceptions.merchant_link.creation.foo")
 * to the matching key PSI element inside a `translations/messages*.php` file, so the
 * IDE can navigate to it.
 */
object TranslationKeyResolver {

    private val SKIP_DIRS = setOf("vendor", "node_modules", ".git", "var", "cache")

    fun resolve(project: Project, key: String, domain: String = "messages"): List<PsiElement> {
        if (key.isBlank()) return emptyList()

        val segments = key.split(".")
        val psiManager = PsiManager.getInstance(project)
        val targets = mutableListOf<PsiElement>()

        for (file in findDomainFiles(project, domain)) {
            val phpFile = psiManager.findFile(file) as? PhpFile ?: continue
            val rootArray = findReturnedArray(phpFile) ?: continue
            findLeaf(rootArray, segments)?.let { targets.add(it) }
        }

        return targets
    }

    /** The translation value text for [key] (e.g. an ICU message), or null if not found. */
    fun messageText(project: Project, key: String, domain: String = "messages"): String? {
        if (key.isBlank()) return null

        val segments = key.split(".")
        val psiManager = PsiManager.getInstance(project)

        for (file in findDomainFiles(project, domain)) {
            val phpFile = psiManager.findFile(file) as? PhpFile ?: continue
            val rootArray = findReturnedArray(phpFile) ?: continue
            val leafKey = findLeaf(rootArray, segments) ?: continue
            // The key element is wrapped in an intermediate PSI node, so walk up to the hash element.
            val hash = PsiTreeUtil.getParentOfType(leafKey, ArrayHashElement::class.java) ?: continue
            (hash.value as? StringLiteralExpression)?.let { return it.contents }
        }

        return null
    }

    /** Every dotted key defined across the project's `<domain>*.php` translation files. */
    fun collectKeys(project: Project, domain: String = "messages"): Set<String> {
        val keys = sortedSetOf<String>()
        val psiManager = PsiManager.getInstance(project)

        for (file in findDomainFiles(project, domain)) {
            val phpFile = psiManager.findFile(file) as? PhpFile ?: continue
            val rootArray = findReturnedArray(phpFile) ?: continue
            collectKeys(rootArray, "", keys)
        }

        return keys
    }

    private fun collectKeys(array: ArrayCreationExpression, prefix: String, out: MutableSet<String>) {
        for (hash in array.hashElements) {
            val keyText = keyText(hash) ?: continue
            val full = if (prefix.isEmpty()) keyText else "$prefix.$keyText"
            when (val value = hash.value) {
                is ArrayCreationExpression -> collectKeys(value, full, out)
                else -> out.add(full)
            }
        }
    }

    private fun findDomainFiles(project: Project, domain: String): List<VirtualFile> {
        val result = mutableListOf<VirtualFile>()
        val fileName = Regex("^" + Regex.escape(domain) + ".*\\.php$")

        for (root in ProjectRootManager.getInstance(project).contentRoots) {
            VfsUtilCore.visitChildrenRecursively(root, object : VirtualFileVisitor<Any>() {
                override fun visitFileEx(file: VirtualFile): Result {
                    if (file.isDirectory) {
                        return if (file.name in SKIP_DIRS) SKIP_CHILDREN else CONTINUE
                    }
                    if (file.parent?.name == "translations" && fileName.matches(file.name)) {
                        result.add(file)
                    }
                    return CONTINUE
                }
            })
        }

        return result
    }

    private fun findReturnedArray(phpFile: PhpFile): ArrayCreationExpression? {
        val phpReturn = PsiTreeUtil.findChildrenOfType(phpFile, PhpReturn::class.java).firstOrNull()
            ?: return null

        return phpReturn.argument as? ArrayCreationExpression
    }

    /**
     * Matches the dotted key against a possibly nested array, supporting both nested
     * arrays and flat dotted keys at any level.
     */
    private fun findLeaf(array: ArrayCreationExpression, path: List<String>): PsiElement? {
        val full = path.joinToString(".")
        for (hash in array.hashElements) {
            if (keyText(hash) == full) {
                return hash.key
            }
        }

        for (splitAt in 1 until path.size) {
            val prefix = path.take(splitAt).joinToString(".")
            val hash = array.hashElements.firstOrNull { keyText(it) == prefix } ?: continue
            val nested = hash.value as? ArrayCreationExpression ?: continue
            findLeaf(nested, path.drop(splitAt))?.let { return it }
        }

        return null
    }

    private fun keyText(hash: ArrayHashElement): String? =
        (hash.key as? StringLiteralExpression)?.contents

    /**
     * The (domain, dotted key) pair for the leaf key element under [element], when it sits on a
     * translation array key inside a recognized `translations/<domain>*.php` file. Used to drive
     * the reverse (translation &rarr; usages) navigation.
     */
    fun keyAt(element: PsiElement): Pair<String, String>? {
        val literal = element.parent as? StringLiteralExpression ?: return null
        val hash = literal.parent as? ArrayHashElement ?: return null
        if (hash.key !== literal || hash.value is ArrayCreationExpression) return null

        val file = literal.containingFile?.virtualFile ?: return null
        if (file.parent?.name != "translations") return null
        val domain = Regex("^([A-Za-z0-9_]+)").find(file.name)?.groupValues?.get(1) ?: return null

        val segments = mutableListOf(keyText(hash) ?: return null)
        var array = PsiTreeUtil.getParentOfType(hash, ArrayCreationExpression::class.java)
        while (array != null) {
            val parentHash = array.parent as? ArrayHashElement ?: break
            segments.add(0, keyText(parentHash) ?: return null)
            array = PsiTreeUtil.getParentOfType(parentHash, ArrayCreationExpression::class.java)
        }

        return domain to segments.joinToString(".")
    }
}
