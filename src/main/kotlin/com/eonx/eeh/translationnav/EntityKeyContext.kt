package com.eonx.eeh.translationnav

import com.intellij.openapi.project.Project
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.elements.ArrayCreationExpression
import com.jetbrains.php.lang.psi.elements.ClassConstantReference
import com.jetbrains.php.lang.psi.elements.Method
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.ParameterList
import com.jetbrains.php.lang.psi.elements.PhpClass
import com.jetbrains.php.lang.psi.elements.PhpReturn
import com.jetbrains.php.lang.psi.elements.PhpTypedElement

/**
 * Given a PHP array whose keys are entity attribute/criteria names, resolves the entity class(es)
 * those keys belong to. Single source of truth shared by the attribute-key completion and the
 * field goto handler. Covers:
 *  - Foundry factory calls (`DisputeFactory::new()->createEntity([...])`) and `defaults()` returns;
 *  - EntityExpectation chains (`assertEntity(X::class)->toBeInDb([...])`);
 *  - DatabaseEntityTrait helpers (`assertEntityExists(X::class, [...])`).
 */
object EntityKeyContext {

    private val FACTORY_METHODS = setOf(
        "new", "createOne", "createMany", "create",
        "with", "createEntity", "makeEntity", "createEntityList",
    )

    // EntityExpectation method -> criteria array index
    private val ENTITY_CRITERIA_INDEX = mapOf("toBeInDb" to 0, "toNotBeInDb" to 0, "toHaveCountInDb" to 1)

    // DatabaseEntityTrait method -> (entity-class arg index, criteria array index)
    private val REPOSITORY_SPECS = mapOf(
        "assertEntityExists" to (0 to 1),
        "assertEntityDoesNotExist" to (0 to 1),
        "getEntity" to (0 to 1),
        "findOneEntity" to (0 to 1),
        "assertEntityCount" to (0 to 2),
    )

    fun entityClasses(array: ArrayCreationExpression, project: Project): Collection<PhpClass> {
        val parameterList = array.parent as? ParameterList
        val call = parameterList?.parent as? MethodReference

        if (call != null && call.name in FACTORY_METHODS) {
            (call.classReference as? PhpTypedElement)?.let { qualifier ->
                val entities = PhpEntityUtil.resolveClasses(qualifier, project)
                    .mapNotNull { FactoryEntityResolver.entityClass(it) }
                if (entities.isNotEmpty()) return entities
            }
        }

        defaultsFactory(array)?.let { factory ->
            FactoryEntityResolver.entityClass(factory)?.let { return listOf(it) }
        }

        if (call != null && parameterList != null) {
            val index = parameterList.parameters.indexOf(array)

            ENTITY_CRITERIA_INDEX[call.name]?.let { criteriaIndex ->
                if (index == criteriaIndex) {
                    EntityExpectationContext.resolveAssertedEntity(call, project)?.let { return listOf(it) }
                }
            }

            REPOSITORY_SPECS[call.name]?.let { (entityIndex, criteriaIndex) ->
                if (index == criteriaIndex) {
                    (call.parameters.getOrNull(entityIndex) as? ClassConstantReference)?.let { classConstant ->
                        PhpEntityUtil.resolveClassConstant(classConstant, project)?.let { return listOf(it) }
                    }
                }
            }
        }

        return emptyList()
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