package com.eonx.eeh.translationnav

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

/**
 * Go to Declaration from an EasyErrorHandler exception message key to its definition
 * in `translations/messages*.php`.
 *
 * Triggers on the first argument of:
 *  - a `new <BaseException subclass>(...)` / `parent::__construct(...)` call, for `exceptions.*` keys;
 *  - a `->setUserMessage(...)` call, for `user_messages.*` keys.
 */
class ExceptionTranslationKeyGotoHandler : GotoDeclarationHandler {

    override fun getGotoDeclarationTargets(
        sourceElement: PsiElement?,
        offset: Int,
        editor: Editor?,
    ): Array<PsiElement>? {
        val element = sourceElement ?: return null
        val literal = element.parent as? StringLiteralExpression ?: return null
        val prefix = MessageKeyContext.requiredPrefix(literal) ?: return null

        val key = literal.contents
        if (!key.startsWith(prefix)) return null

        val targets = TranslationKeyResolver.resolve(element.project, key)
        return targets.takeIf { it.isNotEmpty() }?.toTypedArray()
    }
}