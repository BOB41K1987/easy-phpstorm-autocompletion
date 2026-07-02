package com.eonx.eeh.translationnav

import com.intellij.navigation.ItemPresentation
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Reverse Go to Declaration: from a key defined in `translations/validators*.php` to every
 * constraint attribute `message` argument that references it.
 */
class TranslationKeyUsagesTest : BasePlatformTestCase() {

    private val support = """
        <?php
        namespace Symfony\Component\Validator { class Constraint {} }
        namespace Symfony\Component\Validator\Constraints {
            class Expression extends \Symfony\Component\Validator\Constraint {
                public function __construct(public ?string ${'$'}expression = null, public ?string ${'$'}message = null) {}
            }
        }
    """.trimIndent()

    fun testGotoNavigatesToUsage() {
        myFixture.addFileToProject("support.php", support)
        myFixture.addFileToProject(
            "src/Dto.php",
            """
                <?php
                namespace App\Dto;
                use Symfony\Component\Validator\Constraints as Assert;
                class Dto {
                    #[Assert\Expression(message: 'dispute.incorrect_amount.too_big')]
                    public int ${'$'}amount = 0;
                }
            """.trimIndent(),
        )

        val translations = myFixture.addFileToProject(
            "translations/validators+intl-icu.en.php",
            """
                <?php
                return [
                    'dispute' => [
                        'incorrect_amount' => [
                            'too_big' => 'Amount is too big.',
                        ],
                    ],
                ];
            """.trimIndent(),
        )
        myFixture.configureFromExistingVirtualFile(translations.virtualFile)
        myFixture.editor.caretModel.moveToOffset(translations.text.indexOf("too_big") + "too_big".length)

        val element = myFixture.file.findElementAt(myFixture.caretOffset - 1)
        val targets = TranslationKeyUsagesGotoHandler()
            .getGotoDeclarationTargets(element, myFixture.caretOffset, myFixture.editor)

        assertNotNull("expected a goto target", targets)
        assertTrue(targets!!.isNotEmpty())
        assertTrue(targets[0].containingFile.name == "Dto.php")

        val presentation = (targets[0] as ItemPresentation).presentableText
        assertEquals("Dto.php:5", presentation)
    }

    fun testGotoNotOfferedForUnusedKey() {
        val translations = myFixture.addFileToProject(
            "translations/validators+intl-icu.en.php",
            """
                <?php
                return [
                    'dispute' => [
                        'incorrect_amount' => [
                            'too_big' => 'Amount is too big.',
                        ],
                    ],
                ];
            """.trimIndent(),
        )
        myFixture.configureFromExistingVirtualFile(translations.virtualFile)
        myFixture.editor.caretModel.moveToOffset(translations.text.indexOf("too_big") + "too_big".length)

        val element = myFixture.file.findElementAt(myFixture.caretOffset - 1)
        val targets = TranslationKeyUsagesGotoHandler()
            .getGotoDeclarationTargets(element, myFixture.caretOffset, myFixture.editor)

        assertNull(targets)
    }
}