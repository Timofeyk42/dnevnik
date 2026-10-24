package org.opendnevnik.android.data

import android.content.Context
import android.util.AtomicFile
import org.opendnevnik.shared.planner.PlannerData
import org.opendnevnik.shared.planner.PlannerJson
import java.io.File

class PlannerFileStore(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "planner-v1.json"))
    fun read(): PlannerData = try {
        file.openRead().use { PlannerJson.decode(readBoundedUtf8(it, PlannerJson.MAX_BYTES)) }
    } catch (error: java.io.FileNotFoundException) {
        if (file.baseFile.exists()) throw error
        PlannerData()
    }
    fun save(data: PlannerData) {
        val text = PlannerJson.encode(data)
        val stream = file.startWrite()
        try {
            stream.write(text.toByteArray(Charsets.UTF_8))
            file.finishWrite(stream)
        } catch (error: Exception) {
            file.failWrite(stream)
            throw error
        }
    }
}
