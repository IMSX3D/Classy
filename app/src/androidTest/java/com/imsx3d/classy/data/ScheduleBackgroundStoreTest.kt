package com.imsx3d.classy.data

import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ScheduleBackgroundStoreTest {
    @Test fun boundsDecodePreservesPreviousOnFailureAndClearsCleanly() = runBlocking {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(base.cacheDir, "background-test-${System.nanoTime()}").apply { mkdirs() }
        val context = object : ContextWrapper(base) {
            override fun getFilesDir() = directory
            override fun getCacheDir() = directory
        }
        try {
            val source = File(directory, "source.png")
            val bitmap = Bitmap.createBitmap(3000, 1000, Bitmap.Config.ARGB_8888)
            source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            ScheduleBackgroundStore.import(context, Uri.fromFile(source))
            val saved = ScheduleBackgroundStore.file(context)
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(saved.path, options)
            assertTrue(options.outWidth in 1..2048)
            assertEquals(3, options.outWidth / options.outHeight)
            val before = saved.readBytes()
            val invalid = File(directory, "invalid.txt").apply { writeText("not an image") }
            try { ScheduleBackgroundStore.import(context, Uri.fromFile(invalid)); fail("invalid image accepted") }
            catch (_: IllegalArgumentException) { }
            assertArrayEquals(before, saved.readBytes())
            val oversize = File(directory, "oversize.png")
            java.io.RandomAccessFile(oversize, "rw").use { it.setLength(21L * 1024 * 1024) }
            try { ScheduleBackgroundStore.import(context, Uri.fromFile(oversize)); fail("oversized image accepted") }
            catch (_: IllegalArgumentException) { }
            assertArrayEquals(before, saved.readBytes())
            assertFalse(directory.listFiles()!!.any { it.name.startsWith("wallpaper-") })
            ScheduleBackgroundStore.clear(context)
            assertFalse(saved.exists())
        } finally { directory.deleteRecursively() }
    }
}
