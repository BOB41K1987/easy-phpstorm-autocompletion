package com.eonx.eeh.translationnav

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import com.jetbrains.php.lang.psi.elements.ArrayCreationExpression
import com.jetbrains.php.lang.psi.elements.MethodReference

/**
 * Quick-fix that adds a missing JSONB property key to the `$jsonAttributes` argument of an
 * EntityExpectation assertion, creating that argument when the call doesn't have one yet.
 */
class AddJsonAttributeQuickFix(
    private val keyName: String,
    private val callPointer: SmartPsiElementPointer<MethodReference>,
    private val criteriaIndex: Int,
    private val jsonAttributesIndex: Int,
) : IntentionAction {

    override fun getText(): String = "Add '$keyName' to \$jsonAttributes"

    override fun getFamilyName(): String = "Add missing JSONB attribute"

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean =
        callPointer.element != null

    override fun startInWriteAction(): Boolean = true

    override fun invoke(project: Project, editor: Editor?, file: PsiFile) {
        val call = callPointer.element ?: return
        val parameters = call.parameters
        val criteria = parameters.getOrNull(criteriaIndex) as? ArrayCreationExpression ?: return
        val jsonArray = parameters.getOrNull(jsonAttributesIndex) as? ArrayCreationExpression

        val document = PsiDocumentManager.getInstance(project).getDocument(file) ?: return

        if (jsonArray != null) {
            val isEmpty = jsonArray.text.removePrefix("[").removeSuffix("]").isBlank()
            val insert = if (isEmpty) "'$keyName'" else "'$keyName', "
            document.insertString(jsonArray.textRange.startOffset + 1, insert)
        } else {
            document.insertString(criteria.textRange.endOffset, ", ['$keyName']")
        }

        PsiDocumentManager.getInstance(project).commitDocument(document)
    }
}
