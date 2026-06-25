package com.eonx.eeh.translationnav

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Placeholder completion for setMessageParams / setUserMessageParams, and the "missing
 * placeholder" annotator on the message keys.
 */
class ExceptionMessageParamsTest : BasePlatformTestCase() {

    private val messages = """
        <?php
        return [
            'exceptions' => [
                'foo' => 'Failed for the [{customerBankAccountId}] and {amount}.',
            ],
            'user_messages' => [
                'foo' => 'Issue {ticketId}.',
            ],
        ];
    """.trimIndent()

    private fun source(body: String): String {
        myFixture.addFileToProject("translations/messages+intl-icu.en.php", messages)
        return """
            <?php
            namespace App\Ex;
            class E {
                public static function make(): self {
                    $body
                }
            }
        """.trimIndent()
    }

    private fun completions(body: String): List<String> {
        myFixture.configureByText("E.php", source(body))
        myFixture.completeBasic()
        return myFixture.lookupElementStrings ?: emptyList()
    }

    private fun warnings(body: String): List<String> {
        myFixture.configureByText("E.php", source(body))
        return myFixture.doHighlighting(HighlightSeverity.WARNING).mapNotNull { it.description }
    }

    fun testSetMessageParamsCompletesConstructorPlaceholders() {
        val result = completions(
            "\$e = new self('exceptions.foo', 1); \$e->setMessageParams(['<caret>']);",
        )

        assertContainsElements(result, "customerBankAccountId", "amount")
    }

    fun testSetUserMessageParamsCompletesUserMessagePlaceholders() {
        val result = completions(
            "\$e = new self('exceptions.foo', 1); \$e->setUserMessage('user_messages.foo'); " +
                "\$e->setUserMessageParams(['<caret>']);",
        )

        assertContainsElements(result, "ticketId")
        assertDoesntContain(result, "customerBankAccountId") // those belong to the exception message
    }

    fun testMissingMessageParamPlaceholderIsFlagged() {
        val result = warnings(
            "\$e = new self('exceptions.foo', 1); \$e->setMessageParams(['customerBankAccountId' => 1]);",
        )

        assertTrue(result.toString(), result.any { it.contains("setMessageParams") && it.contains("amount") })
    }

    fun testAllParamsPresentIsNotFlagged() {
        val result = warnings(
            "\$e = new self('exceptions.foo', 1); " +
                "\$e->setMessageParams(['customerBankAccountId' => 1, 'amount' => 2]);",
        )

        assertFalse(result.toString(), result.any { it.contains("setMessageParams") })
    }

    fun testMissingUserMessageParamIsFlaggedWhenNoParamsCall() {
        val result = warnings(
            "\$e = new self('exceptions.foo', 1); \$e->setUserMessage('user_messages.foo');",
        )

        assertTrue(result.toString(), result.any { it.contains("setUserMessageParams") && it.contains("ticketId") })
    }
}