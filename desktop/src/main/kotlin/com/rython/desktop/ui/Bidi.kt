package com.rython.desktop.ui

/**
 * Skiko draws runs in logical order in some mixed-direction cases, which scrambles
 * Persian sentences that contain a Latin word (for example "لینک otpauth://…").
 * Wrapping every Latin/digit run in Unicode direction controls keeps those runs in
 * the right visual place inside the RTL paragraph.
 */
object Bidi {

    private val latinRun = Regex("[A-Za-z0-9][A-Za-z0-9._:/@%+#'&=?*\\-]*")

    /** Left-to-right isolate (U+2066 … U+2069). */
    fun lri(text: String): String = wrap(text, '\u2066', '\u2069')

    /** Left-to-right embedding (U+202A … U+202C). */
    fun lre(text: String): String = wrap(text, '\u202A', '\u202C')

    /** Left-to-right mark around the whole paragraph. */
    fun lrm(text: String): String = "\u200E$text\u200E"

    /** Right-to-left mark around the whole paragraph (forces an RTL paragraph). */
    fun rlm(text: String): String = "\u200F$text\u200F"

    private fun wrap(text: String, open: Char, close: Char): String =
        latinRun.replace(text) { match -> "$open${match.value}$close" }
}
