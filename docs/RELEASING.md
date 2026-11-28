# RELEASING

Инструкция для выпуска OneBar через GitHub Releases и GitHub Pages.

## 1. Подготовка

- JDK 17 и Android SDK API 36;
- Gradle wrapper;
- release keystore и пароли;
- актуальный `CHANGELOG.md` и Fastlane changelog;
- чистый git status.

`versionName` и `versionCode` находятся в `app/build.gradle.kts`. Версия — semver `MAJOR.MINOR.PATCH`, versionCode увеличивается на единицу для каждого раздаваемого билда.

## 2. Локальная подпись

Создайте игнорируемый `keystore.properties`:

```properties
storeFile=onebar-release.jks
storePassword=...
keyAlias=onebar
keyPassword=...
```

Не коммитьте файл. Сделайте две защищённые резервные копии keystore. Потеря ключа не позволит обновлять уже установленные APK без переустановки.

Необязательная аналитика:

```properties
APP_METRICA_API_KEY=xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
```

Эта строка добавляется в `local.properties`, а не в `keystore.properties`.

## 3. Проверки

Windows:

```bat
set JAVA_HOME=C:\Tools\jdk-17
set HTTP_PROXY=
set HTTPS_PROXY=
set ALL_PROXY=
gradlew.bat testDebugUnitTest assembleDebug lintRelease
```

Unix:

```bash
export JAVA_HOME=/path/to/jdk-17
unset HTTP_PROXY HTTPS_PROXY ALL_PROXY
./gradlew testDebugUnitTest assembleDebug lintRelease
```

Сайт:

```bash
cd site
npm ci
npm run build
npm run check
```

Проверить зависимости:

```bash
./gradlew :app:dependencies --configuration releaseRuntimeClasspath
```

Не должно быть `com.google.android.gms`, Firebase, Crashlytics и рекламных SDK.

## 4. Release APK

```bash
./gradlew assembleRelease
```

Выход: `app/build/outputs/apk/release/app-release.apk`.

Проверка подписи:

```bash
apksigner verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk
```

Проверка версии:

```bash
aapt dump badging app/build/outputs/apk/release/app-release.apk | findstr /C:"package:"
```

Создайте артефакты:

```bash
cp app/build/outputs/apk/release/app-release.apk OneBar-0.15.0.apk
sha256sum OneBar-0.15.0.apk > SHA256SUMS
apksigner verify --print-certs OneBar-0.15.0.apk > SIGNING-CERTIFICATE.txt
```

Имена меняются вместе с версией. Публикуйте только подписанный APK, не `app-release-unsigned.apk`.

## 5. Тег и GitHub Actions

Рекомендуемый порядок:

1. Проверить diff и созданный commit.
2. `git tag -a v0.15.0 -m "OneBar 0.15.0"`.
3. `git push origin v0.15.0`.

Workflow `release.yml` запустится на теге и:

- восстановит keystore из secret;
- запустит unit tests;
- соберёт release APK;
- переименует его в `OneBar-<tag>.apk`;
- создаст SHA-256 и сертификат;
- опубликует GitHub Release.

### Secrets

| Secret | Значение |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | base64 содержимого JKS |
| `ANDROID_STORE_PASSWORD` | store password |
| `ANDROID_KEY_ALIAS` | alias |
| `ANDROID_KEY_PASSWORD` | key password |
| `APP_METRICA_API_KEY` | необязательный API key |

Не добавляйте keystore или пароли в repository variables. API key AppMetrica является идентификатором клиентского приложения, но всё равно передаётся через secret, чтобы не фиксировать его в публичной истории.

## 6. GitHub Pages

Один раз в **Settings → Pages** выберите **GitHub Actions**. Workflow `pages.yml` запускается при push в `master` и `release/*`, собирает `site/dist` и публикует артефакт.

Перед первым публичным показом:

- откройте `https://oneba.ru/`;
- проверьте `/OneBar/install.html` и `/OneBar/privacy.html`;
- проверьте мобильное меню, демо поиска и FAQ;
- проверьте, что при пустом Umami ID banner не появляется;
- проверьте, что все скриншоты загружаются.

## 7. После релиза

- Убедитесь, что APK, SHA256SUMS и сертификат появились в Release.
- Скачайте APK из Release и сравните SHA-256 локально.
- Установите обновление на тестовый телефон.
- Проверьте, что аналитика по умолчанию выключена и включается только вручную.
- Проверьте AppMetrica/Umami только после включения согласия.
- Обновите статус каталогов, не добавляя ссылки до фактического включения.
- Сохраните release notes и известные ограничения.

## 8. F-Droid и IzzyOnDroid

Перед подачей выполните инструкции из [DISTRIBUTION.md](DISTRIBUTION.md). Fastlane metadata должен находиться в release commit/tag, который собирает каталог. Не меняйте package name или release key после первой публичной установки без отдельного плана миграции.
