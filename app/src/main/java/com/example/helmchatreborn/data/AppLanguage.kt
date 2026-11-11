package com.example.helmchatreborn.data

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * App-wide UI language override.
 *
 * On Android 13+ the system per-app language API ([LocaleManager]) is the source of truth, so a
 * choice made in the app also shows up in the system "App languages" settings (and vice versa).
 * On older versions the tag is persisted in [android.content.SharedPreferences] and applied by
 * wrapping the base context of the activity and the foreground service.
 */
object AppLanguage {

    /**
     * Tags of every locale that ships translations, sorted alphabetically by the language name
     * shown in the picker (diacritics ignored, Latin before Greek before Cyrillic).
     */
    val supportedTags: List<String> = listOf(
        "cs", "da", "de", "et", "en", "es", "fr", "it", "lv", "lt",
        "hu", "no", "pl", "sk", "fi", "sv", "el", "bg", "uk",
    )

    /** Language names written in their own language. */
    val nativeNames: Map<String, String> = mapOf(
        "cs" to "Čeština",
        "da" to "Dansk",
        "de" to "Deutsch",
        "et" to "Eesti",
        "en" to "English",
        "es" to "Español",
        "fr" to "Français",
        "it" to "Italiano",
        "lv" to "Latviešu",
        "lt" to "Lietuvių",
        "hu" to "Magyar",
        "no" to "Norsk",
        "pl" to "Polski",
        "sk" to "Slovenčina",
        "fi" to "Suomi",
        "sv" to "Svenska",
        "el" to "Ελληνικά",
        "bg" to "Български",
        "uk" to "Українська",
    )

    /**
     * Flag emoji shown next to each language in the picker. Flags are decorative only — the
     * language name in [nativeNames] is what identifies the choice.
     */
    val nativeFlags: Map<String, String> = mapOf(
        "cs" to "🇨🇿",
        "da" to "🇩🇰",
        "de" to "🇩🇪",
        "et" to "🇪🇪",
        "en" to "🇬🇧",
        "es" to "🇪🇸",
        "fr" to "🇫🇷",
        "it" to "🇮🇹",
        "lv" to "🇱🇻",
        "lt" to "🇱🇹",
        "hu" to "🇭🇺",
        "no" to "🇳🇴",
        "pl" to "🇵🇱",
        "sk" to "🇸🇰",
        "fi" to "🇫🇮",
        "sv" to "🇸🇪",
        "el" to "🇬🇷",
        "bg" to "🇧🇬",
        "uk" to "🇺🇦",
    )

    /** Flag used for the "follow the system language" option. */
    const val SYSTEM_FLAG: String = "🌐"

    private const val PREFS_NAME = "helmchat_language"
    private const val KEY_TAG = "language_tag"

    /** Tag currently in effect, or `null` when the app follows the system language. */
    fun currentTag(context: Context): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val locales = context.getSystemService(LocaleManager::class.java)
                ?.applicationLocales
            return locales?.takeUnless { it.isEmpty }?.toLanguageTags()
        }
        return storedTag(context)
    }

    /** Persists and applies [tag]; `null` or blank restores the system language. */
    fun setTag(context: Context, tag: String?) {
        val normalized = tag?.trim()?.takeIf { it.isNotEmpty() }
        prefs(context).edit().putString(KEY_TAG, normalized.orEmpty()).apply()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val localeManager = context.getSystemService(LocaleManager::class.java) ?: return
            localeManager.applicationLocales = if (normalized == null) {
                LocaleList.getEmptyLocaleList()
            } else {
                LocaleList.forLanguageTags(normalized)
            }
        }
    }

    /**
     * Returns [base] configured with the stored language on Android 12L and lower. Android 13+
     * applies the per-app locale itself, so the context is returned unchanged there.
     */
    fun wrapContext(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        val tag = storedTag(base) ?: return base
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val configuration = Configuration(base.resources.configuration).apply {
            setLocale(locale)
            setLayoutDirection(locale)
        }
        return base.createConfigurationContext(configuration)
    }

    private fun storedTag(context: Context): String? =
        prefs(context).getString(KEY_TAG, null)?.takeIf { it.isNotBlank() }

    private fun prefs(context: Context) = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
