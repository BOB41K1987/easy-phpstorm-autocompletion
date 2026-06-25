package com.eonx.eeh.translationnav

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.jetbrains.php.lang.psi.elements.ClassConstantReference
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.PhpClass

/** Shared logic for the EntityExpectation assertion chain (`assertEntity(...)->...`). */
object EntityExpectationContext {

    /** Walks the call chain to `assertEntity(Entity::class)` and resolves that entity. */
    fun resolveAssertedEntity(call: MethodReference, project: Project): PhpClass? {
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