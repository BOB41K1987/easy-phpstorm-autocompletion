package com.eonx.eeh.translationnav

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement

/**
 * Go to Declaration from an entity attribute/criteria array key to the corresponding entity field,
 * e.g. `createEntity(['status' => ...])` or `assertEntityExists(X::class, ['status' => ...])` jumps
 * to the entity's `$status` property.
 */
class EntityFieldGotoHandler : GotoDeclarationHandler {

    override fun getGotoDeclarationTargets(
        sourceElement: PsiElement?,
        offset: Int,
        editor: Editor?,
    ): Array<PsiElement>? {
        val element = sourceElement ?: return null
        val literal = ArrayKeyContext.keyLiteral(element) ?: return null
        val array = ArrayKeyContext.enclosingArray(literal) ?: return null
        if (!ArrayKeyContext.isKeyPosition(literal, array)) return null

        val key = literal.contents
        val targets = EntityKeyContext.entityClasses(array, element.project)
            .mapNotNull { PhpEntityUtil.findField(it, key) }

        return targets.takeIf { it.isNotEmpty() }?.toTypedArray()
    }
}