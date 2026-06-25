package com.eonx.eeh.translationnav

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Regression: dedup must work when a new key is typed at the start of the array, where the
 * missing comma transiently breaks the PSI parse of the trailing entries.
 */
class BeginningInsertDedupTest : BasePlatformTestCase() {

    private val support = """
        <?php
        namespace Zenstruck\Foundry\Persistence {
            abstract class PersistentObjectFactory {
                public static function new(array|callable ${'$'}a = []): static {}
                abstract public static function class(): string;
            }
        }
        namespace Test\Factory {
            abstract class AbstractFactory extends \Zenstruck\Foundry\Persistence\PersistentObjectFactory {}
        }
        namespace App\Dispute {
            class Dispute { protected string ${'$'}status; protected ?string ${'$'}reason = null; protected ${'$'}customer; }
        }
    """.trimIndent()

    fun testDedupWhenInsertingAtBeginning() {
        myFixture.addFileToProject("support.php", support)
        val factory = """
            <?php
            namespace Test\Factory\Dispute;
            use Test\Factory\AbstractFactory;
            class F extends AbstractFactory {
                public static function class(): string { return \App\Dispute\Dispute::class; }
                protected function defaults(): array {
                    return [
                        '<caret>'
                        'status' => 'open',
                        'reason' => null,
                    ];
                }
            }
        """.trimIndent()
        myFixture.configureByText("F.php", factory)
        myFixture.completeBasic()
        val result = myFixture.lookupElementStrings ?: emptyList()

        assertContainsElements(result, "customer") // the only not-yet-present property
        assertDoesntContain(result, "status", "reason") // already present, even without a trailing comma
    }
}