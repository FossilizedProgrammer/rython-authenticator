package com.rython.desktop.security

import com.rython.desktop.app.Prefs
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * The two app passwords. Only salted PBKDF2-HMAC-SHA512 verifiers are stored; the
 * passwords themselves are never written to disk.
 *
 * Both are required, one after the other, before the vault is shown.
 */
object Passwords {

    private const val KEY_FIRST = "app_password_one"
    private const val KEY_SECOND = "app_password_two"

    private const val ITERATIONS = 210_000
    private const val KEY_BITS = 512
    private const val SALT_SIZE = 16

    const val MIN_LENGTH = 8

    fun isConfigured(): Boolean =
        Prefs.getString(KEY_FIRST) != null && Prefs.getString(KEY_SECOND) != null

    fun save(first: String, second: String) {
        require(first.length >= MIN_LENGTH && second.length >= MIN_LENGTH) {
            "passwords are too short"
        }
        Prefs.putString(KEY_FIRST, encode(first))
        Prefs.putString(KEY_SECOND, encode(second))
    }

    /** Replaces only the first password; the second one stays as it is. */
    fun saveFirst(first: String) {
        require(first.length >= MIN_LENGTH) { "password is too short" }
        Prefs.putString(KEY_FIRST, encode(first))
    }

    /** Replaces only the second password; the first one stays as it is. */
    fun saveSecond(second: String) {
        require(second.length >= MIN_LENGTH) { "password is too short" }
        Prefs.putString(KEY_SECOND, encode(second))
    }

    fun verifyFirst(candidate: String): Boolean = verify(KEY_FIRST, candidate)

    fun verifySecond(candidate: String): Boolean = verify(KEY_SECOND, candidate)

    private fun verify(key: String, candidate: String): Boolean {
        val stored = Prefs.getString(key) ?: return false
        val decoded = runCatching { Base64.getDecoder().decode(stored) }.getOrNull() ?: return false
        if (decoded.size <= SALT_SIZE) return false
        val salt = decoded.copyOfRange(0, SALT_SIZE)
        val expected = decoded.copyOfRange(SALT_SIZE, decoded.size)
        val actual = derive(candidate, salt)
        return MessageDigest.isEqual(expected, actual)
    }

    private fun encode(password: String): String {
        val salt = ByteArray(SALT_SIZE).also { SecureRandom().nextBytes(it) }
        return Base64.getEncoder().encodeToString(salt + derive(password, salt))
    }

    private fun derive(password: String, salt: ByteArray): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512")
            .generateSecret(PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_BITS))
            .encoded
}
