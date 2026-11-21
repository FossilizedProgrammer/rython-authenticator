# Rython-Authenticator

[English](README.md) · [فارسی](README.fa.md) · [Русский](README.ru.md) · [Español](README.es.md) · **中文**

一个离线双密码验证器（TOTP / HOTP / Steam），同一个仓库里包含两个应用：

| 目录 | 应用 | 技术栈 |
| --- | --- | --- |
| [`android/`](android) | Android 8.0+（API 26+） | Kotlin、Jetpack Compose |
| [`desktop/`](desktop) | Linux 和 Windows | Kotlin、Compose Multiplatform（JVM） |

两个应用使用相同的保险库与备份格式，因此在手机上导出的备份可以在桌面端恢复，反之亦然。
每次启动都会要求输入两个相互独立的应用密码；验证码在本地计算，任何账户数据都不会离开设备。

桌面应用的详细说明（包括安全与文件格式说明）请见 [`desktop/README.md`](desktop/README.md)。

## 本地构建

需要 JDK 17 或更高版本；构建 Android 还需要 Android SDK（platform 34、build-tools 34.0.0）。
两个项目都已包含 Gradle wrapper。

```bash
cd android && ./gradlew assembleDebug        # or: ./build.sh assembleDebug
cd desktop && ./gradlew run                  # or: ./build.sh run
```

各目录中的 `build.sh` 只是为全局 Gradle 配置指向失效 SOCKS 代理的机器准备的便捷脚本，
在干净的机器上并不需要。

## 由 GitHub Actions 构建发布版本

`.github/workflows/release.yml` 在每次推送 `v*` 标签时运行，并发布一个包含以下文件的 GitHub Release：

* `Rython-Authenticator-<version>-{armeabi-v7a,arm64-v8a,universal}.apk`
* `Rython-Authenticator-<version>-linux-amd64.{deb,rpm}` 以及便携 `.zip`
* `Rython-Authenticator-<version>-windows-x64.{msi,exe}` 以及便携 `.zip`

```bash
git tag v1.0.0
git push origin v1.0.0
```

构建产物（`build/`、`dist/`）和签名密钥永远不会被提交；发布版 APK 在 workflow 内使用仓库
secret 完成签名。

### 签名 secret

| Secret | 含义 |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | `base64 -w0 app/rython-release.jks` |
| `ANDROID_KEYSTORE_PASSWORD` | keystore 密码 |
| `ANDROID_KEY_ALIAS` | 密钥别名（默认 `rython`） |
| `ANDROID_KEY_PASSWORD` | 密钥密码（留空则与 keystore 密码相同） |

```bash
gh secret set ANDROID_KEYSTORE_BASE64 < <(base64 -w0 android/app/rython-release.jks)
```

如果没有设置 `ANDROID_KEYSTORE_BASE64`，workflow 仍会完成构建，但 APK 使用 debug 密钥签名，
无法覆盖安装在设备上的发布版应用。

请在本仓库之外备份 keystore 及其密码：一旦丢失，就永远无法发布可以覆盖安装现有应用的更新。

## 许可证

[MIT](LICENSE) —— 可以自由使用、修改、再分发和出售，无论是否修改，只需在源码中保留版权声明
和许可声明。
