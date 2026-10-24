# SwiftUI-клиент

Исходники нативного клиента: расписание и ручной выбор недели, редактирование уроков,
импорт JSON с предпросмотром, личные события/контрольные/задания, тема.
Валидация выполняется Kotlin-ядром через `NativeDiaryBridge`. Диск — отдельная
последовательная очередь, атомарная запись с File Protection, файлы исключены из backup.
МЭШ и ИИ не подключены. Для внешнего вида используются стандартные SwiftUI
TabView/NavigationStack/Form; оформление системных элементов зависит от iOS/SDK.

## Сборка на Mac

Нужны Xcode, JDK 17, Android SDK 36 (для конфигурации KMP Gradle) и XcodeGen.
Установить XcodeGen из официального Homebrew-пакета: `brew install xcodegen`.
Из корня:

```sh
./gradlew -PenableIos=true :shared:assembleDnevnikSharedDebugXCFramework
cd iosApp
xcodegen generate
xcodebuild -project Dnevnik.xcodeproj -scheme Dnevnik -sdk iphonesimulator -configuration Debug CODE_SIGNING_ALLOWED=NO build
```

Framework строится отдельно: Xcode не запускает shell-script сборки и не требует
отключения sandbox. После изменения Kotlin повторить первую команду.
Для запуска открыть `Dnevnik.xcodeproj` и выбрать iPhone Simulator.
Для физического устройства выбрать свою development team; release signing не настроен.

## Ограничения проверки и паритета

Этот клиент добавлен на Windows. [macOS CI](https://github.com/Timofeyk42/dnevnik/actions/runs/40126183768)
успешно собрал Kotlin XCFramework и SwiftUI-приложение для iOS Simulator.
Нужны интерактивные Simulator, VoiceOver и Dynamic Type проверки; сборка их не заменяет.
Файловый импорт/экспорт расписания и backup планировщика реализованы.
Android сейчас функциональнее: передача в системный календарь, настройка количества
параллельных колонок и компактности.
На iOS редактируются уже импортированные колонки, но их количество не меняется.
Нативный ИИ на iOS не планируется.
