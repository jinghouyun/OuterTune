package com.dd3boh.outertune.remote.crypto

import android.util.Base64
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Common crypto helpers used by the remote music sources.
 * All algorithms mirror the lx-music-mobile / NeteaseCloudMusicApi behaviour.
 */
object Crypto {

    fun md5Hex(input: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun md5Bytes(input: ByteArray): ByteArray =
        MessageDigest.getInstance("MD5").digest(input)

    fun sha1Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun base64Encode(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.NO_WRAP)

    fun base64Decode(s: String): ByteArray =
        Base64.decode(s, Base64.NO_WRAP)

    /** AES-128-CBC with PKCS5/PKCS7 padding. */
    fun aesCbcEncrypt(data: ByteArray, key: String, iv: String): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(key.toByteArray(Charsets.UTF_8), "AES"),
            IvParameterSpec(iv.toByteArray(Charsets.UTF_8))
        )
        return cipher.doFinal(data)
    }

    /** AES-128-ECB, no padding (input must already be block aligned). */
    fun aesEcbEncryptNoPadding(data: String, keyBytes: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/ECB/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "AES"))
        // pad with zero bytes to 16-byte boundary
        val padded = ByteArray(((data.length + 15) / 16) * 16)
        val src = data.toByteArray(Charsets.UTF_8)
        System.arraycopy(src, 0, padded, 0, src.size)
        return cipher.doFinal(padded)
    }

    /**
     * RSA / ECB / PKCS1Padding encrypts [data] with the given X.509 base64 public key,
     * returns lowercase hex.
     */
    fun rsaEncryptHex(data: ByteArray, publicKeyBase64: String): String {
        val keyBytes = Base64.decode(publicKeyBase64, Base64.DEFAULT)
        val spec = X509EncodedKeySpec(keyBytes)
        val key = KeyFactory.getInstance("RSA").generatePublic(spec)
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        return cipher.doFinal(data).joinToString("") { "%02x".format(it) }
    }

    fun bytesToHex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02x".format(it) }
}
