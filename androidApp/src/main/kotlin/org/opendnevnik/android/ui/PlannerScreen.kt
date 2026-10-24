package org.opendnevnik.android.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.CalendarContract
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.opendnevnik.android.presentation.*
import org.opendnevnik.shared.planner.*
import java.time.*
import java.util.UUID

internal fun EventKind.label() = when (this) {
    EventKind.EVENT -> "Событие"
    EventKind.TEST -> "Контрольная"
    EventKind.HOMEWORK -> "Задание"
    EventKind.LESSON -> "Занятие"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlannerScreen(state: DiaryState, model: DiaryViewModel, tasksOnly: Boolean = false) {
    val context = LocalContext.current
    var selectedDate by rememberSaveable { mutableStateOf(LocalDate.now(ZoneId.of("Europe/Moscow")).toString()) }
    var showAll by rememberSaveable { mutableStateOf(tasksOnly) }
    var pickDate by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var deletingId by rememberSaveable { mutableStateOf<String?>(null) }
    val canEdit = !state.working && state.plannerReady
    val events = state.planner.events.filter {
        (!tasksOnly || it.kind == EventKind.HOMEWORK) && (showAll || it.date == selectedDate)
    }.sortedWith(compareBy({ it.date }, { it.startTime ?: "" }))
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(if (state.planner.preferences.compact) 8.dp else 16.dp)) {
        item {
            Text(if (tasksOnly) "Мои задания" else "Мой календарь", style = MaterialTheme.typography.headlineLarge)
            Text(if (tasksOnly) "Личные задания. Данные МЭШ появятся после подключения." else "Контрольные, занятия и личные планы. Неделя расписания выбирается вручную.", style = MaterialTheme.typography.bodyMedium)
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { pickDate = true }, modifier = Modifier.weight(1f)) { Text(selectedDate) }
                Button(onClick = { editingId = "new" }, enabled = canEdit) { Text("Добавить") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(!showAll, { showAll = false }, label = { Text("На дату") })
                FilterChip(showAll, { showAll = true }, label = { Text("Все") })
            }
        }
        if (events.isEmpty()) item { Text("Пока нет событий. Добавьте первое кнопкой выше.") }
        items(events, key = { it.id }) { event ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.fillMaxWidth().padding(if (state.planner.preferences.compact) 12.dp else 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(event.kind.label(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text(event.title, style = MaterialTheme.typography.titleLarge)
                    Text(event.date + (event.startTime?.let { " · $it–${event.endTime}" } ?: " · весь день"))
                    if (event.notes.isNotEmpty()) Text(event.notes)
                    event.source?.let { source ->
                        Text("МЭШ: ${source.date} · ${source.title}", style = MaterialTheme.typography.bodySmall)
                        if (event.date != source.date) Text("Личный перенос; школьная дата не изменена", color = MaterialTheme.colorScheme.tertiary)
                        if (event.sourceMissing) Text("Событие больше не найдено в источнике. Проверьте актуальность.", color = MaterialTheme.colorScheme.error)
                    }
                    if (event.kind == EventKind.HOMEWORK) Row {
                        Checkbox(event.done, { model.saveEvent(event.copy(done = it)) }, enabled = canEdit)
                        Text(if (event.done) "Выполнено" else "Не выполнено", Modifier.padding(top = 12.dp))
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        TextButton({ editingId = event.id }, enabled = canEdit) { Text("Изменить") }
                        TextButton({
                            try {
                                context.startActivity(calendarIntent(event))
                                model.report("Подтвердите сохранение в календаре. Повторный экспорт может создать копию.")
                            } catch (_: ActivityNotFoundException) { model.report("Установите приложение календаря") }
                            catch (_: SecurityException) { model.report("Не удалось открыть календарь") }
                        }) { Text("В календарь…") }
                        TextButton({ deletingId = event.id }, enabled = canEdit) { Text("Удалить") }
                    }
                }
            }
        }
    }
    if (pickDate) {
        val picker = rememberDatePickerState(initialSelectedDateMillis = LocalDate.parse(selectedDate).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(onDismissRequest = { pickDate = false }, confirmButton = {
            TextButton({ picker.selectedDateMillis?.let { selectedDate = Instant.ofEpochMilli(it).atOffset(ZoneOffset.UTC).toLocalDate().toString() }; pickDate = false }) { Text("Выбрать") }
        }) { DatePicker(picker) }
    }
    editingId?.let { id ->
        val event = remember(id) { state.planner.events.find { it.id == id } ?: PlannerEvent(UUID.randomUUID().toString(), "", selectedDate, if (tasksOnly) EventKind.HOMEWORK else EventKind.EVENT) }
        key(id) { EventDialog(event, canEdit, { editingId = null }, { model.saveEvent(it) { editingId = null } }) }
    }
    deletingId?.let { id ->
        AlertDialog(onDismissRequest = { deletingId = null }, title = { Text("Удалить событие?") }, text = { Text("Удалится только локальная запись. Экспортированная копия в другом календаре останется.") }, confirmButton = {
            TextButton({ model.removeEvent(id); deletingId = null }, enabled = canEdit) { Text("Удалить") }
        }, dismissButton = { TextButton({ deletingId = null }) { Text("Отмена") } })
    }
}

@Composable
private fun EventDialog(initial: PlannerEvent, enabled: Boolean, dismiss: () -> Unit, save: (PlannerEvent) -> Unit) {
    var title by rememberSaveable { mutableStateOf(initial.title) }
    var date by rememberSaveable { mutableStateOf(initial.date) }
    var start by rememberSaveable { mutableStateOf(initial.startTime.orEmpty()) }
    var end by rememberSaveable { mutableStateOf(initial.endTime.orEmpty()) }
    var notes by rememberSaveable { mutableStateOf(initial.notes) }
    var kind by rememberSaveable { mutableStateOf(initial.kind) }
    var resetOverrides by rememberSaveable { mutableStateOf(false) }
    val event = initial.copy(title = title.trim(), date = date, startTime = start.ifBlank { null }, endTime = end.ifBlank { null }, notes = notes, kind = kind,
        dateOverridden = initial.source?.let { if (resetOverrides) date != it.date else if (date != initial.date) date != it.date else initial.dateOverridden } ?: false,
        titleOverridden = initial.source?.let { if (resetOverrides) title.trim() != it.title else if (title.trim() != initial.title) title.trim() != it.title else initial.titleOverridden } ?: false,
        timeOverridden = initial.source?.let {
            if (start != initial.startTime.orEmpty() || end != initial.endTime.orEmpty()) start != it.startTime.orEmpty() || end != it.endTime.orEmpty() else initial.timeOverridden
        } ?: false,
        kindOverridden = initial.source?.let { if (kind != initial.kind) kind != it.kind else initial.kindOverridden } ?: false)
    val error = PlannerRules.validate(event)
    AlertDialog(onDismissRequest = dismiss, title = { Text("Событие") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(title, { title = it.take(120) }, label = { Text("Название") }, singleLine = true)
            OutlinedTextField(date, { date = it.take(10) }, label = { Text("Дата: ГГГГ-ММ-ДД") }, singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(start, { start = it.take(5) }, Modifier.weight(1f), label = { Text("С ЧЧ:ММ") }, singleLine = true)
                OutlinedTextField(end, { end = it.take(5) }, Modifier.weight(1f), label = { Text("До ЧЧ:ММ") }, singleLine = true)
            }
            Text("Без времени — событие на весь день", style = MaterialTheme.typography.bodySmall)
            Row(Modifier.horizontalScroll(rememberScrollState())) { EventKind.entries.forEach { option -> FilterChip(kind == option, { kind = option }, label = { Text(option.label()) }) } }
            OutlinedTextField(notes, { notes = it.take(4000) }, label = { Text("Заметка") }, maxLines = 5)
            initial.source?.let { source ->
                Text("Дата МЭШ: ${source.date}. Здесь меняется только ваш план.")
                TextButton({ date = source.date; title = source.title; resetOverrides = true }) { Text("Вернуть дату и название МЭШ") }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton({ save(event) }, enabled = enabled && error == null) { Text("Сохранить") } }, dismissButton = { TextButton(dismiss) { Text("Отмена") } })
}

internal fun calendarIntent(event: PlannerEvent): Intent {
    val date = LocalDate.parse(event.date)
    val allDay = event.startTime == null
    val zone = if (allDay) ZoneOffset.UTC else ZoneId.of("Europe/Moscow")
    val start = if (allDay) date.atStartOfDay(zone) else date.atTime(LocalTime.parse(event.startTime)).atZone(zone)
    val end = if (allDay) date.plusDays(1).atStartOfDay(zone) else date.atTime(LocalTime.parse(event.endTime)).atZone(zone)
    return Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI)
        .putExtra(CalendarContract.Events.TITLE, event.title)
        .putExtra(CalendarContract.Events.DESCRIPTION, event.notes)
        .putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, allDay)
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start.toInstant().toEpochMilli())
        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end.toInstant().toEpochMilli())
        .putExtra(CalendarContract.Events.EVENT_TIMEZONE, zone.id)
}
