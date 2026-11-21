package com.rython.authenticator.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.rython.authenticator.core.OtpType
import com.rython.authenticator.core.Scrypt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** AES-GCM encryption of the on-device vault with a non-exportable KeyStore key. */
private object VaultCrypto {
    private const val ALIAS = "rython_vault_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_SIZE = 12
    private const val TAG_BITS = 128
    private const val HEADER: Byte = 1

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = cipher.iv
        val body = cipher.doFinal(plain)
        return ByteArray(1 + iv.size + body.size).also { out ->
            out[0] = HEADER
            iv.copyInto(out, 1)
            body.copyInto(out, 1 + iv.size)
        }
    }

    fun decrypt(payload: ByteArray): ByteArray {
        require(payload.size > 1 + IV_SIZE + TAG_BITS / 8) { "محتوای رمزشده ناقص است" }
        require(payload[0] == HEADER) { "نسخه فایل پشتیبانی نمی‌شود" }
        val iv = payload.copyOfRange(1, 1 + IV_SIZE)
        val body = payload.copyOfRange(1 + IV_SIZE, payload.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, iv))
        return cipher.doFinal(body)
    }
}

/**
 * Owns the encrypted account list. Every mutation is applied to memory first and then
 * written back to `filesDir/vault.bin` on a background thread.
 */
class VaultStore(private val context: Context) {

    private val vaultFile = File(context.filesDir, "vault.bin")

    private val _accounts = MutableStateFlow<List<Account>>(emptyList())
    val accounts: StateFlow<List<Account>> = _accounts.asStateFlow()

    /** Error codes; the UI turns them into localized text. */
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    companion object {
        const val ERROR_READ = "vault_read"
        const val ERROR_CORRUPT = "vault_corrupt"
        const val ERROR_SAVE = "vault_save"
    }

    suspend fun load() {
        withContext(Dispatchers.IO) {
            if (!vaultFile.exists()) return@withContext
            val bytes = runCatching { vaultFile.readBytes() }.getOrNull()
            if (bytes == null || bytes.isEmpty()) return@withContext
            val decoded = runCatching { VaultCrypto.decrypt(bytes) }
                .onFailure { _error.value = ERROR_READ }
                .getOrNull() ?: return@withContext
            val list = runCatching { JSONArray(String(decoded, Charsets.UTF_8)) }
                .onFailure { _error.value = ERROR_CORRUPT }
                .getOrNull() ?: return@withContext
            val parsed = buildList {
                for (i in 0 until list.length()) {
                    runCatching { Account.fromJson(list.getJSONObject(i)) }.getOrNull()?.let { add(it) }
                }
            }
            _accounts.value = parsed.sortedBy { it.sortOrder }
        }
    }

    fun clearError() {
        _error.value = null
    }

    suspend fun upsert(account: Account) {
        val current = _accounts.value
        val existing = current.any { it.id == account.id }
        _accounts.value = if (existing) {
            current.map { if (it.id == account.id) account else it }
        } else {
            current + account
        }.sortedBy { it.sortOrder }
        persist(_accounts.value)
    }

    suspend fun addAll(accounts: List<Account>) {
        if (accounts.isEmpty()) return
        val merged = (_accounts.value.associateBy { it.id } + accounts.associateBy { it.id }).values
        _accounts.value = merged.sortedBy { it.sortOrder }
        persist(_accounts.value)
    }

    suspend fun delete(id: String) {
        _accounts.value = _accounts.value.filterNot { it.id == id }
        persist(_accounts.value)
    }

    suspend fun deleteAll() {
        _accounts.value = emptyList()
        withContext(Dispatchers.IO) { runCatching { vaultFile.delete() } }
    }

    /** Advances the HOTP counter after a code has been revealed/copied. */
    suspend fun incrementCounter(id: String) {
        val target = _accounts.value.firstOrNull { it.id == id } ?: return
        if (target.type != OtpType.HOTP) return
        val updated = target.copy(counter = target.counter + 1)
        _accounts.value = _accounts.value.map { if (it.id == id) updated else it }
        persist(_accounts.value)
    }

    private suspend fun persist(snapshot: List<Account>) {
        withContext(Dispatchers.IO) {
            runCatching {
                val json = JSONArray().apply { snapshot.forEach { put(it.toJson()) } }
                vaultFile.writeBytes(VaultCrypto.encrypt(json.toString().toByteArray()))
            }.onFailure {
                _error.value = ERROR_SAVE
            }
        }
    }
}

/**
 * Portable backup file. Two formats are readable:
 *
 * * `RYTHONBAK2` (current): scrypt key derivation over **both** app passwords, then
 *   AES-256-GCM with a random salt/IV and the header authenticated as AAD.
 * * `RYTHONBAK1` (old builds): PBKDF2-HMAC-SHA256 with a single password. Still
 *   importable, never written.
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

    private val random = java.security.SecureRandom()

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
        val json = JSONArray().apply { accounts.forEach { put(it.toJson()) } }.toString()
        val body = cipher.doFinal(json.toByteArray(Charsets.UTF_8))
        return header + cipher.iv + body
    }

    /**
     * Decrypts a backup. New files need both passwords; old files only use [first].
     */
    fun import(bytes: ByteArray, first: CharArray, second: CharArray): List<Account> {
        val plain = if (isLegacy(bytes)) legacyBody(bytes, first) else v2Body(bytes, first, second)
        val array = JSONArray(String(plain, Charsets.UTF_8))
        return buildList {
            for (i in 0 until array.length()) {
                runCatching { Account.fromJson(array.getJSONObject(i)) }.getOrNull()?.let { add(it) }
            }
        }
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
        cipher.init(Cipher.DECRYPT_MODE, scryptKey(first, second, salt, n, r, p), GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(header)
        return cipher.doFinal(body)
    }

    private fun legacyBody(bytes: ByteArray, password: CharArray): ByteArray {
        val offset = MAGIC_V1.size
        require(bytes.size > offset + LEGACY_SALT_SIZE + IV_SIZE) { "invalid backup file" }
        val salt = bytes.copyOfRange(offset, offset + LEGACY_SALT_SIZE)
        val iv = bytes.copyOfRange(offset + LEGACY_SALT_SIZE, offset + LEGACY_SALT_SIZE + IV_SIZE)
        val body = bytes.copyOfRange(offset + LEGACY_SALT_SIZE + IV_SIZE, bytes.size)
        val spec = javax.crypto.spec.PBEKeySpec(password, salt, LEGACY_ITERATIONS, KEY_BITS)
        val encoded = javax.crypto.SecretKeyFactory
            .getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(spec)
            .encoded
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            javax.crypto.spec.SecretKeySpec(encoded, "AES"),
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
        return javax.crypto.spec.SecretKeySpec(derived, "AES")
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
