import Foundation

struct LessonVariant: Codable { var subject: String; var room: String?; var teacher: String? }
struct Lesson: Codable, Identifiable {
    var number: Int
    var subject: String?
    var startTime: String?
    var endTime: String?
    var room: String?
    var teacher: String?
    var variants: [LessonVariant?]?
    var id: Int { number }
    var title: String { subject ?? (variants ?? []).map { $0?.subject ?? "—" }.joined(separator: " | ") }
}
struct ScheduleDay: Codable { var day: String; var lessons: [Lesson] }
struct ScheduleWeek: Codable { var number: Int; var days: [ScheduleDay] }
struct Schedule: Codable {
    var schemaVersion: Int
    var name: String
    var timeZone: String
    var days: [ScheduleDay]?
    var weeks: [ScheduleWeek]?
    var effectiveWeeks: [ScheduleWeek] { schemaVersion == 1 ? [ScheduleWeek(number: 1, days: days ?? [])] : weeks ?? [] }
}
struct EventSource: Codable { var key: String; var date: String; var title: String; var startTime: String?; var endTime: String?; var kind: String? }
struct PlannerEvent: Codable, Identifiable {
    var id: String = UUID().uuidString
    var title: String = ""
    var date: String = Self.today
    var kind: String = "EVENT"
    var startTime: String? = nil
    var endTime: String? = nil
    var notes: String = ""
    var done: Bool = false
    var source: EventSource? = nil
    var sourceMissing: Bool = false
    var dateOverridden: Bool = false
    var titleOverridden: Bool = false
    var timeOverridden: Bool = false
    var kindOverridden: Bool = false
    static var today: String {
        let format = DateFormatter(); format.dateFormat = "yyyy-MM-dd"
        format.locale = Locale(identifier: "en_US_POSIX"); format.timeZone = TimeZone(identifier: "Europe/Moscow")
        return format.string(from: Date())
    }
}
struct Preferences: Codable {
    var theme: String = "SYSTEM"
    var dynamicColors: Bool = true
    var compact: Bool = false
    var showTeachers: Bool = true
}
struct Planner: Codable { var version = 1; var events: [PlannerEvent] = []; var preferences = Preferences() }
