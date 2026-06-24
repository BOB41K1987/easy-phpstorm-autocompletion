package com.eonx.eeh.translationnav

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.CompletionUtilCore
import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext
import com.jetbrains.php.lang.psi.elements.ArrayCreationExpression
import com.jetbrains.php.lang.psi.elements.ArrayHashElement
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.ParameterList
import com.jetbrains.php.lang.psi.elements.PhpTypedElement
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

/**
 * Autocompletes the attribute-array keys passed to Zenstruck Foundry factory methods
 * (e.g. `DisputeFactory::new()->createEntity(['<caret>' => ...])`) with the property
 * names of the entity the factory builds.
 */
class FactoryAttributeKeyCompletionContributor : CompletionContributor() {

    private companion object {
        val ATTRIBUTE_METHODS = setOf(
            "new", "createOne", "createMany", "create",
            "with", "createEntity", "makeEntity", "createEntityList",
        )

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
                    if (!isArrayKeyPosition(literal)) return

                    val array = enclosingArray(literal) ?: return
                    val call = factoryCall(array) ?: return
                    val qualifier = call.classReference as? PhpTypedElement ?: return

                    val names = FactoryEntityResolver.resolveClasses(qualifier, literal.project)
                        .flatMap { FactoryEntityResolver.attributeNames(it) }
                        .toSet()
                    if (names.isEmpty()) return

                    val existing = existingKeys(array)
                    val typed = literal.contents.substringBefore(CompletionUtilCore.DUMMY_IDENTIFIER_TRIMMED)
                    val resultSet = result.withPrefixMatcher(typed)

                    for (name in names) {
                        if (name in existing) continue
                        resultSet.addElement(
                            LookupElementBuilder.create(name)
                                .withTypeText("attribute")
                                .withInsertHandler(APPEND_ARROW),
                        )
                    }
                }
            },
        )
    }

    private fun isArrayKeyPosition(literal: StringLiteralExpression): Boolean =
        when (val parent = literal.parent) {
            is ArrayHashElement -> parent.key === literal
            is ArrayCreationExpression -> true // value-style element with no `=>` yet: a key being typed
            else -> false
        }

    private fun enclosingArray(literal: StringLiteralExpression): ArrayCreationExpression? =
        when (val parent = literal.parent) {
            is ArrayCreationExpression -> parent
            is ArrayHashElement -> parent.parent as? ArrayCreationExpression
            else -> null
        }

    private fun factoryCall(array: ArrayCreationExpression): MethodReference? {
        val parameterList = array.parent as? ParameterList ?: return null
        val call = parameterList.parent as? MethodReference ?: return null
        return call.takeIf { it.name in ATTRIBUTE_METHODS }
    }

    private fun existingKeys(array: ArrayCreationExpression): Set<String> =
        array.hashElements
            .mapNotNull { (it.key as? StringLiteralExpression)?.contents }
            .filterNot { it.contains(CompletionUtilCore.DUMMY_IDENTIFIER_TRIMMED) }
            .toSet()
}