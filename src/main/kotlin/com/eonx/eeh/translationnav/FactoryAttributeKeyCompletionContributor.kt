package com.eonx.eeh.translationnav

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.project.Project
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import com.jetbrains.php.lang.psi.elements.ArrayCreationExpression
import com.jetbrains.php.lang.psi.elements.Method
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.ParameterList
import com.jetbrains.php.lang.psi.elements.PhpClass
import com.jetbrains.php.lang.psi.elements.PhpReturn
import com.jetbrains.php.lang.psi.elements.PhpTypedElement
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

/**
 * Autocompletes Zenstruck Foundry attribute-array keys with the property names of the entity
 * the factory builds. Fires both:
 *  - inside a factory call, e.g. `DisputeFactory::new()->createEntity(['<caret>' => ...])`;
 *  - inside the array returned by the factory's own `defaults()` method.
 */
class FactoryAttributeKeyCompletionContributor : CompletionContributor() {

    private companion object {
        val ATTRIBUTE_METHODS = setOf(
            "new", "createOne", "createMany", "create",
            "with", "createEntity", "makeEntity", "createEntityList",
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

                    val names = factoryClasses(array, literal.project)
                        .flatMap { FactoryEntityResolver.attributeNames(it) }
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

    /** The factory class(es) whose entity attributes apply to keys of [array], or empty. */
    private fun factoryClasses(array: ArrayCreationExpression, project: Project): Collection<PhpClass> {
        val call = factoryCall(array)
        if (call != null) {
            val qualifier = call.classReference as? PhpTypedElement ?: return emptyList()
            return PhpEntityUtil.resolveClasses(qualifier, project)
        }
        return defaultsFactory(array)?.let { listOf(it) } ?: emptyList()
    }

    private fun factoryCall(array: ArrayCreationExpression): MethodReference? {
        val parameterList = array.parent as? ParameterList ?: return null
        val call = parameterList.parent as? MethodReference ?: return null
        return call.takeIf { it.name in ATTRIBUTE_METHODS }
    }

    /** The containing factory class when [array] is the literal returned by a `defaults()` method. */
    private fun defaultsFactory(array: ArrayCreationExpression): PhpClass? {
        val returnStatement = PsiTreeUtil.getParentOfType(array, PhpReturn::class.java) ?: return null
        if (returnStatement.argument !== array) return null
        val method = PsiTreeUtil.getParentOfType(returnStatement, Method::class.java) ?: return null
        if (!"defaults".equals(method.name, ignoreCase = true)) return null
        return method.containingClass
    }
}