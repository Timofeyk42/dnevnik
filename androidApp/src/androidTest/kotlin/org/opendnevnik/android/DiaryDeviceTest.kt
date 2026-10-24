package org.opendnevnik.android

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.opendnevnik.android.data.ScheduleFileStore
import org.opendnevnik.android.data.PlannerFileStore
import org.opendnevnik.android.presentation.DiaryViewModel
import org.opendnevnik.shared.schedule.ImportResult
import org.opendnevnik.shared.schedule.ScheduleJson
import org.opendnevnik.shared.schedule.PhotoPrompt
import org.junit.Assert.*
import java.util.UUID
import android.net.Uri
import org.opendnevnik.shared.planner.*
import org.opendnevnik.android.ui.calendarIntent
import android.provider.CalendarContract
import java.io.File
import java.time.Instant
import androidx.compose.ui.semantics.SemanticsProperties

/** Run on a disposable .debug installation; all records below are synthetic. */
@RunWith(AndroidJUnit4::class)
class DiaryDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun model() = ViewModelProvider(compose.activity)[DiaryViewModel::class.java]

    @Test fun importCalendarInvalidInputAndActivityRecreation() {
        val eventTitle = "UI test ${UUID.randomUUID().toString().take(8)}"
        compose.waitUntil(15_000) { !model().state.value.working }
        compose.onNodeWithContentDescription("Загрузить расписание").performClick()
        compose.onNodeWithText("Вставить пример").performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Проверить и посмотреть"))
        compose.onNodeWithText("Проверить и посмотреть").performClick()
        compose.waitUntil(10_000) { model().state.value.preview != null }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Сохранить расписание"))
        compose.onNodeWithText("Сохранить расписание").performClick()
        // Existing synthetic test schedules may require the normal replacement confirmation.
        if (compose.onAllNodesWithText("Заменить").fetchSemanticsNodes().isNotEmpty()) compose.onNodeWithText("Заменить").performClick()
        compose.waitUntil(10_000) { model().state.value.schedule != null && !model().state.value.working && model().state.value.preview == null }
        compose.waitForIdle()
        compose.onNodeWithText("Пн", useUnmergedTree = true).performClick()
        compose.onAllNodesWithText("Математика").assertCountEquals(2)
        compose.onNodeWithText("Календарь").performClick()
        compose.onNodeWithText("Добавить").performClick()
        compose.onNode(hasSetTextAction() and hasText("Название")).performTextInput(eventTitle)
        compose.onNodeWithText("Сохранить", useUnmergedTree = true).performClick()
        compose.waitUntil(10_000) { model().state.value.planner.events.any { it.title == eventTitle } && !model().state.value.working }
        compose.onNodeWithText(eventTitle).assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.waitUntil(15_000) { !model().state.value.working }
        compose.onNodeWithText(eventTitle).assertIsDisplayed()

        val scheduleBefore = ScheduleFileStore(compose.activity).read()
        compose.runOnUiThread { model().editSource("{broken"); model().previewImport() }
        compose.waitUntil(10_000) { !model().state.value.working && model().state.value.issues.isNotEmpty() }
        assertEquals(scheduleBefore, ScheduleFileStore(compose.activity).read())
        assertTrue(PlannerFileStore(compose.activity).read().events.any { it.title == eventTitle })
        assertTrue(ScheduleJson.parse(PhotoPrompt.exampleJson) is ImportResult.Valid)
    }

    @Test fun plannerBackupRejectsCorruptionAndCalendarIntentUsesCorrectTimes() {
        compose.waitUntil(15_000) { !model().state.value.working && model().state.value.plannerReady }
        val event = PlannerEvent("new", "Backup test", "2026-10-26")
        compose.runOnUiThread { model().saveEvent(event) }
        compose.waitUntil(10_000) { !model().state.value.working && model().state.value.planner.events.any { it.id == event.id } }
        val before = PlannerFileStore(compose.activity).read()
        val backup = File(compose.activity.cacheDir, "backup-${event.id}.json")
        compose.runOnUiThread { model().exportPlanner(Uri.fromFile(backup)) }
        compose.waitUntil(10_000) { !model().state.value.working }
        assertEquals(before, PlannerJson.decode(backup.readText()))
        val broken = File(compose.activity.cacheDir, "broken-${event.id}.json").apply { writeText("{broken") }
        compose.runOnUiThread { model().importPlanner(Uri.fromFile(broken)) }
        compose.waitUntil(10_000) { !model().state.value.working }
        assertEquals(before, PlannerFileStore(compose.activity).read())
        compose.runOnUiThread { model().importPlanner(Uri.fromFile(backup)) }
        compose.waitUntil(10_000) { !model().state.value.working }
        assertEquals(before, PlannerFileStore(compose.activity).read())

        // A valid imported ID must never double as the UI's create-mode sentinel.
        compose.onNodeWithText("Календарь").performClick()
        compose.onNodeWithText("Добавить").performClick()
        val titleField = compose.onNode(hasSetTextAction() and hasText("Название"))
        assertEquals("", titleField.fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        val addedTitle = "Created ${UUID.randomUUID().toString().take(8)}"
        titleField.performTextInput(addedTitle)
        compose.onNodeWithText("Сохранить", useUnmergedTree = true).performClick()
        compose.waitUntil(10_000) { !model().state.value.working && model().state.value.planner.events.any { it.title == addedTitle } }
        assertEquals(before.events.size + 1, model().state.value.planner.events.size)
        assertEquals("Backup test", model().state.value.planner.events.single { it.id == "new" }.title)

        val allDay = calendarIntent(event)
        assertTrue(allDay.getBooleanExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, false))
        assertEquals(Instant.parse("2026-10-26T00:00:00Z").toEpochMilli(), allDay.getLongExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, -1))
        assertEquals(86_400_000L, allDay.getLongExtra(CalendarContract.EXTRA_EVENT_END_TIME, -1) - allDay.getLongExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, -1))
        val timed = calendarIntent(event.copy(startTime = "10:00", endTime = "11:00"))
        assertEquals(Instant.parse("2026-10-26T07:00:00Z").toEpochMilli(), timed.getLongExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, -1))
    }
}
