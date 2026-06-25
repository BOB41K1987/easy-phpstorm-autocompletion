package com.eonx.eeh.translationnav

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Headless tests for the JSONB criteria/jsonAttributes consistency annotator.
 */
class JsonbAnnotatorTest : BasePlatformTestCase() {

    private val support = """
        <?php
        namespace Doctrine\ORM\Mapping {
            class Column { public function __construct(mixed ${'$'}type = null, bool ${'$'}nullable = false) {} }
        }
        namespace EonX\EasyDoctrine\Common\Type {
            class JsonbType { const NAME = 'jsonb'; }
        }
        namespace Test\Util\Expectation {
            class EntityExpectation {
                public function toBeInDb(array ${'$'}criteria, ?array ${'$'}json = null, ?array ${'$'}enc = null): self {}
            }
        }
        namespace Test\Util {
            trait DatabaseEntityHelperTrait {
                public function assertEntity(string ${'$'}className): \Test\Util\Expectation\EntityExpectation {}
            }
        }
        namespace App\EventLog {
            use Doctrine\ORM\Mapping as ORM;
            use EonX\EasyDoctrine\Common\Type\JsonbType;
            class EventLog {
                #[ORM\Column(type: JsonbType::NAME)]
                protected array ${'$'}payload;
                #[ORM\Column(type: 'string')]
                protected string ${'$'}type;
            }
        }
    """.trimIndent()

    private fun warningsFor(body: String): List<String> {
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
        return myFixture.doHighlighting(HighlightSeverity.WARNING).mapNotNull { it.description }
    }

    fun testJsonbCriteriaKeyMissingFromJsonAttributesIsFlagged() {
        val warnings = warningsFor(
            "\$this->assertEntity(\\App\\EventLog\\EventLog::class)->toBeInDb(['payload' => []]);",
        )

        assertTrue(warnings.toString(), warnings.any { it.contains("JSONB property 'payload'") })
    }

    fun testJsonbCriteriaKeyListedIsNotFlagged() {
        val warnings = warningsFor(
            "\$this->assertEntity(\\App\\EventLog\\EventLog::class)->toBeInDb(['payload' => []], ['payload']);",
        )

        assertFalse(warnings.toString(), warnings.any { it.contains("JSONB property 'payload'") })
    }

    fun testJsonAttributeNotInCriteriaIsFlagged() {
        val warnings = warningsFor(
            "\$this->assertEntity(\\App\\EventLog\\EventLog::class)->toBeInDb(['payload' => []], ['payload', 'type']);",
        )

        assertTrue(warnings.toString(), warnings.any { it.contains("'type'") && it.contains("not used in") })
    }

    fun testNonJsonbCriteriaKeyIsNotFlagged() {
        val warnings = warningsFor(
            "\$this->assertEntity(\\App\\EventLog\\EventLog::class)->toBeInDb(['type' => 'x']);",
        )

        assertFalse(warnings.toString(), warnings.any { it.contains("JSONB property 'type'") })
    }
}
