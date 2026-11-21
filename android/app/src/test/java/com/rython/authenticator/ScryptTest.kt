package com.rython.authenticator

import com.rython.authenticator.core.Scrypt
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** RFC 7914 test vectors, so the hand-written scrypt stays correct. */
class ScryptTest {

    private fun hex(value: String): ByteArray =
        value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private fun derive(
        password: String,
        salt: String,
        n: Int,
        r: Int,
        p: Int,
        length: Int = 64
    ): ByteArray = Scrypt.derive(
        password = password.toByteArray(Charsets.UTF_8),
        salt = salt.toByteArray(Charsets.UTF_8),
        n = n,
        r = r,
        p = p,
        keyLength = length
    )

    @Test
    fun rfc7914Vector1() {
        val expected = hex(
            "77d6576238657b203b19ca42c18a0497f16b4844e3074ae8dfdffa3fede21442" +
                "fcd0069ded0948f8326a753a0fc81f17e8d3e0fb2e0d3628cf35e20c38d18906"
        )
        assertArrayEquals(expected, derive("", "", n = 16, r = 1, p = 1))
    }

    @Test
    fun rfc7914Vector2() {
        val expected = hex(
            "fdbabe1c9d3472007856e7190d01e9fe7c6ad7cbc8237830e77376634b373162" +
                "2eaf30d92e22a3886ff109279d9830dac727afb94a83ee6d8360cbdfa2cc0640"
        )
        assertArrayEquals(expected, derive("password", "NaCl", n = 1024, r = 8, p = 16))
    }

    @Test
    fun rfc7914Vector3() {
        val expected = hex(
            "7023bdcb3afd7348461c06cd81fd38ebfda8fbba904f8e3ea9b543f6545da1f2" +
                "d5432955613f0fcf62d49705242a9af9e61e85dc0d651e40dfcf017b45575887"
        )
        assertArrayEquals(
            expected,
            derive("pleaseletmein", "SodiumChloride", n = 16384, r = 8, p = 1)
        )
    }

    @Test
    fun outputLengthIsHonoured() {
        val short = derive("password", "salt", n = 16, r = 1, p = 1, length = 32)
        val long = derive("password", "salt", n = 16, r = 1, p = 1, length = 64)
        assertArrayEquals(long.copyOfRange(0, 32), short)
        assertFalse(short.contentEquals(long.copyOfRange(32, 64)))
    }

    @Test
    fun differentSaltsGiveDifferentKeys() {
        val a = derive("password", "salt-one", n = 16, r = 1, p = 1, length = 32)
        val b = derive("password", "salt-two", n = 16, r = 1, p = 1, length = 32)
        assertFalse(a.contentEquals(b))
    }
}
