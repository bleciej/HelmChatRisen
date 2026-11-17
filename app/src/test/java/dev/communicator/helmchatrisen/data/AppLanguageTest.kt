package dev.communicator.helmchatrisen.data

import java.text.Collator
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class AppLanguageTest {

    @Test
    fun supportedTags_haveNativeNameForEveryTag() {
        assertEquals(AppLanguage.nativeNames.keys, AppLanguage.supportedTags.toSet())
    }

    @Test
    fun supportedTags_haveFlagForEveryTag() {
        assertEquals(AppLanguage.nativeFlags.keys, AppLanguage.supportedTags.toSet())
        assertEquals(
            AppLanguage.nativeFlags.size,
            AppLanguage.nativeFlags.values.toSet().size,
        )
    }

    @Test
    fun supportedTags_areSortedAlphabeticallyByNativeName() {
        val collator = Collator.getInstance(Locale.ENGLISH)
        val expected = AppLanguage.nativeNames.entries
            .sortedWith(compareBy(collator) { it.value })
            .map { it.key }

        assertEquals(expected, AppLanguage.supportedTags)
    }
}
