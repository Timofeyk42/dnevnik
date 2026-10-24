# Mac, Codex и проверка iOS без iPhone

## Рекомендуемый путь: локально на Mac

1. Установить актуальное приложение ChatGPT/Codex для Mac и войти в тот же аккаунт
   и workspace. В Codex открыть локальную папку проекта. Это стандартный процесс
   [официального quickstart](https://learn.chatgpt.com/docs/quickstart).
2. Установить полный Xcode, запустить его один раз и завершить настройку iOS platform
   support/Simulator. Одних Command Line Tools недостаточно.
3. Установить JDK 17, Android SDK Platform 36 + Build Tools 36.0.0 (они нужны текущему
   KMP Gradle-проекту даже при подготовке iOS) и XcodeGen. `brew install xcodegen`
   использован и проверен в CI.
4. Клонировать репозиторий, а не копировать кеши и build с Windows:

```sh
git clone https://github.com/Timofeyk42/dnevnik.git
cd dnevnik
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
```

Задать установленный Android SDK через ANDROID_HOME либо local.properties.
Не копировать Windows-путь SDK. Открыть эту папку в Codex как проект.

## Если нужно продолжать именно этот чат с Windows

На Mac открыть Settings → Connections → Control this Mac и настроить доступ.
На Windows — Settings → Connections → Control other devices и добавить Mac,
если этот пункт доступен аккаунту. Оба приложения должны быть в одном аккаунте
и workspace. На Mac сохранить проект того же GitHub-репозитория. Затем внизу чата
выбрать место выполнения → Mac → Hand off. Наличие функции зависит от rollout.
Пока идёт работа, Mac должен быть включён и подключён к сети.
Это описано в [Remote connections](https://learn.chatgpt.com/docs/remote-connections).

Если функция недоступна, работать локально в Codex на Mac и передавать изменения
обычными Git commit/push/pull. Это не требует удалённого управления Windows.
Не открывать обеим машинам одну общую синхронизируемую папку с Gradle-кешами.

## Сборка и запуск

Из корня репозитория:

```sh
./gradlew -PenableIos=true :shared:assembleDnevnikSharedDebugXCFramework
cd iosApp
xcodegen generate
open Dnevnik.xcodeproj
```

В Xcode выбрать схему **Dnevnik**, затем доступный **iPhone Simulator**, нажать Run.
Если нет Simulator runtime, загрузить его в настройках компонентов Xcode.
Формат проекта совместим с Xcode 15; для современного системного оформления
проверять также сборку новым SDK и соответствующим runtime.

Команда проверки без release signing:

```sh
xcodebuild -project Dnevnik.xcodeproj -scheme Dnevnik -sdk iphonesimulator -configuration Debug CODE_SIGNING_ALLOWED=NO build
```

Сам iPhone для Simulator не нужен. Выбор схемы, симулятора и Run описан в
[инструкции Apple](https://help.apple.com/xcode/mac/current/en.lproj/devfd9e28378.html).

## Что проверить в Simulator

- Импорт синтетического JSON v1/v2, предпросмотр и отказ на неверном JSON.
- Редактирование урока/параллельной колонки, ручной выбор недели, вторая неделя.
- Создание контрольной/задания, изменение даты, отметка выполнения, перезапуск.
- Файловый экспорт расписания и backup/restore календаря; ошибочная копия не заменяет данные.
- Светлая/тёмная тема, клавиатура, узкий экран, крупный шрифт и VoiceOver.

Simulator не воспроизводит все свойства и производительность реального устройства.
Перед выпуском нужен физический iPhone — например, тестировщик с согласия владельца.
Не считать прошедшую сборку проверкой поведения на устройстве.
[Ограничения Simulator — Apple](https://developer.apple.com/documentation/xcode/running-your-app-on-simulated-or-physical-devices).

## Готовое задание Codex на Mac

> Прочитай README.md, docs/INTEGRATION_HANDOFF.md, docs/REVIEW.md и iosApp/README.md.
> Проверь инструменты Mac, собери DnevnikShared XCFramework и SwiftUI, запусти приложение
> в iPhone Simulator. Пройди синтетические сценарии из docs/MAC_IOS_SETUP.md,
> исправь найденные ошибки и сохрани отчёт. API/OAuth МЭШ остаются отключёнными
> шаблонами; не придумывай endpoints и credentials. Реальные школьные данные не
> используй в fixtures/логах. Не подписывай release и не публикуй в App Store.
