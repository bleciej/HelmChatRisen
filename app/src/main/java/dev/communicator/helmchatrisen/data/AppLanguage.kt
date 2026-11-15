package dev.communicator.helmchatrisen.data

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
     * shown in the picker (diacritics ignored; Latin, Greek, Cyrillic, Hebrew, Devanagari, Han
     * and Kana scripts follow the collator's order).
     */
    val supportedTags: List<String> = listOf(
        "cs", "da", "de", "et", "en", "es", "fr", "hr", "it", "lv", "lt",
        "hu", "nl", "no", "pl", "pt", "ro", "sk", "fi", "sv", "tr", "el",
        "bg", "ru", "sr", "uk", "he", "hi", "zh", "ja",
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
        "hr" to "Hrvatski",
        "it" to "Italiano",
        "lv" to "Latviešu",
        "lt" to "Lietuvių",
        "hu" to "Magyar",
        "nl" to "Nederlands",
        "no" to "Norsk",
        "pl" to "Polski",
        "pt" to "Português",
        "ro" to "Română",
        "sk" to "Slovenčina",
        "fi" to "Suomi",
        "sv" to "Svenska",
        "tr" to "Türkçe",
        "el" to "Ελληνικά",
        "bg" to "Български",
        "ru" to "Русский",
        "sr" to "Српски",
        "uk" to "Українська",
        "he" to "עברית",
        "hi" to "हिन्दी",
        "zh" to "中文",
        "ja" to "日本語",
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
        "hr" to "🇭🇷",
        "it" to "🇮🇹",
        "lv" to "🇱🇻",
        "lt" to "🇱🇹",
        "hu" to "🇭🇺",
        "nl" to "🇳🇱",
        "no" to "🇳🇴",
        "pl" to "🇵🇱",
        "pt" to "🇵🇹",
        "ro" to "🇷🇴",
        "sk" to "🇸🇰",
        "fi" to "🇫🇮",
        "sv" to "🇸🇪",
        "tr" to "🇹🇷",
        "el" to "🇬🇷",
        "bg" to "🇧🇬",
        "ru" to "🇷🇺",
        "sr" to "🇷🇸",
        "uk" to "🇺🇦",
        "he" to "🇮🇱",
        "hi" to "🇮🇳",
        "zh" to "🇨🇳",
        "ja" to "🇯🇵",
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
