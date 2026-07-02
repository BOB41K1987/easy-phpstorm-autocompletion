package com.eonx.eeh.translationnav

import com.intellij.psi.PsiElement
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.NewExpression
import com.jetbrains.php.lang.psi.elements.ParameterList
import com.jetbrains.php.lang.psi.elements.PhpAttribute
import com.jetbrains.php.lang.psi.elements.PhpClass
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

/**
 * Detects a Symfony validator constraint `message`-like argument written as a PHP attribute,
 * e.g. `#[Assert\Expression(message: '<caret>')]`. The Symfony plugin handles the `new Assert\…`
 * and `trans()` forms but not the attribute form.
 */
object ConstraintMessageContext {

    /** Translation domain validator messages live in. */
    const val DOMAIN = "validators"

    private const val CONSTRAINTS_NAMESPACE = "\\Symfony\\Component\\Validator\\Constraints\\"
    private const val CONSTRAINT_BASE_CLASS = "\\Symfony\\Component\\Validator\\Constraint"

    fun isConstraintMessageArgument(literal: StringLiteralExpression): Boolean {
        val parameterList = literal.parent as? ParameterList ?: return false
        val attribute = parameterList.parent as? PhpAttribute ?: return false

        val argumentName = namedArgumentName(literal) ?: return false
        if (!argumentName.lowercase().endsWith("message")) return false

        return isConstraintAttribute(attribute)
    }

    /**
     * Same as [isConstraintMessageArgument] but also matches `new Assert\X(message: '<caret>')`
     * constructor calls, e.g. constraints nested inside `Assert\Sequentially`/`Assert\When`'s
     * `constraints: [...]` array, which aren't attributes themselves. The bundled Symfony plugin
     * already resolves those forward, but doesn't offer a reverse (usages) direction, so the
     * reverse Go to Declaration needs to recognize this shape itself.
     */
    fun isConstraintMessageUsage(literal: StringLiteralExpression): Boolean {
        val parameterList = literal.parent as? ParameterList ?: return false
        val argumentName = namedArgumentName(literal) ?: return false
        if (!argumentName.lowercase().endsWith("message")) return false

        return when (val call = parameterList.parent) {
            is PhpAttribute -> isConstraintAttribute(call)
            is NewExpression -> PhpEntityUtil.resolveClasses(call, call.project).any { isConstraintClass(it) }
            else -> false
        }
    }

    /** The name of the named argument [literal] is the value of, or null when positional. */
    private fun namedArgumentName(literal: StringLiteralExpression): String? {
        val colon = skipWhitespaceBackwards(literal.prevSibling)
        if (colon?.text != ":") return null
        return skipWhitespaceBackwards(colon.prevSibling)?.text
    }

    private fun skipWhitespaceBackwards(start: PsiElement?): PsiElement? {
        var sibling = start
        while (sibling != null && sibling.text.isBlank()) sibling = sibling.prevSibling
        return sibling
    }

    private fun isConstraintAttribute(attribute: PhpAttribute): Boolean {
        val fqn = attribute.fqn ?: return false
        if (fqn.startsWith(CONSTRAINTS_NAMESPACE)) return true

        return PhpIndex.getInstance(attribute.project)
            .getClassesByFQN(fqn)
            .any { isConstraintClass(it) }
    }

    private fun isConstraintClass(phpClass: PhpClass): Boolean {
        val visited = mutableSetOf<String>()
        var current: PhpClass? = phpClass
        while (current != null && visited.add(current.fqn)) {
            if (current.fqn == CONSTRAINT_BASE_CLASS) return true
            current = current.superClass
        }
        return false
    }
}