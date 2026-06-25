package com.eonx.eeh.translationnav

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Go to Declaration for the `message` argument of a Symfony validator constraint attribute,
 * resolved against the `validators` translation domain. (Completion is left to the Symfony plugin.)
 */
class ConstraintMessageTest : BasePlatformTestCase() {

    private val support = """
        <?php
        namespace Symfony\Component\Validator { class Constraint {} }
        namespace Symfony\Component\Validator\Constraints {
            class Expression extends \Symfony\Component\Validator\Constraint {
                public function __construct(public ?string ${'$'}expression = null, public ?string ${'$'}message = null) {}
            }
        }
    """.trimIndent()

    private val validators = """
        <?php
        return [
            'dispute' => [
                'incorrect_amount' => [
                    'too_big' => 'Amount is too big.',
                ],
            ],
        ];
    """.trimIndent()

    private fun configure(attributeArgs: String) {
        myFixture.addFileToProject("support.php", support)
        myFixture.addFileToProject("translations/validators+intl-icu.en.php", validators)
        val dto = """
            <?php
            namespace App\Dto;
            use Symfony\Component\Validator\Constraints as Assert;
            class Dto {
                #[Assert\Expression($attributeArgs)]
                public int ${'$'}amount = 0;
            }
        """.trimIndent()
        myFixture.configureByText("Dto.php", dto)
    }

    fun testGotoNavigatesToValidatorsFile() {
        configure("message: 'dispute.incorrect_amount.too_big<caret>'")
        val element = myFixture.file.findElementAt(myFixture.caretOffset - 1)

        val targets = ConstraintMessageGotoHandler()
            .getGotoDeclarationTargets(element, myFixture.caretOffset, myFixture.editor)

        assertNotNull("expected a goto target", targets)
        assertTrue(targets!!.isNotEmpty())
        assertTrue(targets[0].containingFile.name.startsWith("validators"))
    }

    fun testGotoNotOfferedForNonMessageArgument() {
        configure("expression: 'dispute.incorrect_amount.too_big<caret>'")
        val element = myFixture.file.findElementAt(myFixture.caretOffset - 1)

        val targets = ConstraintMessageGotoHandler()
            .getGotoDeclarationTargets(element, myFixture.caretOffset, myFixture.editor)

        assertNull(targets)
    }
}