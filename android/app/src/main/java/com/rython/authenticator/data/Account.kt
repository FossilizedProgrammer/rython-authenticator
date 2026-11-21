package com.rython.authenticator.data

import com.rython.authenticator.core.Base32
import com.rython.authenticator.core.OtpAlgorithm
import com.rython.authenticator.core.OtpType
import org.json.JSONObject
import java.util.UUID

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

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("issuer", issuer)
        put("name", name)
        put("secret", secret)
        put("type", type.name)
        put("algorithm", algorithm.name)
        put("digits", digits)
        put("period", period)
        put("counter", counter)
        put("folder", folder)
        put("sortOrder", sortOrder)
    }

    companion object {
        fun fromJson(o: JSONObject): Account = Account(
            id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
            issuer = o.optString("issuer"),
            name = o.optString("name"),
            secret = o.optString("secret"),
            type = OtpType.from(o.optString("type")),
            algorithm = runCatching { OtpAlgorithm.valueOf(o.optString("algorithm")) }
                .getOrDefault(OtpAlgorithm.SHA1),
            digits = o.optInt("digits", 6).coerceIn(4, 10),
            period = o.optInt("period", 30).coerceAtLeast(1),
            counter = o.optLong("counter", 0L),
            folder = o.optString("folder"),
            sortOrder = o.optLong("sortOrder", 0L)
        )

        fun normalizeSecret(raw: String): String = runCatching {
            Base32.decode(raw).let { Base32.encode(it) }
        }.getOrDefault(raw.filterNot { it.isWhitespace() }.uppercase().trimEnd('='))
    }
}
