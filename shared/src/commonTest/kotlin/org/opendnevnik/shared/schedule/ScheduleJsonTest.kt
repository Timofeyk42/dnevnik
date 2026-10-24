package org.opendnevnik.shared.schedule

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ScheduleJsonTest {
    private fun fixture(lessons: List<Lesson> = listOf(Lesson(1, "ЗС", "08:30", "09:15"))) =
        Schedule(1, "Моё расписание", "Europe/Moscow", listOf(ScheduleDay(Weekday.MONDAY, lessons)))
    private fun parse(schedule: Schedule) = ScheduleJson.parse(ScheduleJson.encode(schedule))

    @Test fun preservesZsAsOrdinaryLesson() {
        val result = assertIs<ImportResult.Valid>(parse(fixture()))
        assertEquals("ЗС", result.schedule.days.single().lessons.single().subject)
        assertTrue(result.warnings.isEmpty())
    }
    @Test fun preservesRepeatedSubjectAndZeroLesson() {
        val lessons = listOf(Lesson(2, "Алгебра"), Lesson(0, "ЗС"), Lesson(1, "Алгебра"))
        val result = assertIs<ImportResult.Valid>(parse(fixture(lessons)))
        assertEquals(listOf(0, 1, 2), result.schedule.days.single().lessons.map { it.number })
        assertEquals(3, result.warnings.size)
    }
    @Test fun rejectsDuplicateLessonNumbers() {
        assertIs<ImportResult.Invalid>(parse(fixture(listOf(Lesson(1, "ЗС"), Lesson(1, "Алгебра")))))
    }
    @Test fun rejectsDuplicateDays() {
        val schedule = fixture()
        assertIs<ImportResult.Invalid>(parse(schedule.copy(days = schedule.days + schedule.days)))
    }
    @Test fun rejectsUnknownVersionAndTimeZone() {
        assertIs<ImportResult.Invalid>(parse(fixture().copy(schemaVersion = 2)))
        assertIs<ImportResult.Invalid>(parse(fixture().copy(timeZone = "UTC")))
    }
    @Test fun rejectsInvalidTimesAndPartialTime() {
        listOf(Lesson(1, "ЗС", "8:30", "09:15"), Lesson(1, "ЗС", "24:00", "25:00"),
            Lesson(1, "ЗС", "09:15", "08:30"), Lesson(1, "ЗС", "08:30")).forEach {
            assertIs<ImportResult.Invalid>(parse(fixture(listOf(it))))
        }
    }
    @Test fun rejectsOverlapsEvenAcrossUntimedLessons() {
        assertIs<ImportResult.Invalid>(parse(fixture(listOf(
            Lesson(1, "ЗС", "08:30", "09:15"), Lesson(2, "Русский"), Lesson(3, "Алгебра", "09:00", "09:45"),
        ))))
    }
    @Test fun acceptsTouchingTimeBoundaries() {
        assertIs<ImportResult.Valid>(parse(fixture(listOf(
            Lesson(1, "ЗС", "08:30", "09:15"), Lesson(2, "Русский", "09:15", "10:00"),
        ))))
    }
    @Test fun acceptsBomAndEmptyDay() {
        assertIs<ImportResult.Valid>(ScheduleJson.parse("\uFEFF" + ScheduleJson.encode(fixture(emptyList()))))
    }
    @Test fun rejectsMalformedMarkdownAndUnknownFields() {
        listOf("", "{", "```json\n{}\n```", "[]", ScheduleJson.encode(fixture()).replace("\"schemaVersion\"", "\"other\"")).forEach {
            assertIs<ImportResult.Invalid>(ScheduleJson.parse(it))
        }
    }
    @Test fun rejectsDuplicateKeysAndEscapedDuplicateKeys() {
        assertIs<ImportResult.Invalid>(ScheduleJson.parse("{\"name\":\"a\",\"name\":\"b\"}"))
        assertIs<ImportResult.Invalid>(ScheduleJson.parse("{\"name\":\"a\",\"na\\u006de\":\"b\"}"))
    }
    @Test fun rejectsDeepAndLargeInput() {
        assertIs<ImportResult.Invalid>(ScheduleJson.parse("[".repeat(20) + "]".repeat(20)))
        assertIs<ImportResult.Invalid>(ScheduleJson.parse("я".repeat(ScheduleJson.MAX_BYTES)))
    }
    @Test fun bracketsAndEscapedQuotesInsideStringsAreNotStructure() {
        assertIs<ImportResult.Valid>(parse(fixture(listOf(Lesson(1, "Предмет [ { \\\" } ]")))))
    }
    @Test fun rejectsBlankAndControlCharacters() {
        assertIs<ImportResult.Invalid>(parse(fixture(listOf(Lesson(1, "  ")))))
        assertIs<ImportResult.Invalid>(parse(fixture(listOf(Lesson(1, "ЗС\n")))))
        assertIs<ImportResult.Invalid>(parse(fixture(listOf(Lesson(1, "\u202EЗС")))))
    }
    @Test fun promptExampleMatchesActualParser() {
        val example = PhotoPrompt.text.substring(PhotoPrompt.text.indexOf('{')).trim()
        assertEquals(PhotoPrompt.exampleJson, example)
        val schedule = assertIs<ImportResult.Valid>(ScheduleJson.parse(example)).schedule
        assertEquals(listOf(1, 2, 3), schedule.weeks.single().days.single().lessons.map { it.number })
        assertTrue(schedule.weeks.single().days.single().lessons.all { it.startTime == null && it.endTime == null })
        assertTrue(PhotoPrompt.text.contains("ЗС — обычный урок"))
    }
    @Test fun preservesZsSuffixAndSeparatePeriodsWithoutTimes() {
        val result = assertIs<ImportResult.Valid>(parse(fixture(listOf(
            Lesson(3, "Физика ЗС", room = "100"), Lesson(4, "Физика ЗС", room = "100"),
        ))))
        assertEquals(listOf(3, 4), result.schedule.days.single().lessons.map { it.number })
        assertTrue(result.schedule.days.single().lessons.all { it.subject == "Физика ЗС" && it.room == "100" })
        assertEquals(2, result.warnings.size)
    }
    private fun cycle() = Schedule(2, "Пример", "Europe/Moscow", weeks = listOf(
        ScheduleWeek(1, listOf(ScheduleDay(Weekday.MONDAY, listOf(Lesson(1, "Математика"))))),
        ScheduleWeek(2, listOf(ScheduleDay(Weekday.MONDAY, listOf(Lesson(1, "Литература"))))),
    ))
    @Test fun alternatingWeeksKeepSameDayAndLessonNumbersSeparate() {
        val result = assertIs<ImportResult.Valid>(parse(cycle().copy(weeks = cycle().weeks.reversed())))
        assertEquals(listOf(1, 2), result.schedule.weeks.map { it.number })
        assertEquals(listOf("Математика", "Литература"), result.schedule.weeks.map { it.days.single().lessons.single().subject })
        assertEquals(result.schedule, assertIs<ImportResult.Valid>(parse(result.schedule)).schedule)
    }
    @Test fun rejectsMissingDuplicateAndInvalidWeeks() {
        assertIs<ImportResult.Invalid>(parse(cycle().copy(weeks = emptyList())))
        assertIs<ImportResult.Invalid>(parse(cycle().copy(weeks = listOf(cycle().weeks[0], cycle().weeks[0]))))
        assertIs<ImportResult.Invalid>(parse(cycle().copy(weeks = listOf(cycle().weeks[0].copy(number = 3)))))
    }
    @Test fun preservesParallelColumnsAndEmptyColumn() {
        val row = Lesson(2, variants = listOf(null, LessonVariant("Физика ЗС", "100")))
        val schedule = cycle().copy(weeks = listOf(ScheduleWeek(1, listOf(ScheduleDay(Weekday.MONDAY, listOf(row))))))
        val result = assertIs<ImportResult.Valid>(parse(schedule)).schedule
        assertEquals(listOf(null, LessonVariant("Физика ЗС", "100")), result.weeks.single().days.single().lessons.single().variants)
    }
    @Test fun rejectsAmbiguousAndEmptyVariants() {
        listOf(Lesson(1, "Математика", variants = listOf(LessonVariant("Русский"), null)),
            Lesson(1, variants = listOf(null, null)), Lesson(1, variants = listOf(LessonVariant("Русский")))).forEach { row ->
            assertIs<ImportResult.Invalid>(parse(cycle().copy(weeks = listOf(ScheduleWeek(1, listOf(ScheduleDay(Weekday.MONDAY, listOf(row))))))))
        }
    }
    @Test fun keepsV1FormatAndRejectsV2FieldsInV1() {
        val result = assertIs<ImportResult.Valid>(parse(fixture())).schedule
        assertEquals(1, result.schemaVersion)
        assertEquals(result.days, result.effectiveWeeks().single().days)
        assertIs<ImportResult.Invalid>(ScheduleJson.parse(ScheduleJson.encode(fixture()).dropLast(1) + ",\"weeks\":[]}"))
        assertIs<ImportResult.Invalid>(parse(fixture(listOf(Lesson(1, variants = listOf(LessonVariant("Русский"), null))))))
    }
    @Test fun rejectsCompactImportThatWouldExceedLimitWhenStored() {
        val days = Weekday.entries.map { day ->
            val lessons = (1..20).map { number ->
                Lesson(number, variants = List(4) {
                    LessonVariant("A".repeat(70), "C".repeat(20), "B".repeat(70))
                })
            }
            ScheduleDay(day, lessons)
        }
        val schedule = cycle().copy(weeks = (1..2).map { ScheduleWeek(it, days) })
        val compact = kotlinx.serialization.json.Json.encodeToString(Schedule.serializer(), schedule)
        assertTrue(compact.encodeToByteArray().size < ScheduleJson.MAX_BYTES)
        assertTrue(ScheduleJson.encode(schedule).encodeToByteArray().size > ScheduleJson.MAX_BYTES)
        assertIs<ImportResult.Invalid>(ScheduleJson.parse(compact))
    }
}
