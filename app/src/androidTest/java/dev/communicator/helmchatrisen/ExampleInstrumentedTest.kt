package dev.communicator.helmchatrisen

import androidx.appcompat.widget.AppCompatTextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented smoke checks for the application context and its host theme.
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun useAppContext() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("dev.communicator.helmchatrisen", appContext.packageName)
    }

    /**
     * Jitsi 13.1.1 starts its bridgeless ReactHost during Application startup. React Native creates
     * AppCompat widgets and aborts the process if the host theme does not descend from
     * Theme.AppCompat, so constructing one with the application context guards the launch path.
     */
    @Test
    fun appTheme_supportsJitsiReactHost() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        AppCompatTextView(appContext)
    }
}
