package org.opendnevnik.android.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.opendnevnik.android.R
import org.opendnevnik.android.presentation.DiaryState
import org.opendnevnik.android.presentation.DiaryViewModel
import org.opendnevnik.shared.schedule.*
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first

private val dayNames = listOf(R.string.monday, R.string.tuesday, R.string.wednesday, R.string.thursday, R.string.friday, R.string.saturday, R.string.sunday)
private val shortDays = listOf(R.string.mon, R.string.tue, R.string.wed, R.string.thu, R.string.fri, R.string.sat, R.string.sun)
private val tabs = listOf(R.string.schedule, R.string.homework, R.string.marks, R.string.calendar, R.string.settings)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaryApp(model: DiaryViewModel) {
    val state by model.state.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var importing by rememberSaveable { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf(false) }
    val screens = rememberSaveableStateHolder()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val openDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(model::readDocument) }
    val exportDocument = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let(model::exportDocument) }
    BackHandler(enabled = importing || editing) { importing = false; editing = false }
    LaunchedEffect(model, snackbar) {
        while (true) {
            val detail = model.state.first { it.detail != null }.detail ?: continue
            model.dismissDetail()
            snackbar.showSnackbar(detail)
        }
    }
    LaunchedEffect(model, snackbar, context) {
        while (true) {
            val message = model.state.first { it.message != null }.message ?: continue
            model.dismissMessage()
            if (message == R.string.import_success) { importing = false; selectedTab = 0 }
            snackbar.showSnackbar(context.getString(message))
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (editing) "Редактор" else stringResource(if (importing) R.string.import_title else tabs[selectedTab]), fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    if (importing || editing) IconButton(onClick = { importing = false; editing = false }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    if (!importing && !editing && selectedTab == 0) IconButton(onClick = { editing = true }, enabled = !state.working) {
                        Icon(Icons.Outlined.Edit, "Редактировать расписание")
                    }
                    if (!importing && !editing && selectedTab == 0) IconButton(onClick = { importing = true }, enabled = !state.working) {
                        Icon(Icons.Outlined.FileUpload, stringResource(R.string.import_action))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = { if (!importing && !editing) DiaryNavigation(selectedTab) { selectedTab = it } },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            screens.SaveableStateProvider(if (importing) "import" else if (editing) "editor" else "tab-$selectedTab") { when {
                editing -> ScheduleEditorScreen(state, model)
                importing -> ImportScreen(state, model, onFile = {
                    openDocument.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                })
                selectedTab == 0 -> ScheduleScreen(state) { importing = true }
                selectedTab == 1 -> PlannerScreen(state, model, tasksOnly = true)
                selectedTab == 2 -> MarksScreen()
                selectedTab == 3 -> PlannerScreen(state, model)
                else -> SettingsScreen(state, model, onImport = { importing = true }, onExport = { exportDocument.launch("schedule.json") })
            } }
            if (state.working) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
        }
    }
}

@Composable
private fun DiaryNavigation(selected: Int, onSelect: (Int) -> Unit) {
    val icons = listOf(Icons.Outlined.Schedule, Icons.AutoMirrored.Outlined.Assignment, Icons.Outlined.BarChart, Icons.Outlined.CalendarMonth, Icons.Outlined.Tune)
    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        tabs.forEachIndexed { index, title ->
            NavigationBarItem(
                selected = index == selected, onClick = { onSelect(index) },
                icon = { Icon(icons[index], contentDescription = null) },
                label = { Text(stringResource(title)) },
            )
        }
    }
}

@Composable
private fun ScheduleScreen(state: DiaryState, onImport: () -> Unit) {
    var selectedDay by rememberSaveable { mutableIntStateOf(LocalDate.now(ZoneId.of("Europe/Moscow")).dayOfWeek.value - 1) }
    var selectedWeek by rememberSaveable { mutableIntStateOf(0) }
    val schedule = state.schedule
    val weeks = schedule?.effectiveWeeks().orEmpty()
    val week = weeks.getOrNull(selectedWeek.coerceIn(0, (weeks.size - 1).coerceAtLeast(0)))
    val day = week?.days?.find { it.day.ordinal == selectedDay }
    LazyColumn(
        modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { Label(stringResource(R.string.offline)) }
        item { Hero(stringResource(R.string.schedule_intro), stringResource(R.string.schedule_description)) }
        if (schedule == null) {
            item {
                InfoCard(Icons.Outlined.CalendarMonth, stringResource(R.string.empty_schedule), stringResource(R.string.empty_schedule_body)) {
                    Button(onClick = onImport, enabled = !state.working) { Text(stringResource(R.string.import_action)) }
                }
            }
        } else {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(schedule.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.weekly_template), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            if (weeks.size > 1) item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        weeks.forEachIndexed { index, cycleWeek ->
                            FilterChip(week?.number == cycleWeek.number, onClick = { selectedWeek = index },
                                label = { Text(stringResource(R.string.week_number, cycleWeek.number)) },
                                modifier = Modifier.heightIn(min = 48.dp))
                        }
                    }
                    Text(stringResource(R.string.week_manual), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    shortDays.forEachIndexed { index, resource ->
                        FilterChip(selectedDay == index, onClick = { selectedDay = index }, label = { Text(stringResource(resource)) }, modifier = Modifier.heightIn(min = 48.dp))
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(dayNames[selectedDay]), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.lesson_count, day?.lessons?.size ?: 0), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (day == null) {
                item { InfoCard(Icons.Outlined.EventBusy, stringResource(R.string.day_not_imported), stringResource(R.string.day_not_imported_body)) }
            } else if (day.lessons.isEmpty()) {
                item { InfoCard(Icons.Outlined.EventAvailable, stringResource(R.string.empty_day), stringResource(R.string.weekly_template)) }
            } else {
                items(day.lessons, key = { "${week?.number}-${selectedDay}-${it.number}" }) { LessonCard(it, state.planner.preferences.showTeachers, state.planner.preferences.compact) }
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun LessonCard(lesson: Lesson, showTeachers: Boolean = true, compact: Boolean = false) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(Modifier.fillMaxWidth().padding(if (compact) 8.dp else 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.primaryContainer) {
                Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.lesson_number, lesson.number), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                if (lesson.variants.isEmpty()) {
                    Text(lesson.subject.orEmpty(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                } else lesson.variants.forEachIndexed { index, variant ->
                    if (index > 0) HorizontalDivider()
                    Text(stringResource(if (lesson.variants.size == 2 && index == 0) R.string.left_column
                        else if (lesson.variants.size == 2) R.string.right_column else R.string.variant_number, index + 1),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Text(variant?.subject ?: stringResource(R.string.no_lesson), style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold)
                    variant?.room?.let { Text(stringResource(R.string.room, it), style = MaterialTheme.typography.bodySmall) }
                    if (showTeachers) variant?.teacher?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
                Text(
                    if (lesson.startTime != null && lesson.endTime != null) "${lesson.startTime} – ${lesson.endTime}" else stringResource(R.string.no_time),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                lesson.room?.let { Text(stringResource(R.string.room, it), style = MaterialTheme.typography.bodySmall) }
                if (showTeachers) lesson.teacher?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

@Composable
private fun ImportScreen(state: DiaryState, model: DiaryViewModel, onFile: () -> Unit) {
    val context = LocalContext.current
    var replacing by rememberSaveable { mutableStateOf(false) }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Label(stringResource(R.string.photo_step)) }
        item {
            InfoCard(Icons.Outlined.AutoAwesome, stringResource(R.string.photo_heading), stringResource(R.string.photo_body)) {
                FilledTonalButton(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.prompt_clip_label), PhotoPrompt.text))
                    model.notify(R.string.prompt_copied)
                }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.ContentCopy, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.copy_prompt))
                }
            }
        }
        item {
            Text(stringResource(R.string.zs_notice), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
        item { Label(stringResource(R.string.json_step)) }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onFile, enabled = !state.working, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.choose_file)) }
                TextButton(onClick = { model.editSource(PhotoPrompt.exampleJson) }, enabled = !state.working, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.example)) }
            }
        }
        item {
            OutlinedTextField(
                value = state.source, onValueChange = model::editSource, enabled = !state.working,
                modifier = Modifier.fillMaxWidth(), minLines = 5, maxLines = 10,
                label = { Text(stringResource(R.string.json_label)) },
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                supportingText = { Text(stringResource(R.string.json_hint)) },
                isError = state.issues.isNotEmpty(),
            )
        }
        item {
            Button(model::previewImport, enabled = !state.working && state.source.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.check_json))
            }
        }
        if (state.issues.isNotEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.error_title), fontWeight = FontWeight.Bold)
                        state.issues.take(12).forEach { Text("${it.path}: ${it.message}", style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            }
        }
        state.preview?.let { preview ->
            item { Label(stringResource(R.string.preview_step)) }
            item {
                Text(stringResource(R.string.preview_summary, preview.schedule.name, preview.schedule.effectiveWeeks().size,
                    preview.schedule.effectiveWeeks().sumOf { it.days.size },
                    preview.schedule.effectiveWeeks().sumOf { it.days.sumOf { day -> day.lessons.size } }), style = MaterialTheme.typography.titleMedium)
            }
            if (preview.warnings.isNotEmpty()) item {
                Text(stringResource(R.string.warning_count, preview.warnings.size), color = MaterialTheme.colorScheme.tertiary)
            }
            preview.schedule.effectiveWeeks().forEach { cycleWeek ->
              if (preview.schedule.weeks.isNotEmpty()) item { Text(stringResource(R.string.week_number, cycleWeek.number), style = MaterialTheme.typography.titleLarge) }
              cycleWeek.days.forEach { day ->
                item { Text(stringResource(dayNames[day.day.ordinal]), fontWeight = FontWeight.SemiBold) }
                if (day.lessons.isEmpty()) item { Text(stringResource(R.string.empty_day)) }
                items(day.lessons, key = { "preview-${cycleWeek.number}-${day.day}-${it.number}" }) { LessonCard(it) }
              }
            }
            item {
                Button(onClick = { if (state.schedule == null) model.confirmImport() else replacing = true },
                    enabled = !state.working, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.save_schedule)) }
            }
        }
    }
    if (replacing) ConfirmDialog(R.string.replace_title, R.string.replace_body, R.string.replace,
        onDismiss = { replacing = false }, onConfirm = { replacing = false; model.confirmImport() })
}

@Composable
private fun TasksScreen() {
    var tests by rememberSaveable { mutableStateOf(false) }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item { Hero(stringResource(R.string.tasks_heading), stringResource(R.string.tasks_body)) }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(!tests, onClick = { tests = false }, label = { Text(stringResource(R.string.homework_tab)) }, modifier = Modifier.heightIn(min = 48.dp))
                FilterChip(tests, onClick = { tests = true }, label = { Text(stringResource(R.string.tests_tab)) }, modifier = Modifier.heightIn(min = 48.dp))
            }
        }
        item { InfoCard(Icons.Outlined.CloudOff, stringResource(R.string.not_connected), stringResource(if (tests) R.string.tests_empty else R.string.homework_empty)) }
    }
}

@Composable
private fun MarksScreen() {
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        item { Hero(stringResource(R.string.marks_heading), stringResource(R.string.marks_body)) }
        item { InfoCard(Icons.Outlined.BarChart, stringResource(R.string.marks_empty), stringResource(R.string.marks_empty_body)) }
    }
}

@Composable
private fun SettingsScreen(state: DiaryState, model: DiaryViewModel, onImport: () -> Unit, onExport: () -> Unit) {
    val context = LocalContext.current
    var clearing by rememberSaveable { mutableStateOf(false) }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item { PlannerSettings(state, model) }
        item {
            InfoCard(Icons.Outlined.School, stringResource(R.string.connection_title), stringResource(R.string.connection_body)) {
                Label(stringResource(R.string.connection_status))
                OutlinedButton(onClick = { openMesh(context) { model.notify(R.string.browser_failed) } }) {
                    Icon(Icons.Outlined.OpenInBrowser, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.open_mesh))
                }
            }
        }
        item {
            InfoCard(Icons.Outlined.Shield, stringResource(R.string.privacy_title), stringResource(R.string.privacy_body)) {
                Text(stringResource(R.string.ai_privacy), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            InfoCard(Icons.Outlined.FolderOpen, stringResource(R.string.local_data), state.schedule?.name ?: stringResource(R.string.empty_schedule)) {
                TextButton(onClick = onImport, enabled = !state.working) { Text(stringResource(R.string.import_action)) }
                TextButton(onClick = onExport, enabled = state.schedule != null && !state.working) { Text(stringResource(R.string.export_json)) }
                TextButton(onClick = { clearing = true }, enabled = !state.working, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    Text(stringResource(R.string.clear_schedule))
                }
            }
        }
        item { InfoCard(Icons.Outlined.Code, stringResource(R.string.open_source_title), stringResource(R.string.open_source_body)) }
        item { Text(stringResource(R.string.version), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
    }
    if (clearing) ConfirmDialog(R.string.clear_title, R.string.clear_body, R.string.delete,
        onDismiss = { clearing = false }, onConfirm = { clearing = false; model.clearSchedule() })
}

private fun openMesh(context: Context, failed: () -> Unit) {
    try { CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, Uri.parse("https://school.mos.ru/")) }
    catch (_: ActivityNotFoundException) { failed() }
    catch (_: SecurityException) { failed() }
}

@Composable
private fun Hero(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
        Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun InfoCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String, content: @Composable ColumnScope.() -> Unit = {}) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(icon, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}

@Composable
private fun ConfirmDialog(title: Int, body: Int, action: Int, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(title)) }, text = { Text(stringResource(body)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(action)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}
