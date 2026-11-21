package com.rython.authenticator.core

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * scrypt (RFC 7914) key derivation: memory-hard and much more expensive to attack on
 * GPUs/ASICs than plain PBKDF2. It is built here on top of the JCE HMAC-SHA256 PRF so
 * that export files need no extra native dependency.
 *
 * Verified against the RFC 7914 test vectors in `ScryptTest`.
 */
object Scrypt {

    /** Memory / CPU cost used for backup files: 32 MiB, about a second on a phone. */
    const val DEFAULT_N = 32_768
    const val DEFAULT_R = 8
    const val DEFAULT_P = 1

    fun derive(
        password: ByteArray,
        salt: ByteArray,
        n: Int = DEFAULT_N,
        r: Int = DEFAULT_R,
        p: Int = DEFAULT_P,
        keyLength: Int = 32
    ): ByteArray {
        require(n > 1 && (n and (n - 1)) == 0) { "n must be a power of two greater than 1" }
        require(r >= 1) { "r must be positive" }
        require(p >= 1) { "p must be positive" }
        require(keyLength >= 1) { "keyLength must be positive" }

        val blockSize = 128 * r
        val b = pbkdf2(password, salt, 1, p * blockSize)
        for (i in 0 until p) romix(b, i * blockSize, r, n)
        return pbkdf2(password, b, 1, keyLength)
    }

    // ------------------------------------------------------------------ ROMix

    private fun romix(b: ByteArray, offset: Int, r: Int, n: Int) {
        val blockSize = 128 * r
        val x = ByteArray(blockSize)
        System.arraycopy(b, offset, x, 0, blockSize)

        val v = ByteArray(blockSize * n)
        val mixed = ByteArray(blockSize)
        val words = IntArray(16)
        val scratch = IntArray(16)

        for (i in 0 until n) {
            System.arraycopy(x, 0, v, i * blockSize, blockSize)
            blockMix(x, mixed, r, words, scratch)
            System.arraycopy(mixed, 0, x, 0, blockSize)
        }

        for (i in 0 until n) {
            val j = leInt(x, (2 * r - 1) * 64) and (n - 1)
            val vj = j * blockSize
            for (k in 0 until blockSize) {
                x[k] = (x[k].toInt() xor v[vj + k].toInt()).toByte()
            }
            blockMix(x, mixed, r, words, scratch)
            System.arraycopy(mixed, 0, x, 0, blockSize)
        }

        System.arraycopy(x, 0, b, offset, blockSize)
    }

    /** BlockMix: X = Salsa20/8(X xor B[i]); output in the scrypt interleaved order. */
    private fun blockMix(input: ByteArray, output: ByteArray, r: Int, x: IntArray, t: IntArray) {
        loadBlock(input, (2 * r - 1) * 64, x)
        for (i in 0 until 2 * r) {
            val base = i * 64
            for (k in 0 until 16) x[k] = x[k] xor leInt(input, base + k * 4)
            salsa20Core(x, t)
            // B' = (Y0, Y2, ..., Y2r-2, Y1, Y3, ..., Y2r-1)
            val even = i shr 1
            val destination = if (i and 1 == 0) even * 64 else (r + even) * 64
            storeBlock(x, output, destination)
        }
    }

    // --------------------------------------------------------------- Salsa20/8

    private fun salsa20Core(x: IntArray, t: IntArray) {
        System.arraycopy(x, 0, t, 0, 16)
        // Salsa20/8: four double rounds.
        repeat(4) {
            t[4] = t[4] xor Integer.rotateLeft(t[0] + t[12], 7)
            t[8] = t[8] xor Integer.rotateLeft(t[4] + t[0], 9)
            t[12] = t[12] xor Integer.rotateLeft(t[8] + t[4], 13)
            t[0] = t[0] xor Integer.rotateLeft(t[12] + t[8], 18)
            t[9] = t[9] xor Integer.rotateLeft(t[5] + t[1], 7)
            t[13] = t[13] xor Integer.rotateLeft(t[9] + t[5], 9)
            t[1] = t[1] xor Integer.rotateLeft(t[13] + t[9], 13)
            t[5] = t[5] xor Integer.rotateLeft(t[1] + t[13], 18)
            t[14] = t[14] xor Integer.rotateLeft(t[10] + t[6], 7)
            t[2] = t[2] xor Integer.rotateLeft(t[14] + t[10], 9)
            t[6] = t[6] xor Integer.rotateLeft(t[2] + t[14], 13)
            t[10] = t[10] xor Integer.rotateLeft(t[6] + t[2], 18)
            t[3] = t[3] xor Integer.rotateLeft(t[15] + t[11], 7)
            t[7] = t[7] xor Integer.rotateLeft(t[3] + t[15], 9)
            t[11] = t[11] xor Integer.rotateLeft(t[7] + t[3], 13)
            t[15] = t[15] xor Integer.rotateLeft(t[11] + t[7], 18)

            t[1] = t[1] xor Integer.rotateLeft(t[0] + t[3], 7)
            t[2] = t[2] xor Integer.rotateLeft(t[1] + t[0], 9)
            t[3] = t[3] xor Integer.rotateLeft(t[2] + t[1], 13)
            t[0] = t[0] xor Integer.rotateLeft(t[3] + t[2], 18)
            t[6] = t[6] xor Integer.rotateLeft(t[5] + t[4], 7)
            t[7] = t[7] xor Integer.rotateLeft(t[6] + t[5], 9)
            t[4] = t[4] xor Integer.rotateLeft(t[7] + t[6], 13)
            t[5] = t[5] xor Integer.rotateLeft(t[4] + t[7], 18)
            t[11] = t[11] xor Integer.rotateLeft(t[10] + t[9], 7)
            t[8] = t[8] xor Integer.rotateLeft(t[11] + t[10], 9)
            t[9] = t[9] xor Integer.rotateLeft(t[8] + t[11], 13)
            t[10] = t[10] xor Integer.rotateLeft(t[9] + t[8], 18)
            t[12] = t[12] xor Integer.rotateLeft(t[15] + t[14], 7)
            t[13] = t[13] xor Integer.rotateLeft(t[12] + t[15], 9)
            t[14] = t[14] xor Integer.rotateLeft(t[13] + t[12], 13)
            t[15] = t[15] xor Integer.rotateLeft(t[14] + t[13], 18)
        }
        for (i in 0 until 16) x[i] = x[i] + t[i]
    }

    // ------------------------------------------------------------ PBKDF2-HMAC

    private fun pbkdf2(password: ByteArray, salt: ByteArray, iterations: Int, length: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        // HMAC pads a short key with zeros, so a single zero byte is the same key as
        // an empty one (which SecretKeySpec refuses).
        val key = if (password.isEmpty()) ByteArray(1) else password
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        val hLen = mac.macLength
        val out = ByteArray(length)
        var offset = 0
        var block = 1
        while (offset < length) {
            mac.reset()
            mac.update(salt)
            mac.update((block ushr 24).toByte())
            mac.update((block ushr 16).toByte())
            mac.update((block ushr 8).toByte())
            mac.update(block.toByte())
            var u = mac.doFinal()
            val acc = u.copyOf()
            for (i in 1 until iterations) {
                u = mac.doFinal(u)
                for (k in acc.indices) acc[k] = (acc[k].toInt() xor u[k].toInt()).toByte()
            }
            val take = minOf(hLen, length - offset)
            System.arraycopy(acc, 0, out, offset, take)
            offset += take
            block++
        }
        return out
    }

    // ---------------------------------------------------------------- helpers

    private fun leInt(b: ByteArray, o: Int): Int =
        (b[o].toInt() and 0xFF) or
            ((b[o + 1].toInt() and 0xFF) shl 8) or
            ((b[o + 2].toInt() and 0xFF) shl 16) or
            ((b[o + 3].toInt() and 0xFF) shl 24)

    private fun loadBlock(bytes: ByteArray, offset: Int, words: IntArray) {
        for (k in 0 until 16) words[k] = leInt(bytes, offset + k * 4)
    }

    private fun storeBlock(words: IntArray, bytes: ByteArray, offset: Int) {
        for (k in 0 until 16) {
            val v = words[k]
            val o = offset + k * 4
            bytes[o] = v.toByte()
            bytes[o + 1] = (v ushr 8).toByte()
            bytes[o + 2] = (v ushr 16).toByte()
            bytes[o + 3] = (v ushr 24).toByte()
        }
    }
}
