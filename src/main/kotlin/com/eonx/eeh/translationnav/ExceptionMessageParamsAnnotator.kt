package com.eonx.eeh.translationnav

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.psi.PsiElement
import com.intellij.ui.JBColor
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression
import java.awt.Font

/**
 * Underlines an exception/user message key in yellow when the message declares ICU placeholders
 * that are not set in the corresponding `setMessageParams` / `setUserMessageParams` call.
 */
class ExceptionMessageParamsAnnotator : Annotator {

    private companion object {
        val YELLOW_UNDERSCORE =
            TextAttributes(null, null, JBColor.YELLOW, EffectType.LINE_UNDERSCORE, Font.PLAIN)

        // message key prefix -> the params method expected to provide its placeholders
        val PARAMS_METHOD = mapOf(
            "exceptions." to "setMessageParams",
            "user_messages." to "setUserMessageParams",
        )
    }

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        val literal = element as? StringLiteralExpression ?: return
        val prefix = MessageKeyContext.requiredPrefix(literal) ?: return
        val paramsMethod = PARAMS_METHOD[prefix] ?: return

        val key = literal.contents
        if (!key.startsWith(prefix)) return

        val messageText = TranslationKeyResolver.messageText(element.project, key) ?: return
        val placeholders = ExceptionMessageParamsContext.placeholders(messageText)
        if (placeholders.isEmpty()) return

        val provided = ExceptionMessageParamsContext.providedParamKeys(literal, paramsMethod) ?: emptySet()
        val missing = placeholders - provided
        if (missing.isEmpty()) return

        holder.newAnnotation(
            HighlightSeverity.WARNING,
            "Message placeholders not set in $paramsMethod: ${missing.joinToString(", ")}.",
        ).range(literal).enforcedTextAttributes(YELLOW_UNDERSCORE).create()
    }
}