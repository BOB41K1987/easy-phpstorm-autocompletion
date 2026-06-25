package com.eonx.eeh.translationnav

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Criteria-key completion for DatabaseEntityTrait-style helpers that take the entity class as
 * their first argument (assertEntityExists, getEntity, assertEntityCount, …).
 */
class RepositoryCriteriaCompletionTest : BasePlatformTestCase() {

    private val support = """
        <?php
        namespace App\Common\Entity {
            class Foo {
                protected string ${'$'}action;
                protected ?string ${'$'}subjectType = null;
                protected ${'$'}payload;
            }
        }
        namespace Test\Util {
            trait DatabaseEntityTrait {
                protected static function assertEntityExists(string ${'$'}e, array ${'$'}c, ?array ${'$'}j = null): void {}
                protected static function getEntity(string ${'$'}e, array ${'$'}c, ?array ${'$'}j = null) {}
                protected static function assertEntityCount(string ${'$'}e, int ${'$'}n, ?array ${'$'}c = null): void {}
            }
        }
    """.trimIndent()

    private fun completionsFor(body: String): List<String> {
        myFixture.addFileToProject("support.php", support)
        val source = """
            <?php
            namespace App\Test;
            class T {
                use \Test\Util\DatabaseEntityTrait;
                public function t(): void {
                    $body
                }
            }
        """.trimIndent()
        myFixture.configureByText("T.php", source)
        myFixture.completeBasic()
        return myFixture.lookupElementStrings ?: emptyList()
    }

    fun testAssertEntityExistsCriteriaKeys() {
        val result = completionsFor("self::assertEntityExists(\\App\\Common\\Entity\\Foo::class, ['<caret>']);")

        assertContainsElements(result, "action", "subjectType", "payload")
    }

    fun testGetEntityCriteriaKeys() {
        val result = completionsFor("\$x = self::getEntity(\\App\\Common\\Entity\\Foo::class, ['<caret>']);")

        assertContainsElements(result, "action", "subjectType")
    }

    fun testAssertEntityCountCriteriaIsThirdArg() {
        val result = completionsFor("self::assertEntityCount(\\App\\Common\\Entity\\Foo::class, 1, ['<caret>']);")

        assertContainsElements(result, "action", "subjectType")
    }

    fun testJsonAttributesArgIsNotCompletedAsCriteria() {
        // 3rd arg of assertEntityExists is the jsonAttributes list, not criteria.
        val result = completionsFor(
            "self::assertEntityExists(\\App\\Common\\Entity\\Foo::class, ['action' => 'x'], ['<caret>']);",
        )

        assertDoesntContain(result, "subjectType", "payload")
    }

    fun testJsonAttributesListValuesScopedToCriteriaKeys() {
        val result = completionsFor(
            "self::assertEntityExists(\\App\\Common\\Entity\\Foo::class, " +
                "['action' => 'x', 'payload' => []], ['<caret>']);",
        )

        assertContainsElements(result, "action", "payload") // only the keys present in the criteria array
        assertDoesntContain(result, "subjectType") // a real property, but not used in this criteria
    }
}