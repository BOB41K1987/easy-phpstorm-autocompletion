package com.eonx.eeh.translationnav

import com.intellij.codeInsight.completion.CompletionUtilCore
import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.elements.ArrayCreationExpression
import com.jetbrains.php.lang.psi.elements.ArrayHashElement
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

/** Shared helpers for completing keys of a PHP array literal. */
object ArrayKeyContext {

    /** The string literal being completed, tolerating the wrapping leaf at the caret. */
    fun keyLiteral(position: PsiElement): StringLiteralExpression? =
        PsiTreeUtil.getParentOfType(position, StringLiteralExpression::class.java, false)

    fun enclosingArray(literal: StringLiteralExpression): ArrayCreationExpression? =
        PsiTreeUtil.getParentOfType(literal, ArrayCreationExpression::class.java)

    /**
     * True when [literal] is a key (not a value) of [array]. Array elements may be wrapped in an
     * intermediate PSI node, so the array/hash are located via tree walk rather than direct parent.
     */
    fun isKeyPosition(literal: StringLiteralExpression, array: ArrayCreationExpression): Boolean {
        val hash = PsiTreeUtil.getParentOfType(
            literal,
            ArrayHashElement::class.java,
            false,
            ArrayCreationExpression::class.java,
        )
        // No enclosing hash before the array → a bare element (no `=>` yet): a key being typed.
        if (hash == null) return true
        // Inside a hash → it's a key unless it sits within the value side.
        val value = hash.value
        return value == null || !PsiTreeUtil.isAncestor(value, literal, false)
    }

    fun existingKeys(array: ArrayCreationExpression): Set<String> =
        array.hashElements
            .mapNotNull { (it.key as? StringLiteralExpression)?.contents }
            .filterNot { it.contains(CompletionUtilCore.DUMMY_IDENTIFIER_TRIMMED) }
            .toSet()

    /** Text typed before the caret, used as the completion prefix matcher. */
    fun typedPrefix(literal: StringLiteralExpression): String =
        literal.contents.substringBefore(CompletionUtilCore.DUMMY_IDENTIFIER_TRIMMED)

    /** Turns the completed string into an array key by appending ` => ` when absent. */
    val APPEND_ARROW = InsertHandler<LookupElement> { context, _ ->
        val chars = context.document.charsSequence
        var pos = context.tailOffset
        if (pos < chars.length && (chars[pos] == '\'' || chars[pos] == '"')) pos++

        var look = pos
        while (look < chars.length && chars[look] == ' ') look++
        val hasArrow = look + 1 < chars.length && chars[look] == '=' && chars[look + 1] == '>'

        if (hasArrow) {
            context.editor.caretModel.moveToOffset(look + 2)
        } else {
            context.document.insertString(pos, " => ")
            context.editor.caretModel.moveToOffset(pos + 4)
        }
    }
}