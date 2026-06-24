package com.eonx.eeh.translationnav

import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.NewExpression
import com.jetbrains.php.lang.psi.elements.ParameterList
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

/**
 * Detects whether a string literal is the message-key argument of an EasyErrorHandler
 * exception API, and which translation-key prefix is expected there.
 */
object MessageKeyContext {

    private const val EXCEPTIONS_PREFIX = "exceptions."
    private const val USER_MESSAGES_PREFIX = "user_messages."

    /**
     * @return the expected key prefix ("exceptions." / "user_messages.") when [literal] is the
     *   first argument of a recognized call, or null otherwise. Purely syntactic — does not look
     *   at the literal's current text, so it also works for incomplete keys during completion.
     */
    fun requiredPrefix(literal: StringLiteralExpression): String? {
        val parameterList = literal.parent as? ParameterList ?: return null
        if (parameterList.parameters.indexOf(literal) != 0) return null

        return when (val call = parameterList.parent) {
            is NewExpression -> EXCEPTIONS_PREFIX
            is MethodReference -> when (call.name) {
                "setUserMessage" -> USER_MESSAGES_PREFIX
                "__construct" -> EXCEPTIONS_PREFIX
                else -> null
            }
            else -> null
        }
    }
}