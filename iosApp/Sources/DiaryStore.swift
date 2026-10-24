import Foundation
import Combine
import DnevnikShared

@MainActor final class DiaryStore: ObservableObject {
    @Published var schedule: Schedule?
    @Published var planner = Planner()
    @Published var busy = true
    @Published var error: String?
    @Published var plannerReady = false
    private let worker = DispatchQueue(label: "org.opendnevnik.storage")
    nonisolated private static func directory() throws -> URL {
        var url = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0].appendingPathComponent("Dnevnik", isDirectory: true)
        try FileManager.default.createDirectory(at: url, withIntermediateDirectories: true)
        var values = URLResourceValues(); values.isExcludedFromBackup = true
        try url.setResourceValues(values)
        return url
    }
    init() { load() }
    func load() {
        worker.async {
            do {
                let directory = try Self.directory()
                let bridge = NativeDiaryBridge()
                let scheduleURL = directory.appendingPathComponent("schedule.json")
                let plannerURL = directory.appendingPathComponent("planner.json")
                var schedule: Schedule? = nil
                var planner: Planner? = nil
                var problems: [String] = []
                do {
                    if FileManager.default.fileExists(atPath: scheduleURL.path) {
                        let raw = try String(contentsOf: scheduleURL, encoding: .utf8)
                        schedule = try JSONDecoder().decode(Schedule.self, from: Data(bridge.normalizeSchedule(text: raw).utf8))
                    }
                } catch { problems.append("Не удалось прочитать расписание. Можно импортировать его заново.") }
                do {
                    let raw = FileManager.default.fileExists(atPath: plannerURL.path) ? try String(contentsOf: plannerURL, encoding: .utf8) : bridge.emptyPlanner()
                    planner = try JSONDecoder().decode(Planner.self, from: Data(bridge.normalizePlanner(text: raw).utf8))
                } catch { problems.append("Не удалось прочитать календарь. Повреждённый файл не перезаписан.") }
                let loadedSchedule = schedule
                let loadedPlanner = planner
                let message = problems.isEmpty ? nil : problems.joined(separator: "\n")
                DispatchQueue.main.async {
                    self.schedule = loadedSchedule
                    if let loadedPlanner { self.planner = loadedPlanner; self.plannerReady = true }
                    self.error = message; self.busy = false
                }
            } catch { DispatchQueue.main.async { self.busy = false; self.error = "Не удалось загрузить данные. Сохранённые файлы не заменены." } }
        }
    }
    func saveSchedule(_ value: Schedule, completion: @escaping () -> Void = {}) { write(schedule: value, planner: nil, completion: completion) }
    func savePlanner(_ value: Planner, completion: @escaping () -> Void = {}) { guard plannerReady else { return }; write(schedule: nil, planner: value, completion: completion) }
    private func write(schedule: Schedule?, planner: Planner?, completion: @escaping () -> Void) {
        guard !busy else { return }; busy = true; error = nil
        worker.async {
            do {
                let bridge = NativeDiaryBridge()
                let raw: String
                let filename: String
                if let schedule {
                    raw = try bridge.normalizeSchedule(text: String(decoding: JSONEncoder().encode(schedule), as: UTF8.self)); filename = "schedule.json"
                } else if let planner {
                    raw = try bridge.normalizePlanner(text: String(decoding: JSONEncoder().encode(planner), as: UTF8.self)); filename = "planner.json"
                } else { return }
                try Data(raw.utf8).write(to: Self.directory().appendingPathComponent(filename), options: [.atomic, .completeFileProtection])
                let savedSchedule = schedule == nil ? nil : try JSONDecoder().decode(Schedule.self, from: Data(raw.utf8))
                let savedPlanner = planner == nil ? nil : try JSONDecoder().decode(Planner.self, from: Data(raw.utf8))
                DispatchQueue.main.async {
                    if let savedSchedule { self.schedule = savedSchedule }
                    if let savedPlanner { self.planner = savedPlanner }
                    self.busy = false
                    completion()
                }
            } catch { DispatchQueue.main.async { self.busy = false; self.error = "Не удалось сохранить. Проверьте поля и время. Прежние данные не заменены." } }
        }
    }
    func importSchedule(_ raw: String, completion: @escaping () -> Void = {}) {
        guard !busy else { return }; busy = true; error = nil
        worker.async {
            do {
                let normalized = try NativeDiaryBridge().normalizeSchedule(text: raw)
                let decoded = try JSONDecoder().decode(Schedule.self, from: Data(normalized.utf8))
                try Data(normalized.utf8).write(to: Self.directory().appendingPathComponent("schedule.json"), options: [.atomic, .completeFileProtection])
                DispatchQueue.main.async { self.schedule = decoded; self.busy = false; completion() }
            } catch { DispatchQueue.main.async { self.busy = false; self.error = "Неверное расписание. Прежние данные не заменены." } }
        }
    }
    func putEvent(_ event: PlannerEvent, completion: @escaping () -> Void = {}) {
        var edited = event
        if let source = event.source, let old = planner.events.first(where: { $0.id == event.id }) {
            if old.date != event.date { edited.dateOverridden = event.date != source.date }
            if old.title != event.title { edited.titleOverridden = event.title != source.title }
            if old.startTime != event.startTime || old.endTime != event.endTime { edited.timeOverridden = event.startTime != source.startTime || event.endTime != source.endTime }
            if old.kind != event.kind { edited.kindOverridden = event.kind != source.kind }
        }
        var next = planner; next.events.removeAll { $0.id == event.id }; next.events.append(edited)
        savePlanner(next, completion: completion)
    }
    func removeEvent(_ id: String) { var next = planner; next.events.removeAll { $0.id == id }; savePlanner(next) }

    func readImportFile(_ url: URL, planner: Bool, completion: @escaping (String) -> Void) {
        guard !busy else { return }; busy = true; error = nil
        worker.async {
            let accessed = url.startAccessingSecurityScopedResource()
            defer { if accessed { url.stopAccessingSecurityScopedResource() } }
            do {
                let maxBytes = planner ? 2_097_152 : 262_144
                let handle = try FileHandle(forReadingFrom: url)
                defer { try? handle.close() }
                var data = Data()
                while data.count <= maxBytes {
                    let part = try handle.read(upToCount: min(8192, maxBytes + 1 - data.count)) ?? Data()
                    if part.isEmpty { break }
                    data.append(part)
                }
                guard data.count <= maxBytes, let text = String(data: data, encoding: .utf8) else { throw CocoaError(.fileReadCorruptFile) }
                DispatchQueue.main.async { self.busy = false; completion(text) }
            } catch { DispatchQueue.main.async { self.busy = false; self.error = "Не удалось прочитать UTF-8 JSON или файл слишком велик." } }
        }
    }

    func restorePlanner(_ raw: String) {
        guard !busy else { return }; busy = true; error = nil
        worker.async {
            do {
                let text = try NativeDiaryBridge().normalizePlanner(text: raw)
                let decoded = try JSONDecoder().decode(Planner.self, from: Data(text.utf8))
                try Data(text.utf8).write(to: Self.directory().appendingPathComponent("planner.json"), options: [.atomic, .completeFileProtection])
                DispatchQueue.main.async { self.planner = decoded; self.plannerReady = true; self.busy = false }
            } catch { DispatchQueue.main.async { self.busy = false; self.error = "Неверная копия. Прежний календарь не заменён." } }
        }
    }

    func prepareExport(planner: Bool, completion: @escaping (String) -> Void) {
        guard !busy, planner ? plannerReady : schedule != nil else { return }
        let scheduleSnapshot = schedule
        let plannerSnapshot = self.planner
        busy = true
        worker.async {
            do {
                let bridge = NativeDiaryBridge()
                let text: String
                if planner { text = try bridge.normalizePlanner(text: String(decoding: JSONEncoder().encode(plannerSnapshot), as: UTF8.self)) }
                else if let scheduleSnapshot { text = try bridge.normalizeSchedule(text: String(decoding: JSONEncoder().encode(scheduleSnapshot), as: UTF8.self)) }
                else { throw CocoaError(.fileReadNoSuchFile) }
                DispatchQueue.main.async { self.busy = false; completion(text) }
            } catch { DispatchQueue.main.async { self.busy = false; self.error = "Не удалось подготовить экспорт." } }
        }
    }
}
