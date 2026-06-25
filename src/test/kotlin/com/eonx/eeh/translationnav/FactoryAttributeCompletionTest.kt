package com.eonx.eeh.translationnav

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Headless functional tests for factory attribute-key completion against the real PHP PSI.
 * A self-contained PHP fixture defines the Foundry base, a factory and its entity.
 */
class FactoryAttributeCompletionTest : BasePlatformTestCase() {

    private val support = """
        <?php
        namespace Zenstruck\Foundry\Persistence {
            abstract class PersistentObjectFactory {
                public static function new(array|callable ${'$'}attributes = []): static {}
                public function with(array|callable ${'$'}attributes = []): static {}
                public function create(array|callable ${'$'}attributes = []) {}
                abstract public static function class(): string;
            }
        }
        namespace Test\Factory {
            abstract class AbstractFactory extends \Zenstruck\Foundry\Persistence\PersistentObjectFactory {
                public function createEntity(?array ${'$'}attributes = null) {}
            }
        }
        namespace App\Dispute {
            class Dispute {
                protected string ${'$'}status;
                protected ?string ${'$'}reason = null;
                protected ${'$'}customer;
                const FOO = 'foo';
            }
        }
        namespace Test\Factory\Dispute {
            class DisputeFactory extends \Test\Factory\AbstractFactory {
                public function withCustomer(${'$'}c): self {}
                public static function class(): string { return \App\Dispute\Dispute::class; }
            }
        }
    """.trimIndent()

    private fun completionsFor(call: String): List<String> {
        myFixture.addFileToProject("support.php", support)
        val source = """
            <?php
            namespace App\Test;
            class T {
                public function t(): void {
                    $call
                }
            }
        """.trimIndent()
        myFixture.configureByText("test_case.php", source)
        myFixture.completeBasic()
        return myFixture.lookupElementStrings ?: emptyList()
    }

    fun testStaticNewCall() {
        val result = completionsFor("\\Test\\Factory\\Dispute\\DisputeFactory::new(['<caret>']);")

        assertContainsElements(result, "status", "reason", "customer")
        assertDoesntContain(result, "FOO") // constants are not settable attributes
    }

    fun testChainedCreateEntityCall() {
        val result = completionsFor(
            "\\Test\\Factory\\Dispute\\DisputeFactory::new()->withCustomer(null)->createEntity(['<caret>']);",
        )

        assertContainsElements(result, "status", "reason", "customer")
    }

    fun testSecondKeyCompletes() {
        val result = completionsFor(
            "\\Test\\Factory\\Dispute\\DisputeFactory::new(['status' => 'x', '<caret>']);",
        )

        assertContainsElements(result, "reason", "customer")
        assertDoesntContain(result, "status") // already used → not offered again
    }

    fun testValuePositionIsNotCompleted() {
        val result = completionsFor("\\Test\\Factory\\Dispute\\DisputeFactory::new(['status' => '<caret>']);")

        assertDoesntContain(result, "reason")
    }

    fun testDefaultsMethodReturnArrayKeys() {
        myFixture.addFileToProject("support.php", support)
        val factory = """
            <?php
            namespace Test\Factory\Dispute;
            use Test\Factory\AbstractFactory;
            class MyDisputeFactory extends AbstractFactory {
                public static function class(): string { return \App\Dispute\Dispute::class; }
                protected function defaults(): array {
                    return ['<caret>'];
                }
            }
        """.trimIndent()
        myFixture.configureByText("MyDisputeFactory.php", factory)
        myFixture.completeBasic()
        val result = myFixture.lookupElementStrings ?: emptyList()

        assertContainsElements(result, "status", "reason", "customer")
    }
}