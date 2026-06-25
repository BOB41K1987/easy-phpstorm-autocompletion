package com.eonx.eeh.translationnav

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext
import com.jetbrains.php.lang.psi.elements.ClassConstantReference
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.ParameterList
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

/**
 * Autocompletes the `$criteria` array keys of `DatabaseEntityTrait`-style helpers with the
 * properties of the entity passed as the first argument, e.g.
 * `self::assertEntityExists(EventLog::class, ['<caret>' => ...])`.
 */
class RepositoryCriteriaKeyCompletionContributor : CompletionContributor() {

    private companion object {
        // method name -> (entity-class argument index, criteria-array argument index)
        val SPECS = mapOf(
            "assertEntityExists" to (0 to 1),
            "assertEntityDoesNotExist" to (0 to 1),
            "getEntity" to (0 to 1),
            "findOneEntity" to (0 to 1),
            "assertEntityCount" to (0 to 2),
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
                    if (!ArrayKeyContext.isKeyPosition(literal, array)) return

                    val parameterList = array.parent as? ParameterList ?: return
                    val call = parameterList.parent as? MethodReference ?: return
                    val (entityIndex, criteriaIndex) = SPECS[call.name] ?: return
                    if (parameterList.parameters.indexOf(array) != criteriaIndex) return

                    val entityArgument = call.parameters.getOrNull(entityIndex) as? ClassConstantReference ?: return
                    val entity = PhpEntityUtil.resolveClassConstant(entityArgument, literal.project) ?: return
                    val names = PhpEntityUtil.propertiesOf(entity)
                    if (names.isEmpty()) return

                    val existing = ArrayKeyContext.existingKeys(array)
                    val resultSet = result.withPrefixMatcher(ArrayKeyContext.typedPrefix(literal))

                    for (name in names) {
                        if (name in existing) continue
                        resultSet.addElement(
                            LookupElementBuilder.create(name)
                                .withIcon(PluginIcons.EONX)
                                .withTypeText("criteria")
                                .withInsertHandler(ArrayKeyContext.APPEND_ARROW),
                        )
                    }
                }
            },
        )
    }
}