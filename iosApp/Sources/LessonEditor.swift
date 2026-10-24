import SwiftUI

struct LessonEditor: View {
    @EnvironmentObject var store: DiaryStore
    @Environment(\.dismiss) private var dismiss
    @State var lesson: Lesson
    let week: Int
    let day: String
    let isNew: Bool
    @State private var originalNumber: Int?
    @State private var deleteConfirm = false
    var body: some View {
        NavigationStack {
            Form {
                Stepper("Номер: \(lesson.number)", value: $lesson.number, in: 0...20)
                if let variants = lesson.variants, !variants.isEmpty {
                    ForEach(variants.indices, id: \.self) { index in
                        Section("Колонка \(index + 1)") {
                            TextField("Предмет (пусто = нет урока)", text: Binding(get: { lesson.variants?[index]?.subject ?? "" }, set: { value in
                                if value.isEmpty { lesson.variants?[index] = nil }
                                else if lesson.variants?[index] == nil { lesson.variants?[index] = LessonVariant(subject: value) }
                                else { lesson.variants?[index]?.subject = value }
                            }))
                            TextField("Кабинет", text: Binding(get: { lesson.variants?[index]?.room ?? "" }, set: { lesson.variants?[index]?.room = $0.isEmpty ? nil : $0 }))
                            TextField("Учитель", text: Binding(get: { lesson.variants?[index]?.teacher ?? "" }, set: { lesson.variants?[index]?.teacher = $0.isEmpty ? nil : $0 }))
                        }
                    }
                } else {
                    TextField("Предмет", text: Binding(get: { lesson.subject ?? "" }, set: { lesson.subject = $0 }))
                    TextField("Кабинет", text: Binding(get: { lesson.room ?? "" }, set: { lesson.room = $0.isEmpty ? nil : $0 }))
                    TextField("Учитель", text: Binding(get: { lesson.teacher ?? "" }, set: { lesson.teacher = $0.isEmpty ? nil : $0 }))
                }
                TextField("Начало ЧЧ:ММ", text: Binding(get: { lesson.startTime ?? "" }, set: { lesson.startTime = $0.isEmpty ? nil : $0 }))
                TextField("Конец ЧЧ:ММ", text: Binding(get: { lesson.endTime ?? "" }, set: { lesson.endTime = $0.isEmpty ? nil : $0 }))
                if !isNew { Button("Удалить урок", role: .destructive) { deleteConfirm = true } }
            }
            .navigationTitle("Урок")
            .onAppear { if originalNumber == nil { originalNumber = lesson.number } }
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Отмена") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) { Button("Сохранить") { save(remove: false) }.disabled(store.busy) }
            }
            .confirmationDialog("Удалить урок из выбранной недели?", isPresented: $deleteConfirm) { Button("Удалить", role: .destructive) { save(remove: true) } }
        }
    }
    private func save(remove: Bool) {
        guard var schedule = store.schedule else { return }
        var weeks = schedule.effectiveWeeks
        guard let wi = weeks.firstIndex(where: { $0.number == week }) else { return }
        if !weeks[wi].days.contains(where: { $0.day == day }) { weeks[wi].days.append(ScheduleDay(day: day, lessons: [])) }
        guard let di = weeks[wi].days.firstIndex(where: { $0.day == day }) else { return }
        if !remove && weeks[wi].days[di].lessons.contains(where: { $0.number == lesson.number && (isNew || $0.number != originalNumber) }) { store.error = "Номер урока уже занят"; return }
        if !isNew { weeks[wi].days[di].lessons.removeAll { $0.number == originalNumber } }
        if !remove { weeks[wi].days[di].lessons.append(lesson) }
        if schedule.schemaVersion == 1 { schedule.days = weeks[0].days } else { schedule.weeks = weeks }
        store.saveSchedule(schedule) { dismiss() }
    }
}
