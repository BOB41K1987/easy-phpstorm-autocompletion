package com.eonx.eeh.translationnav

import com.intellij.openapi.project.Project
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.ClassConstantReference
import com.jetbrains.php.lang.psi.elements.PhpClass
import com.jetbrains.php.lang.psi.elements.PhpReturn
import com.jetbrains.php.lang.psi.elements.PhpTypedElement

/**
 * Resolves a Zenstruck Foundry factory to the entity it builds, and that entity's
 * settable property names — used to complete the attribute-array keys passed to
 * factory methods such as `createEntity([...])`, `new([...])`, `with([...])`.
 */
object FactoryEntityResolver {

    /** Property names of the entity built by [factory], including inherited ones. */
    fun attributeNames(factory: PhpClass): Set<String> {
        val entity = resolveEntityClass(factory) ?: return emptySet()
        return collectProperties(entity)
    }

    fun resolveClasses(element: PhpTypedElement, project: Project): Collection<PhpClass> {
        val phpIndex = PhpIndex.getInstance(project)
        // global() resolves method-return signatures (e.g. a chained factory call) to real FQNs.
        return element.type.global(project).types
            .filter { it.startsWith("\\") }
            .flatMap { phpIndex.getClassesByFQN(it) }
    }

    /** Reads the factory's `class(): string { return Entity::class; }` and resolves that entity. */
    private fun resolveEntityClass(factory: PhpClass): PhpClass? {
        val classMethod = factory.findMethodByName("class") ?: return null
        val returnExpr = PsiTreeUtil.findChildOfType(classMethod, PhpReturn::class.java)?.argument
        val classConstant = returnExpr as? ClassConstantReference ?: return null
        if (!"class".equals(classConstant.name, ignoreCase = true)) return null

        val classReference = classConstant.classReference as? PhpTypedElement ?: return null
        return resolveClasses(classReference, factory.project).firstOrNull()
    }

    private fun collectProperties(entity: PhpClass): Set<String> {
        val names = linkedSetOf<String>()
        val visited = mutableSetOf<String>()
        var current: PhpClass? = entity

        while (current != null && visited.add(current.fqn)) {
            for (field in current.fields) {
                if (!field.isConstant) {
                    names.add(field.name)
                }
            }
            current = current.superClass
        }

        return names
    }
}