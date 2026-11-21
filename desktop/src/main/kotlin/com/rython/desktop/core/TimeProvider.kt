package com.rython.desktop.core

import com.rython.desktop.app.Prefs
import java.net.HttpURLConnection
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** Effective "now" for TOTP/HOTP computation, honoring user settings. */
object TimeProvider {

    private const val KEY_TZ = "time_zone_id"
    private const val KEY_INTERNET = "use_internet_time"
    private const val KEY_OFFSET = "internet_offset_millis"

    fun selectedTimeZone(): String? =
        Prefs.getString(KEY_TZ)?.takeIf { it.isNotBlank() }

    fun setSelectedTimeZone(id: String?) = Prefs.putString(KEY_TZ, id)

    fun useInternetTime(): Boolean = Prefs.getBoolean(KEY_INTERNET, false)

    fun setUseInternetTime(value: Boolean) = Prefs.putBoolean(KEY_INTERNET, value)

    fun internetOffset(): Long = Prefs.getLong(KEY_OFFSET, 0L)

    fun setInternetOffset(offset: Long) = Prefs.putLong(KEY_OFFSET, offset)

    /** All timezone ids known to this JVM, device default first. */
    fun commonZoneIds(): List<String> =
        TimeZone.getAvailableIDs().toList().distinct().sorted()

    /** Effective current time in millis, applying internet offset and timezone shift. */
    fun nowMillis(): Long {
        var base = System.currentTimeMillis()
        if (useInternetTime()) base += internetOffset()
        val selected = selectedTimeZone()
        if (!selected.isNullOrBlank()) {
            val deviceOffset = TimeZone.getDefault().getOffset(base)
            val selectedOffset = runCatching { TimeZone.getTimeZone(selected).getOffset(base) }
                .getOrDefault(deviceOffset)
            base += (selectedOffset - deviceOffset).toLong()
        }
        return base
    }

    /**
     * Fetches the current time from the internet (HTTP Date header) and stores the
     * offset between network time and device time. Returns the offset on success.
     */
    fun syncInternetTime(): Long? {
        val urls = listOf("https://www.google.com", "http://www.google.com")
        for (url in urls) {
            try {
                val conn = URI(url).toURL().openConnection() as HttpURLConnection
                conn.requestMethod = "HEAD"
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.instanceFollowRedirects = true
                conn.connect()
                val dateHeader = conn.getHeaderField("Date")
                conn.disconnect()
                if (dateHeader.isNullOrBlank()) continue
                val fmt = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US)
                fmt.timeZone = TimeZone.getTimeZone("GMT")
                val netTime = fmt.parse(dateHeader)?.time ?: continue
                val offset = netTime - System.currentTimeMillis()
                setInternetOffset(offset)
                setUseInternetTime(true)
                return offset
            } catch (_: Exception) {
                // try the next endpoint
            }
        }
        return null
    }
}
