package dev.communicator.helmchatrisen.ui.call

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.communicator.helmchatrisen.R
import dev.communicator.helmchatrisen.ui.theme.HelmChatRisenTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies that the call timer is exposed to assistive technology as a single labeled value.
 */
@RunWith(AndroidJUnit4::class)
class CallDurationDisplayTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    /** The localized label and formatted duration must be merged into one semantics node. */
    @Test
    fun labelAndValue_shareOneSemanticsNode() {
        val label = InstrumentationRegistry.getInstrumentation()
            .targetContext
            .getString(R.string.call_duration)

        composeTestRule.setContent {
            HelmChatRisenTheme {
                CallDurationDisplay(elapsedMillis = 83_000L)
            }
        }

        composeTestRule.onNode(
            hasText(label, substring = true) and hasText("01:23", substring = true),
        ).assertIsDisplayed()
        composeTestRule.onAllNodes(hasText(label, substring = true)).assertCountEquals(1)
    }
}
