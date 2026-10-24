# Сторонние компоненты

MIT в корне относится к собственному коду проекта, а не меняет лицензии зависимостей.
Версии зависимостей перечислены в `gradle/libs.versions.toml` и wrapper properties.

| Компонент | Лицензия / источник |
| --- | --- |
| Kotlin, Kotlin stdlib, kotlinx.coroutines, kotlinx.serialization | Apache-2.0; https://github.com/JetBrains/kotlin |
| AndroidX, Jetpack Compose, Material Components | Apache-2.0; https://android.googlesource.com/platform/frameworks/support/ |
| Gradle wrapper | Apache-2.0; https://github.com/gradle/gradle |
| JUnit 4 (только тесты) | EPL-1.0; https://github.com/junit-team/junit4 |

Перед распространением release APK/IPA нужен полный отчёт транзитивных зависимостей
и включение их LICENSE/NOTICE. Этот файл не заменяет такой отчёт и не утверждает,
что аудит release-дистрибутива уже проведён. Release в данном проекте не публикуется.
