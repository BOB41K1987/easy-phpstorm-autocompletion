package com.eonx.eeh.translationnav

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.project.Project
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.util.ProcessingContext
import com.jetbrains.php.lang.psi.elements.ClassConstantReference
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.ParameterList
import com.jetbrains.php.lang.psi.elements.PhpClass
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

/**
 * Autocompletes the `$criteria` array keys of EntityExpectation assertions with the property
 * names of the entity asserted via `assertEntity(Entity::class)`, e.g.
 * `$this->assertEntity(EventLog::class)->toBeInDb(['<caret>' => ...])`.
 */
class EntityCriteriaKeyCompletionContributor : CompletionContributor() {

    private companion object {
        /** Method name -> index of its `$criteria` array parameter. */
        val CRITERIA_PARAMETER_INDEX = mapOf(
            "toBeInDb" to 0,
            "toNotBeInDb" to 0,
            "toHaveCountInDb" to 1,
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
                    val criteriaIndex = CRITERIA_PARAMETER_INDEX[call.name] ?: return
                    if (parameterList.parameters.indexOf(array) != criteriaIndex) return

                    val entity = resolveAssertedEntity(call, literal.project) ?: return
                    val names = PhpEntityUtil.propertiesOf(entity)
                    if (names.isEmpty()) return

                    val existing = ArrayKeyContext.existingKeys(array)
                    val resultSet = result.withPrefixMatcher(ArrayKeyContext.typedPrefix(literal))

                    for (name in names) {
                        if (name in existing) continue
                        resultSet.addElement(
                            LookupElementBuilder.create(name)
                                .withTypeText("criteria")
                                .withInsertHandler(ArrayKeyContext.APPEND_ARROW),
                        )
                    }
                }
            },
        )
    }

    /** Walks the call chain to `assertEntity(Entity::class)` and resolves that entity. */
    private fun resolveAssertedEntity(call: MethodReference, project: Project): PhpClass? {
        var qualifier: PsiElement? = call.classReference
        while (qualifier is MethodReference) {
            if ("assertEntity".equals(qualifier.name, ignoreCase = true)) {
                val classConstant = qualifier.parameters.firstOrNull() as? ClassConstantReference ?: return null
                return PhpEntityUtil.resolveClassConstant(classConstant, project)
            }
            qualifier = qualifier.classReference
        }
        return null
    }
}