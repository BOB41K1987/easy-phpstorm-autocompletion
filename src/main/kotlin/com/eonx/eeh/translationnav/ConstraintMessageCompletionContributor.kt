package com.eonx.eeh.translationnav

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.CompletionUtilCore
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

/**
 * Autocompletes `validators`-domain translation keys for the `message` argument of a Symfony
 * validator constraint written as an attribute, e.g. `#[Assert\Expression(message: '<caret>')]`.
 */
class ConstraintMessageCompletionContributor : CompletionContributor() {

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
                    val literal = PsiTreeUtil.getParentOfType(
                        parameters.position,
                        StringLiteralExpression::class.java,
                        false,
                    ) ?: return
                    if (!ConstraintMessageContext.isConstraintMessageArgument(literal)) return

                    val typed = literal.contents.substringBefore(CompletionUtilCore.DUMMY_IDENTIFIER_TRIMMED)
                    val resultSet = result.withPrefixMatcher(typed)

                    for (key in TranslationKeyResolver.collectKeys(literal.project, ConstraintMessageContext.DOMAIN)) {
                        resultSet.addElement(LookupElementBuilder.create(key))
                    }
                }
            },
        )
    }
}