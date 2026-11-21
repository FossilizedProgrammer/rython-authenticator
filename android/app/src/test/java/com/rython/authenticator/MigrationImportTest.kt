package com.rython.authenticator

import com.rython.authenticator.core.Base32
import com.rython.authenticator.core.OtpAlgorithm
import com.rython.authenticator.core.OtpType
import com.rython.authenticator.core.OtpUri
import com.rython.authenticator.core.TransferSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.Base64

/**
 * Verifies the Google Authenticator export ("Transfer accounts") import against
 * hand-built protobuf payloads, mirroring the frames GA produces.
 */
class MigrationImportTest {

    private fun varint(value: Long): ByteArray {
        val out = ByteArrayOutputStream()
        var v = value
        while (true) {
            if ((v and 0x7FL.inv()) == 0L) {
                out.write(v.toInt())
                return out.toByteArray()
            }
            out.write(((v and 0x7F) or 0x80).toInt())
            v = v ushr 7
        }
    }

    private fun varintField(field: Int, value: Long): ByteArray =
        varint((field shl 3).toLong()) + varint(value)

    private fun lenField(field: Int, payload: ByteArray): ByteArray =
        varint(((field shl 3) or 2).toLong()) + varint(payload.size.toLong()) + payload

    private fun otpEntry(
        secret: ByteArray,
        name: String,
        issuer: String,
        algorithm: Int = 1,
        digits: Int = 1,
        type: Int = 2,
        counter: Long? = null
    ): ByteArray {
        var entry = lenField(1, secret) +
            lenField(2, name.toByteArray(Charsets.UTF_8)) +
            lenField(3, issuer.toByteArray(Charsets.UTF_8)) +
            varintField(4, algorithm.toLong()) +
            varintField(5, digits.toLong()) +
            varintField(6, type.toLong())
        if (counter != null) entry += varintField(7, counter)
        return entry
    }

    private fun encode(
        entries: List<ByteArray>,
        batchSize: Int = 0,
        batchIndex: Int = 0,
        batchId: Int = 0
    ): String {
        var payload = ByteArray(0)
        entries.forEach { payload += lenField(1, it) }
        payload += varintField(2, 1L)
        if (batchSize > 0) {
            payload += varintField(3, batchSize.toLong())
            payload += varintField(4, batchIndex.toLong())
            payload += varintField(5, batchId.toLong())
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(payload)
    }

    private val rfcSecret = "12345678901234567890".toByteArray(Charsets.US_ASCII)

    @Test
    fun parsesAccountsFromExportFrame() {
        val data = encode(
            listOf(
                otpEntry(rfcSecret, "alice@example.com", "Example"),
                otpEntry(
                    "secondarysecret".toByteArray(Charsets.US_ASCII),
                    "GitHub:bob",
                    "",
                    algorithm = 2,
                    digits = 2,
                    type = 1,
                    counter = 42L
                )
            )
        )

        val frame = OtpUri.parseMigrationData(data)

        assertEquals(2, frame.accounts.size)

        val first = frame.accounts[0]
        assertEquals("Example", first.issuer)
        assertEquals("alice@example.com", first.name)
        assertEquals("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", first.secret)
        assertEquals(OtpType.TOTP, first.type)
        assertEquals(OtpAlgorithm.SHA1, first.algorithm)
        assertEquals(6, first.digits)

        // "GitHub:bob" in the name field, no issuer field.
        val second = frame.accounts[1]
        assertEquals("GitHub", second.issuer)
        assertEquals("bob", second.name)
        assertEquals(OtpAlgorithm.SHA256, second.algorithm)
        assertEquals(OtpType.HOTP, second.type)
        assertEquals(8, second.digits)
        assertEquals(42L, second.counter)
        assertEquals(Base32.encode("secondarysecret".toByteArray(Charsets.US_ASCII)), second.secret)
    }

    @Test
    fun parsesBatchMetadata() {
        val data = encode(
            listOf(otpEntry(rfcSecret, "a", "B")),
            batchSize = 3,
            batchIndex = 1,
            batchId = 4660
        )
        val frame = OtpUri.parseMigrationData(data)
        assertTrue(frame.isBatched)
        assertEquals(3, frame.batchSize)
        assertEquals(1, frame.batchIndex)
        assertEquals(4660, frame.batchId)
    }

    @Test
    fun singleFrameIsNotBatched() {
        val frame = OtpUri.parseMigrationData(encode(listOf(otpEntry(rfcSecret, "a", "B"))))
        assertEquals(false, frame.isBatched)
        assertFalse(TransferSession().also { it.acceptFrame(frame) }.isBatched)
    }

    @Test
    fun sessionMergesBatchesAndIgnoresDuplicates() {
        val session = TransferSession()
        val batchOne = encode(
            listOf(otpEntry(rfcSecret, "a", "Alpha"), otpEntry(rfcSecret, "b", "Beta")),
            batchSize = 2,
            batchIndex = 0,
            batchId = 7
        )
        val batchTwo = encode(
            listOf(otpEntry("other".toByteArray(), "c", "Gamma")),
            batchSize = 2,
            batchIndex = 1,
            batchId = 7
        )

        assertEquals(2, session.acceptMigrationData(batchOne))
        assertEquals(2, session.accounts.size)
        // Scanning the same QR again adds nothing.
        assertEquals(0, session.acceptMigrationData(batchOne))
        assertEquals(1, session.acceptMigrationData(batchTwo))
        assertEquals(3, session.accounts.size)
        assertEquals(2, session.batchesReceived)
        assertTrue(session.isBatched)
        assertEquals(2, session.batchSize)

        session.clear()
        assertTrue(session.isEmpty)
        assertFalse(session.isBatched)
        assertEquals(0, session.batchSize)
    }

    @Test
    fun newOnlyFiltersAccountsAlreadyInVault() {
        val frame = OtpUri.parseMigrationData(
            encode(listOf(otpEntry(rfcSecret, "a", "Alpha"), otpEntry("x".toByteArray(), "c", "Gamma")))
        )
        val existing = listOf(frame.accounts[0])
        val fresh = TransferSession.newOnly(frame.accounts, existing)
        assertEquals(1, fresh.size)
        assertEquals("Gamma", fresh.first().issuer)
    }




}
