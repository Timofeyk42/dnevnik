package org.opendnevnik.android.data

import android.content.Context
import android.util.AtomicFile
import org.opendnevnik.shared.schedule.Schedule
import org.opendnevnik.shared.schedule.ScheduleJson
import java.io.File
import java.io.InputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** Private, backup-excluded storage. AtomicFile prevents a failed import from replacing valid data. */
class ScheduleFileStore(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "schedule-v1.json"))

    fun read(): String? = try {
        file.openRead().use(::readBoundedUtf8)
    } catch (error: java.io.FileNotFoundException) {
        if (file.baseFile.exists()) throw error
        null
    }

    fun save(schedule: Schedule) {
        val output = file.startWrite()
        try {
            output.write(ScheduleJson.encode(schedule).toByteArray(Charsets.UTF_8))
            file.finishWrite(output)
        } catch (error: Exception) {
            file.failWrite(output)
            throw error
        }
    }

    fun clear() {
        file.delete()
        check(!file.baseFile.exists()) { "Schedule could not be removed" }
    }
}

/** Never trust provider MIME type, filename or declared size; bound the actual stream. */
fun readBoundedUtf8(input: InputStream, maxBytes: Int = ScheduleJson.MAX_BYTES): String {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (output.size() <= maxBytes) {
        val remaining = maxBytes + 1 - output.size()
        val count = input.read(buffer, 0, minOf(buffer.size, remaining))
        if (count < 0) break
        if (count == 0) {
            val single = input.read()
            if (single < 0) break
            output.write(single)
        } else output.write(buffer, 0, count)
    }
    val bytes = output.toByteArray()
    require(bytes.size <= maxBytes) { "File too large" }
    return Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes)).toString()
}
