import Foundation
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
                if FileManager.default.fileExists(atPath: scheduleURL.path) {
                    let raw = try String(contentsOf: scheduleURL, encoding: .utf8)
                    schedule = try JSONDecoder().decode(Schedule.self, from: Data(bridge.normalizeSchedule(text: raw).utf8))
                }
                let raw = FileManager.default.fileExists(atPath: plannerURL.path) ? try String(contentsOf: plannerURL, encoding: .utf8) : bridge.emptyPlanner()
                let planner = try JSONDecoder().decode(Planner.self, from: Data(bridge.normalizePlanner(text: raw).utf8))
                let loadedSchedule = schedule
                DispatchQueue.main.async { self.schedule = loadedSchedule; self.planner = planner; self.plannerReady = true; self.busy = false }
            } catch { DispatchQueue.main.async { self.busy = false; self.error = "Не удалось загрузить данные. Сохранённые файлы не заменены." } }
        }
    }
    func saveSchedule(_ value: Schedule) { write(schedule: value, planner: nil) }
    func savePlanner(_ value: Planner) { guard plannerReady else { return }; write(schedule: nil, planner: value) }
    private func write(schedule: Schedule?, planner: Planner?) {
        guard !busy else { return }; busy = true
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
                DispatchQueue.main.async {
                    if let schedule { self.schedule = schedule }
                    if let planner { self.planner = planner }
                    self.busy = false
                }
            } catch { DispatchQueue.main.async { self.busy = false; self.error = "Не удалось сохранить. Проверьте поля и время. Прежние данные не заменены." } }
        }
    }
    func importSchedule(_ raw: String) {
        guard !busy else { return }; busy = true
        worker.async {
            do {
                let normalized = try NativeDiaryBridge().normalizeSchedule(text: raw)
                let decoded = try JSONDecoder().decode(Schedule.self, from: Data(normalized.utf8))
                try Data(normalized.utf8).write(to: Self.directory().appendingPathComponent("schedule.json"), options: [.atomic, .completeFileProtection])
                DispatchQueue.main.async { self.schedule = decoded; self.busy = false }
            } catch { DispatchQueue.main.async { self.busy = false; self.error = "Неверное расписание. Прежние данные не заменены." } }
        }
    }
    func putEvent(_ event: PlannerEvent) {
        var next = planner; next.events.removeAll { $0.id == event.id }; next.events.append(event)
        savePlanner(next)
    }
    func removeEvent(_ id: String) { var next = planner; next.events.removeAll { $0.id == id }; savePlanner(next) }
}
