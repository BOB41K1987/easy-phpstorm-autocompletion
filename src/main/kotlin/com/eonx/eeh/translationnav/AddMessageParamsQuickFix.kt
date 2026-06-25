package com.eonx.eeh.translationnav

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.elements.ArrayCreationExpression
import com.jetbrains.php.lang.psi.elements.AssignmentExpression
import com.jetbrains.php.lang.psi.elements.Function
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.NewExpression
import com.jetbrains.php.lang.psi.elements.Statement
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression
import com.jetbrains.php.lang.psi.elements.Variable

/**
 * Quick-fix that supplies the message placeholders missing from a `setMessageParams` /
 * `setUserMessageParams` call: it merges them into an existing params array, or appends a new
 * params-call statement on the exception variable when no such call exists. Values are scaffolded
 * as `null` for the developer to fill in.
 */
class AddMessageParamsQuickFix(
    private val missing: List<String>,
    private val paramsMethod: String,
    private val messagePointer: SmartPsiElementPointer<StringLiteralExpression>,
) : IntentionAction {

    override fun getText(): String = "Add missing $paramsMethod placeholder(s): ${missing.joinToString(", ")}"

    override fun getFamilyName(): String = "Add missing message params"

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean =
        messagePointer.element != null && missing.isNotEmpty()

    override fun startInWriteAction(): Boolean = true

    override fun invoke(project: Project, editor: Editor?, file: PsiFile) {
        val literal = messagePointer.element ?: return
        val function = PsiTreeUtil.getParentOfType(literal, Function::class.java) ?: return
        val document = PsiDocumentManager.getInstance(project).getDocument(file) ?: return
        val entries = missing.joinToString(", ") { "'$it' => null" }

        val existingArray = PsiTreeUtil.findChildrenOfType(function, MethodReference::class.java)
            .firstOrNull { it.name == paramsMethod }
            ?.parameters?.firstOrNull() as? ArrayCreationExpression

        if (existingArray != null) {
            val isEmpty = existingArray.text.removePrefix("[").removeSuffix("]").isBlank()
            val insert = if (isEmpty) entries else "$entries, "
            document.insertString(existingArray.textRange.startOffset + 1, insert)
        } else {
            val anchor = anchorStatement(function) ?: return
            val variable = exceptionVariableName(function) ?: return
            val indent = lineIndent(document, anchor.textRange.startOffset)
            document.insertString(
                anchor.textRange.endOffset,
                "\n$indent\$$variable->$paramsMethod([$entries]);",
            )
        }

        PsiDocumentManager.getInstance(project).commitDocument(document)
    }

    /** Statement after which a new params call should be inserted. */
    private fun anchorStatement(function: Function): Statement? {
        val anchorCall: PsiElement? = when (paramsMethod) {
            "setUserMessageParams" -> PsiTreeUtil.findChildrenOfType(function, MethodReference::class.java)
                .firstOrNull { it.name == "setUserMessage" }
            else -> PsiTreeUtil.findChildrenOfType(function, NewExpression::class.java).firstOrNull()
        }
        return PsiTreeUtil.getParentOfType(anchorCall, Statement::class.java)
    }

    private fun exceptionVariableName(function: Function): String? {
        val newExpression = PsiTreeUtil.findChildrenOfType(function, NewExpression::class.java).firstOrNull()
        val assignment = PsiTreeUtil.getParentOfType(newExpression, AssignmentExpression::class.java)
        (assignment?.variable as? Variable)?.name?.let { return it }

        var qualifier: PsiElement? = PsiTreeUtil.findChildrenOfType(function, MethodReference::class.java)
            .firstOrNull { it.name == "setUserMessage" || it.name in ExceptionMessageParamsContext.PARAMS_METHODS }
            ?.classReference
        while (qualifier is MethodReference) qualifier = qualifier.classReference
        return (qualifier as? Variable)?.name
    }

    private fun lineIndent(document: Document, offset: Int): String {
        val lineStart = document.getLineStartOffset(document.getLineNumber(offset))
        return document.charsSequence.subSequence(lineStart, offset).takeWhile { it == ' ' || it == '\t' }.toString()
    }
}