package com.eonx.eeh.translationnav

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.ui.JBColor
import com.jetbrains.php.lang.psi.elements.ArrayCreationExpression
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression
import java.awt.Font

/**
 * Cross-checks `EntityExpectation` JSONB assertions:
 *  - a JSONB criteria key missing from `$jsonAttributes` is highlighted (yellow background);
 *  - a `$jsonAttributes` value not present in `$criteria` is underlined (yellow underscore).
 */
class EntityCriteriaJsonbAnnotator : Annotator {

    private companion object {
        val YELLOW_HIGHLIGHT = TextAttributes(null, JBColor.YELLOW, null, null, Font.PLAIN)
        val YELLOW_UNDERSCORE =
            TextAttributes(null, null, JBColor.YELLOW, EffectType.LINE_UNDERSCORE, Font.PLAIN)

        /** Method name -> (criteria array index, jsonAttributes array index). */
        val JSON_ATTRIBUTE_SPECS = mapOf(
            "toBeInDb" to (0 to 1),
            "toNotBeInDb" to (0 to 1),
        )
    }

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        val call = element as? MethodReference ?: return
        val (criteriaIndex, jsonIndex) = JSON_ATTRIBUTE_SPECS[call.name] ?: return
        val callPointer by lazy { SmartPointerManager.createPointer(call) }

        val parameters = call.parameters
        val criteria = parameters.getOrNull(criteriaIndex) as? ArrayCreationExpression ?: return
        val entity = EntityExpectationContext.resolveAssertedEntity(call, element.project) ?: return

        val criteriaKeyElements = keyElements(criteria)
        val criteriaKeys = criteriaKeyElements.keys
        val jsonValueElements = (parameters.getOrNull(jsonIndex) as? ArrayCreationExpression)
            ?.let { valueElements(it) }
            ?: emptyMap()
        val jsonValues = jsonValueElements.keys

        val jsonbProperties = PhpEntityUtil.jsonbProperties(entity)

        for ((key, keyElement) in criteriaKeyElements) {
            if (key in jsonbProperties && key !in jsonValues) {
                holder.newAnnotation(
                    HighlightSeverity.WARNING,
                    "JSONB property '$key' should be listed in the \$jsonAttributes argument.",
                ).range(keyElement)
                    .enforcedTextAttributes(YELLOW_HIGHLIGHT)
                    .withFix(AddJsonAttributeQuickFix(key, callPointer, criteriaIndex, jsonIndex))
                    .create()
            }
        }

        for ((value, valueElement) in jsonValueElements) {
            if (value !in criteriaKeys) {
                holder.newAnnotation(
                    HighlightSeverity.WARNING,
                    "'$value' is listed in \$jsonAttributes but is not used in \$criteria.",
                ).range(valueElement).enforcedTextAttributes(YELLOW_UNDERSCORE).create()
            }
        }
    }

    private fun keyElements(array: ArrayCreationExpression): Map<String, StringLiteralExpression> {
        val result = LinkedHashMap<String, StringLiteralExpression>()
        for (hash in array.hashElements) {
            val key = hash.key as? StringLiteralExpression ?: continue
            result.putIfAbsent(key.contents, key)
        }
        return result
    }

    private fun valueElements(array: ArrayCreationExpression): Map<String, StringLiteralExpression> {
        val result = LinkedHashMap<String, StringLiteralExpression>()
        for (literal in PsiTreeUtil.findChildrenOfType(array, StringLiteralExpression::class.java)) {
            result.putIfAbsent(literal.contents, literal)
        }
        return result
    }
}