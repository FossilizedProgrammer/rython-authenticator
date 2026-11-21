package com.rython.desktop

import com.rython.desktop.core.Base32
import com.rython.desktop.core.OtpAlgorithm
import com.rython.desktop.core.OtpEngine
import com.rython.desktop.core.OtpType
import com.rython.desktop.core.OtpUri
import com.rython.desktop.data.Account
import com.rython.desktop.data.Backup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreTest {

    @Test
    fun totpMatchesRfc6238Vector() {
        // RFC 6238 appendix B: secret "12345678901234567890", SHA1, 8 digits.
        val secret = Base32.encode("12345678901234567890".toByteArray())
        val code = OtpEngine.code(
            secretBase32 = secret,
            type = OtpType.TOTP,
            algorithm = OtpAlgorithm.SHA1,
            digits = 8,
            period = 30,
            counter = 0,
            nowMillis = 59_000L
        )
        assertEquals("94287082", code)
    }

    @Test
    fun hotpMatchesRfc4226Vector() {
        val secret = Base32.encode("12345678901234567890".toByteArray())
        assertEquals(
            "755224",
            OtpEngine.code(secret, OtpType.HOTP, OtpAlgorithm.SHA1, 6, 30, 0)
        )
    }

    @Test
    fun parsesOtpAuthLink() {
        val account = OtpUri.parse(
            "otpauth://totp/GitHub:alice?secret=JBSWY3DPEHPK3PXP&issuer=GitHub&digits=8&period=60"
        ).single()
        assertEquals("GitHub", account.issuer)
        assertEquals("alice", account.name)
        assertEquals(8, account.digits)
        assertEquals(60, account.period)
        assertEquals("JBSWY3DPEHPK3PXP", account.secret)
    }

    @Test
    fun backupRoundTripNeedsBothPasswords() {
        val accounts = listOf(
            Account(issuer = "GitHub", name = "alice", secret = "JBSWY3DPEHPK3PXP"),
            Account(issuer = "Steam", name = "bob", secret = "KRSXG5CTMVRXEZLU", type = OtpType.STEAM)
        )
        val file = Backup.export(accounts, "first-password".toCharArray(), "second-password".toCharArray())

        val restored = Backup.import(file, "first-password".toCharArray(), "second-password".toCharArray())
        assertEquals(2, restored.size)
        assertEquals("GitHub", restored[0].issuer)
        assertEquals(OtpType.STEAM, restored[1].type)

        val wrong = runCatching {
            Backup.import(file, "first-password".toCharArray(), "wrong-password".toCharArray())
        }
        assertTrue("wrong password must fail", wrong.isFailure)
    }
}
