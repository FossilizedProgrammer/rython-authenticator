# Rython-Authenticator

[English](README.md) · [فارسی](README.fa.md) · [Русский](README.ru.md) · **Español** · [中文](README.zh.md)

Un autenticador sin conexión con dos contraseñas (TOTP / HOTP / Steam), con dos aplicaciones
en un mismo repositorio:

| Carpeta | Aplicación | Tecnologías |
| --- | --- | --- |
| [`android/`](android) | Android 8.0+ (API 26+) | Kotlin, Jetpack Compose |
| [`desktop/`](desktop) | Linux y Windows | Kotlin, Compose Multiplatform (JVM) |

Ambas aplicaciones usan los mismos formatos de bóveda y de copia de seguridad, así que una copia
exportada en el teléfono se restaura en el escritorio y viceversa. Cada inicio pide dos
contraseñas independientes de la aplicación; los códigos se calculan localmente y ningún dato
de las cuentas sale del dispositivo.

Consulta [`desktop/README.md`](desktop/README.md) para los detalles de la aplicación de
escritorio, incluidas las notas de seguridad y de formato.

## Compilar en local

Requisitos: JDK 17 o superior y, para Android, el SDK de Android (platform 34, build-tools 34.0.0).
El wrapper de Gradle está incluido en ambos proyectos.

```bash
cd android && ./gradlew assembleDebug        # or: ./build.sh assembleDebug
cd desktop && ./gradlew run                  # or: ./build.sh run
```

El archivo `build.sh` de cada carpeta es un envoltorio sencillo para equipos cuya configuración
global de Gradle apunta a un proxy SOCKS caído; no es necesario en un equipo limpio.

## Las versiones las compila GitHub Actions

`.github/workflows/release.yml` se ejecuta con cada etiqueta `v*` y publica una versión de GitHub con:

* `Rython-Authenticator-<version>-{armeabi-v7a,arm64-v8a,universal}.apk`
* `Rython-Authenticator-<version>-linux-amd64.{deb,rpm}` y un `.zip` portable
* `Rython-Authenticator-<version>-windows-x64.{msi,exe}` y un `.zip` portable

```bash
git tag v1.0.0
git push origin v1.0.0
```

Los resultados de la compilación (`build/`, `dist/`) y la clave de firma nunca se suben al
repositorio; los APK de la versión se firman dentro del workflow con los secretos del repositorio.

### Secretos de firma

| Secreto | Significado |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | `base64 -w0 app/rython-release.jks` |
| `ANDROID_KEYSTORE_PASSWORD` | contraseña del keystore |
| `ANDROID_KEY_ALIAS` | alias de la clave (por defecto `rython`) |
| `ANDROID_KEY_PASSWORD` | contraseña de la clave (por defecto, la del keystore) |

```bash
gh secret set ANDROID_KEYSTORE_BASE64 < <(base64 -w0 android/app/rython-release.jks)
```

Sin `ANDROID_KEYSTORE_BASE64` el workflow compila igualmente, pero los APK quedan firmados con
la clave de depuración y no pueden actualizar una instalación firmada con la clave de versión.

Guarda una copia del keystore y de sus contraseñas fuera de este repositorio: si se pierden,
nunca podrás publicar una actualización que se instale sobre la aplicación existente.

## Licencia

[MIT](LICENSE): úsalo, modifícalo, redistribúyelo y véndelo, con o sin cambios, siempre que se
conserven el aviso de copyright y el texto del permiso en el código fuente.
