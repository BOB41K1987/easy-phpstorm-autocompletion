package com.eonx.eeh.translationnav

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Headless functional tests for EntityExpectation `$criteria` key completion against the real PHP PSI.
 */
class EntityCriteriaCompletionTest : BasePlatformTestCase() {

    private val support = """
        <?php
        namespace Test\Util\Expectation {
            class EntityExpectation {
                public function toBeInDb(array ${'$'}criteria, ?array ${'$'}json = null, ?array ${'$'}enc = null): self {}
                public function toNotBeInDb(array ${'$'}criteria, ?array ${'$'}json = null): self {}
                public function toHaveCountInDb(int ${'$'}count, ?array ${'$'}criteria = null): self {}
            }
        }
        namespace Test\Util {
            trait DatabaseEntityHelperTrait {
                public function assertEntity(string ${'$'}className): \Test\Util\Expectation\EntityExpectation {}
            }
        }
        namespace App\EventLog {
            class EventLog {
                protected string ${'$'}type;
                protected array ${'$'}payload;
                protected ${'$'}occurredAt;
            }
        }
    """.trimIndent()

    private fun completionsFor(body: String): List<String> {
        myFixture.addFileToProject("support.php", support)
        val source = """
            <?php
            namespace App\Test;
            class T {
                use \Test\Util\DatabaseEntityHelperTrait;
                public function t(): void {
                    $body
                }
            }
        """.trimIndent()
        myFixture.configureByText("test_case.php", source)
        myFixture.completeBasic()
        return myFixture.lookupElementStrings ?: emptyList()
    }

    fun testToBeInDbCriteriaKeys() {
        val result = completionsFor(
            "\$this->assertEntity(\\App\\EventLog\\EventLog::class)->toHaveCountInDb(1)->toBeInDb(['<caret>']);",
        )

        assertContainsElements(result, "type", "payload", "occurredAt")
    }

    fun testToHaveCountInDbSecondArgCriteriaKeys() {
        val result = completionsFor(
            "\$this->assertEntity(\\App\\EventLog\\EventLog::class)->toHaveCountInDb(1, ['<caret>']);",
        )

        assertContainsElements(result, "type", "payload", "occurredAt")
    }

    fun testNonCriteriaArrayArgIsNotCompletedWithEntityProperties() {
        // The 2nd arg of toBeInDb is the jsonAttributes list, not the criteria — no entity-property keys there.
        val result = completionsFor(
            "\$this->assertEntity(\\App\\EventLog\\EventLog::class)->toBeInDb(['type' => 'x'], ['<caret>']);",
        )

        assertDoesntContain(result, "occurredAt") // not a criteria key here
    }

    fun testJsonAttributesListValuesScopedToCriteriaKeys() {
        val result = completionsFor(
            "\$this->assertEntity(\\App\\EventLog\\EventLog::class)" +
                "->toBeInDb(['type' => 'x', 'payload' => []], ['<caret>']);",
        )

        assertContainsElements(result, "type", "payload") // only the keys present in the criteria array
        assertDoesntContain(result, "occurredAt") // a real property, but not used in this criteria
    }

    fun testEncryptableListExcludesAlreadyListedValue() {
        val result = completionsFor(
            "\$this->assertEntity(\\App\\EventLog\\EventLog::class)" +
                "->toBeInDb(['type' => 'x', 'payload' => []], ['payload'], ['<caret>']);",
        )

        assertContainsElements(result, "type", "payload") // 3rd arg (encryptableAttributes) → criteria keys
    }
}