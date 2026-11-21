# Rython-Authenticator (desktop)

Same app as the Android build, for Linux and Windows: two app passwords asked in order
at every start, TOTP / HOTP / Steam codes, folders and search, Google Authenticator
import, encrypted backups and independently changeable passwords.

Kotlin + Compose Multiplatform (JVM): one code base, the same app runs on Linux and
Windows, and the backup files it writes are byte-compatible with the Android app.

## Requirements

* JDK 17 or newer (the build here uses JDK 21)
* No Gradle needed: the wrapper (`gradlew` / `gradlew.bat`) fetches Gradle 8.7

## Run

```bash
cd desktop
./build.sh run          # Linux (clears the dead SOCKS proxy of this machine)
gradlew.bat run         # Windows
```

`build.sh` is a small wrapper around Gradle: it prefers a locally installed Gradle,
clears the broken `socksProxyHost` from `~/.gradle/gradle.properties`, and falls back
to `./gradlew`. On Windows use the wrapper directly.

## Tests

```bash
./build.sh test         # or: gradlew.bat test
```

Covers the RFC 6238 / RFC 4226 test vectors, `otpauth://` parsing and a backup
encrypt/decrypt round-trip (a wrong password must fail).

## Package a native app

```bash
./build.sh createDistributable          # app image in build/compose/binaries
./build.sh packageDistributionForCurrentOS
```

* On Linux this produces `.deb` and `.rpm` packages.
* On Windows it produces `.msi` and `.exe` installers (`gradlew.bat
  packageDistributionForCurrentOS`).
* A ready-to-run folder without installing: `build/compose/binaries/main/app/`.

## Where the data lives

| Platform | Folder |
| --- | --- |
| Linux | `$XDG_CONFIG_HOME/rython-authenticator` (usually `~/.config/rython-authenticator`) |
| Windows | `%APPDATA%\Rython-Authenticator` |

Files: `prefs.properties` (settings and the two password verifiers, PBKDF2-HMAC-SHA512),
`vault.bin` (AES-256-GCM encrypted accounts), `vault.key` (the 256-bit data key, mode
0600 on Linux). Set `RYTHON_CONFIG_DIR` to use a different folder (portable setups,
tests).

## Security and formats (technical)

This is the reference for the internals; the app's own screens deliberately keep the
wording plain and mention none of it.

* **App passwords** — two independent passwords, asked in order at every start. Each is
  stored only as a PBKDF2-HMAC-SHA512 verifier in `prefs.properties`, never in clear text.
  They cannot be recovered; a forgotten password means the vault key cannot be unwrapped.
* **Vault at rest** — accounts are serialised and encrypted with AES-256-GCM. The random
  256-bit data key sits in `vault.key` (mode 0600 on Linux) and is not derived from the
  passwords, so changing a password leaves the stored accounts untouched, as on Android.
* **Backups** — the same stream, keyed by both passwords through scrypt (N = 2^15,
  r = 8, p = 1, 32 MiB) and then AES-256-GCM, with the salt and nonce in the file header.
  Backup files from the Android app and this one are byte-compatible in both directions;
  a backup made with old passwords stays readable with those old passwords.
* **Codes** — RFC 6238 TOTP and RFC 4226 HOTP, HMAC-SHA1/256/512, 6–8 digits, adjustable
  period and counter, plus Steam's 5-character alphabet. `otpauth://` URIs and Google
  Authenticator `otpauth-migration://` exports (the protobuf behind *Export accounts*) are
  parsed locally.
* **Network** — used only by the optional *internet time* switch, to read the current time.
  No account data, code or password ever leaves the device.
* **Text direction** — Persian strings contain Latin runs (service names, URLs, numbers).
  In the desktop app `Lang.t()` wraps every such run in U+2066…U+2069 (LRI/PDI); in the
  Android app the same isolation is baked into `res/values-fa/strings.xml`, placeholders
  included (`\u2066%1$s\u2069`). Without it the renderer reorders mixed sentences.

## Differences from the Android app

* **No camera**: instead of scanning QR codes, paste the link. `otpauth://` links and
  Google Authenticator `otpauth-migration://` export links (the "Copy link" under the
  QR code) both work; see *Settings → Import from Google Authenticator*.
* **No biometric lock**: the app already asks for both passwords at every start.
* **Two languages** (English, فارسی) instead of five; the Android build also has
  Spanish, Russian and Chinese.
* Backups, the vault format and the TOTP/HOTP/Steam code computation are identical, so
  an export from the phone restores here and vice versa.

## Layout

```
src/main/kotlin/com/rython/desktop/
  Main.kt              window + entry point
  core/                OTP engine, Base32, scrypt, otpauth/migration parser, time
  data/                Account model, encrypted vault, backup format
  security/            the two app passwords (PBKDF2 verifiers)
  app/Prefs.kt         platform config folder and settings store
  ui/                  Compose screens: gate, accounts, editor, settings, transfer
```

## Note on repositories

`settings.gradle.kts` declares Maven Central, Google Maven and a Google Maven mirror.
The mirror is only a fallback: on some networks Google Maven answers Gradle's HEAD
requests with 404 even though the files are fetchable, which otherwise breaks
resolution of the Compose Desktop transitive `androidx.*` artifacts.
