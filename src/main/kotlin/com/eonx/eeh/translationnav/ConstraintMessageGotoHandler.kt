package com.eonx.eeh.translationnav

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

/**
 * Go to Declaration from a Symfony validator constraint attribute `message` key to its
 * definition in `translations/validators*.php`, e.g. `#[Assert\Expression(message: '<caret>')]`.
 */
class ConstraintMessageGotoHandler : GotoDeclarationHandler {

    override fun getGotoDeclarationTargets(
        sourceElement: PsiElement?,
        offset: Int,
        editor: Editor?,
    ): Array<PsiElement>? {
        val element = sourceElement ?: return null
        val literal = element.parent as? StringLiteralExpression ?: return null
        if (!ConstraintMessageContext.isConstraintMessageArgument(literal)) return null

        val targets = TranslationKeyResolver.resolve(element.project, literal.contents, ConstraintMessageContext.DOMAIN)
        return targets.takeIf { it.isNotEmpty() }?.toTypedArray()
    }
}