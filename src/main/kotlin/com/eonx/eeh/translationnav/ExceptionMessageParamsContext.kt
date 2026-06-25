package com.eonx.eeh.translationnav

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.elements.ArrayCreationExpression
import com.jetbrains.php.lang.psi.elements.Function
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.NewExpression
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

/**
 * Relates EasyErrorHandler `setMessageParams` / `setUserMessageParams` calls to the placeholders
 * declared in the corresponding message:
 *  - `setMessageParams`     ↔ the exception message (constructor / `parent::__construct` first arg);
 *  - `setUserMessageParams` ↔ the `setUserMessage(...)` argument.
 *
 * Resolution is scoped to the enclosing function, matching the one-exception-per-factory pattern.
 */
object ExceptionMessageParamsContext {

    /** Params method name -> the method whose first string argument carries the message key. */
    val PARAMS_METHODS = setOf("setMessageParams", "setUserMessageParams")

    private val PLACEHOLDER = Regex("\\{\\s*([A-Za-z0-9_]+)")

    fun placeholders(messageText: String): Set<String> =
        PLACEHOLDER.findAll(messageText).map { it.groupValues[1] }.toCollection(linkedSetOf())

    /** The message key whose placeholders apply to the given params [call], or null. */
    fun messageKeyForParamsCall(call: MethodReference): String? {
        val function = PsiTreeUtil.getParentOfType(call, Function::class.java) ?: return null
        return when (call.name) {
            "setMessageParams" -> constructorMessageKey(function)
            "setUserMessageParams" -> userMessageKey(function)
            else -> null
        }
    }

    /**
     * Keys present in the params call paired with [messageMethod] inside the function enclosing
     * [messageLiteral]; null when there is no such call at all (i.e. every placeholder is missing).
     */
    fun providedParamKeys(messageLiteral: StringLiteralExpression, messageMethod: String): Set<String>? {
        val function = PsiTreeUtil.getParentOfType(messageLiteral, Function::class.java) ?: return null
        val call = PsiTreeUtil.findChildrenOfType(function, MethodReference::class.java)
            .firstOrNull { it.name == messageMethod } ?: return null
        val array = call.parameters.firstOrNull() as? ArrayCreationExpression ?: return emptySet()
        return ArrayKeyContext.existingKeys(array)
    }

    private fun constructorMessageKey(function: Function): String? {
        PsiTreeUtil.findChildrenOfType(function, NewExpression::class.java).forEach { newExpression ->
            firstStringArgument(newExpression.parameters)?.let { return it }
        }
        PsiTreeUtil.findChildrenOfType(function, MethodReference::class.java)
            .firstOrNull { it.name == "__construct" }
            ?.let { firstStringArgument(it.parameters)?.let { key -> return key } }
        return null
    }

    private fun userMessageKey(function: Function): String? =
        PsiTreeUtil.findChildrenOfType(function, MethodReference::class.java)
            .firstOrNull { it.name == "setUserMessage" }
            ?.let { firstStringArgument(it.parameters) }

    private fun firstStringArgument(parameters: Array<PsiElement>): String? =
        (parameters.firstOrNull() as? StringLiteralExpression)?.contents
}