package com.dd3boh.outertune.remote.crypto

import org.json.JSONObject
import kotlin.random.Random

/**
 * NetEase Cloud Music (wy) request encryption: weapi & eapi.
 * Ported from lx-music-mobile src/utils/musicSdk/wy/utils/crypto.js
 */
object WyCrypto {

    private const val PRESET_KEY = "0CoJUm6Qyw8W8jud"
    private const val IV = "0102030405060708"
    private const val EAPI_KEY = "e82ckenh8dichen8"

    private const val PUBLIC_KEY =
        "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDgtQn2JZ34ZC28NWYpAUd98iZ37BUrX/aKzmFbt7clFSs6sXqHauqKWqdtLkF2KexO40H1YTX8z2lSgBBOAxLsvaklV8k4cBFK9snQXE9/DDaFt6Rr7iVZMldczhC0JNgTz+SHXT6CBHuX3e9SdB1Ua44oncaTWz7OBGLbCiK45wIDAQAB"

    /** weapi: returns form fields params + encSecKey. */
    fun weapi(json: JSONObject): Map<String, String> {
        val text = json.toString()
        val secretKey = random16Digit()

        // layer 1: aes-cbc(text) with preset key -> base64
        val layer1 = Crypto.base64Encode(
            Crypto.aesCbcEncrypt(text.toByteArray(Charsets.UTF_8), PRESET_KEY, IV)
        )
        // layer 2: aes-cbc(layer1 base64 string) with random secret key -> hex
        val params = Crypto.bytesToHex(
            Crypto.aesCbcEncrypt(layer1.toByteArray(Charsets.UTF_8), secretKey, IV)
        )
        // encSecKey: RSA encrypt reversed secret key bytes
        val reversed = secretKey.toByteArray(Charsets.UTF_8).reversedArray()
        val encSecKey = Crypto.rsaEncryptHex(reversed, PUBLIC_KEY)

        return mapOf("params" to params, "encSecKey" to encSecKey)
    }

    /** eapi: returns form field params (hex uppercase). [url] is the business path, e.g. /api/song/lyric/v1. */
    fun eapi(url: String, json: JSONObject): Map<String, String> {
        val text = json.toString()
        val message = "nobody${url}use${text}md5forencrypt"
        val digest = Crypto.md5Hex(message)
        val data = "$url-36cd479b6b5-$text-36cd479b6b5-$digest"
        // base64(data) then AES-128-ECB NoPadding with eapiKey bytes
        val base64Data = Crypto.base64Encode(data.toByteArray(Charsets.UTF_8))
        val encrypted = Crypto.aesEcbEncryptNoPadding(base64Data, EAPI_KEY.toByteArray(Charsets.UTF_8))
        return mapOf("params" to Crypto.bytesToHex(encrypted).uppercase())
    }

    private fun random16Digit(): String {
        val sb = StringBuilder()
        repeat(16) { sb.append(Random.nextInt(10)) }
        return sb.toString()
    }
}
