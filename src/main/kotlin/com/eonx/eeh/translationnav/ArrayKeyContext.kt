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

    fun existingKeys(array: ArrayCreationExpression): Set<String> {
        val fromPsi = array.hashElements
            .mapNotNull { (it.key as? StringLiteralExpression)?.contents }
            .filterNot { it.contains(CompletionUtilCore.DUMMY_IDENTIFIER_TRIMMED) }
        // Union with a text scan: while a new key is typed at the start of the array the missing
        // comma breaks the parse and the trailing entries vanish from the PSI tree (but not the text).
        return (fromPsi + scanTopLevel(array).first).toSet()
    }

    /** True when [literal] is a value (not a key) inside [array]. */
    fun isValuePosition(literal: StringLiteralExpression, array: ArrayCreationExpression): Boolean {
        val hash = PsiTreeUtil.getParentOfType(
            literal,
            ArrayHashElement::class.java,
            false,
            ArrayCreationExpression::class.java,
        )
        // No enclosing hash → a bare list element, which is a value.
        if (hash == null) return true
        val value = hash.value
        return value != null && PsiTreeUtil.isAncestor(value, literal, false)
    }

    /** String values currently present in [array] (excluding the one being typed). */
    fun stringValues(array: ArrayCreationExpression): Set<String> {
        val fromPsi = PsiTreeUtil.findChildrenOfType(array, StringLiteralExpression::class.java)
            .map { it.contents }
            .filterNot { it.contains(CompletionUtilCore.DUMMY_IDENTIFIER_TRIMMED) }
        return (fromPsi + scanTopLevel(array).second).toSet()
    }

    /**
     * Scans the array's source text from its opening bracket, returning its top-level
     * (keys, listValues) — robust to the transient parse breakage that occurs while a new
     * element is typed before an existing one without a separating comma.
     */
    private fun scanTopLevel(array: ArrayCreationExpression): Pair<Set<String>, Set<String>> {
        val text = array.containingFile.text
        val keys = linkedSetOf<String>()
        val values = linkedSetOf<String>()

        var i = array.textRange.startOffset
        while (i < text.length && text[i] != '[') i++
        if (i >= text.length) return keys to values
        i++ // past '['

        var depth = 1
        while (i < text.length && depth > 0) {
            when (val c = text[i]) {
                '\'', '"' -> {
                    i++
                    val content = StringBuilder()
                    while (i < text.length && text[i] != c) {
                        if (text[i] == '\\' && i + 1 < text.length) {
                            content.append(text[i + 1]); i += 2
                        } else {
                            content.append(text[i]); i++
                        }
                    }
                    i++ // past closing quote
                    if (depth == 1) {
                        var j = i
                        while (j < text.length && text[j].isWhitespace()) j++
                        val isKey = j + 1 < text.length && text[j] == '=' && text[j + 1] == '>'
                        (if (isKey) keys else values).add(content.toString())
                    }
                }
                '[', '(', '{' -> { depth++; i++ }
                ']', ')', '}' -> { depth--; i++ }
                else -> i++
            }
        }

        keys.removeAll { it.contains(CompletionUtilCore.DUMMY_IDENTIFIER_TRIMMED) }
        values.removeAll { it.contains(CompletionUtilCore.DUMMY_IDENTIFIER_TRIMMED) }
        return keys to values
    }

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