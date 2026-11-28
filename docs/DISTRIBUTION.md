# DISTRIBUTION

План распространения OneBar без Google Play. Проект некоммерческий, поэтому приоритет — бесплатные каналы, открытый исходный код и минимум обслуживания.

## Рекомендация для первого релиза

1. **GitHub Releases** — основной источник подписанного APK и SHA-256.
2. **GitHub Pages** — презентационный сайт и инструкция установки.
3. **F-Droid** — подать merge request в `fdroiddata` после проверки сборки и метаданных.
4. **IzzyOnDroid** — подать заявку со ссылкой на тегированный GitHub Release; учесть более строгое отношение к аналитике.
5. **Собственный F-Droid repo** — резервный вариант, если основной каталог не примет опциональную аналитику.

## Сравнение каналов

| Канал | Стоимость | Что нужно | Ограничения |
|---|---:|---|---|
| GitHub Releases | Бесплатно | Релизный ключ, тег, APK, SHA-256 | Нет автообновления; downloads ≠ installs |
| GitHub Pages | Бесплатно | Статический сайт, Actions | Нет backend, секретов и server-side аналитики |
| F-Droid main | Бесплатно | FOSS-код и зависимости, fastlane metadata, build metadata | Ревью и сборка с нуля; другая подпись без reproducible build |
| IzzyOnDroid | Бесплатно | Открытый код, release APK, metadata, подпись разработчика | Обычно до 30 МБ на приложение; аналитика может не пройти ревью |
| Свой F-Droid repo | Сервер | `fdroidserver`, GPG, HTTPS hosting | Нужно поддерживать index, зеркала и обновления |
| Accrescent | Бесплатно | GitHub allowlist, `.apks`, иконка 512×512, domain verification | Регистрация сейчас ограничена; ручной review разрешений |

## GitHub Releases

Бесплатно для публичных репозиториев. Один asset может быть до 2 GiB; для APK OneBar лимит не проблема. GitHub автоматически добавляет source code zip/tar.

Что публиковать:

- `OneBar-0.15.0.apk` — подписанный release APK;
- `SHA256SUMS` — хеш APK;
- `SIGNING-CERTIFICATE.txt` — SHA-256 сертификата;
- release notes с изменениями, требованиями и известными ограничениями.

Workflow `.github/workflows/release.yml` собирает и публикует эти файлы при push тега `v*`. Не запускайте его без release keystore.

## GitHub Pages

Для этого проекта подходит: сайт статический, некоммерческий, без транзакций и backend. Ожидаемый адрес:

`https://oneba.ru/`

Официальные лимиты на 28.11.2026:

- опубликованный сайт — не более 1 GB;
- source repository — рекомендуемый размер 1 GB;
- мягкий bandwidth limit — 100 GB/месяц;
- deployment timeout — 10 минут;
- 10 сборок в час для стандартного Pages; лимит не применяется к custom GitHub Actions workflow;
- запрещены коммерческие транзакции, SaaS и чувствительные данные.

Настройка: **Settings → Pages → Build and deployment → GitHub Actions**. Workflow `.github/workflows/pages.yml` собирает Tailwind и загружает артефакт. Секреты на сайте не используются.

## F-Droid main

### Что готово в репозитории

- `LICENSE` MIT;
- `fastlane/metadata/android/en-US/` и `ru/`;
- title, short description до 80 символов, full description до 4000;
- `images/icon.png` 512×512;
- `images/featureGraphic.png` 1024×500;
- `images/phoneScreenshots/1..3.png`;
- `changelogs/32.txt`.

### Что проверить перед подачей

- В `releaseRuntimeClasspath` нет `com.google.android.gms`, Firebase, Crashlytics и проприетарных библиотек.
- AppMetrica MIT, opt-in по умолчанию выключен, crash/ANR и идентификаторы отключены.
- Сборка из чистого клона не требует `local.properties` с API key.
- Версия и versionCode в Gradle соответствуют тегу.
- Есть реальный changelog для versionCode.
- Репозиторий активен и приложение полезно конечным пользователям.

F-Droid требует разрабатывать приложение не менее трёх месяцев, если нет пользователей или исключения. Репозиторий OneBar создан 23.11.2026, поэтому до 23.02.2027 включение в основной F-Droid без исключения маловероятно. Возможные основания для исключения: доказанные пользователи, другие поддерживаемые приложения автора или разработка для события. До этого момента лучше распространять APK через GitHub Releases и подготовить metadata/IzzyOnDroid.

### Подача

1. Форкнуть `https://gitlab.com/fdroid/fdroiddata`.
2. Создать `metadata/dev.onebar.app.yml` по образцу Gradle-приложений.
3. Указать `RepoType: git`, `Repo: https://github.com/croupier/OneBar`, `Builds` с versionName `0.15.0`, versionCode `32`, commit и `subdir: app`, `gradle: [full]`.
4. Включить `AutoUpdateMode: Version` и `UpdateCheckMode: Tags`.
5. Проверить `fdroid lint`, `fdroid rewritemeta`, `fdroid build`.
6. Открыть merge request в `fdroiddata`.

После merge до появления в основном репозитории обычно проходит около 24–48 часов, если сборка успешна. F-Droid подписывает APK своим ключом, поэтому поверх GitHub APK обновление может потребовать переустановки. Reproducible builds не обязательны, но желательны.

### AntiFeatures

Если AppMetrica останется в общей сборке, ожидаемый anti-feature — `Tracking` и, возможно, `NonFreeNet` из-за внешнего сервиса. Пользователь должен явно включать аналитику. Финальные флаги определяет команда F-Droid. Если каталог сочтёт сервис несовместимым, потребуется отдельная no-analytics сборка.

## IzzyOnDroid

Требования:

- OSI/FSF-лицензия и публичный исходный код;
- отсутствие проприетарных компонентов; trackers в целом не приветствуются;
- Fastlane metadata: short/full description, icon и скриншоты;
- APK подписан ключом разработчика, не debug и не testOnly;
- APK доступен как asset тегированного GitHub Release;
- ориентировочный лимит — до 30 МБ на приложение.

Подача: создать issue в `https://gitlab.com/IzzyOnDroid/repo/-/issues` с названием приложения, package name `dev.onebar.app`, ссылкой на репозиторий и последний release. Указать, что аналитика AppMetrica MIT, opt-in и выключена по умолчанию. Решение о включении принимает команда IzzyOnDroid.

**Важно про AI-политику:** на 28.11.2026 IzzyOnDroid прямо отклоняет vibe-coded приложения и просит не скрывать AI-участие. OneBar разрабатывалась с AI-ассистентом, поэтому включение в IzzyOnDroid маловероятно. Не подавать заявку без честного раскрытия обстоятельств; основной реалистичный путь — GitHub Releases, затем F-Droid после срока разработки или собственный F-Droid repo.

## Собственный F-Droid binary repo

Если F-Droid/Izzy не принимают опциональную аналитику:

1. Поднять HTTPS-хостинг для `repo/`.
2. Установить `fdroidserver`.
3. Создать ключ подписи репозитория и сохранить его резервную копию.
4. `fdroid init`, затем `fdroid update`, `fdroid publish`.
5. Добавить fingerprint репозитория на сайт.
6. Обновлять APK вручную или через workflow.

Это даёт пользователям установку из F-Droid client, но требует поддерживать index и доступность.

## Accrescent

На 28.11.2026 новая регистрация разработчиков закрыта и allowlist-заявки не принимаются. Поэтому канал указан как резервный, а не как действие первого релиза. После открытия понадобятся:

- GitHub-аккаунт из allowlist;
- `.apks` до 1 GiB, не debug, с v2/v3 подписью;
- иконка 512×512 PNG;
- соответствие target SDK;
- domain verification для домена из applicationId;
- ручной review `QUERY_ALL_PACKAGES`.

## Google Play

Не планируется. Для некоммерческого учебного проекта это лишние расходы и ограничения:

- регистрационный взнос разработчика;
- обязательная target API политика и testing requirements;
- `QUERY_ALL_PACKAGES` требует отдельного обоснования;
- Data safety и privacy-раскрытие;
- проверка аккаунта и модерация.

Отказ от Play не мешает распространять MIT APK через GitHub и F-Droid.

## Чек-лист публикации

- [ ] Release APK собран и проверен `apksigner`.
- [ ] SHA-256 совпадает с `SHA256SUMS`.
- [ ] Keystore и пароли сохранены в двух защищённых местах.
- [ ] Тег `v0.15.0` указывает на release commit.
- [ ] GitHub Release содержит APK, checksums и сертификат.
- [ ] Сайт доступен по GitHub Pages и открывается на мобильном.
- [ ] AppMetrica и Umami ID либо заданы, либо аналитика честно выключена.
- [ ] Fastlane metadata соответствует APK.
- [ ] Privacy policy и install instructions доступны по HTTPS.
- [ ] Issue templates принимают баги без персональных данных.
- [ ] F-Droid/Izzy ссылки добавляются только после фактического включения.
