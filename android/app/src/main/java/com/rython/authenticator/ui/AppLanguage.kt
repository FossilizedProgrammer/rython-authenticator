package com.rython.authenticator.ui

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import java.util.Locale

/** Languages shipped with the app. English is the default. */
enum class AppLanguage(val code: String, val nativeName: String) {
    EN("en", "English"),
    FA("fa", "فارسی"),
    RU("ru", "Русский"),
    ES("es", "Español"),
    ZH("zh", "中文");

    companion object {
        fun fromCode(code: String?): AppLanguage = entries.firstOrNull { it.code == code } ?: EN
    }
}

object AppLocale {

    private const val PREFS = "rython_prefs"
    private const val KEY = "app_language"

    fun current(context: Context): AppLanguage =
        AppLanguage.fromCode(prefs(context).getString(KEY, null))

    fun save(context: Context, language: AppLanguage) {
        prefs(context).edit().putString(KEY, language.code).apply()
    }

    /** Wraps the base context so resources (and layout direction) follow the setting. */
    fun wrap(context: Context): Context {
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(Locale(current(context).code))
        return context.createConfigurationContext(configuration)
    }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
