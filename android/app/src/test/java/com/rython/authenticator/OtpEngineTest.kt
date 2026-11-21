package com.rython.authenticator

import com.rython.authenticator.core.Base32
import com.rython.authenticator.core.OtpAlgorithm
import com.rython.authenticator.core.OtpEngine
import com.rython.authenticator.core.OtpType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OtpEngineTest {

    private val rfcSecret = Base32.decode("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ")
    private val rfcSecret32 = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"

    @Test
    fun base32RoundTrip() {
        assertEquals("12345678901234567890", String(rfcSecret, Charsets.US_ASCII))
        assertEquals(rfcSecret32, Base32.encode(rfcSecret))
        assertTrue(Base32.decode("gezd gnbv gy3t qojq gezd gnbv gy3t qojq").contentEquals(rfcSecret))
    }

    @Test
    fun rfc4226HotpVectors() {
        val expected = listOf(
            "755224", "287082", "359152", "969429", "338314",
            "254676", "287922", "162583", "399871", "520489"
        )
        expected.forEachIndexed { counter, code ->
            assertEquals(
                "counter=$counter",
                code,
                OtpEngine.hotp(rfcSecret, counter.toLong(), OtpAlgorithm.SHA1, 6)
            )
        }
    }

    @Test
    fun rfc6238TotpVectors() {
        // time(seconds) -> 8 digit SHA1 code from RFC 6238 appendix B
        val vectors = listOf(
            59L to "94287082",
            1111111109L to "07081804",
            1111111111L to "14050471",
            1234567890L to "89005924",
            2000000000L to "69279037",
            20000000000L to "65353130"
        )
        vectors.forEach { (seconds, expected) ->
            assertEquals(
                "t=$seconds",
                expected,
                OtpEngine.code(
                    secretBase32 = rfcSecret32,
                    type = OtpType.TOTP,
                    algorithm = OtpAlgorithm.SHA1,
                    digits = 8,
                    period = 30,
                    counter = 0,
                    nowMillis = seconds * 1000L
                )
            )
        }
    }

    @Test
    fun steamCodeShape() {
        val code = OtpEngine.code(
            secretBase32 = rfcSecret32,
            type = OtpType.STEAM,
            algorithm = OtpAlgorithm.SHA1,
            digits = 5,
            period = 30,
            counter = 0,
            nowMillis = 1_600_000_000_000L
        )
        assertEquals(5, code.length)
        assertTrue(code.all { it in "23456789BCDFGHJKMNPQRTVWXY" })
    }

    @Test
    fun secondsRemainingIsBounded() {
        val remaining = OtpEngine.secondsRemaining(30, 1_600_000_000_000L)
        assertTrue(remaining in 1..30)
    }
}
