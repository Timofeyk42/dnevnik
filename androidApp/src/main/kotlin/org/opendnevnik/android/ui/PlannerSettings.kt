package org.opendnevnik.android.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.opendnevnik.android.presentation.*
import org.opendnevnik.shared.planner.ThemeMode

@Composable
fun PlannerSettings(state: DiaryState, model: DiaryViewModel) {
    val preferences = state.planner.preferences
    val enabled = state.plannerReady && !state.working
    var confirm by rememberSaveable { mutableStateOf<String?>(null) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let(model::exportPlanner) }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(model::importPlanner) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Внешний вид", style = MaterialTheme.typography.titleLarge)
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            ThemeMode.entries.forEach { mode -> FilterChip(preferences.theme == mode, { model.preferences(preferences.copy(theme = mode)) }, enabled = enabled, label = { Text(when (mode) { ThemeMode.SYSTEM -> "Система"; ThemeMode.LIGHT -> "Светлая"; ThemeMode.DARK -> "Тёмная" }) }) }
        }
        ToggleSetting("Цвета обоев", preferences.dynamicColors, enabled) { model.preferences(preferences.copy(dynamicColors = it)) }
        ToggleSetting("Компактный вид", preferences.compact, enabled) { model.preferences(preferences.copy(compact = it)) }
        ToggleSetting("Показывать учителей", preferences.showTeachers, enabled) { model.preferences(preferences.copy(showTeachers = it)) }
        HorizontalDivider()
        Text("Календарь и настройки", style = MaterialTheme.typography.titleLarge)
        Text("Копия содержит личные записи. Сохраняйте её в доверенном месте. Расписание экспортируется отдельно ниже.")
        TextButton({ export.launch("dnevnik-planner.json") }, enabled = enabled) { Text("Сохранить резервную копию") }
        TextButton({ confirm = "restore" }, enabled = !state.working) { Text("Восстановить из копии") }
        TextButton({ confirm = "clear" }, enabled = enabled) { Text("Удалить все личные события") }
        Text("Google Календарь: откройте событие → «В календарь…», выберите календарь и подтвердите. Автоматической синхронизации нет.", style = MaterialTheme.typography.bodySmall)
    }
    confirm?.let { action ->
        AlertDialog(onDismissRequest = { confirm = null }, title = { Text(if (action == "restore") "Заменить календарь и настройки?" else "Удалить все события?") }, text = { Text("Сначала сохраните копию важных записей. Расписание и копии в Google Календаре останутся без изменений.") }, confirmButton = {
            TextButton({ confirm = null; if (action == "restore") restore.launch(arrayOf("application/json", "text/plain")) else model.clearPlanner() }, enabled = !state.working) { Text("Продолжить") }
        }, dismissButton = { TextButton({ confirm = null }) { Text("Отмена") } })
    }
}

@Composable
private fun ToggleSetting(title: String, checked: Boolean, enabled: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, Modifier.weight(1f).padding(top = 12.dp))
        Switch(checked, change, enabled = enabled)
    }
}
