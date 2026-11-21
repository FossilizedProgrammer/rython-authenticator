# Rython-Authenticator

[English](README.md) · [فارسی](README.fa.md) · **Русский** · [Español](README.es.md) · [中文](README.zh.md)

Офлайн-аутентификатор с двумя паролями (TOTP / HOTP / Steam): два приложения в одном репозитории.

| Папка | Приложение | Стек |
| --- | --- | --- |
| [`android/`](android) | Android 8.0+ (API 26+) | Kotlin, Jetpack Compose |
| [`desktop/`](desktop) | Linux и Windows | Kotlin, Compose Multiplatform (JVM) |

Оба приложения используют одинаковые форматы хранилища и резервных копий, поэтому резервная
копия, сделанная на телефоне, восстанавливается на компьютере и наоборот. При каждом запуске
запрашиваются два независимых пароля приложения; коды вычисляются локально, и никакие данные
аккаунтов не покидают устройство.

Подробности о настольном приложении — включая заметки о безопасности и форматах файлов —
см. в [`desktop/README.md`](desktop/README.md).

## Локальная сборка

Требования: JDK 17 или новее, а для Android — Android SDK (platform 34, build-tools 34.0.0).
Gradle wrapper включён в оба проекта.

```bash
cd android && ./gradlew assembleDebug        # or: ./build.sh assembleDebug
cd desktop && ./gradlew run                  # or: ./build.sh run
```

Файл `build.sh` в каждой папке — простая обёртка для машин, где глобальные настройки Gradle
указывают на неработающий SOCKS-прокси; на чистой машине он не нужен.

## Релизы собирает GitHub Actions

`.github/workflows/release.yml` запускается на каждый тег `v*` и публикует GitHub Release с файлами:

* `Rython-Authenticator-<version>-{armeabi-v7a,arm64-v8a,universal}.apk`
* `Rython-Authenticator-<version>-linux-amd64.{deb,rpm}` и переносимый `.zip`
* `Rython-Authenticator-<version>-windows-x64.{msi,exe}` и переносимый `.zip`

```bash
git tag v1.0.0
git push origin v1.0.0
```

Результаты сборки (`build/`, `dist/`) и ключ подписи никогда не попадают в репозиторий;
APK релиза подписываются внутри workflow из секретов репозитория.

### Секреты подписи

| Секрет | Значение |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | `base64 -w0 app/rython-release.jks` |
| `ANDROID_KEYSTORE_PASSWORD` | пароль хранилища ключей |
| `ANDROID_KEY_ALIAS` | псевдоним ключа (по умолчанию `rython`) |
| `ANDROID_KEY_PASSWORD` | пароль ключа (по умолчанию совпадает с паролем хранилища) |

```bash
gh secret set ANDROID_KEYSTORE_BASE64 < <(base64 -w0 android/app/rython-release.jks)
```

Без `ANDROID_KEYSTORE_BASE64` сборка всё равно пройдёт, но APK будут подписаны отладочным
ключом и не смогут обновить установленное приложение, подписанное ключом релиза.

Храните резервную копию хранилища ключей и его паролей вне этого репозитория: если они
потеряются, вы больше не сможете выпустить обновление, устанавливаемое поверх существующего приложения.

## Лицензия

[MIT](LICENSE) — используйте, изменяйте, распространяйте и продавайте, с изменениями или без,
при условии сохранения уведомления об авторских правах и текста разрешения в исходниках.
