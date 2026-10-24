package org.opendnevnik.shared.schedule

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement

/** The single import boundary shared by Android and the future SwiftUI client. */
object ScheduleJson {
    const val MAX_BYTES = 262_144
    private const val MAX_DEPTH = 12
    private val json = Json {
        ignoreUnknownKeys = false
        isLenient = false
        coerceInputValues = false
        prettyPrint = true
        encodeDefaults = false
    }
    private val timePattern = Regex("(?:[01][0-9]|2[0-3]):[0-5][0-9]")

    fun parse(source: String): ImportResult {
        if (source.length > MAX_BYTES || source.encodeToByteArray().size > MAX_BYTES) {
            return invalid("$", "Файл больше 256 КиБ. Уменьшите расписание.")
        }
        val text = source.removePrefix("\uFEFF").trim()
        if (text.isEmpty()) return invalid("$", "Добавьте JSON или выберите файл.")
        preflight(text)?.let { return invalid("$", it) }
        val document: JsonObject
        val schedule = try {
            document = json.parseToJsonElement(text) as? JsonObject ?: return invalid("$", "Расписание должно быть JSON-объектом.")
            if (hasQuotedNumber(document)) return invalid("$", "Номера и schemaVersion должны быть числами, без кавычек.")
            json.decodeFromJsonElement<Schedule>(document)
        } catch (_: SerializationException) {
            return invalid("$", "Проверьте структуру JSON, типы полей и названия дней. Уберите Markdown и комментарии.")
        } catch (_: IllegalArgumentException) {
            return invalid("$", "Некорректный JSON. Проверьте кавычки, запятые и типы значений.")
        }
        if (schedule.schemaVersion == 1) {
            if ("weeks" in document) return invalid("weeks", "В версии 1 используйте days; для недель — версию 2.")
            (document["days"] as? JsonArray)?.forEach { day ->
                ((day as? JsonObject)?.get("lessons") as? JsonArray)?.forEach { lesson ->
                    if (lesson is JsonObject && "variants" in lesson) return invalid("variants", "Варианты доступны в версии 2.")
                }
            }
        } else if (schedule.schemaVersion == 2 && "days" in document) {
            return invalid("days", "В версии 2 поместите days внутри weeks.")
        }
        return validate(schedule)
    }

    fun encode(schedule: Schedule): String = json.encodeToString(Schedule.serializer(), schedule)

    private fun hasQuotedNumber(element: JsonElement): Boolean = when (element) {
        is JsonObject -> element.any { (key, value) ->
            ((key == "number" || key == "schemaVersion") && (value as? JsonPrimitive)?.isString == true) || hasQuotedNumber(value)
        }
        is JsonArray -> element.any(::hasQuotedNumber)
        else -> false
    }

    private fun validate(schedule: Schedule): ImportResult {
        val errors = mutableListOf<ValidationIssue>()
        val warnings = mutableListOf<ValidationIssue>()
        fun fail(path: String, message: String) { errors += ValidationIssue(path, message) }
        if (schedule.schemaVersion !in 1..2) fail("schemaVersion", "Поддерживаются версии 1 и 2.")
        if (!validText(schedule.name, 80)) fail("name", "Название: от 1 до 80 символов без управляющих символов.")
        if (schedule.timeZone != "Europe/Moscow") fail("timeZone", "Для МЭШ укажите Europe/Moscow.")
        if (schedule.schemaVersion == 1 && schedule.weeks.isNotEmpty()) fail("weeks", "Для нескольких недель используйте schemaVersion: 2.")
        if (schedule.schemaVersion == 2) {
            if (schedule.days.isNotEmpty()) fail("days", "В версии 2 дни находятся внутри weeks.")
            if (schedule.weeks.size !in 1..2) fail("weeks", "Укажите одну или две недели.")
            if (schedule.weeks.map { it.number }.distinct().size != schedule.weeks.size) fail("weeks", "Номера недель не должны повторяться.")
            schedule.weeks.forEachIndexed { index, week ->
                if (week.number !in 1..2) fail("weeks[$index].number", "Номер недели: 1 или 2.")
            }
            if (schedule.weeks.size == 2 && schedule.weeks.map { it.number }.toSet() != setOf(1, 2)) fail("weeks", "Для двух недель нужны номера 1 и 2.")
        }
        schedule.effectiveWeeks().forEachIndexed { weekIndex, week ->
          val weekPath = if (schedule.schemaVersion == 1) "days" else "weeks[$weekIndex].days"
          if (week.days.size !in 1..7) fail(weekPath, "Укажите от 1 до 7 дней.")
          if (week.days.map { it.day }.distinct().size != week.days.size) fail(weekPath, "Дни недели не должны повторяться внутри недели.")
          week.days.forEachIndexed { dayIndex, day ->
            val path = "$weekPath[$dayIndex].lessons"
            if (day.lessons.size > 20) fail(path, "Не больше 20 уроков в день.")
            if (day.lessons.map { it.number }.distinct().size != day.lessons.size) fail(path, "Номера уроков в одном дне должны быть уникальными.")
            var previousEnd: Int? = null
            day.lessons.sortedBy { it.number }.forEach { lesson ->
                val row = "$path[number=${lesson.number}]"
                if (lesson.number !in 0..20) fail("$row.number", "Номер урока: от 0 до 20.")
                if (lesson.variants.isEmpty()) {
                    if (lesson.subject == null || !validText(lesson.subject, 100)) fail("$row.subject", "Укажите название предмета.")
                } else {
                    if (schedule.schemaVersion == 1) fail("$row.variants", "Для параллельных столбцов используйте schemaVersion: 2.")
                    if (lesson.subject != null || lesson.room != null || lesson.teacher != null) fail(row, "При variants предмет, кабинет и учитель указываются только внутри вариантов.")
                    if (lesson.variants.size !in 2..4) fail("$row.variants", "Укажите от 2 до 4 параллельных вариантов.")
                    if (lesson.variants.all { it == null }) fail("$row.variants", "Укажите хотя бы один предмет в вариантах.")
                    lesson.variants.forEachIndexed variantLoop@{ index, variant ->
                        if (variant == null) return@variantLoop
                        if (!validText(variant.subject, 100)) fail("$row.variants[$index].subject", "Укажите название предмета.")
                        if (variant.room != null && !validText(variant.room, 40)) fail("$row.variants[$index].room", "Кабинет: от 1 до 40 символов.")
                        if (variant.teacher != null && !validText(variant.teacher, 120)) fail("$row.variants[$index].teacher", "Учитель: от 1 до 120 символов.")
                    }
                }
                if (lesson.room != null && !validText(lesson.room, 40)) fail("$row.room", "Кабинет: от 1 до 40 символов.")
                if (lesson.teacher != null && !validText(lesson.teacher, 120)) fail("$row.teacher", "Учитель: от 1 до 120 символов.")
                val start = lesson.startTime
                val end = lesson.endTime
                if (start == null && end == null) {
                    warnings += ValidationIssue(row, "Время не указано. Урок сохранится без времени.")
                } else if (start == null || end == null || !timePattern.matches(start) || !timePattern.matches(end)) {
                    fail(row, "Укажите оба времени в формате HH:mm либо оба null.")
                } else {
                    val startMinutes = minutes(start)
                    val endMinutes = minutes(end)
                    if (startMinutes >= endMinutes) fail(row, "Начало урока должно быть раньше окончания.")
                    if (previousEnd?.let { startMinutes < it } == true) fail(row, "Время уроков пересекается или не соответствует их номерам.")
                    previousEnd = maxOf(previousEnd ?: 0, endMinutes)
                }
            }
          }
        }
        if (errors.isNotEmpty()) return ImportResult.Invalid(errors)
        val normalized = schedule.copy(
            days = sortedDays(schedule.days),
            weeks = schedule.weeks.sortedBy { it.number }.map { it.copy(days = sortedDays(it.days)) },
        )
        // The stored/exported representation must fit the same limit as the next import.
        if (encode(normalized).encodeToByteArray().size > MAX_BYTES) {
            return invalid("$", "После форматирования расписание больше 256 КиБ. Уменьшите файл.")
        }
        return ImportResult.Valid(normalized, warnings)
    }

    private fun sortedDays(days: List<ScheduleDay>) = days.sortedBy { it.day.ordinal }.map {
        it.copy(lessons = it.lessons.sortedBy { lesson -> lesson.number })
    }

    private fun validText(text: String, max: Int): Boolean =
        text.trim().isNotEmpty() && text.length <= max && text.none {
            it.code < 32 || it.code in 127..159 || it.code in 0x202A..0x202E || it.code in 0x2066..0x2069
        }

    private fun minutes(time: String) = time.substring(0, 2).toInt() * 60 + time.substring(3).toInt()
    private fun invalid(path: String, message: String) = ImportResult.Invalid(listOf(ValidationIssue(path, message)))

    // Cap structural depth before deserialization and reject duplicate keys, including escaped spellings.
    // Brackets inside strings are ignored. The serialization library validates JSON syntax afterwards.
    internal fun preflight(text: String): String? {
        val stack = mutableListOf<MutableSet<String>?>()
        var index = 0
        while (index < text.length) {
            when (text[index]) {
                '{', '[' -> {
                    stack.add(if (text[index] == '{') mutableSetOf<String>() else null)
                    if (stack.size > MAX_DEPTH) return "JSON слишком глубоко вложен. Используйте схему расписания."
                }
                '}', ']' -> if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
                '"' -> {
                    val begin = index
                    index++
                    while (index < text.length && text[index] != '"') {
                        if (text[index] == '\\') index++
                        index++
                    }
                    if (index >= text.length) return "В JSON есть незакрытая строка."
                    var next = index + 1
                    while (next < text.length && text[next].isWhitespace()) next++
                    if (next < text.length && text[next] == ':') {
                        val key = try {
                            json.decodeFromString<String>(text.substring(begin, index + 1))
                        } catch (_: IllegalArgumentException) { return "Некорректный ключ JSON." }
                        if (stack.lastOrNull()?.add(key) == false) return "В JSON есть повторяющиеся ключи. Оставьте одно значение каждого поля."
                    }
                }
            }
            index++
        }
        return null
    }
}
