package com.eonx.eeh.translationnav

import com.intellij.openapi.project.Project
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.ClassConstantReference
import com.jetbrains.php.lang.psi.elements.Field
import com.jetbrains.php.lang.psi.elements.PhpAttributesOwner
import com.jetbrains.php.lang.psi.elements.PhpClass
import com.jetbrains.php.lang.psi.elements.PhpTypedElement
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

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
    fun propertiesOf(entity: PhpClass): Set<String> = collectFields(entity) { true }

    /** The field named [name] on [entity] or an ancestor (constants excluded), or null. */
    fun findField(entity: PhpClass, name: String): Field? {
        val visited = mutableSetOf<String>()
        var current: PhpClass? = entity
        while (current != null && visited.add(current.fqn)) {
            current.fields.firstOrNull { !it.isConstant && it.name == name }?.let { return it }
            current = current.superClass
        }
        return null
    }

    /** Property names whose `#[ORM\Column]` maps to the JSONB Doctrine type. */
    fun jsonbProperties(entity: PhpClass): Set<String> = collectFields(entity) { isJsonbColumn(it) }

    private fun collectFields(entity: PhpClass, predicate: (Field) -> Boolean): Set<String> {
        val names = linkedSetOf<String>()
        val visited = mutableSetOf<String>()
        var current: PhpClass? = entity

        while (current != null && visited.add(current.fqn)) {
            for (field in current.fields) {
                if (!field.isConstant && predicate(field)) {
                    names.add(field.name)
                }
            }
            current = current.superClass
        }

        return names
    }

    private fun isJsonbColumn(field: Field): Boolean {
        val owner = field as? PhpAttributesOwner ?: return false
        val columnAttributes = owner.attributes.filter { it.fqn == "\\Doctrine\\ORM\\Mapping\\Column" }
        if (columnAttributes.isEmpty()) return false

        return columnAttributes.any { attribute ->
            // The type is written as `type: JsonbType::NAME` (a class constant) or the literal 'jsonb'.
            PsiTreeUtil.findChildrenOfType(attribute, ClassConstantReference::class.java).any { reference ->
                reference.classReference?.text?.endsWith("JsonbType") == true
            } || PsiTreeUtil.findChildrenOfType(attribute, StringLiteralExpression::class.java).any {
                it.contents == "jsonb"
            }
        }
    }
}