package com.eonx.eeh.translationnav

import com.intellij.openapi.project.Project
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.ClassConstantReference
import com.jetbrains.php.lang.psi.elements.PhpClass
import com.jetbrains.php.lang.psi.elements.PhpTypedElement

/** Shared PHP-PSI helpers for resolving entity classes and their settable property names. */
object PhpEntityUtil {

    /** Resolves the class(es) an expression's type points to. */
    fun resolveClasses(element: PhpTypedElement, project: Project): Collection<PhpClass> {
        val phpIndex = PhpIndex.getInstance(project)
        // global() resolves method-return signatures (e.g. a chained call) to real FQNs.
        return element.type.global(project).types
            .filter { it.startsWith("\\") }
            .flatMap { phpIndex.getClassesByFQN(it) }
    }

    /** Resolves the entity behind a `Entity::class` expression. */
    fun resolveClassConstant(reference: ClassConstantReference, project: Project): PhpClass? {
        if (!"class".equals(reference.name, ignoreCase = true)) return null
        val classReference = reference.classReference as? PhpTypedElement ?: return null
        return resolveClasses(classReference, project).firstOrNull()
    }

    /** Property names declared on [entity] and all of its ancestors (constants excluded). */
    fun propertiesOf(entity: PhpClass): Set<String> {
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