package com.eonx.eeh.translationnav

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.NewExpression
import com.jetbrains.php.lang.psi.elements.ParameterList
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
        val key = literal.contents
        if (!isMessageKeyArgument(literal, key)) return null

        val targets = TranslationKeyResolver.resolve(element.project, key)
        return targets.takeIf { it.isNotEmpty() }?.toTypedArray()
    }

    private fun isMessageKeyArgument(literal: StringLiteralExpression, key: String): Boolean {
        val parameterList = literal.parent as? ParameterList ?: return false
        if (parameterList.parameters.indexOf(literal) != 0) return false

        return when (val call = parameterList.parent) {
            is NewExpression -> key.startsWith("exceptions.")
            is MethodReference -> when (call.name) {
                "setUserMessage" -> key.startsWith("user_messages.")
                "__construct" -> key.startsWith("exceptions.")
                else -> false
            }
            else -> false
        }
    }
}
