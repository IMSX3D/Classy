package com.imsx3d.classy.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** A bounded private copy: no retained provider permission, remote URL or full-size decode. */
object ScheduleBackgroundStore {
    private val mutex = Mutex()
    val revision = MutableStateFlow(0L)
    fun file(context: Context) = File(context.filesDir, "schedule-background.jpg")

    suspend fun import(context: Context, uri: Uri) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val source = File.createTempFile("wallpaper-", ".source", context.cacheDir)
            var decoded: Bitmap? = null
            var oriented: Bitmap? = null
            try {
                context.contentResolver.openInputStream(uri).use { input ->
                    requireNotNull(input)
                    source.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var total = 0
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            require(total <= 20 * 1024 * 1024) { "image too large" }
                            output.write(buffer, 0, count)
                        }
                    }
                }
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(source.path, bounds)
                require(bounds.outWidth > 0 && bounds.outHeight > 0) { "invalid image" }
                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2048) sample *= 2
                val bitmap = requireNotNull(BitmapFactory.decodeFile(source.path,
                    BitmapFactory.Options().apply { inSampleSize = sample }))
                decoded = bitmap
                val exif = runCatching { ExifInterface(source.path).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }.getOrDefault(1)
                val matrix = Matrix().apply {
                    when (exif) {
                        2 -> setScale(-1f, 1f)
                        3 -> setRotate(180f)
                        4 -> setScale(1f, -1f)
                        5 -> { setRotate(90f); postScale(-1f, 1f) }
                        6 -> setRotate(90f)
                        7 -> { setRotate(270f); postScale(-1f, 1f) }
                        8 -> setRotate(270f)
                    }
                }
                val result = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                oriented = result
                val atomic = AtomicFile(file(context))
                val stream = atomic.startWrite()
                try {
                    check(result.compress(Bitmap.CompressFormat.JPEG, 90, stream))
                    atomic.finishWrite(stream)
                } catch (e: Exception) { atomic.failWrite(stream); throw e }
                revision.value++
            } finally {
                if (oriented !== decoded) oriented?.recycle()
                decoded?.recycle()
                source.delete()
            }
        }
    }

    suspend fun clear(context: Context) = withContext(Dispatchers.IO) {
        mutex.withLock { AtomicFile(file(context)).delete(); revision.value++ }
    }
}
