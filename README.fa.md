# Rython-Authenticator

[English](README.md) · **فارسی** · [Русский](README.ru.md) · [Español](README.es.md) · [中文](README.zh.md)

یک تأییدکنندهٔ آفلاین رمز دومرحله‌ای (TOTP / HOTP / Steam) که هر دو اپ آن در یک مخزن قرار دارند:

| پوشه | اپ | فناوری |
| --- | --- | --- |
| [`android/`](android) | اندروید ۸.۰ به بالا (API 26+) | Kotlin، Jetpack Compose |
| [`desktop/`](desktop) | لینوکس و ویندوز | Kotlin، Compose Multiplatform (JVM) |

هر دو اپ از یک قالب گاوصندوق و پشتیبان‌گیری استفاده می‌کنند؛ پس نسخهٔ پشتیبانِ گرفته‌شده روی
گوشی روی دسکتاپ بازیابی می‌شود و برعکس. هنگام هر بار اجرا دو رمز مستقل خواسته می‌شود، کدها
به‌صورت محلی محاسبه می‌شوند و هیچ داده‌ای از دستگاه خارج نمی‌شود.

برای جزئیات اپ دسکتاپ — از جمله نکات امنیتی و قالب فایل‌ها — فایل
[`desktop/README.md`](desktop/README.md) را ببینید.

## ساخت محلی

پیش‌نیازها: JDK 17 یا بالاتر، و برای اندروید، Android SDK (پلتفرم 34، build-tools 34.0.0).
Gradle wrapper در هر دو پروژه موجود است.

```bash
cd android && ./gradlew assembleDebug        # or: ./build.sh assembleDebug
cd desktop && ./gradlew run                  # or: ./build.sh run
```

فایل `build.sh` در هر پوشه یک لفاف ساده برای ماشین‌هایی است که تنظیمات سراسری Gradle آن‌ها
به یک پروکسی SOCKS از کار افتاده اشاره می‌کند؛ روی ماشین سالم لازم نیست.

## ریلیز توسط GitHub Actions ساخته می‌شود

فایل `.github/workflows/release.yml` با هر تگ `v*` اجرا می‌شود و یک GitHub Release با این فایل‌ها می‌سازد:

* `Rython-Authenticator-<version>-{armeabi-v7a,arm64-v8a,universal}.apk`
* `Rython-Authenticator-<version>-linux-amd64.{deb,rpm}` و یک `.zip` قابل حمل
* `Rython-Authenticator-<version>-windows-x64.{msi,exe}` و یک `.zip` قابل حمل

```bash
git tag v1.0.0
git push origin v1.0.0
```

خروجی بیلد (پوشه‌های `build/` و `dist/`) و کلید امضا هرگز کامیت نمی‌شوند؛ APKهای ریلیز
داخل همان workflow و از روی secretهای مخزن امضا می‌شوند.

### Secretهای امضا

| Secret | معنی |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | `base64 -w0 app/rython-release.jks` |
| `ANDROID_KEYSTORE_PASSWORD` | رمز keystore |
| `ANDROID_KEY_ALIAS` | نام مستعار کلید (پیش‌فرض `rython`) |
| `ANDROID_KEY_PASSWORD` | رمز کلید (اگر خالی بماند، همان رمز keystore استفاده می‌شود) |

```bash
gh secret set ANDROID_KEYSTORE_BASE64 < <(base64 -w0 android/app/rython-release.jks)
```

اگر `ANDROID_KEYSTORE_BASE64` تنظیم نشده باشد، workflow باز هم بیلد می‌شود ولی APKها با کلید
debug امضا می‌شوند و نمی‌توانند نسخهٔ نصب‌شده با کلید ریلیز را به‌روزرسانی کنند.

از keystore و رمزهایش خارج از این مخزن پشتیبان بگیرید: اگر گم شوند، دیگر هرگز نمی‌توانید
به‌روزرسانی‌ای منتشر کنید که روی اپ نصب‌شده بنشیند.

## مجوز

[MIT](LICENSE) — استفاده، تغییر، بازتوزیع و فروش آن، با یا بدون تغییر، آزاد است؛ تنها شرط این است
که متن حق نشر و متن اجازه‌نامه در سورس باقی بماند.
