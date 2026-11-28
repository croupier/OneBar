# ANALYTICS

OneBar использует AppMetrica для Android и Umami для сайта. Оба решения бесплатны на выбранных тарифах, но требуют явного согласия и отдельной настройки аккаунта.

## Почему AppMetrica

- Бесплатный тариф: стандартные отчёты и базовые события без лимита; пользовательские SDK/Post API события — 3,25 млн в день и 100 млн в месяц по состоянию на 28.11.2026.
- Android SDK 8.6.0 распространяется под MIT.
- Работает без Google Play и Google Play Services.
- Есть сессии, аудитория, события и атрибуция.
- Поддерживает запуск с выключенной отправкой до согласия.

Firebase Analytics бесплатна, но тянет GMS и не подходит для основной F-Droid-сборки. Self-hosted Matomo/Countly/PostHog дают больше контроля, но требуют сервера и обслуживания; для учебного проекта это лишние затраты.

## Настройка Android

1. Зарегистрируйте Android-приложение в [AppMetrica](https://appmetrica.yandex.com/application/new).
2. Скопируйте API key из **Settings → Main**.
3. Добавьте строку в игнорируемый `local.properties`:

   ```properties
   APP_METRICA_API_KEY=xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
   ```

4. Соберите APK. API key попадёт в `BuildConfig`, но не в git.
5. В приложении откройте **Настройки → Приватность → Анонимная аналитика**.
6. В AppMetrica проверьте появление Audience, Sessions и Events.

Без API key настройка недоступна, SDK не активируется, приложение работает без аналитики. Для CI key задаётся через Gradle property `APP_METRICA_API_KEY` или environment `ORG_GRADLE_PROJECT_APP_METRICA_API_KEY`.

## Что отправляется

SDK автоматически собирает технические сведения об устройстве, ОС, версии приложения и сессиях. OneBar добавляет только короткие имена событий:

| Событие | Смысл |
|---|---|
| `app_open` | Создан SearchViewModel |
| `analytics_enabled` | Пользователь включил аналитику |
| `search_submitted` | Явно отправлен непустой запрос |
| `app_launched` | Открыто установленное приложение |
| `contact_call` | Запрошено действие звонка |
| `contact_sms` | Запрошено действие СМС |
| `web_search_yandex` | Открыт поиск Яндекса |
| `web_search_google` | Открыт поиск Google |
| `favorite_app_added` / `favorite_app_removed` | Изменено избранное приложения |
| `favorite_contact_added` / `favorite_contact_removed` | Изменён избранный контакт |
| `favorites_opened` / `favorites_closed` | Переключён режим избранного |

Не отправляются: текст запроса, имена, номера, содержание сообщений, package name, пользовательские ID, рекламные ID, App Set ID и геолокация. Сам SDK создаёт собственный `appmetrica_device_id` для объединения сессий; OneBar не задаёт профиль или рекламный ID.

## Ограничения SDK

В `app/build.gradle.kts` исключены необязательные модули AppMetrica:

- ad revenue и все рекламные адаптеры;
- billing и product flow;
- identifiers и location;
- MIT App Set ID adapter остаётся для ABI-совместимости, но GMS исключён полностью; без GMS SDK выбирает собственный DummyAppSetIdRetriever и не получает App Set ID;
- certificates;
- remote permissions и reporter extension;
- screenshots;
- NDK crashes.

Дополнительно в конфигурации отключены crash reporting, ANR, revenue auto tracking, app open auto tracking, location и advertising identifiers. Автоотслеживание сессий включено. Согласие хранится в `onebar_analytics.xml` и исключено из backup/device transfer.

Проверка зависимостей:

```bash
./gradlew :app:dependencies --configuration releaseRuntimeClasspath
```

В выводе не должно быть `com.google.android.gms`, Firebase, Crashlytics или рекламных SDK.

## Установки и атрибуция

Точное число установок sideload APK получить нельзя:

- GitHub Releases считает скачивания, а не установки.
- F-Droid не передаёт разработчику статистику установок.
- AppMetrica видит только пользователей, которые открыли приложение и дали согласие.
- AppMetrica tracker может атрибутировать клики, но для прямого APK нет install referrer магазина; используется вероятностное сопоставление с окном около 24 часов, а не гарантия.
- Органические установки без tracker не показывают источник.

Для первого релиза достаточно смотреть:

1. download count в GitHub Releases API;
2. переходы `download_apk_click` и UTM на сайте;
3. Audience / New users / Sessions в AppMetrica с пометкой «opt-in audience»;
4. события Retention, Regular users и Devicess.

Не называйте число opt-in пользователей общим числом установок.

## Настройка сайта Umami

1. Создайте бесплатный сайт в [Umami Cloud](https://cloud.umami.is).
2. Скопируйте website ID.
3. Вставьте его в `UMAMI_WEBSITE_ID` в `site/assets/analytics.js`.
4. Выполните `npm run build` в `site/`.
5. Проверьте banner и события после согласия.

Hobby: 1 сайт, до 100 000 событий в месяц, 6 месяцев хранения. Umami не использует cookies и по заявлению сервиса не собирает PII. Серверы в США и ЕС. Скрипт не загружается до согласия; отзыв доступен в подвале.

Сайт передаёт только имена событий:

- `download_apk_click`;
- `github_release_click`;
- `github_source_click`;
- `demo_search` с числом результатов, без текста запроса.

## Источники

- [AppMetrica pricing](https://appmetrica.yandex.com/about/pricing)
- [AppMetrica Android SDK](https://github.com/appmetrica/appmetrica-sdk-android)
- [AppMetrica consent](https://appmetrica.yandex.com/docs/en/data-security/notification)
- [AppMetrica attribution](https://appmetrica.yandex.com/docs/en/mobile-tracking/technology)
- [Umami pricing](https://umami.is/pricing)
- [Umami tracker functions](https://docs.umami.is/docs/tracker-functions)
