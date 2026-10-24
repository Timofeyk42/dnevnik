import SwiftUI
import UniformTypeIdentifiers
import DnevnikShared

@main struct DnevnikApp: App {
    @StateObject private var store = DiaryStore()
    var body: some Scene {
        WindowGroup {
            TabView {
                ScheduleView().tabItem { Label("Расписание", systemImage: "list.bullet") }
                CalendarView().tabItem { Label("Календарь", systemImage: "calendar") }
                SettingsView().tabItem { Label("Настройки", systemImage: "gearshape") }
            }
            .environmentObject(store)
            .preferredColorScheme(store.planner.preferences.theme == "LIGHT" ? .light : store.planner.preferences.theme == "DARK" ? .dark : nil)
            .alert("Дневник", isPresented: Binding(get: { store.error != nil }, set: { if !$0 { store.error = nil } })) { Button("Понятно") { store.error = nil } } message: { Text(store.error ?? "") }
        }
    }
}

private let days = ["monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday"]
private let dayNames = ["Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс"]

struct ScheduleView: View {
    @EnvironmentObject var store: DiaryStore
    @State private var week = 1
    @State private var day = "monday"
    @State private var importing = false
    @State private var editing: Lesson?
    @State private var newLesson = false
    var body: some View {
        NavigationStack {
            List {
                if let schedule = store.schedule {
                    Section(schedule.name) {
                        Picker("Неделя (вручную)", selection: $week) {
                            ForEach(schedule.effectiveWeeks, id: \.number) { Text("Неделя \($0.number)").tag($0.number) }
                        }
                        Picker("День", selection: $day) { ForEach(Array(days.enumerated()), id: \.element) { index, value in Text(dayNames[index]).tag(value) } }
                        ForEach(schedule.effectiveWeeks.first { $0.number == week }?.days.first { $0.day == day }?.lessons ?? []) { lesson in
                            Button { editing = lesson } label: {
                                VStack(alignment: .leading) {
                                    Text("\(lesson.number). \(lesson.title)").font(.headline)
                                    Text(lesson.startTime.map { "\($0)–\(lesson.endTime ?? "")" } ?? "Без времени").font(.caption)
                                    if let room = lesson.room { Text("Кабинет \(room)") }
                                    if store.planner.preferences.showTeachers, let teacher = lesson.teacher { Text(teacher).font(.caption) }
                                }.foregroundStyle(.primary)
                            }
                        }
                        Button("Добавить урок") { newLesson = true }
                        if schedule.effectiveWeeks.count == 1 {
                            Button("Добавить вторую неделю — копию") {
                                var next = schedule
                                let first = schedule.effectiveWeeks[0]
                                next.schemaVersion = 2; next.days = nil
                                next.weeks = [first, ScheduleWeek(number: first.number == 1 ? 2 : 1, days: first.days)]
                                store.saveSchedule(next)
                            }
                        }
                    }
                } else {
                    ContentUnavailableView("Нет расписания", systemImage: "calendar", description: Text("Импортируйте JSON или создайте неделю."))
                    Button("Создать расписание") { store.importSchedule(NativeDiaryBridge().emptySchedule()) }
                }
                Text("Локальный шаблон. ЗС — обычный урок. Недели не привязаны к датам автоматически.").font(.caption)
            }
            .disabled(store.busy)
            .onChange(of: store.schedule?.effectiveWeeks.map(\.number) ?? []) { _, numbers in
                if !numbers.contains(week) { week = numbers.first ?? 1 }
            }
            .navigationTitle("Расписание")
            .toolbar { Button("Импорт") { importing = true } }
            .sheet(isPresented: $importing) { ImportView() }
            .sheet(item: $editing) { lesson in LessonEditor(lesson: lesson, week: week, day: day, isNew: false) }
            .sheet(isPresented: $newLesson) {
                let used = store.schedule?.effectiveWeeks.first { $0.number == week }?.days.first { $0.day == day }?.lessons.map(\.number) ?? []
                LessonEditor(lesson: Lesson(number: (1...20).first { !used.contains($0) } ?? 0, subject: ""), week: week, day: day, isNew: true)
            }
        }
    }
}

struct ImportView: View {
    @EnvironmentObject var store: DiaryStore
    @Environment(\.dismiss) private var dismiss
    @State private var source = ""
    @State private var preview: Schedule?
    @State private var normalized = ""
    @State private var error: String?
    @State private var checking = false
    @State private var confirming = false
    @State private var choosingFile = false
    var body: some View {
        NavigationStack {
            Form {
                Button("Скопировать инструкцию для фото") { UIPasteboard.general.string = NativeDiaryBridge().photoPrompt() }
                Button("Выбрать JSON-файл") { choosingFile = true }.disabled(checking || store.busy)
                TextEditor(text: $source).frame(minHeight: 180).disabled(checking).onChange(of: source) { _, _ in preview = nil }
                Button("Проверить JSON") {
                    checking = true
                    let raw = source
                    DispatchQueue.global(qos: .userInitiated).async {
                        do {
                            let text = try NativeDiaryBridge().normalizeSchedule(text: raw)
                            let value = try JSONDecoder().decode(Schedule.self, from: Data(text.utf8))
                            DispatchQueue.main.async { normalized = text; preview = value; error = nil; checking = false }
                        } catch { DispatchQueue.main.async { self.error = "Проверьте JSON, номера, предметы и время"; checking = false } }
                    }
                }.disabled(checking || source.utf8.count > 262144)
                if let error { Text(error).foregroundStyle(.red) }
                if let preview {
                    Section(preview.name) {
                        ForEach(preview.effectiveWeeks, id: \.number) { week in
                            Text("Неделя \(week.number)")
                            ForEach(week.days, id: \.day) { day in
                                Text(day.day)
                                ForEach(day.lessons) { Text("\($0.number). \($0.title)") }
                            }
                        }
                        Button("Сохранить расписание") { confirming = true }
                    }
                }
            }
            .navigationTitle("Импорт JSON")
            .toolbar { Button("Закрыть") { dismiss() } }
            .fileImporter(isPresented: $choosingFile, allowedContentTypes: [.json, .plainText]) { result in
                if case let .success(url) = result { store.readImportFile(url, planner: false) { source = $0; preview = nil } }
            }
            .confirmationDialog("Заменить текущее расписание?", isPresented: $confirming, titleVisibility: .visible) {
                Button("Заменить") { store.importSchedule(normalized) { dismiss() } }
            }
        }
    }
}

struct CalendarView: View {
    @EnvironmentObject var store: DiaryStore
    @State private var editing: PlannerEvent?
    @State private var remove: PlannerEvent?
    var body: some View {
        NavigationStack {
            List {
                if store.planner.events.isEmpty { ContentUnavailableView("Нет событий", systemImage: "calendar", description: Text("Добавьте контрольную, задание или личное событие.")) }
                ForEach(store.planner.events.sorted { $0.date < $1.date }) { event in
                    Button { editing = event } label: {
                        VStack(alignment: .leading) {
                            Text(event.title).font(.headline)
                            Text(event.date + (event.startTime.map { " · \($0)" } ?? " · весь день"))
                            if event.done { Text("Выполнено").font(.caption) }
                            if let source = event.source { Text("МЭШ: \(source.date)").font(.caption) }
                            if event.sourceMissing { Text("Источник удалил событие — проверьте актуальность").font(.caption) }
                        }.foregroundStyle(.primary)
                    }.swipeActions { Button("Удалить", role: .destructive) { remove = event } }
                }
            }
            .disabled(store.busy || !store.plannerReady)
            .navigationTitle("Календарь")
            .toolbar { Button("Добавить", systemImage: "plus") { editing = PlannerEvent() }.disabled(store.busy || !store.plannerReady) }
            .sheet(item: $editing) { EventEditor(event: $0) }
            .confirmationDialog("Удалить локальное событие?", isPresented: Binding(get: { remove != nil }, set: { if !$0 { remove = nil } })) {
                Button("Удалить", role: .destructive) { if let remove { store.removeEvent(remove.id) }; remove = nil }
            }
        }
    }
}

struct EventEditor: View {
    @EnvironmentObject var store: DiaryStore
    @Environment(\.dismiss) private var dismiss
    @State var event: PlannerEvent
    var body: some View {
        NavigationStack {
            Form {
                TextField("Название", text: $event.title)
                TextField("ГГГГ-ММ-ДД", text: $event.date)
                TextField("Начало ЧЧ:ММ (необязательно)", text: Binding(get: { event.startTime ?? "" }, set: { event.startTime = $0.isEmpty ? nil : $0 }))
                TextField("Окончание ЧЧ:ММ", text: Binding(get: { event.endTime ?? "" }, set: { event.endTime = $0.isEmpty ? nil : $0 }))
                Picker("Тип", selection: $event.kind) { Text("Событие").tag("EVENT"); Text("Контрольная").tag("TEST"); Text("Задание").tag("HOMEWORK"); Text("Занятие").tag("LESSON") }
                TextField("Заметка", text: $event.notes, axis: .vertical)
                if event.kind == "HOMEWORK" { Toggle("Выполнено", isOn: $event.done) }
                if let source = event.source {
                    Text("Школьная дата: \(source.date). Изменения здесь личные.")
                    Button("Вернуть дату и название МЭШ") { event.date = source.date; event.title = source.title; event.dateOverridden = false; event.titleOverridden = false }
                }
            }
            .navigationTitle("Событие")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Отмена") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) { Button("Сохранить") { store.putEvent(event) { dismiss() } }.disabled(store.busy || event.title.isEmpty) }
            }
        }
    }
}

struct SettingsView: View {
    @EnvironmentObject var store: DiaryStore
    @State private var exportDocument: JSONDocument?
    @State private var exporting = false
    @State private var exportName = "dnevnik"
    @State private var restoring = false
    @State private var confirmRestore = false
    var body: some View {
        NavigationStack {
            Form {
                Section("Внешний вид") {
                    Picker("Тема", selection: Binding(get: { store.planner.preferences.theme }, set: { value in var next = store.planner; next.preferences.theme = value; store.savePlanner(next) })) {
                        Text("Система").tag("SYSTEM"); Text("Светлая").tag("LIGHT"); Text("Тёмная").tag("DARK")
                    }
                }.disabled(store.busy || !store.plannerReady)
                Section("МЭШ") {
                    Text("Не подключено. API и OAuth заполняются после получения официальных условий доступа.")
                    Link("Открыть МЭШ", destination: URL(string: "https://school.mos.ru/")!)
                    Text("Оценки не редактируются. ИИ в iOS не входит.").font(.caption)
                }
                Section("Файлы и резервная копия") {
                    Button("Экспорт расписания") {
                        store.prepareExport(planner: false) { exportDocument = JSONDocument(text: $0); exportName = "schedule"; exporting = true }
                    }.disabled(store.busy || store.schedule == nil)
                    Button("Копия календаря и настроек") {
                        store.prepareExport(planner: true) { exportDocument = JSONDocument(text: $0); exportName = "dnevnik-planner"; exporting = true }
                    }.disabled(store.busy || !store.plannerReady)
                    Button("Восстановить календарь и настройки") { confirmRestore = true }.disabled(store.busy)
                    Text("Копии содержат личные записи. Выбирайте доверенное хранилище.").font(.caption)
                }
                Section("Приватность") { Text("Локальное хранение. Нет аналитики и рекламы. Независимый проект, не официальное приложение МЭШ.") }
            }.navigationTitle("Настройки")
            .fileExporter(isPresented: $exporting, document: exportDocument, contentType: .json, defaultFilename: exportName) { result in
                if case .failure = result { store.error = "Не удалось экспортировать файл" }
            }
            .confirmationDialog("Заменить календарь и настройки?", isPresented: $confirmRestore, titleVisibility: .visible) {
                Button("Выбрать копию и заменить") { restoring = true }
            } message: { Text("Сначала сохраните важные записи. Расписание останется без изменений.") }
            .fileImporter(isPresented: $restoring, allowedContentTypes: [.json, .plainText]) { result in
                if case let .success(url) = result { store.readImportFile(url, planner: true) { store.restorePlanner($0) } }
            }
        }
    }
}

struct JSONDocument: FileDocument {
    static var readableContentTypes: [UTType] { [.json] }
    var text: String
    init(text: String) { self.text = text }
    init(configuration: ReadConfiguration) throws {
        guard let data = configuration.file.regularFileContents, data.count <= 2_097_152,
              let value = String(data: data, encoding: .utf8) else { throw CocoaError(.fileReadCorruptFile) }
        text = value
    }
    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper { FileWrapper(regularFileWithContents: Data(text.utf8)) }
}
