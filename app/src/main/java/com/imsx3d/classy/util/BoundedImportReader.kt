package com.imsx3d.classy.util

import java.io.ByteArrayOutputStream
import java.io.InputStream

object BoundedImportReader {
    const val MAX_BYTES = 2 * 1024 * 1024

    fun read(input: InputStream, limit: Int = MAX_BYTES): String {
        require(limit > 0)
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var count = 0
        while (true) {
            if (Thread.currentThread().isInterrupted) throw java.io.InterruptedIOException()
            val n = input.read(buffer, 0, minOf(buffer.size, limit - count + 1))
            if (n < 0) break
            count += n
            require(count <= limit) { "导入文件过大，请选择不超过 2 MB 的课表文件" }
            output.write(buffer, 0, n)
        }
        return output.toString(Charsets.UTF_8.name()).removePrefix("\uFEFF")
    }
}
