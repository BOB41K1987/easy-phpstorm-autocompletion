package com.eonx.eeh.translationnav

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext
import com.jetbrains.php.lang.psi.elements.ArrayCreationExpression
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.ParameterList
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

/**
 * Autocompletes the values of the `$jsonAttributes` / `$encryptableAttributes` list arguments of
 * EntityExpectation assertions with the keys present in the same call's first `$criteria` array,
 * e.g. `->toBeInDb(['type' => ..., 'payload' => ...], ['<caret>'])` offers `type`, `payload`.
 */
class EntityCriteriaListValueCompletionContributor : CompletionContributor() {

    private data class ListSpec(val criteriaIndex: Int, val listIndices: Set<Int>)

    private companion object {
        val LIST_SPECS = mapOf(
            "toBeInDb" to ListSpec(criteriaIndex = 0, listIndices = setOf(1, 2)),
            "toNotBeInDb" to ListSpec(criteriaIndex = 0, listIndices = setOf(1)),
        )
    }

    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement().withParent(StringLiteralExpression::class.java),
            object : CompletionProvider<CompletionParameters>() {
                override fun addCompletions(
                    parameters: CompletionParameters,
                    context: ProcessingContext,
                    result: CompletionResultSet,
                ) {
                    val literal = ArrayKeyContext.keyLiteral(parameters.position) ?: return
                    val array = ArrayKeyContext.enclosingArray(literal) ?: return
                    if (!ArrayKeyContext.isValuePosition(literal, array)) return

                    val parameterList = array.parent as? ParameterList ?: return
                    val call = parameterList.parent as? MethodReference ?: return
                    val spec = LIST_SPECS[call.name] ?: return

                    val parameters2 = parameterList.parameters
                    if (parameters2.indexOf(array) !in spec.listIndices) return

                    val criteria = parameters2.getOrNull(spec.criteriaIndex) as? ArrayCreationExpression ?: return
                    val candidates = ArrayKeyContext.existingKeys(criteria)
                    if (candidates.isEmpty()) return

                    val alreadyListed = ArrayKeyContext.stringValues(array)
                    val resultSet = result.withPrefixMatcher(ArrayKeyContext.typedPrefix(literal))

                    for (candidate in candidates) {
                        if (candidate in alreadyListed) continue
                        resultSet.addElement(
                            LookupElementBuilder.create(candidate).withTypeText("criteria field"),
                        )
                    }
                }
            },
        )
    }
}