package com.rython.desktop.core

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.pow

/** OTP hash algorithms supported by the otpauth:// spec plus MD5 (legacy exports). */
enum class OtpAlgorithm(val macName: String, val label: String) {
    SHA1("HmacSHA1", "SHA1"),
    SHA256("HmacSHA256", "SHA256"),
    SHA512("HmacSHA512", "SHA512"),
    MD5("HmacMD5", "MD5");

    companion object {
        fun from(value: String?): OtpAlgorithm =
            entries.firstOrNull { it.label.equals(value, ignoreCase = true) } ?: SHA1
    }
}

enum class OtpType {
    TOTP, HOTP, STEAM;

    companion object {
        fun from(value: String?): OtpType =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: TOTP
    }
}

/** RFC 4648 base32 without padding, case-insensitive, spaces tolerated. */
object Base32 {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    fun encode(data: ByteArray): String {
        if (data.isEmpty()) return ""
        val sb = StringBuilder((data.size * 8 + 4) / 5)
        var buffer = 0
        var bits = 0
        for (b in data) {
            buffer = (buffer shl 8) or (b.toInt() and 0xFF)
            bits += 8
            while (bits >= 5) {
                sb.append(ALPHABET[(buffer shr (bits - 5)) and 0x1F])
                bits -= 5
            }
        }
        if (bits > 0) sb.append(ALPHABET[(buffer shl (5 - bits)) and 0x1F])
        return sb.toString()
    }

    /** @throws IllegalArgumentException when the input contains invalid base32 characters. */
    fun decode(input: String): ByteArray {
        val cleaned = input.filterNot { it.isWhitespace() || it == '=' || it == '-' }.uppercase()
        if (cleaned.isEmpty()) throw IllegalArgumentException("Secret is empty")
        val out = java.io.ByteArrayOutputStream(cleaned.length * 5 / 8)
        var buffer = 0
        var bits = 0
        for (c in cleaned) {
            val idx = ALPHABET.indexOf(c)
            require(idx >= 0) { "Invalid base32 character: $c" }
            buffer = (buffer shl 5) or idx
            bits += 5
            if (bits >= 8) {
                out.write((buffer shr (bits - 8)) and 0xFF)
                bits -= 8
            }
        }
        val bytes = out.toByteArray()
        require(bytes.isNotEmpty()) { "Secret is too short" }
        return bytes
    }

    fun isValid(secret: String): Boolean = runCatching { decode(secret) }.isSuccess
}

object OtpEngine {
    private const val STEAM_ALPHABET = "23456789BCDFGHJKMNPQRTVWXY"
    const val STEAM_DIGITS = 5
    const val STEAM_PERIOD = 30

    private fun hmac(algorithm: OtpAlgorithm, key: ByteArray, message: ByteArray): ByteArray {
        val mac = Mac.getInstance(algorithm.macName)
        mac.init(SecretKeySpec(key, algorithm.macName))
        return mac.doFinal(message)
    }

    private fun counterBytes(counter: Long): ByteArray {
        val buf = ByteArray(8)
        var value = counter
        for (i in 7 downTo 0) {
            buf[i] = (value and 0xFF).toByte()
            value = value ushr 8
        }
        return buf
    }

    /** Raw RFC 4226 value: 31-bit dynamic truncation of the HMAC. */
    private fun truncated(algorithm: OtpAlgorithm, key: ByteArray, counter: Long): Int {
        val hash = hmac(algorithm, key, counterBytes(counter))
        val offset = hash[hash.size - 1].toInt() and 0x0F
        return ((hash[offset].toInt() and 0x7F) shl 24) or
            ((hash[offset + 1].toInt() and 0xFF) shl 16) or
            ((hash[offset + 2].toInt() and 0xFF) shl 8) or
            (hash[offset + 3].toInt() and 0xFF)
    }

    fun hotp(secret: ByteArray, counter: Long, algorithm: OtpAlgorithm, digits: Int): String {
        val safeDigits = digits.coerceIn(6, 10)
        val modulus = 10.0.pow(safeDigits).toLong()
        val value = truncated(algorithm, secret, counter).toLong() % modulus
        return value.toString().padStart(safeDigits, '0')
    }

    fun steam(secret: ByteArray, counter: Long): String {
        var value = truncated(OtpAlgorithm.SHA1, secret, counter).toLong()
        val sb = StringBuilder(STEAM_DIGITS)
        repeat(STEAM_DIGITS) {
            sb.append(STEAM_ALPHABET[(value % STEAM_ALPHABET.length).toInt()])
            value /= STEAM_ALPHABET.length
        }
        return sb.toString()
    }

    fun counterFor(period: Int, nowMillis: Long): Long {
        val safePeriod = period.coerceAtLeast(1)
        return (nowMillis / 1000L) / safePeriod
    }

    fun code(
        secretBase32: String,
        type: OtpType,
        algorithm: OtpAlgorithm,
        digits: Int,
        period: Int,
        counter: Long,
        nowMillis: Long = System.currentTimeMillis()
    ): String {
        val key = runCatching { Base32.decode(secretBase32) }.getOrNull() ?: return "—"
        return when (type) {
            OtpType.STEAM -> steam(key, counterFor(STEAM_PERIOD, nowMillis))
            OtpType.HOTP -> hotp(key, counter, algorithm, digits)
            OtpType.TOTP -> hotp(key, counterFor(period, nowMillis), algorithm, digits)
        }
    }

    /** Seconds until the current period rolls over (TOTP / Steam only). */
    fun secondsRemaining(period: Int, nowMillis: Long): Int {
        val safePeriod = period.coerceAtLeast(1)
        val seconds = (nowMillis / 1000L) % safePeriod
        return (safePeriod - seconds).toInt().coerceIn(1, safePeriod)
    }

    fun formatCode(code: String, groupSize: Int = 3): String {
        if (code.length <= groupSize || code.any { it.isLetter() }) return code
        return code.chunked(groupSize).joinToString(" ")
    }
}
