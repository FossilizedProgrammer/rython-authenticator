package com.rython.authenticator.core

import android.net.Uri
import com.rython.authenticator.data.Account
import java.util.Base64

/**
 * Parser for `otpauth://` links and Google Authenticator's
 * `otpauth-migration://offline?data=<base64 protobuf>` export frame.
 */
/**
 * One QR code of a Google Authenticator export. A single export can be split over
 * several QR codes; the batch fields say which one this is.
 */
data class MigrationFrame(
    val accounts: List<Account>,
    val batchSize: Int = 0,
    val batchIndex: Int = 0,
    val batchId: Int = -1
) {
    val isBatched: Boolean get() = batchSize > 1
}

object OtpUri {

    class ParseException(message: String) : Exception(message)

    fun parse(raw: String): List<Account> {
        val uri = runCatching { Uri.parse(raw.trim()) }
            .getOrElse { throw ParseException("لینک نامعتبر است") }
        return when (uri.scheme?.lowercase()) {
            "otpauth" -> listOf(parseSingle(uri))
            "otpauth-migration" -> parseMigration(uri).accounts
            else -> throw ParseException("فقط لینک otpauth پشتیبانی می‌شود")
        }
    }

    // ---------------------------------------------------------------- otpauth

    private fun parseSingle(uri: Uri): Account {
        val type = when (uri.host?.lowercase()) {
            "totp" -> OtpType.TOTP
            "hotp" -> OtpType.HOTP
            "steam" -> OtpType.STEAM
            else -> throw ParseException("نوع OTP پشتیبانی نمی‌شود")
        }
        val label = runCatching { uri.pathSegments.firstOrNull().orEmpty() }.getOrDefault("")
            .let { runCatching { Uri.decode(it) }.getOrDefault(it) }
            .trim()

        var labelIssuer = ""
        var accountName = label
        val colon = label.indexOf(':')
        if (colon > 0) {
            labelIssuer = label.substring(0, colon).trim()
            accountName = label.substring(colon + 1).trim()
        }

        val params = uri.queryParameterNames.associateWith { key ->
            uri.getQueryParameter(key).orEmpty()
        }

        val secret = params["secret"]
        if (secret.isNullOrBlank()) throw ParseException("لینک فاقد secret است")

        val issuer = params["issuer"]?.trim().takeUnless { it.isNullOrBlank() } ?: labelIssuer
        val digits = params["digits"]?.toIntOrNull()?.coerceIn(6, 10)
            ?: if (type == OtpType.STEAM) OtpEngine.STEAM_DIGITS else 6
        val period = params["period"]?.toIntOrNull()?.coerceAtLeast(1)
            ?: if (type == OtpType.STEAM) OtpEngine.STEAM_PERIOD else 30

        return Account(
            issuer = issuer,
            name = accountName.ifBlank { issuer },
            secret = Account.normalizeSecret(secret),
            type = type,
            algorithm = if (type == OtpType.STEAM) OtpAlgorithm.SHA1 else OtpAlgorithm.from(params["algorithm"]),
            digits = digits,
            period = period,
            counter = params["counter"]?.toLongOrNull() ?: 0L,
            folder = ""
        )
    }

    // --------------------------------------------------------------- migration

    /**
     * Parses a full `otpauth-migration://offline?data=...` export frame, keeping the
     * batch metadata so a multi-QR export can be assembled.
     */
    fun parseMigrationFrame(raw: String): MigrationFrame {
        val trimmed = raw.trim()
        val uri = runCatching { Uri.parse(trimmed) }
            .getOrElse { throw ParseException("لینک نامعتبر است") }
        if (uri.scheme?.lowercase() != "otpauth-migration") {
            throw ParseException("این کد، خروجی Google Authenticator نیست")
        }
        val data = uri.getQueryParameter("data")
            ?: throw ParseException("داده مهاجرت در لینک موجود نیست")
        return parseMigrationData(data)
    }

    private fun parseMigration(uri: Uri): MigrationFrame {
        val data = uri.getQueryParameter("data")
            ?: throw ParseException("داده مهاجرت در لینک موجود نیست")
        return parseMigrationData(data)
    }

    /** Parses the base64 `data` payload of a migration frame (protobuf). */
    fun parseMigrationData(data: String): MigrationFrame {
        val bytes = decodeBase64(data) ?: throw ParseException("داده مهاجرت خراب است")
        val payload = ProtoReader(bytes)
        val accounts = mutableListOf<Account>()
        var batchSize = 0
        var batchIndex = 0
        var batchId = -1

        while (payload.hasMore()) {
            val tag = payload.readTag()
            when (tag.field) {
                1 -> {
                    val length = payload.readLength()
                    val entryBytes = payload.readBytes(length)
                    val entry = ProtoReader(entryBytes)
                    accounts += readOtpParameters(entry)
                }
                3 -> batchSize = payload.readVarint().toInt()
                4 -> batchIndex = payload.readVarint().toInt()
                5 -> batchId = payload.readVarint().toInt()
                else -> payload.skip(tag.wire)
            }
        }
        if (accounts.isEmpty()) throw ParseException("اکانتی در داده مهاجرت پیدا نشد")
        return MigrationFrame(
            accounts = accounts,
            batchSize = if (batchSize > 0) batchSize else 0,
            batchIndex = batchIndex.coerceAtLeast(0),
            batchId = batchId
        )
    }

    private fun readOtpParameters(reader: ProtoReader): Account {
        var secret: ByteArray? = null
        var name = ""
        var issuer = ""
        var algorithm = OtpAlgorithm.SHA1
        var digits = 6
        var type = OtpType.TOTP
        var counter = 0L

        while (reader.hasMore()) {
            val tag = reader.readTag()
            when (tag.field) {
                1 -> secret = reader.readBytes(reader.readLength())
                2 -> name = reader.readString(reader.readLength())
                3 -> issuer = reader.readString(reader.readLength())
                4 -> algorithm = when (reader.readVarint()) {
                    1L -> OtpAlgorithm.SHA1
                    2L -> OtpAlgorithm.SHA256
                    3L -> OtpAlgorithm.SHA512
                    4L -> OtpAlgorithm.MD5
                    else -> OtpAlgorithm.SHA1
                }
                5 -> digits = if (reader.readVarint() == 2L) 8 else 6
                6 -> type = if (reader.readVarint() == 1L) OtpType.HOTP else OtpType.TOTP
                7 -> counter = reader.readVarint()
                else -> reader.skip(tag.wire)
            }
        }

        val rawSecret = secret ?: throw ParseException("اکانت بدون secret در داده مهاجرت")
        var finalIssuer = issuer.trim()
        var finalName = name.trim()
        // Older exports packed "Issuer:account" into the name field.
        if (finalIssuer.isEmpty()) {
            val colon = finalName.indexOf(':')
            if (colon > 0) {
                finalIssuer = finalName.substring(0, colon).trim()
                finalName = finalName.substring(colon + 1).trim()
            }
        }
        return Account(
            issuer = finalIssuer,
            name = finalName.ifBlank { finalIssuer },
            secret = Base32.encode(rawSecret),
            type = type,
            algorithm = algorithm,
            digits = digits,
            period = 30,
            counter = counter
        )
    }

    private fun decodeBase64(data: String): ByteArray? {
        val trimmed = data.trim()
        // Google exports as URL-safe base64 without padding; some tools use standard base64.
        val decoders = listOf(Base64.getUrlDecoder(), Base64.getDecoder())
        for (decoder in decoders) {
            val decoded = runCatching { decoder.decode(trimmed) }.getOrNull()
            if (decoded != null && decoded.isNotEmpty()) return decoded
        }
        return null
    }

    private data class Tag(val field: Int, val wire: Int)

    /** Minimal protobuf reader: varint, length-delimited fields and skip support. */
    private class ProtoReader(private val buf: ByteArray) {
        private var pos = 0

        fun hasMore() = pos < buf.size

        fun readTag(): Tag {
            val key = readVarint().toInt()
            val field = key ushr 3
            if (field == 0) throw ParseException("پروتوبوف نامعتبر است")
            return Tag(field, key and 0x7)
        }

        fun readVarint(): Long {
            var shift = 0
            var result = 0L
            while (true) {
                if (pos >= buf.size || shift > 63) throw ParseException("varint نامعتبر")
                val b = buf[pos++].toInt() and 0xFF
                result = result or ((b and 0x7F).toLong() shl shift)
                if (b and 0x80 == 0) return result
                shift += 7
            }
        }

        fun readLength(): Int {
            val length = readVarint()
            if (length < 0 || length > buf.size - pos) throw ParseException("طول فیلد نامعتبر")
            return length.toInt()
        }

        fun readBytes(length: Int): ByteArray {
            if (length > buf.size - pos) throw ParseException("طول فیلد نامعتبر")
            val out = buf.copyOfRange(pos, pos + length)
            pos += length
            return out
        }

        fun readString(length: Int) = String(readBytes(length), Charsets.UTF_8)

        fun skip(wire: Int) {
            when (wire) {
                0 -> readVarint()
                1 -> pos += 8
                2 -> { val len = readLength(); pos += len }
                5 -> pos += 4
                else -> throw ParseException("نوع فیلد پروتوبوفر پشتیبانی نمی‌شود")
            }
            if (pos > buf.size) throw ParseException("پروتوبوفر ناقص است")
        }
    }
}
