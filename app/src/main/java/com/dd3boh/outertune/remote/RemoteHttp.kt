package com.dd3boh.outertune.remote

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Minimal synchronous HTTP wrapper used by all remote sources.
 * The ResolvingDataSource callback already runs on a background thread, so
 * synchronous calls are fine; we expose them as plain functions.
 */
object RemoteHttp {

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val FORM = "application/x-www-form-urlencoded".toMediaType()
    private val JSON = "application/json; charset=utf-8".toMediaType()

    fun get(
        url: String,
        headers: Map<String, String> = emptyMap(),
    ): String {
        val builder = Request.Builder().url(url).get()
        headers.forEach { (k, v) -> builder.header(k, v) }
        client.newCall(builder.build()).execute().use { resp ->
            return resp.body?.string().orEmpty()
        }
    }

    fun getBytes(
        url: String,
        headers: Map<String, String> = emptyMap(),
    ): ByteArray {
        val builder = Request.Builder().url(url).get()
        headers.forEach { (k, v) -> builder.header(k, v) }
        client.newCall(builder.build()).execute().use { resp ->
            return resp.body?.bytes() ?: ByteArray(0)
        }
    }

    fun postForm(
        url: String,
        form: Map<String, String>,
        headers: Map<String, String> = emptyMap(),
    ): String {
        val body = form.entries.joinToString("&") { (k, v) ->
            "${k}=${java.net.URLEncoder.encode(v, "UTF-8")}"
        }.toRequestBody(FORM)
        val builder = Request.Builder().url(url).post(body)
        headers.forEach { (k, v) -> builder.header(k, v) }
        client.newCall(builder.build()).execute().use { resp ->
            return resp.body?.string().orEmpty()
        }
    }

    fun postJson(
        url: String,
        json: String,
        headers: Map<String, String> = emptyMap(),
    ): String {
        val builder = Request.Builder().url(url).post(json.toRequestBody(JSON))
        headers.forEach { (k, v) -> builder.header(k, v) }
        client.newCall(builder.build()).execute().use { resp ->
            return resp.body?.string().orEmpty()
        }
    }
}
