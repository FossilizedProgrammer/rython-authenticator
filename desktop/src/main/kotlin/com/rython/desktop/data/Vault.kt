package com.rython.desktop.data

import com.rython.desktop.app.Prefs
import com.rython.desktop.core.OtpType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-GCM encryption of the on-disk vault.
 *
 * The desktop has no Android Keystore, so the data key is a random 256-bit value kept
 * in `vault.key` (mode 0600 on POSIX) next to the encrypted file. It never leaves the
 * machine and is not derived from the app passwords, so changing a password does not
 * touch the stored accounts — the same behaviour as on Android.
 */
private object VaultCrypto {
    private const val IV_SIZE = 12
    private const val TAG_BITS = 128
    private const val HEADER: Byte = 1
    private const val KEY_SIZE = 32

    private fun key(): ByteArray {
        val file = File(Prefs.directory, "vault.key")
        if (file.exists() && file.length() == KEY_SIZE.toLong()) {
            val existing = file.readBytes()
            if (existing.size == KEY_SIZE) return existing
        }
        val generated = ByteArray(KEY_SIZE).also { SecureRandom().nextBytes(it) }
        file.writeBytes(generated)
        Prefs.restrictToOwner(file)
        return generated
    }

    fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key(), "AES"))
        val iv = cipher.iv
        val body = cipher.doFinal(plain)
        return ByteArray(1 + iv.size + body.size).also { out ->
            out[0] = HEADER
            iv.copyInto(out, 1)
            body.copyInto(out, 1 + iv.size)
        }
    }

    fun decrypt(payload: ByteArray): ByteArray {
        require(payload.size > 1 + IV_SIZE + TAG_BITS / 8) { "encrypted content is incomplete" }
        require(payload[0] == HEADER) { "unsupported vault version" }
        val iv = payload.copyOfRange(1, 1 + IV_SIZE)
        val body = payload.copyOfRange(1 + IV_SIZE, payload.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(key(), "AES"),
            GCMParameterSpec(TAG_BITS, iv)
        )
        return cipher.doFinal(body)
    }
}

/**
 * Owns the encrypted account list. Every mutation is applied to memory first and then
 * written back to `vault.bin` on a background thread.
 */
class Vault {

    private val vaultFile = File(Prefs.directory, "vault.bin")

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
            val parsed = runCatching { Backup.decodeAccounts(String(decoded, Charsets.UTF_8)) }
                .onFailure { _error.value = ERROR_CORRUPT }
                .getOrNull() ?: return@withContext
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
                val json = Backup.encodeAccounts(snapshot)
                val encrypted = VaultCrypto.encrypt(json.toByteArray(Charsets.UTF_8))
                vaultFile.writeBytes(encrypted)
                Prefs.restrictToOwner(vaultFile)
            }.onFailure {
                _error.value = ERROR_SAVE
            }
        }
    }
}
