package org.opendnevnik.shared.planner

import kotlin.test.*
import org.opendnevnik.shared.schedule.*

class PlannerTest {
    private fun remote(date: String = "2026-10-26") = PlannerEvent("remote-1", "Контрольная", date, EventKind.TEST, source = EventSource("account:child:test-1", date, "Контрольная"))

    @Test fun datesHandleLeapYearsAndInvalidDays() {
        assertTrue(PlannerRules.validDate("2028-02-29"))
        listOf("2026-02-29", "2026-04-31", "2026-13-01", "2026-1-01", "0000-01-01").forEach { assertFalse(PlannerRules.validDate(it)) }
    }
    @Test fun eventValidationRequiresPairedOrderedTimes() {
        val event = PlannerEvent("1", "Занятие", "2026-10-26")
        assertNull(PlannerRules.validate(event))
        assertNotNull(PlannerRules.validate(event.copy(startTime = "12:00")))
        assertNotNull(PlannerRules.validate(event.copy(startTime = "12:00", endTime = "11:00")))
        assertNotNull(PlannerRules.validate(event.copy(startTime = "25:00", endTime = "26:00")))
        assertNull(PlannerRules.validate(event.copy(startTime = "12:00", endTime = "13:00")))
    }
    @Test fun remoteRefreshPreservesPersonalDateNotesAndIdentity() {
        val personal = remote().copy(date = "2026-10-28", notes = "Подготовиться", done = true)
        val result = PlannerRules.reconcile(PlannerData(events = listOf(personal)), "account:child:", listOf(remote("2026-10-27")))
        assertEquals(1, result.events.size)
        val event = result.events.single()
        assertEquals("2026-10-28", event.date)
        assertEquals("2026-10-27", event.source?.date)
        assertEquals("Подготовиться", event.notes)
        assertTrue(event.done)
        assertEquals("2026-10-27", PlannerRules.resetToSource(event).date)
    }
    @Test fun unchangedDateFollowsSourceAndRemovedSourceIsRetainedForReview() {
        val result = PlannerRules.reconcile(PlannerData(events = listOf(remote())), "account:child:", listOf(remote("2026-10-27")))
        assertEquals("2026-10-27", result.events.single().date)
        assertTrue(PlannerRules.reconcile(result, "account:child:", emptyList()).events.single().sourceMissing)
        assertFalse(PlannerRules.reconcile(result, "other:child:", emptyList()).events.single().sourceMissing)
    }
    @Test fun backupRoundTripAndInvalidInput() {
        val data = PlannerData(events = listOf(remote()), preferences = Preferences(theme = ThemeMode.DARK))
        assertEquals(data, PlannerJson.decode(PlannerJson.encode(data)))
        assertFails { PlannerJson.decode("{\"version\":99}") }
        assertFails { PlannerJson.encode(data.copy(events = listOf(remote(), remote()))) }
        assertFails { PlannerJson.decode(" ".repeat(PlannerJson.MAX_BYTES + 1)) }
        assertFails { PlannerJson.decode("{}") }
        assertFails { PlannerJson.decode("{\"version\":1,\"version\":1,\"events\":[],\"preferences\":{}}") }
        assertFails { PlannerJson.decode("[".repeat(100) + "]".repeat(100)) }
    }
    @Test fun sourceCannotOverwriteUnrelatedLocalEventWithSameId() {
        val local = PlannerEvent("remote-1", "Моя запись", "2026-10-26")
        assertFails { PlannerRules.reconcile(PlannerData(events = listOf(local)), "account:child:", listOf(remote())) }
    }
    @Test fun personalDateRemainsPinnedWhenSourceTemporarilyCoincides() {
        val personal = remote().copy(date = "2026-10-28")
        val sameDate = PlannerRules.reconcile(PlannerData(events = listOf(personal)), "account:child:", listOf(remote("2026-10-28")))
        val later = PlannerRules.reconcile(sameDate, "account:child:", listOf(remote("2026-10-29")))
        assertEquals("2026-10-28", later.events.single().date)
        assertTrue(later.events.single().dateOverridden)
        assertFalse(PlannerRules.resetToSource(later.events.single()).dateOverridden)
    }
    @Test fun remoteTimeAndKindRefreshUnlessPersonallyEdited() {
        val before = remote().copy(startTime = "09:00", endTime = "10:00")
        val first = PlannerRules.reconcile(PlannerData(), "account:child:", listOf(before))
        val after = before.copy(startTime = "11:00", endTime = "12:00", kind = EventKind.EVENT)
        val updated = PlannerRules.reconcile(first, "account:child:", listOf(after)).events.single()
        assertEquals("11:00", updated.startTime)
        assertEquals(EventKind.EVENT, updated.kind)
        val personal = first.copy(events = listOf(first.events.single().copy(startTime = "13:00", endTime = "14:00")))
        val kept = PlannerRules.reconcile(personal, "account:child:", listOf(after)).events.single()
        assertEquals("13:00", kept.startTime)
        assertEquals("11:00", kept.source?.startTime)
    }
    @Test fun editPreservesOtherWeekRepeatedSubjectsAndNullColumn() {
        val lesson = Lesson(1, variants = listOf(LessonVariant("ЗС"), null))
        val schedule = Schedule(2, "Тест", "Europe/Moscow", weeks = listOf(
            ScheduleWeek(1, listOf(ScheduleDay(Weekday.MONDAY, listOf(lesson, lesson.copy(number = 2))))),
            ScheduleWeek(2, listOf(ScheduleDay(Weekday.MONDAY, listOf(lesson))))),
        )
        val edited = assertIs<ImportResult.Valid>(ScheduleEditor.put(schedule, 1, Weekday.MONDAY, 1, lesson.copy(startTime = "08:00", endTime = "08:45"))).schedule
        assertEquals(schedule.weeks[1], edited.weeks[1])
        assertEquals(lesson.copy(number = 2), edited.weeks[0].days[0].lessons[1])
        assertNull(edited.weeks[0].days[0].lessons[0].variants[1])
        assertIs<ImportResult.Invalid>(ScheduleEditor.put(schedule, 1, Weekday.MONDAY, 1, lesson.copy(number = 2)))
    }
    @Test fun quotedNumbersAreRejectedButPromptStillImports() {
        assertIs<ImportResult.Valid>(ScheduleJson.parse(PhotoPrompt.exampleJson))
        assertIs<ImportResult.Invalid>(ScheduleJson.parse(PhotoPrompt.exampleJson.replace("\"schemaVersion\": 2", "\"schemaVersion\": \"2\"")))
    }
    @Test fun v1EditorRemainsV1() {
        val original = Schedule(1, "Одна неделя", "Europe/Moscow", days = listOf(ScheduleDay(Weekday.MONDAY, emptyList())))
        val changed = assertIs<ImportResult.Valid>(ScheduleEditor.put(original, 1, Weekday.TUESDAY, null, Lesson(1, "ЗС"))).schedule
        assertEquals(1, changed.schemaVersion)
        assertTrue(changed.weeks.isEmpty())
        assertEquals("ЗС", changed.days[1].lessons.single().subject)
    }
}
