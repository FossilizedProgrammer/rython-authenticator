package com.rython.desktop.data

import com.rython.desktop.core.Scrypt
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Portable backup file. Two formats are readable:
 *
 * * `RYTHONBAK2` (current): scrypt key derivation over **both** app passwords, then
 *   AES-256-GCM with a random salt/IV and the header authenticated as AAD.
 * * `RYTHONBAK1` (old builds): PBKDF2-HMAC-SHA256 with a single password. Still
 *   importable, never written.
 *
 * The bytes are identical to the Android app's format, so a backup made on the phone
 * opens here and the other way round.
 */
object Backup {

    private val MAGIC_V1 = "RYTHONBAK1".toByteArray(Charsets.US_ASCII)
    private val MAGIC_V2 = "RYTHONBAK2".toByteArray(Charsets.US_ASCII)

    private const val LEGACY_ITERATIONS = 210_000
    private const val KEY_BITS = 256
    private const val SALT_SIZE = 32
    private const val LEGACY_SALT_SIZE = 16
    private const val IV_SIZE = 12
    private const val TAG_BITS = 128

    // Header layout: magic(10) | n(4) | r(4) | p(4) | salt(32)
    private const val HEADER_SIZE = 10 + 4 + 4 + 4 + SALT_SIZE

    private val random = SecureRandom()

    val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        prettyPrint = false
    }

    private val accountList = ListSerializer(Account.serializer())

    fun encodeAccounts(accounts: List<Account>): String = json.encodeToString(accountList, accounts)

    fun decodeAccounts(text: String): List<Account> = json.decodeFromString(accountList, text)

    /** True when the file was written by an older build with a single password. */
    fun isLegacy(bytes: ByteArray): Boolean =
        bytes.size > MAGIC_V1.size &&
            bytes.copyOfRange(0, MAGIC_V1.size).contentEquals(MAGIC_V1)

    fun export(accounts: List<Account>, first: CharArray, second: CharArray): ByteArray {
        val salt = ByteArray(SALT_SIZE).also { random.nextBytes(it) }
        val header = ByteArray(HEADER_SIZE)
        MAGIC_V2.copyInto(header, 0)
        writeInt(header, 10, Scrypt.DEFAULT_N)
        writeInt(header, 14, Scrypt.DEFAULT_R)
        writeInt(header, 18, Scrypt.DEFAULT_P)
        salt.copyInto(header, 22)

        val key = scryptKey(first, second, salt, Scrypt.DEFAULT_N, Scrypt.DEFAULT_R, Scrypt.DEFAULT_P)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        // The header is authenticated, so the KDF parameters cannot be tampered with.
        cipher.updateAAD(header)
        val body = cipher.doFinal(encodeAccounts(accounts).toByteArray(Charsets.UTF_8))
        return header + cipher.iv + body
    }

    /** Decrypts a backup. New files need both passwords; old files only use [first]. */
    fun import(bytes: ByteArray, first: CharArray, second: CharArray): List<Account> {
        val plain = if (isLegacy(bytes)) legacyBody(bytes, first) else v2Body(bytes, first, second)
        return decodeAccounts(String(plain, Charsets.UTF_8))
    }

    private fun v2Body(bytes: ByteArray, first: CharArray, second: CharArray): ByteArray {
        require(bytes.size > HEADER_SIZE + IV_SIZE + TAG_BITS / 8) { "invalid backup file" }
        require(bytes.copyOfRange(0, MAGIC_V2.size).contentEquals(MAGIC_V2)) {
            "not a Rython backup"
        }
        val header = bytes.copyOfRange(0, HEADER_SIZE)
        val n = readInt(header, 10)
        val r = readInt(header, 14)
        val p = readInt(header, 18)
        // Refuse parameters that would exhaust memory or take minutes to derive.
        require(n in 2..(1 shl 20) && n and (n - 1) == 0) { "invalid scrypt cost" }
        require(r in 1..32) { "invalid scrypt block size" }
        require(p in 1..16) { "invalid scrypt parallelism" }
        val salt = header.copyOfRange(22, HEADER_SIZE)

        val iv = bytes.copyOfRange(HEADER_SIZE, HEADER_SIZE + IV_SIZE)
        val body = bytes.copyOfRange(HEADER_SIZE + IV_SIZE, bytes.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            scryptKey(first, second, salt, n, r, p),
            GCMParameterSpec(TAG_BITS, iv)
        )
        cipher.updateAAD(header)
        return cipher.doFinal(body)
    }

    private fun legacyBody(bytes: ByteArray, password: CharArray): ByteArray {
        val offset = MAGIC_V1.size
        require(bytes.size > offset + LEGACY_SALT_SIZE + IV_SIZE) { "invalid backup file" }
        val salt = bytes.copyOfRange(offset, offset + LEGACY_SALT_SIZE)
        val iv = bytes.copyOfRange(offset + LEGACY_SALT_SIZE, offset + LEGACY_SALT_SIZE + IV_SIZE)
        val body = bytes.copyOfRange(offset + LEGACY_SALT_SIZE + IV_SIZE, bytes.size)
        val spec = PBEKeySpec(password, salt, LEGACY_ITERATIONS, KEY_BITS)
        val encoded = javax.crypto.SecretKeyFactory
            .getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(spec)
            .encoded
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(encoded, "AES"),
            GCMParameterSpec(TAG_BITS, iv)
        )
        return cipher.doFinal(body)
    }

    private fun scryptKey(
        first: CharArray,
        second: CharArray,
        salt: ByteArray,
        n: Int,
        r: Int,
        p: Int
    ): SecretKey {
        val derived = Scrypt.derive(passphrase(first, second), salt, n, r, p, KEY_BITS / 8)
        return SecretKeySpec(derived, "AES")
    }

    /**
     * Both passwords in one KDF input. Each is length-prefixed, so ("ab","c") and
     * ("a","bc") derive different keys.
     */
    private fun passphrase(first: CharArray, second: CharArray): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        for (password in listOf(first, second)) {
            val bytes = String(password).toByteArray(Charsets.UTF_8)
            out.write(bytes.size ushr 24)
            out.write(bytes.size ushr 16)
            out.write(bytes.size ushr 8)
            out.write(bytes.size)
            out.write(bytes)
        }
        return out.toByteArray()
    }

    private fun writeInt(target: ByteArray, offset: Int, value: Int) {
        target[offset] = (value ushr 24).toByte()
        target[offset + 1] = (value ushr 16).toByte()
        target[offset + 2] = (value ushr 8).toByte()
        target[offset + 3] = value.toByte()
    }

    private fun readInt(source: ByteArray, offset: Int): Int =
        ((source[offset].toInt() and 0xFF) shl 24) or
            ((source[offset + 1].toInt() and 0xFF) shl 16) or
            ((source[offset + 2].toInt() and 0xFF) shl 8) or
            (source[offset + 3].toInt() and 0xFF)
}
