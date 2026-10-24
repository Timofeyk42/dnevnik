package org.opendnevnik.shared.planner

import org.opendnevnik.shared.schedule.*

/** Native clients use the same validators. File-sized input belongs on a worker queue. */
class NativeDiaryBridge {
    @Throws(IllegalArgumentException::class)
    fun normalizeSchedule(text: String): String = when (val result = ScheduleJson.parse(text)) {
        is ImportResult.Valid -> ScheduleJson.encode(result.schedule)
        is ImportResult.Invalid -> throw IllegalArgumentException(result.issues.joinToString("\n") { "${it.path}: ${it.message}" })
    }
    @Throws(IllegalArgumentException::class)
    fun normalizePlanner(text: String): String = try {
        PlannerJson.encode(PlannerJson.decode(text))
    } catch (_: Exception) { throw IllegalArgumentException("Некорректная копия планировщика") }
    fun emptySchedule(): String = ScheduleJson.encode(ScheduleEditor.empty())
    fun emptyPlanner(): String = PlannerJson.encode(PlannerData())
    fun photoPrompt(): String = PhotoPrompt.text
}
