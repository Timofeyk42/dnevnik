# Шаблоны API и OAuth

Это структура для самостоятельной реализации интеграции. Методы с `TODO()` намеренно не работают и не подключены к интерфейсу. Зависимости для HTTP-клиента ещё не добавлены: транспорт и подтверждённый протокол выбирает разработчик.

## Общая часть Kotlin Multiplatform

Все пути ниже относительно `shared/src/commonMain/kotlin/org/opendnevnik/shared/mesh/`.

```text
mesh/
├── MeshRepository.kt              # существующий контракт и модели для UI
├── api/
│   ├── MeshApiConfig.kt            # base URL, задаваемый разработчиком
│   ├── MeshApi.kt                  # интерфейс загрузки дневника
│   ├── MeshApiClient.kt            # место для HTTP-запросов
│   └── dto/MeshDiaryResponse.kt    # место для реальных DTO провайдера
├── oauth/
│   ├── OAuthConfig.kt              # endpoints, clientId, redirectUri, scopes
│   ├── OAuthModels.kt              # запрос авторизации и локальная сессия
│   ├── OAuthClient.kt              # интерфейс входа, обновления и отзыва
│   ├── OAuthClientTemplate.kt      # места для реализации OAuth/PKCE
│   └── TokenStore.kt               # интерфейс платформенного хранилища
└── data/
    ├── MeshDiaryMapper.kt          # DTO → DiarySnapshot
    └── RemoteMeshRepository.kt     # сессия → API → mapper → результат для UI
```

`MeshDiaryResponse` пока содержит `JsonElement`, чтобы не придумывать поля ответа МЭШ. Когда формат подтверждён, замени его типизированными `@Serializable` DTO. `OAuthTokens` — локальная модель сессии, а не предполагаемый формат ответа сервера. Строковое представление моделей авторизации скрывает значения.

## Android

`androidApp/src/main/kotlin/org/opendnevnik/android/auth/`:

- `AndroidTokenStore.kt` — шаблон реализации `TokenStore` через Android Keystore и зашифрованное приватное хранилище.
- `AndroidOAuthBrowser.kt` — шаблон запуска системного браузера/Custom Tabs. Объект принадлежит Activity, а не глобальному singleton.

Callback Activity и intent-filter добавляются при реализации, когда известен реальный redirect URI. Текущая кнопка МЭШ продолжает открывать официальный сайт. `INTERNET`, OAuth callback и HTTP-клиент пока не подключены.

Для будущего iOS реализуй тот же `TokenStore` через Keychain, браузерный вход — через ASWebAuthenticationSession. Общий OAuth-код остаётся в `commonMain`.

## Порядок заполнения

1. Задать подтверждённые `MeshApiConfig` и `OAuthConfig`.
2. Реализовать `OAuthClientTemplate`: PKCE S256, случайный одноразовый state/verifier, проверку callback, обмен кода и обновление сессии.
3. Заполнить Android browser handoff и `AndroidTokenStore`.
4. Реализовать `MeshApiClient`, DTO и `MeshDiaryMapper`.
5. Заполнить `RemoteMeshRepository` и только затем подключить его к состоянию экранов.

Пример будущего соединения готовых реализаций на Android:

```kotlin
val repository = RemoteMeshRepository(
    api = MeshApiClient(apiConfig),
    oauth = OAuthClientTemplate(oauthConfig),
    tokens = AndroidTokenStore(context),
    mapper = MeshDiaryMapper(),
)
```

Этот пример создаёт объекты, но вызов незаполненного метода завершится `NotImplementedError`. Никаких действующих credentials или endpoints в шаблонах нет.

## Динамические цвета

`DiaryTheme` использует `dynamicLightColorScheme` / `dynamicDarkColorScheme` начиная с Android 12. На более ранних системах остаётся существующая палитра. Для preview можно вызвать `DiaryTheme(dynamicColor = false)` или задать `darkTheme`. Компоненты и разметка экранов сохраняются.

Источник настройки темы: [официальная документация Material 3](https://developer.android.com/develop/ui/compose/designsystems/material3#dynamic-color-schemes).
