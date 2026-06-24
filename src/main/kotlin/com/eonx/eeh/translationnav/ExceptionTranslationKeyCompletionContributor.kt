package com.eonx.eeh.translationnav

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.CompletionUtilCore
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

/**
 * Autocompletes EasyErrorHandler exception message keys from the live
 * `translations/messages*.php` files (no generated metadata):
 *  - `exceptions.*` inside a `new <Exception>(...)` / `parent::__construct(...)` call;
 *  - `user_messages.*` inside a `->setUserMessage(...)` call.
 */
class ExceptionTranslationKeyCompletionContributor : CompletionContributor() {

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
                    val literal = parameters.position.parent as? StringLiteralExpression ?: return
                    val prefix = MessageKeyContext.requiredPrefix(literal) ?: return

                    // Match against everything typed before the caret (dots included), so the whole
                    // string content is replaced on insert instead of just the last segment.
                    val typed = literal.contents.substringBefore(CompletionUtilCore.DUMMY_IDENTIFIER_TRIMMED)
                    val resultSet = result.withPrefixMatcher(typed)

                    for (key in TranslationKeyResolver.collectKeys(literal.project)) {
                        if (key.startsWith(prefix)) {
                            resultSet.addElement(LookupElementBuilder.create(key))
                        }
                    }
                }
            },
        )
    }
}