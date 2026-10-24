package org.opendnevnik.shared.schedule

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class Weekday {
    @SerialName("monday") MONDAY,
    @SerialName("tuesday") TUESDAY,
    @SerialName("wednesday") WEDNESDAY,
    @SerialName("thursday") THURSDAY,
    @SerialName("friday") FRIDAY,
    @SerialName("saturday") SATURDAY,
    @SerialName("sunday") SUNDAY,
}

/** Weekly template in Moscow local time. Missing days do not imply school holidays. */
@Serializable
data class Schedule(
    val schemaVersion: Int,
    val name: String,
    val timeZone: String,
    val days: List<ScheduleDay> = emptyList(),
    val weeks: List<ScheduleWeek> = emptyList(),
)

@Serializable
data class ScheduleWeek(val number: Int, val days: List<ScheduleDay>)

/** v1 remains readable; v2 explicitly preserves each week of a repeating cycle. */
fun Schedule.effectiveWeeks(): List<ScheduleWeek> =
    if (schemaVersion == 1) listOf(ScheduleWeek(1, days)) else weeks

@Serializable
data class ScheduleDay(val day: Weekday, val lessons: List<Lesson>)

/** Subject names, including ЗС, are preserved without classification or abbreviation expansion. */
@Serializable
data class Lesson(
    val number: Int,
    val subject: String? = null,
    val startTime: String? = null,
    val endTime: String? = null,
    val room: String? = null,
    val teacher: String? = null,
    val variants: List<LessonVariant?> = emptyList(),
)

/** Parallel columns, preserved left to right without assuming the student's subgroup. */
@Serializable
data class LessonVariant(val subject: String, val room: String? = null, val teacher: String? = null)

data class ValidationIssue(val path: String, val message: String)

sealed interface ImportResult {
    data class Valid(val schedule: Schedule, val warnings: List<ValidationIssue>) : ImportResult
    data class Invalid(val issues: List<ValidationIssue>) : ImportResult
}
