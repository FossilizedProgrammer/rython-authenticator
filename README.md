# Rython-Authenticator

**English** · [فارسی](README.fa.md) · [Русский](README.ru.md) · [Español](README.es.md) · [中文](README.zh.md)

An offline two-password authenticator (TOTP / HOTP / Steam), in one repository with two apps:

| Folder | App | Stack |
| --- | --- | --- |
| [`android/`](android) | Android 8.0+ (API 26+) | Kotlin, Jetpack Compose |
| [`desktop/`](desktop) | Linux and Windows | Kotlin, Compose Multiplatform (JVM) |

Both apps use the same vault/backup formats, so a backup exported on the phone restores
on the desktop and vice versa. Every start asks for two independent app passwords; codes
are computed locally and no account data leaves the device.

See [`desktop/README.md`](desktop/README.md) for the desktop app in detail, including the
security and format notes.

## Build locally

Requirements: JDK 17 or newer, and for Android the Android SDK (platform 34, build-tools 34.0.0).
The Gradle wrapper is included in both projects.

```bash
cd android && ./gradlew assembleDebug        # or: ./build.sh assembleDebug
cd desktop && ./gradlew run                  # or: ./build.sh run
```

`build.sh` in each folder is a convenience wrapper for machines whose global Gradle
configuration points at a dead SOCKS proxy; it is not needed on a clean machine.

## Releases are built by GitHub Actions

`.github/workflows/release.yml` runs on every `v*` tag and publishes a GitHub Release with:

* `Rython-Authenticator-<version>-{armeabi-v7a,arm64-v8a,universal}.apk`
* `Rython-Authenticator-<version>-linux-amd64.{deb,rpm}` and a portable `.zip`
* `Rython-Authenticator-<version>-windows-x64.{msi,exe}` and a portable `.zip`

```bash
git tag v1.0.0
git push origin v1.0.0
```

Build output (`build/`, `dist/`) and the signing key are never committed; the release APKs
are signed inside the workflow from repository secrets.

### Signing secrets

| Secret | Meaning |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | `base64 -w0 app/rython-release.jks` |
| `ANDROID_KEYSTORE_PASSWORD` | keystore password |
| `ANDROID_KEY_ALIAS` | key alias (default `rython`) |
| `ANDROID_KEY_PASSWORD` | key password (defaults to the keystore password) |

```bash
gh secret set ANDROID_KEYSTORE_BASE64 < <(base64 -w0 android/app/rython-release.jks)
```

Without `ANDROID_KEYSTORE_BASE64` the workflow still builds, but the APKs are debug-signed
and cannot update an installation signed with the release key.

Keep the keystore and its passwords backed up outside this repository: losing them means
you can never ship an update that installs over the existing app.

## License

[MIT](LICENSE) — use, modify, redistribute and sell it, with or without changes, as long as
the copyright notice and permission notice stay in the source.
