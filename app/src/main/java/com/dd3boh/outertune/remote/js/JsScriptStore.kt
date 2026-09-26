package com.dd3boh.outertune.remote.js

import android.content.Context
import java.io.File

/**
 * Stores user-imported JS source scripts as plain files under
 * `filesDir/user_scripts/<id>.js`.
 *
 * Script bodies can be large (tens/hundreds of KB), so they do NOT live in SharedPreferences;
 * only the metadata (id, name, enabled, scriptUrl) is kept in [com.dd3boh.outertune.remote.CustomSourceStore].
 */
class JsScriptStore(private val context: Context) {

    private val dir: File = File(context.filesDir, "user_scripts").apply { mkdirs() }

    private fun fileFor(id: String) = File(dir, "$id.js")

    fun save(id: String, content: String) {
        fileFor(id).writeText(content, Charsets.UTF_8)
    }

    fun read(id: String): String? {
        val f = fileFor(id)
        if (!f.exists() || !f.isFile) return null
        return runCatching { f.readText(Charsets.UTF_8) }.getOrNull()
    }

    fun exists(id: String): Boolean = fileFor(id).exists()

    fun delete(id: String) {
        runCatching { fileFor(id).delete() }
    }
}
