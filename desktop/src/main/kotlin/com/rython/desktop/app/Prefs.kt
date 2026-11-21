package com.rython.desktop.app

import java.io.File
import java.util.Properties

/**
 * Small properties-backed store kept next to the vault. The Android app uses
 * SharedPreferences; on the desktop the same keys live in `prefs.properties` inside
 * the per-user config directory:
 *
 *   Linux:   $XDG_CONFIG_HOME/rython-authenticator   (usually ~/.config/...)
 *   Windows: %APPDATA%\Rython-Authenticator
 */
object Prefs {

    private val lock = Any()
    private val properties = Properties()
    private var loaded = false

    val directory: File by lazy {
        // RYTHON_CONFIG_DIR overrides the location (portable use, tests).
        System.getenv("RYTHON_CONFIG_DIR")?.takeIf { it.isNotBlank() }?.let {
            return@lazy File(it).apply { mkdirs() }
        }
        val os = System.getProperty("os.name").orEmpty().lowercase()
        val base = when {
            os.contains("win") -> System.getenv("APPDATA")?.let { File(it, "Rython-Authenticator") }
            os.contains("mac") -> System.getProperty("user.home")?.let {
                File(it, "Library/Application Support/Rython-Authenticator")
            }
            else -> {
                val xdg = System.getenv("XDG_CONFIG_HOME")
                if (!xdg.isNullOrBlank()) File(xdg, "rython-authenticator")
                else File(System.getProperty("user.home"), ".config/rython-authenticator")
            }
        } ?: File(System.getProperty("user.home"), ".rython-authenticator")
        base.apply { mkdirs() }
    }

    private val file: File by lazy { File(directory, "prefs.properties") }

    private fun ensureLoaded() {
        if (loaded) return
        synchronized(lock) {
            if (loaded) return
            if (file.exists()) {
                runCatching { file.inputStream().use { properties.load(it) } }
            }
            loaded = true
        }
    }

    fun getString(key: String, default: String? = null): String? {
        ensureLoaded()
        return properties.getProperty(key, default)
    }

    fun putString(key: String, value: String?) {
        ensureLoaded()
        synchronized(lock) {
            if (value == null) properties.remove(key) else properties.setProperty(key, value)
            persist()
        }
    }

    fun getBoolean(key: String, default: Boolean = false): Boolean =
        getString(key)?.toBooleanStrictOrNull() ?: default

    fun putBoolean(key: String, value: Boolean) = putString(key, value.toString())

    fun getLong(key: String, default: Long = 0L): Long =
        getString(key)?.toLongOrNull() ?: default

    fun putLong(key: String, value: Long) = putString(key, value.toString())

    private fun persist() {
        runCatching {
            file.outputStream().use { properties.store(it, "Rython-Authenticator settings") }
            restrictToOwner(file)
        }
    }

    /** Best effort 0600 on POSIX systems; Windows inherits the user profile ACL. */
    fun restrictToOwner(target: File) {
        runCatching {
            if (!System.getProperty("os.name").orEmpty().lowercase().contains("win")) {
                target.setReadable(false, false)
                target.setWritable(false, false)
                target.setReadable(true, true)
                target.setWritable(true, true)
            }
        }
    }
}
