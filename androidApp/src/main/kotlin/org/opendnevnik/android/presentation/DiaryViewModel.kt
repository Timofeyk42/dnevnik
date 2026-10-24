package org.opendnevnik.android.presentation

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.opendnevnik.android.R
import org.opendnevnik.android.data.ScheduleFileStore
import org.opendnevnik.android.data.readBoundedUtf8
import org.opendnevnik.shared.schedule.ImportResult
import org.opendnevnik.shared.schedule.Schedule
import org.opendnevnik.shared.schedule.ScheduleJson
import org.opendnevnik.shared.schedule.ValidationIssue
import org.opendnevnik.shared.planner.*
import org.opendnevnik.android.data.PlannerFileStore

data class DiaryState(
    val schedule: Schedule? = null,
    val source: String = "",
    val preview: ImportResult.Valid? = null,
    val issues: List<ValidationIssue> = emptyList(),
    val working: Boolean = true,
    val message: Int? = null,
    val planner: PlannerData = PlannerData(),
    val plannerReady: Boolean = false,
    val detail: String? = null,
)

class DiaryViewModel(application: Application) : AndroidViewModel(application) {
    private val store = ScheduleFileStore(application)
    private val plannerStore = PlannerFileStore(application)
    private val mutableState = MutableStateFlow(DiaryState())
    val state = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val planner = withContext(Dispatchers.IO) { plannerStore.read() }
                mutableState.update { it.copy(planner = planner, plannerReady = true) }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutableState.update { it.copy(detail = "Не удалось прочитать планировщик. Восстановите резервную копию; повреждённый файл не перезаписан.") }
            }
            try {
                val result = withContext(Dispatchers.IO) { store.read()?.let(ScheduleJson::parse) }
                mutableState.update { it.copy(
                    schedule = (result as? ImportResult.Valid)?.schedule,
                    working = false,
                    message = if (result is ImportResult.Invalid) R.string.saved_invalid else null,
                ) }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutableState.update { it.copy(working = false, message = R.string.load_failed) }
            }
        }
    }

    fun editSource(source: String) {
        if (mutableState.value.working) return
        if (source.length > ScheduleJson.MAX_BYTES) {
            mutableState.update { it.copy(message = R.string.file_too_large) }
            return
        }
        mutableState.update { it.copy(source = source, preview = null, issues = emptyList()) }
    }

    fun previewImport() {
        if (mutableState.value.working) return
        val source = mutableState.value.source
        mutableState.update { it.copy(working = true, preview = null, issues = emptyList()) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) { ScheduleJson.parse(source) }
            applyPreview(result)
        }
    }

    fun readDocument(uri: Uri) {
        if (mutableState.value.working) return
        mutableState.update { it.copy(working = true, preview = null, issues = emptyList()) }
        viewModelScope.launch {
            try {
                val source = withContext(Dispatchers.IO) {
                    requireNotNull(getApplication<Application>().contentResolver.openInputStream(uri)).use(::readBoundedUtf8)
                }
                mutableState.update { it.copy(source = source) }
                applyPreview(withContext(Dispatchers.Default) { ScheduleJson.parse(source) })
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutableState.update { it.copy(working = false, message = R.string.file_read_failed) }
            }
        }
    }

    fun confirmImport() {
        val current = mutableState.value
        val schedule = current.preview?.schedule ?: return
        if (current.working) return
        mutableState.update { it.copy(working = true) }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { store.save(schedule) }
                mutableState.update { it.copy(
                    schedule = schedule, source = "", preview = null, issues = emptyList(),
                    working = false, message = R.string.import_success,
                ) }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutableState.update { it.copy(working = false, message = R.string.save_failed) }
            }
        }
    }

    fun exportDocument(uri: Uri) {
        val current = mutableState.value
        val schedule = current.schedule ?: return
        if (current.working) return
        mutableState.update { it.copy(working = true) }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    requireNotNull(getApplication<Application>().contentResolver.openOutputStream(uri, "wt")).use {
                        it.write(ScheduleJson.encode(schedule).toByteArray(Charsets.UTF_8))
                    }
                }
                mutableState.update { it.copy(working = false, message = R.string.export_success) }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutableState.update { it.copy(working = false, message = R.string.export_failed) }
            }
        }
    }

    fun clearSchedule() {
        if (mutableState.value.working) return
        mutableState.update { it.copy(working = true) }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { store.clear() }
                mutableState.update { it.copy(schedule = null, source = "", preview = null, issues = emptyList(), working = false, message = R.string.clear_success) }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutableState.update { it.copy(working = false, message = R.string.clear_failed) }
            }
        }
    }

    fun notify(message: Int) { mutableState.update { it.copy(message = message) } }
    fun dismissMessage() { mutableState.update { it.copy(message = null) } }

    fun dismissDetail() { mutableState.update { it.copy(detail = null) } }
    fun report(text: String) { mutableState.update { it.copy(detail = text) } }

    fun saveSchedule(schedule: Schedule) {
        if (state.value.working) return
        val result = ScheduleJson.parse(ScheduleJson.encode(schedule))
        if (result is ImportResult.Invalid) { report(result.issues.joinToString("\n") { it.message }); return }
        result as ImportResult.Valid
        mutableState.update { it.copy(working = true) }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { store.save(result.schedule) }
                mutableState.update { it.copy(schedule = result.schedule, working = false, detail = "Расписание сохранено") }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutableState.update { it.copy(working = false, message = R.string.save_failed) }
            }
        }
    }

    fun saveEvent(event: PlannerEvent) = changePlanner { PlannerRules.put(it, event) }
    fun removeEvent(id: String) = changePlanner { it.copy(events = it.events.filterNot { e -> e.id == id }) }
    fun preferences(preferences: Preferences) = changePlanner { it.copy(preferences = preferences) }
    fun clearPlanner() = changePlanner { PlannerData(preferences = it.preferences) }

    private fun changePlanner(transform: (PlannerData) -> PlannerData) {
        if (state.value.working || !state.value.plannerReady) return
        val next = try { transform(state.value.planner) } catch (_: IllegalArgumentException) {
            report("Проверьте поля события и лимит 1000 событий"); return
        }
        persistPlanner(next)
    }

    private fun persistPlanner(next: PlannerData) {
        mutableState.update { it.copy(working = true) }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { plannerStore.save(next) }
                mutableState.update { it.copy(planner = next, plannerReady = true, working = false) }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutableState.update { it.copy(working = false, detail = "Не удалось сохранить планировщик. Прежние данные сохранены.") }
            }
        }
    }

    fun exportPlanner(uri: Uri) {
        if (state.value.working || !state.value.plannerReady) return
        val data = state.value.planner
        mutableState.update { it.copy(working = true) }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    requireNotNull(getApplication<Application>().contentResolver.openOutputStream(uri, "wt")).use {
                        it.write(PlannerJson.encode(data).toByteArray(Charsets.UTF_8))
                    }
                }
                mutableState.update { it.copy(working = false, detail = "Копия календаря и настроек сохранена") }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutableState.update { it.copy(working = false, message = R.string.export_failed) }
            }
        }
    }

    fun importPlanner(uri: Uri) {
        if (state.value.working) return
        mutableState.update { it.copy(working = true) }
        viewModelScope.launch {
            try {
                val data = withContext(Dispatchers.IO) {
                    requireNotNull(getApplication<Application>().contentResolver.openInputStream(uri)).use {
                        PlannerJson.decode(readBoundedUtf8(it, PlannerJson.MAX_BYTES))
                    }
                }
                // Confirmation is presented before selecting the replacement file.
                withContext(Dispatchers.IO) { plannerStore.save(data) }
                mutableState.update { it.copy(planner = data, plannerReady = true, working = false, detail = "Календарь и настройки восстановлены") }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutableState.update { it.copy(working = false, detail = "Неверная копия или ошибка чтения. Прежний планировщик не заменён.") }
            }
        }
    }

    private fun applyPreview(result: ImportResult) {
        mutableState.update { it.copy(
            working = false,
            preview = result as? ImportResult.Valid,
            issues = (result as? ImportResult.Invalid)?.issues.orEmpty(),
        ) }
    }
}
