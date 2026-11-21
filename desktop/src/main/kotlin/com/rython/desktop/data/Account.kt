package com.rython.desktop.data

import com.rython.desktop.core.Base32
import com.rython.desktop.core.OtpAlgorithm
import com.rython.desktop.core.OtpType
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * One OTP account. The serialized field names match the Android app, so vaults and
 * backup files created by either app are readable by the other.
 */
@Serializable
data class Account(
    val id: String = UUID.randomUUID().toString(),
    val issuer: String = "",
    val name: String = "",
    val secret: String = "",
    val type: OtpType = OtpType.TOTP,
    val algorithm: OtpAlgorithm = OtpAlgorithm.SHA1,
    val digits: Int = 6,
    val period: Int = 30,
    val counter: Long = 0L,
    val folder: String = "",
    val sortOrder: Long = System.currentTimeMillis()
) {
    val displayIssuer: String get() = issuer.ifBlank { name.ifBlank { "Account" } }
    val displayName: String get() = if (issuer.isBlank()) "" else name

    /** First characters used for the offline generated service badge. */
    val initials: String
        get() {
            val source = issuer.ifBlank { name }.trim()
            if (source.isEmpty()) return "?"
            val words = source.split(' ', '-', '_', '.').filter { it.isNotBlank() }
            return when {
                words.size >= 2 -> (words[0].take(1) + words[1].take(1)).uppercase()
                else -> source.take(2).uppercase()
            }
        }

    companion object {
        fun normalizeSecret(raw: String): String = runCatching {
            Base32.decode(raw).let { Base32.encode(it) }
        }.getOrDefault(raw.filterNot { it.isWhitespace() }.uppercase().trimEnd('='))
    }
}
