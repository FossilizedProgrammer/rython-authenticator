package com.rython.authenticator.security

import android.content.Context
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
object AppPasswords {

    private const val PREFS = "rython_prefs"
    private const val KEY_FIRST = "app_password_one"
    private const val KEY_SECOND = "app_password_two"

    private const val ITERATIONS = 210_000
    private const val KEY_BITS = 512
    private const val SALT_SIZE = 16

    const val MIN_LENGTH = 8

    fun isConfigured(context: Context): Boolean =
        prefs(context).getString(KEY_FIRST, null) != null &&
            prefs(context).getString(KEY_SECOND, null) != null

    fun save(context: Context, first: String, second: String) {
        require(first.length >= MIN_LENGTH && second.length >= MIN_LENGTH) {
            "passwords are too short"
        }
        prefs(context).edit()
            .putString(KEY_FIRST, encode(first))
            .putString(KEY_SECOND, encode(second))
            .apply()
    }

    /** Replaces only the first password; the second one stays as it is. */
    fun saveFirst(context: Context, first: String) {
        require(first.length >= MIN_LENGTH) { "password is too short" }
        prefs(context).edit().putString(KEY_FIRST, encode(first)).apply()
    }

    /** Replaces only the second password; the first one stays as it is. */
    fun saveSecond(context: Context, second: String) {
        require(second.length >= MIN_LENGTH) { "password is too short" }
        prefs(context).edit().putString(KEY_SECOND, encode(second)).apply()
    }

    fun verifyFirst(context: Context, candidate: String): Boolean =
        verify(context, KEY_FIRST, candidate)

    fun verifySecond(context: Context, candidate: String): Boolean =
        verify(context, KEY_SECOND, candidate)

    private fun verify(context: Context, key: String, candidate: String): Boolean {
        val stored = prefs(context).getString(key, null) ?: return false
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

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
