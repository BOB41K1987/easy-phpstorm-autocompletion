package com.eonx.eeh.translationnav

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

/**
 * Autocompletes entity attribute/criteria array keys with the entity's property names, across all
 * supported call shapes (resolution lives in [EntityKeyContext]): Foundry factory attributes &
 * `defaults()`, EntityExpectation `toBeInDb`/…, and DatabaseEntityTrait helpers.
 */
class EntityAttributeKeyCompletionContributor : CompletionContributor() {

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

                    val names = EntityKeyContext.entityClasses(array, literal.project)
                        .flatMap { PhpEntityUtil.propertiesOf(it) }
                        .toSet()
                    if (names.isEmpty()) return

                    val existing = ArrayKeyContext.existingKeys(array)
                    val resultSet = result.withPrefixMatcher(ArrayKeyContext.typedPrefix(literal))

                    for (name in names) {
                        if (name in existing) continue
                        resultSet.addElement(
                            LookupElementBuilder.create(name)
                                .withIcon(PluginIcons.EONX)
                                .withTypeText("attribute")
                                .withInsertHandler(ArrayKeyContext.APPEND_ARROW),
                        )
                    }
                }
            },
        )
    }
}