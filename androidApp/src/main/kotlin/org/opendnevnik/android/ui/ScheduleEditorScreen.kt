package org.opendnevnik.android.ui

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
import androidx.compose.ui.unit.dp
import org.opendnevnik.android.presentation.*
import org.opendnevnik.shared.planner.ScheduleEditor
import org.opendnevnik.shared.schedule.*

private val weekdayLabels = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")

@Composable
fun ScheduleEditorScreen(state: DiaryState, model: DiaryViewModel) {
    val schedule = state.schedule ?: ScheduleEditor.empty()
    var weekNumber by rememberSaveable { mutableIntStateOf(1) }
    var dayNumber by rememberSaveable { mutableIntStateOf(0) }
    var editNumber by rememberSaveable { mutableStateOf<Int?>(null) }
    var rename by rememberSaveable { mutableStateOf(false) }
    val weeks = schedule.effectiveWeeks()
    val week = weeks.find { it.number == weekNumber } ?: weeks.first()
    val day = Weekday.entries[dayNumber]
    val lessons = week.days.find { it.day == day }?.lessons.orEmpty()
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Редактор недели", style = MaterialTheme.typography.headlineMedium)
            Text("Меняется локальный шаблон. Для занятия на конкретную дату добавьте событие в календарь.")
            TextButton({ rename = true }, enabled = !state.working) { Text(schedule.name + " · переименовать") }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                weeks.forEach { w -> FilterChip(w.number == week.number, { weekNumber = w.number }, label = { Text("Неделя ${w.number}") }) }
            }
            if (weeks.size == 1) TextButton({
                val other = if (week.number == 1) 2 else 1
                model.saveSchedule(schedule.copy(schemaVersion = 2, days = emptyList(), weeks = weeks + ScheduleWeek(other, week.days)))
            }, enabled = !state.working) { Text("Добавить вторую неделю — копию первой") }
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                weekdayLabels.forEachIndexed { i, label -> FilterChip(dayNumber == i, { dayNumber = i }, label = { Text(label) }) }
            }
        }
        items(lessons, key = { it.number }) { lesson ->
            OutlinedCard(onClick = { editNumber = lesson.number }, enabled = !state.working) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("${lesson.number}. " + (lesson.subject ?: lesson.variants.joinToString(" | ") { it?.subject ?: "—" }), style = MaterialTheme.typography.titleMedium)
                    Text(lesson.startTime?.let { "$it–${lesson.endTime}" } ?: "Без времени")
                }
            }
        }
        item { Button({ editNumber = -1 }, enabled = !state.working && lessons.size < 20) { Text("Добавить урок") } }
    }
    if (rename) {
        var name by rememberSaveable { mutableStateOf(schedule.name) }
        AlertDialog(onDismissRequest = { rename = false }, title = { Text("Название расписания") }, text = {
            OutlinedTextField(name, { name = it.take(80) }, singleLine = true)
        }, confirmButton = { TextButton({ model.saveSchedule(schedule.copy(name = name.trim())); rename = false }, enabled = !state.working && name.isNotBlank()) { Text("Сохранить") } }, dismissButton = { TextButton({ rename = false }) { Text("Отмена") } })
    }
    editNumber?.let { number ->
        val initial = lessons.find { it.number == number }
        key(week.number, day, number) {
            LessonDialog(initial, schedule.schemaVersion == 2, !state.working, lessons.map { it.number }, { editNumber = null }) { lesson ->
                when (val result = ScheduleEditor.put(schedule, week.number, day, initial?.number, lesson)) {
                    is ImportResult.Valid -> { model.saveSchedule(result.schedule); editNumber = null }
                    is ImportResult.Invalid -> model.report(result.issues.joinToString("\n") { it.message })
                }
            }
        }
    }
}

@Composable
private fun LessonDialog(initial: Lesson?, v2: Boolean, enabled: Boolean, used: List<Int>, dismiss: () -> Unit, save: (Lesson?) -> Unit) {
    var number by rememberSaveable { mutableStateOf((initial?.number ?: (1..20).firstOrNull { it !in used } ?: 0).toString()) }
    var start by rememberSaveable { mutableStateOf(initial?.startTime.orEmpty()) }
    var end by rememberSaveable { mutableStateOf(initial?.endTime.orEmpty()) }
    var columns by rememberSaveable { mutableIntStateOf(initial?.variants?.size?.takeIf { it > 0 } ?: 1) }
    var subjects by rememberSaveable { mutableStateOf(List(4) { i -> if (initial?.variants?.isNotEmpty() == true) initial.variants.getOrNull(i)?.subject.orEmpty() else if (i == 0) initial?.subject.orEmpty() else "" }) }
    var rooms by rememberSaveable { mutableStateOf(List(4) { i -> if (initial?.variants?.isNotEmpty() == true) initial.variants.getOrNull(i)?.room.orEmpty() else if (i == 0) initial?.room.orEmpty() else "" }) }
    var teachers by rememberSaveable { mutableStateOf(List(4) { i -> if (initial?.variants?.isNotEmpty() == true) initial.variants.getOrNull(i)?.teacher.orEmpty() else if (i == 0) initial?.teacher.orEmpty() else "" }) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    AlertDialog(onDismissRequest = dismiss, title = { Text(if (initial == null) "Новый урок" else "Редактирование урока") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(number, { number = it.take(2) }, label = { Text("Номер (0–20)") }, singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(start, { start = it.take(5) }, Modifier.weight(1f), label = { Text("С ЧЧ:ММ") }, singleLine = true)
                OutlinedTextField(end, { end = it.take(5) }, Modifier.weight(1f), label = { Text("До ЧЧ:ММ") }, singleLine = true)
            }
            if (v2) Row(Modifier.horizontalScroll(rememberScrollState())) { (1..4).forEach { count -> FilterChip(columns == count, { columns = count }, label = { Text(if (count == 1) "Один" else "$count колонки") }) } }
            repeat(columns) { i ->
                if (columns > 1) Text("Колонка ${i + 1}. Пустой предмет = нет урока.")
                OutlinedTextField(subjects[i], { value -> subjects = subjects.mapIndexed { index, s -> if (index == i) value.take(100) else s } }, label = { Text("Предмет") }, singleLine = true)
                OutlinedTextField(rooms[i], { value -> rooms = rooms.mapIndexed { index, s -> if (index == i) value.take(40) else s } }, label = { Text("Кабинет") }, singleLine = true)
                OutlinedTextField(teachers[i], { value -> teachers = teachers.mapIndexed { index, s -> if (index == i) value.take(120) else s } }, label = { Text("Учитель") }, singleLine = true)
            }
            if (initial != null) TextButton({ deleting = true }, enabled = enabled) { Text("Удалить урок") }
        }
    }, confirmButton = { TextButton({
        val n = number.toIntOrNull() ?: return@TextButton
        save(Lesson(n, if (columns == 1) subjects[0].trim() else null, start.ifBlank { null }, end.ifBlank { null },
            if (columns == 1) rooms[0].trim().ifBlank { null } else null,
            if (columns == 1) teachers[0].trim().ifBlank { null } else null,
            if (columns == 1) emptyList() else (0 until columns).map { i -> subjects[i].trim().takeIf { it.isNotEmpty() }?.let { LessonVariant(it, rooms[i].trim().ifBlank { null }, teachers[i].trim().ifBlank { null }) } }))
    }, enabled = enabled && number.toIntOrNull() in 0..20) { Text("Сохранить") } }, dismissButton = { TextButton(dismiss) { Text("Отмена") } })
    if (deleting) AlertDialog(onDismissRequest = { deleting = false }, title = { Text("Удалить урок из этой недели?") }, confirmButton = { TextButton({ save(null) }, enabled = enabled) { Text("Удалить") } }, dismissButton = { TextButton({ deleting = false }) { Text("Отмена") } })
}
