package com.dd3boh.outertune.remote.crypto

import android.util.Base64

/**
 * QQ Music zzcSign algorithm (spec section 8.2).
 * SHA1 the request body, then pick chars by fixed indexes and XOR the
 * scramble table with decoded hex pairs, base64 the result.
 */
object TxCrypto {

    private val PART_1_INDEXES = intArrayOf(23, 14, 6, 36, 16, 40, 7, 19)
    private val PART_2_INDEXES = intArrayOf(16, 1, 32, 12, 19, 27, 8, 5)
    private val SCRAMBLE = intArrayOf(
        89, 39, 179, 150, 218, 82, 58, 252, 177, 52,
        186, 123, 120, 64, 242, 133, 143, 161, 121, 179
    )

    fun zzcSign(text: String): String {
        val hash = Crypto.sha1Hex(text) // 40 lowercase hex chars
        val part1 = PART_1_INDEXES.map { hash[it] }.joinToString("")
        val part2 = PART_2_INDEXES.map { hash[it] }.joinToString("")
        val xored = ByteArray(SCRAMBLE.size)
        for (i in SCRAMBLE.indices) {
            val hexPair = hash.substring(i * 2, i * 2 + 2)
            val byteVal = hexPair.toInt(16)
            xored[i] = (SCRAMBLE[i] xor byteVal).toByte()
        }
        val part3 = Base64.encodeToString(xored, Base64.NO_WRAP)
            .replace("/", "").replace("+", "").replace("=", "")
        return "zzc$part1$part3$part2".lowercase()
    }
}
