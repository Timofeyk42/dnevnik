package org.opendnevnik.shared.planner

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.opendnevnik.shared.schedule.*

@Serializable enum class EventKind { EVENT, TEST, HOMEWORK, LESSON }
@Serializable enum class ThemeMode { SYSTEM, LIGHT, DARK }
@Serializable data class Preferences(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColors: Boolean = true,
    val compact: Boolean = false,
    val showTeachers: Boolean = true,
)

/** A remote key must include the account/profile namespace; never match by title. */
@Serializable data class EventSource(val key: String, val date: String, val title: String)
@Serializable data class PlannerEvent(
    val id: String,
    val title: String,
    val date: String,
    val kind: EventKind = EventKind.EVENT,
    val startTime: String? = null,
    val endTime: String? = null,
    val notes: String = "",
    val done: Boolean = false,
    val source: EventSource? = null,
    val sourceMissing: Boolean = false,
)
@Serializable data class PlannerData(
    val version: Int = 1,
    val events: List<PlannerEvent> = emptyList(),
    val preferences: Preferences = Preferences(),
)

/** ISO date validation without a platform calendar dependency, shared with SwiftUI. */
object PlannerRules {
    fun validDate(value: String): Boolean {
        if (!Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}").matches(value)) return false
        val year = value.take(4).toInt()
        val month = value.substring(5, 7).toInt()
        val day = value.takeLast(2).toInt()
        if (year !in 1900..9999 || month !in 1..12) return false
        val leap = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)
        return day in 1..listOf(31, if (leap) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)[month - 1]
    }
    fun validate(event: PlannerEvent): String? = when {
        event.id.isBlank() || event.id.length > 200 -> "Некорректный идентификатор события"
        event.title.isBlank() || event.title.length > 120 || event.title.any { it.code < 32 } -> "Название: от 1 до 120 символов"
        !validDate(event.date) -> "Дата должна быть в формате ГГГГ-ММ-ДД"
        event.notes.length > 4000 -> "Заметка: не больше 4000 символов"
        (event.startTime == null) != (event.endTime == null) -> "Укажите начало и окончание либо оставьте оба поля пустыми"
        event.startTime != null && (!Regex("(?:[01][0-9]|2[0-3]):[0-5][0-9]").matches(event.startTime) ||
            !Regex("(?:[01][0-9]|2[0-3]):[0-5][0-9]").matches(event.endTime.orEmpty()) || event.startTime >= event.endTime.orEmpty()) -> "Время: ЧЧ:ММ, окончание позже начала"
        event.source?.let { it.key.isBlank() || it.key.length > 500 || !validDate(it.date) || it.title.isBlank() || it.title.length > 120 } == true -> "Некорректные сведения об источнике"
        else -> null
    }
    fun validate(data: PlannerData): String? = when {
        data.version != 1 -> "Неизвестная версия планировщика"
        data.events.size > 1000 -> "Не больше 1000 событий"
        data.events.map { it.id }.distinct().size != data.events.size -> "Повторяются идентификаторы событий"
        data.events.mapNotNull { it.source?.key }.let { it.distinct().size != it.size } -> "Повторяются события источника"
        else -> data.events.firstNotNullOfOrNull(::validate)
    }
    fun put(data: PlannerData, event: PlannerEvent): PlannerData {
        require(validate(event) == null) { validate(event).orEmpty() }
        val next = data.copy(events = (data.events.filterNot { it.id == event.id } + event).sortedWith(compareBy({ it.date }, { it.startTime ?: "" }, { it.id })))
        require(validate(next) == null) { validate(next).orEmpty() }
        return next
    }
    fun resetToSource(event: PlannerEvent): PlannerEvent = event.source?.let {
        event.copy(date = it.date, title = it.title)
    } ?: event

    /** Only call with a complete, validated source snapshot for this namespace, never on network failure. */
    fun reconcile(data: PlannerData, namespace: String, incoming: List<PlannerEvent>): PlannerData {
        require(namespace.isNotBlank() && namespace.endsWith(":"))
        require(incoming.all { it.source?.key?.startsWith(namespace) == true && validate(it) == null })
        require(incoming.map { it.source!!.key }.distinct().size == incoming.size)
        val keys = incoming.map { it.source!!.key }.toSet()
        var result = data.copy(events = data.events.map {
            if (it.source?.key?.startsWith(namespace) == true) it.copy(sourceMissing = it.source.key !in keys) else it
        })
        incoming.forEach { remote ->
            val old = result.events.find { it.source?.key == remote.source!!.key }
            val merged = if (old == null) remote else old.copy(
                title = if (old.title == old.source!!.title) remote.title else old.title,
                date = if (old.date == old.source!!.date) remote.date else old.date,
                source = remote.source, sourceMissing = false,
            )
            result = put(result, merged)
        }
        return result
    }
}

object PlannerJson {
    const val MAX_BYTES = 2_097_152
    private val json = Json { prettyPrint = true; encodeDefaults = true; ignoreUnknownKeys = false }
    fun encode(data: PlannerData): String {
        require(PlannerRules.validate(data) == null)
        return json.encodeToString(PlannerData.serializer(), data).also { require(it.encodeToByteArray().size <= MAX_BYTES) }
    }
    fun decode(text: String): PlannerData {
        require(text.encodeToByteArray().size <= MAX_BYTES)
        require(ScheduleJson.preflight(text) == null)
        return json.decodeFromString<PlannerData>(text).also { require(PlannerRules.validate(it) == null) }
    }
}

/** Edits preserve every other week, lesson and parallel column. Numbers are identities within a day. */
object ScheduleEditor {
    fun empty(name: String = "Моё расписание"): Schedule = Schedule(2, name, "Europe/Moscow", weeks = listOf(ScheduleWeek(1, listOf(ScheduleDay(Weekday.MONDAY, emptyList())))))
    fun put(schedule: Schedule, week: Int, day: Weekday, previousNumber: Int?, lesson: Lesson?): ImportResult {
        val weeks = schedule.effectiveWeeks()
        require(weeks.any { it.number == week })
        val next = weeks.map { w ->
            if (w.number != week) w else {
                val current = w.days.find { it.day == day } ?: ScheduleDay(day, emptyList())
                if (lesson != null && current.lessons.any { it.number == lesson.number && it.number != previousNumber }) {
                    return ImportResult.Invalid(listOf(ValidationIssue("number", "Этот номер урока уже занят")))
                }
                val lessons = current.lessons.filterNot { it.number == previousNumber } + listOfNotNull(lesson)
                w.copy(days = w.days.filterNot { it.day == day } + current.copy(lessons = lessons))
            }
        }
        val edited = if (schedule.schemaVersion == 1) schedule.copy(days = next.single().days) else schedule.copy(weeks = next)
        return ScheduleJson.parse(ScheduleJson.encode(edited))
    }
}
