package com.eonx.eeh.translationnav

import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.elements.ClassConstantReference
import com.jetbrains.php.lang.psi.elements.PhpClass
import com.jetbrains.php.lang.psi.elements.PhpReturn

/**
 * Resolves a Zenstruck Foundry factory to the settable property names of the entity it builds,
 * via the factory's `class(): string { return Entity::class; }` method.
 */
object FactoryEntityResolver {

    fun attributeNames(factory: PhpClass): Set<String> {
        val entity = resolveEntityClass(factory) ?: return emptySet()
        return PhpEntityUtil.propertiesOf(entity)
    }

    private fun resolveEntityClass(factory: PhpClass): PhpClass? {
        val classMethod = factory.findMethodByName("class") ?: return null
        val returnExpr = PsiTreeUtil.findChildOfType(classMethod, PhpReturn::class.java)?.argument
        val classConstant = returnExpr as? ClassConstantReference ?: return null
        return PhpEntityUtil.resolveClassConstant(classConstant, factory.project)
    }
}