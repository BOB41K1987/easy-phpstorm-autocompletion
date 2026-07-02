package com.eonx.eeh.translationnav

import com.intellij.navigation.ItemPresentation
import com.intellij.navigation.NavigationItem
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Reverse Go to Declaration: from a key defined in `translations/validators*.php` /
 * `translations/messages*.php` to every place that references it — constraint attribute
 * `message` arguments, exception constructor/`parent::__construct` calls, and `setUserMessage`.
 */
class TranslationKeyUsagesTest : BasePlatformTestCase() {

    private val support = """
        <?php
        namespace Symfony\Component\Validator { class Constraint {} }
        namespace Symfony\Component\Validator\Constraints {
            class Expression extends \Symfony\Component\Validator\Constraint {
                public function __construct(public ?string ${'$'}expression = null, public ?string ${'$'}message = null) {}
            }
            class When extends \Symfony\Component\Validator\Constraint {
                public function __construct(public ?string ${'$'}expression = null, public ?array ${'$'}constraints = null) {}
            }
            class Sequentially extends \Symfony\Component\Validator\Constraint {
                public function __construct(public ?array ${'$'}constraints = null) {}
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
        assertEquals("expected exactly one target, got duplicates", 1, targets!!.size)
        assertTrue(targets[0].containingFile.name == "Dto.php")

        // Platform utilities (hover, quick-doc, popup rendering) gate on this before using
        // getPresentation() at all — without it they fall back to a generic element dump.
        assertTrue(targets[0] is NavigationItem)

        val presentation = targets[0] as ItemPresentation
        assertEquals("Dto.php:5", presentation.presentableText)
        assertEquals("src", presentation.locationString)
        assertSame(PluginIcons.EONX, presentation.getIcon(false))

        // Quick Documentation / Ctrl+hover resolve through these before rendering; if they stay
        // null (the FakePsiElement default) the IDE falls back to a raw debug dump of this class.
        assertEquals("'dispute.incorrect_amount.too_big'", targets[0].text)
        assertNotNull(targets[0].textRange)
        assertTrue(targets[0].navigationElement.text.contains("dispute.incorrect_amount.too_big"))
        assertTrue(targets[0].navigationElement !is TranslationKeyUsageTarget)
    }

    fun testGotoDoesNotDuplicateAcrossMultipleUsages() {
        myFixture.addFileToProject("support.php", support)
        myFixture.addFileToProject(
            "src/DtoOne.php",
            """
                <?php
                namespace App\Dto;
                use Symfony\Component\Validator\Constraints as Assert;
                class DtoOne {
                    #[Assert\Expression(message: 'dispute.incorrect_amount.too_big')]
                    public int ${'$'}amount = 0;
                }
            """.trimIndent(),
        )
        myFixture.addFileToProject(
            "src/DtoTwo.php",
            """
                <?php
                namespace App\Dto;
                use Symfony\Component\Validator\Constraints as Assert;
                class DtoTwo {
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

        assertNotNull(targets)
        assertEquals(
            "expected one target per usage file, not duplicated",
            listOf("DtoOne.php", "DtoTwo.php"),
            targets!!.map { it.containingFile.name },
        )
    }

    fun testGotoNavigatesToUsageInsideSequentiallyConstraint() {
        myFixture.addFileToProject("support.php", support)
        myFixture.addFileToProject(
            "src/Dto.php",
            """
                <?php
                namespace App\Dto;
                use Symfony\Component\Validator\Constraints as Assert;
                class Dto {
                    #[Assert\When(
                        expression: 'value !== null',
                        constraints: [
                            new Assert\Sequentially(
                                constraints: [
                                    new Assert\Expression(
                                        expression: 'value.isActive()',
                                        message: 'recurring_payment.creation.payment_method.not_active',
                                    ),
                                ],
                            ),
                        ],
                    )]
                    public ?int ${'$'}amount = null;
                }
            """.trimIndent(),
        )

        val translations = myFixture.addFileToProject(
            "translations/validators+intl-icu.en.php",
            """
                <?php
                return [
                    'recurring_payment' => [
                        'creation' => [
                            'payment_method' => [
                                'not_active' => 'This payment method must be active.',
                            ],
                        ],
                    ],
                ];
            """.trimIndent(),
        )
        myFixture.configureFromExistingVirtualFile(translations.virtualFile)
        myFixture.editor.caretModel.moveToOffset(translations.text.indexOf("not_active") + "not_active".length)

        val element = myFixture.file.findElementAt(myFixture.caretOffset - 1)
        val targets = TranslationKeyUsagesGotoHandler()
            .getGotoDeclarationTargets(element, myFixture.caretOffset, myFixture.editor)

        assertNotNull("expected a goto target", targets)
        assertEquals(1, targets!!.size)
        assertEquals("Dto.php", targets[0].containingFile.name)
    }

    fun testGotoNavigatesFromMessagesKeyToExceptionConstructor() {
        myFixture.addFileToProject(
            "src/SomeException.php",
            """
                <?php
                namespace App\Exception;
                class SomeException extends \Exception {}
            """.trimIndent(),
        )
        myFixture.addFileToProject(
            "src/Service.php",
            """
                <?php
                namespace App\Service;
                use App\Exception\SomeException;
                class Service {
                    public function run(): void {
                        throw new SomeException('exceptions.foo');
                    }
                }
            """.trimIndent(),
        )

        val translations = myFixture.addFileToProject(
            "translations/messages+intl-icu.en.php",
            """
                <?php
                return [
                    'exceptions' => [
                        'foo' => 'Something went wrong.',
                    ],
                ];
            """.trimIndent(),
        )
        myFixture.configureFromExistingVirtualFile(translations.virtualFile)
        myFixture.editor.caretModel.moveToOffset(translations.text.indexOf("foo") + "foo".length)

        val element = myFixture.file.findElementAt(myFixture.caretOffset - 1)
        val targets = TranslationKeyUsagesGotoHandler()
            .getGotoDeclarationTargets(element, myFixture.caretOffset, myFixture.editor)

        assertNotNull("expected a goto target", targets)
        assertEquals(1, targets!!.size)
        assertEquals("Service.php", targets[0].containingFile.name)
    }

    fun testGotoNavigatesFromMessagesKeyToParentConstructCall() {
        myFixture.addFileToProject(
            "src/BaseException.php",
            """
                <?php
                namespace App\Exception;
                class BaseException extends \Exception {}
            """.trimIndent(),
        )
        myFixture.addFileToProject(
            "src/SomeException.php",
            """
                <?php
                namespace App\Exception;
                class SomeException extends BaseException {
                    public function __construct() {
                        parent::__construct('exceptions.baz');
                    }
                }
            """.trimIndent(),
        )

        val translations = myFixture.addFileToProject(
            "translations/messages+intl-icu.en.php",
            """
                <?php
                return [
                    'exceptions' => [
                        'baz' => 'Something went wrong.',
                    ],
                ];
            """.trimIndent(),
        )
        myFixture.configureFromExistingVirtualFile(translations.virtualFile)
        myFixture.editor.caretModel.moveToOffset(translations.text.indexOf("baz") + "baz".length)

        val element = myFixture.file.findElementAt(myFixture.caretOffset - 1)
        val targets = TranslationKeyUsagesGotoHandler()
            .getGotoDeclarationTargets(element, myFixture.caretOffset, myFixture.editor)

        assertNotNull("expected a goto target", targets)
        assertEquals(1, targets!!.size)
        assertEquals("SomeException.php", targets[0].containingFile.name)
    }

    fun testGotoNavigatesFromMessagesKeyToSetUserMessageCall() {
        myFixture.addFileToProject(
            "src/SomeException.php",
            """
                <?php
                namespace App\Exception;
                class SomeException extends \Exception {
                    public function setUserMessage(string ${'$'}key): static { return ${'$'}this; }
                }
            """.trimIndent(),
        )
        myFixture.addFileToProject(
            "src/Service.php",
            """
                <?php
                namespace App\Service;
                use App\Exception\SomeException;
                class Service {
                    public function run(): void {
                        throw (new SomeException())->setUserMessage('user_messages.bar');
                    }
                }
            """.trimIndent(),
        )

        val translations = myFixture.addFileToProject(
            "translations/messages+intl-icu.en.php",
            """
                <?php
                return [
                    'user_messages' => [
                        'bar' => 'Please try again.',
                    ],
                ];
            """.trimIndent(),
        )
        myFixture.configureFromExistingVirtualFile(translations.virtualFile)
        myFixture.editor.caretModel.moveToOffset(translations.text.indexOf("bar") + "bar".length)

        val element = myFixture.file.findElementAt(myFixture.caretOffset - 1)
        val targets = TranslationKeyUsagesGotoHandler()
            .getGotoDeclarationTargets(element, myFixture.caretOffset, myFixture.editor)

        assertNotNull("expected a goto target", targets)
        assertEquals(1, targets!!.size)
        assertEquals("Service.php", targets[0].containingFile.name)
    }

    fun testGotoNavigatesFromMessagesKeyToTranslatorTransCall() {
        myFixture.addFileToProject(
            "src/Service.php",
            """
                <?php
                namespace App\Service;
                use Symfony\Contracts\Translation\TranslatorInterface;
                class Service {
                    public function __construct(private TranslatorInterface ${'$'}translator) {}
                    public function run(): string {
                        return ${'$'}this->translator->trans('event_log.merchant_created.title');
                    }
                }
            """.trimIndent(),
        )

        val translations = myFixture.addFileToProject(
            "translations/messages+intl-icu.en.php",
            """
                <?php
                return [
                    'event_log' => [
                        'merchant_created' => [
                            'title' => 'Merchant created',
                        ],
                    ],
                ];
            """.trimIndent(),
        )
        myFixture.configureFromExistingVirtualFile(translations.virtualFile)
        myFixture.editor.caretModel.moveToOffset(translations.text.indexOf("title") + "title".length)

        val element = myFixture.file.findElementAt(myFixture.caretOffset - 1)
        val targets = TranslationKeyUsagesGotoHandler()
            .getGotoDeclarationTargets(element, myFixture.caretOffset, myFixture.editor)

        assertNotNull("expected a goto target", targets)
        assertEquals(1, targets!!.size)
        assertEquals("Service.php", targets[0].containingFile.name)
    }

    fun testGotoNavigatesFromMessagesKeyToClassConstant() {
        myFixture.addFileToProject(
            "src/EmailBuilder.php",
            """
                <?php
                namespace App\Mailer;
                class EmailBuilder {
                    private const string SUBJECT = 'mailer.reseller_admin.refund_payout_declined';
                }
            """.trimIndent(),
        )

        val translations = myFixture.addFileToProject(
            "translations/messages+intl-icu.en.php",
            """
                <?php
                return [
                    'mailer' => [
                        'reseller_admin' => [
                            'refund_payout_declined' => 'Refund payout declined',
                        ],
                    ],
                ];
            """.trimIndent(),
        )
        myFixture.configureFromExistingVirtualFile(translations.virtualFile)
        myFixture.editor.caretModel.moveToOffset(
            translations.text.indexOf("refund_payout_declined") + "refund_payout_declined".length,
        )

        val element = myFixture.file.findElementAt(myFixture.caretOffset - 1)
        val targets = TranslationKeyUsagesGotoHandler()
            .getGotoDeclarationTargets(element, myFixture.caretOffset, myFixture.editor)

        assertNotNull("expected a goto target", targets)
        assertEquals(1, targets!!.size)
        assertEquals("EmailBuilder.php", targets[0].containingFile.name)
    }

    fun testGotoNavigatesFromMessagesKeyToBuildViolationCall() {
        myFixture.addFileToProject(
            "src/DisputeValidator.php",
            """
                <?php
                namespace App\Validator;
                class DisputeValidator {
                    public function validate(${'$'}context): void {
                        ${'$'}context->buildViolation('dispute.description_cannot_be_decoded')->addViolation();
                    }
                }
            """.trimIndent(),
        )

        val translations = myFixture.addFileToProject(
            "translations/messages+intl-icu.en.php",
            """
                <?php
                return [
                    'dispute' => [
                        'description_cannot_be_decoded' => 'The value cannot be decoded.',
                    ],
                ];
            """.trimIndent(),
        )
        myFixture.configureFromExistingVirtualFile(translations.virtualFile)
        myFixture.editor.caretModel.moveToOffset(
            translations.text.indexOf("description_cannot_be_decoded") + "description_cannot_be_decoded".length,
        )

        val element = myFixture.file.findElementAt(myFixture.caretOffset - 1)
        val targets = TranslationKeyUsagesGotoHandler()
            .getGotoDeclarationTargets(element, myFixture.caretOffset, myFixture.editor)

        assertNotNull("expected a goto target", targets)
        assertEquals(1, targets!!.size)
        assertEquals("DisputeValidator.php", targets[0].containingFile.name)
    }

    fun testGotoDoesNotTreatOwnDefinitionAsUsage() {
        val translations = myFixture.addFileToProject(
            "translations/messages+intl-icu.en.php",
            """
                <?php
                return [
                    'dispute' => [
                        'description_cannot_be_decoded' => 'The value cannot be decoded.',
                    ],
                ];
            """.trimIndent(),
        )
        myFixture.configureFromExistingVirtualFile(translations.virtualFile)
        myFixture.editor.caretModel.moveToOffset(
            translations.text.indexOf("description_cannot_be_decoded") + "description_cannot_be_decoded".length,
        )

        val element = myFixture.file.findElementAt(myFixture.caretOffset - 1)
        val targets = TranslationKeyUsagesGotoHandler()
            .getGotoDeclarationTargets(element, myFixture.caretOffset, myFixture.editor)

        assertNull(targets)
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