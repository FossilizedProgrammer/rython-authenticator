package com.rython.authenticator.core

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** Effective "now" for TOTP/HOTP computation, honoring user settings. */
object TimeProvider {

    private const val PREFS = "rython_prefs"
    private const val KEY_TZ = "time_zone_id"
    private const val KEY_INTERNET = "use_internet_time"
    private const val KEY_OFFSET = "internet_offset_millis"

    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun selectedTimeZone(context: Context): String? =
        prefs(context).getString(KEY_TZ, null)?.takeIf { it.isNotBlank() }

    fun setSelectedTimeZone(context: Context, id: String?) {
        prefs(context).edit().putString(KEY_TZ, id).apply()
    }

    fun useInternetTime(context: Context): Boolean =
        prefs(context).getBoolean(KEY_INTERNET, false)

    fun setUseInternetTime(context: Context, v: Boolean) {
        prefs(context).edit().putBoolean(KEY_INTERNET, v).apply()
    }

    fun internetOffset(context: Context): Long =
        prefs(context).getLong(KEY_OFFSET, 0L)

    fun setInternetOffset(context: Context, offset: Long) {
        prefs(context).edit().putLong(KEY_OFFSET, offset).apply()
    }

    /** A curated list of common zone ids plus the device default sentinel. */
    fun commonZoneIds(): List<String> = listOf(
        "UTC",
        "Europe/London",
        "Europe/Paris",
        "Europe/Berlin",
        "Europe/Moscow",
        "Asia/Tehran",
        "Asia/Dubai",
        "Asia/Kolkata",
        "Asia/Shanghai",
        "Asia/Tokyo",
        "Asia/Seoul",
        "Asia/Singapore",
        "Asia/Bangkok",
        "Australia/Sydney",
        "Pacific/Auckland",
        "America/New_York",
        "America/Chicago",
        "America/Denver",
        "America/Los_Angeles",
        "America/Sao_Paulo",
        "America/Mexico_City",
        "Africa/Cairo",
        "Africa/Johannesburg"
    ).distinct().sorted()

    /** Effective current time in millis, applying internet offset and timezone shift. */
    fun nowMillis(context: Context): Long {
        var base = System.currentTimeMillis()
        if (useInternetTime(context)) {
            base += internetOffset(context)
        }
        val sel = selectedTimeZone(context)
        if (!sel.isNullOrBlank()) {
            val deviceOffset = TimeZone.getDefault().getOffset(base)
            val selectedOffset = runCatching { TimeZone.getTimeZone(sel).getOffset(base) }
                .getOrDefault(deviceOffset)
            base += (selectedOffset - deviceOffset).toLong()
        }
        return base
    }

    /**
     * Fetches the current time from the internet (HTTP Date header) and stores the
     * offset between network time and device time. Returns the offset on success.
     */
    fun syncInternetTime(context: Context): Long? {
        val urls = listOf("https://www.google.com", "http://www.google.com")
        for (url in urls) {
            try {
                val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
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
                setInternetOffset(context, offset)
                setUseInternetTime(context, true)
                return offset
            } catch (e: Exception) {
                // try next
            }
        }
        return null
    }
}
