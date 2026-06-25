package com.eonx.eeh.translationnav

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.ParameterList
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

/**
 * Autocompletes the array keys of `setMessageParams([...])` / `setUserMessageParams([...])` with
 * the placeholders declared in the corresponding message (constructor message / setUserMessage).
 */
class ExceptionMessageParamsCompletionContributor : CompletionContributor() {

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
                    if (!ArrayKeyContext.isKeyPosition(literal, array)) return

                    val call = (array.parent as? ParameterList)?.parent as? MethodReference ?: return
                    if (call.name !in ExceptionMessageParamsContext.PARAMS_METHODS) return

                    val key = ExceptionMessageParamsContext.messageKeyForParamsCall(call) ?: return
                    val messageText = TranslationKeyResolver.messageText(literal.project, key) ?: return
                    val placeholders = ExceptionMessageParamsContext.placeholders(messageText)
                    if (placeholders.isEmpty()) return

                    val existing = ArrayKeyContext.existingKeys(array)
                    val resultSet = result.withPrefixMatcher(ArrayKeyContext.typedPrefix(literal))

                    for (placeholder in placeholders) {
                        if (placeholder in existing) continue
                        resultSet.addElement(
                            LookupElementBuilder.create(placeholder)
                                .withIcon(PluginIcons.EONX)
                                .withTypeText("placeholder")
                                .withInsertHandler(ArrayKeyContext.APPEND_ARROW),
                        )
                    }
                }
            },
        )
    }
}