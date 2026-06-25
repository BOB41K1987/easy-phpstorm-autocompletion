package com.eonx.eeh.translationnav

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.php.lang.psi.elements.Field

/**
 * Go to Declaration from an attribute/criteria array key to the entity field it names.
 */
class EntityFieldGotoTest : BasePlatformTestCase() {

    private val support = """
        <?php
        namespace Zenstruck\Foundry\Persistence {
            abstract class PersistentObjectFactory {
                public static function new(array|callable ${'$'}a = []): static {}
                public function createEntity(?array ${'$'}a = null) {}
                abstract public static function class(): string;
            }
        }
        namespace Test\Factory {
            abstract class AbstractFactory extends \Zenstruck\Foundry\Persistence\PersistentObjectFactory {}
        }
        namespace App\Dispute {
            class Dispute { protected string ${'$'}status; protected ?string ${'$'}reason = null; }
        }
        namespace Test\Factory\Dispute {
            class DisputeFactory extends \Test\Factory\AbstractFactory {
                public static function class(): string { return \App\Dispute\Dispute::class; }
            }
        }
        namespace Test\Util {
            trait DatabaseEntityTrait {
                protected static function assertEntityExists(string ${'$'}e, array ${'$'}c, ?array ${'$'}j = null): void {}
            }
        }
        namespace App\Common\Entity {
            class Foo { protected string ${'$'}action; }
        }
    """.trimIndent()

    private fun gotoTargetField(body: String): Field? {
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
        val element = myFixture.file.findElementAt(myFixture.caretOffset)
        val targets = EntityFieldGotoHandler()
            .getGotoDeclarationTargets(element, myFixture.caretOffset, myFixture.editor)
        return targets?.firstOrNull() as? Field
    }

    fun testGotoFromFactoryAttributeKey() {
        val field = gotoTargetField(
            "\\Test\\Factory\\Dispute\\DisputeFactory::new()->createEntity(['sta<caret>tus' => null]);",
        )

        assertNotNull("expected a field target", field)
        assertEquals("status", field!!.name)
        assertEquals("\\App\\Dispute\\Dispute", field.containingClass?.fqn)
    }

    fun testGotoFromRepositoryCriteriaKey() {
        val field = gotoTargetField(
            "self::assertEntityExists(\\App\\Common\\Entity\\Foo::class, ['act<caret>ion' => 'x']);",
        )

        assertNotNull("expected a field target", field)
        assertEquals("action", field!!.name)
        assertEquals("\\App\\Common\\Entity\\Foo", field.containingClass?.fqn)
    }

    fun testNoGotoForUnknownKey() {
        val field = gotoTargetField(
            "\\Test\\Factory\\Dispute\\DisputeFactory::new()->createEntity(['nope<caret>' => null]);",
        )

        assertNull(field)
    }
}