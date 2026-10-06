package com.imsx3d.classy.util

import android.content.Context
import java.io.File
import java.util.UUID

/** Small token in the Intent; bounded document on disk survives process recreation.
 * Only confirmation applies data. Reopening an unfinished draft never imports it automatically.
 */
object PendingImportStore {
    private fun prefs(context: Context) = context.getSharedPreferences("pending_external_import", Context.MODE_PRIVATE)
    private fun file(context: Context, token: String): File {
        require(runCatching { UUID.fromString(token).toString() == token }.getOrDefault(false))
        return File(File(context.filesDir, "pending-imports").apply { mkdirs() }, "$token.txt")
    }
    fun active(context: Context): String? = prefs(context).getString("token", null)

    @Synchronized
    fun save(context: Context, text: String): String {
        val bytes = text.toByteArray(Charsets.UTF_8)
        require(bytes.size <= BoundedImportReader.MAX_BYTES) { "导入文件过大，请选择不超过 2 MB 的课表文件" }
        val token = UUID.randomUUID().toString()
        val target = android.util.AtomicFile(file(context, token))
        val out = target.startWrite()
        try {
            out.write(bytes)
            target.finishWrite(out)
        } catch (e: Exception) {
            target.failWrite(out)
            throw e
        }
        val previous = active(context)
        check(prefs(context).edit().putString("token", token).commit())
        previous?.let { runCatching { file(context, it).delete() } }
        return token
    }

    fun read(context: Context, token: String): String =
        file(context, token).inputStream().use { BoundedImportReader.read(it) }

    @Synchronized
    fun discard(context: Context, token: String) {
        if (active(context) == token) check(prefs(context).edit().remove("token").commit())
        file(context, token).delete()
    }
}
