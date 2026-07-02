package com.eonx.eeh.translationnav

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.psi.search.UsageSearchContext
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

/**
 * Go to Declaration from a translation key's definition in `translations/messages*.php` /
 * `translations/validators*.php` to every place in the project that references it — the reverse
 * of [ExceptionTranslationKeyGotoHandler] / [ConstraintMessageGotoHandler].
 */
class TranslationKeyUsagesGotoHandler : GotoDeclarationHandler {

    override fun getGotoDeclarationTargets(
        sourceElement: PsiElement?,
        offset: Int,
        editor: Editor?,
    ): Array<PsiElement>? {
        val element = sourceElement ?: return null
        val (domain, key) = TranslationKeyResolver.keyAt(element) ?: return null
        val word = key.substringAfterLast(".")
        if (word.isBlank()) return null

        val targets = mutableListOf<StringLiteralExpression>()
        val scope = GlobalSearchScope.projectScope(element.project)

        PsiSearchHelper.getInstance(element.project).processElementsWithWord(
            { found, _ ->
                val literal = PsiTreeUtil.getParentOfType(found, StringLiteralExpression::class.java, false)
                if (literal != null && literal.contents == key && matches(literal, domain)) {
                    targets.add(literal)
                }
                true
            },
            scope,
            word,
            UsageSearchContext.IN_STRINGS,
            true,
        )

        return targets
            .distinctBy { it.containingFile to it.textOffset }
            .sortedWith(compareBy({ it.containingFile.name }, { it.textOffset }))
            .map { TranslationKeyUsageTarget(it) as PsiElement }
            .takeIf { it.isNotEmpty() }
            ?.toTypedArray()
    }

    private fun matches(literal: StringLiteralExpression, domain: String): Boolean = when (domain) {
        "messages" -> MessageKeyContext.requiredPrefix(literal)?.let { literal.contents.startsWith(it) } == true
        ConstraintMessageContext.DOMAIN -> ConstraintMessageContext.isConstraintMessageArgument(literal)
        else -> false
    }
}