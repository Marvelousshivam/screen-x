package com.gxdevs.screenx.utils

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

data class AppLanguage(
    val code: String,
    val nativeName: String,
    val englishName: String,
    val badge: String
)

object LocaleHelper {
    const val LANG_ENGLISH = "en"
    const val LANG_SPANISH = "es"
    const val LANG_UKRAINIAN = "uk"
    const val LANG_RUSSIAN = "ru"
    const val LANG_HINDI = "hi"

    val SUPPORTED_LANGUAGES = listOf(
        AppLanguage(LANG_ENGLISH, "English", "English", "EN"),
        AppLanguage(LANG_SPANISH, "Español", "Spanish", "ES"),
        AppLanguage(LANG_UKRAINIAN, "Українська", "Ukrainian", "UK"),
        AppLanguage(LANG_RUSSIAN, "Русский", "Russian", "RU"),
        AppLanguage(LANG_HINDI, "हिन्दी", "Hindi", "HI")
    )

    fun getLanguageByCode(code: String): AppLanguage {
        return SUPPORTED_LANGUAGES.firstOrNull { it.code.equals(code, ignoreCase = true) }
            ?: SUPPORTED_LANGUAGES.first()
    }

    /**
     * Creates a Configuration context wrapping the requested locale.
     */
    fun applyLocale(context: Context, languageCode: String): Context {
        val locale = Locale.forLanguageTag(languageCode.ifEmpty { LANG_ENGLISH })
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return context.createConfigurationContext(config)
    }

    /**
     * Wraps an Activity in a ContextWrapper that overrides resources/assets with the localized
     * configuration while preserving ActivityResultRegistryOwner, Activity, and Lifecycle delegation.
     */
    fun wrapActivityContext(activity: androidx.activity.ComponentActivity, languageCode: String): Context {
        val localized = applyLocale(activity, languageCode)
        return LocalizedActivityContext(activity, localized)
    }

    /**
     * Updates Android 13+ Per-App language preferences (LocaleManager)
     */
    fun updateSystemLocales(context: Context, languageCode: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                val localeManager = context.getSystemService(LocaleManager::class.java)
                localeManager?.applicationLocales = LocaleList.forLanguageTags(languageCode)
            } catch (e: Exception) {
                // Ignore if not supported on custom ROMs
            }
        }
    }
}

class LocalizedActivityContext(
    base: Context,
    private val localizedContext: Context
) : android.content.ContextWrapper(base), androidx.activity.result.ActivityResultRegistryOwner {

    override fun getResources(): android.content.res.Resources = localizedContext.resources
    override fun getAssets(): android.content.res.AssetManager = localizedContext.assets

    override val activityResultRegistry: androidx.activity.result.ActivityResultRegistry
        get() = (baseContext as? androidx.activity.result.ActivityResultRegistryOwner)?.activityResultRegistry
            ?: throw IllegalStateException("Base context does not implement ActivityResultRegistryOwner")
}

