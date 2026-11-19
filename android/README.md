# Harfix — Android ilova

`uz.harfix.app`, versiya **1.5.0** (versionCode 9). Kotlin, minSdk 26 (Android 8.0), targetSdk 36.

## Bu kod qayerdan keldi

Asl Android loyiha faqat kompyuterda turgan va Windows qayta o‘rnatilganda yo‘qolgan.
Bu loyiha 17.11.2026 da saytdagi `Harfix.apk` 1.4.0 dan
(GitHub release `harfix-web` v1.4.0, 12.11.2026) qayta tiklandi:

| Qism | Holati |
|---|---|
| `assets/ui/` (ilova interfeysi) | APK’dan **aynan o‘zi**; keyin faqat «Maxfiylik» kartasiga reklama haqidagi gap va «Maxfiylik siyosati» havolasi qo‘shildi |
| Ikonkalar, mavzular, `method.xml`, `accessibility.xml` | APK’dan **aynan o‘zi** |
| `AndroidManifest.xml` | Yig‘ilgan manifest asl APK’niki bilan bir xil |
| Kotlin kodi | APK ichidagi kod o‘qib chiqilib, **qaytadan toza yozildi**: xatti-harakati bir xil, lekin satrma-satr asl manba emas |
| Konvertor (`Converter.kt`) | `assets/ui/harfix.js` ning porti; 3 128 ta misolda × 3 yo‘nalishda (`new`, `old`, `cyr`) `harfix.js` bilan bir xil natija beradi |
| Hujjatlar (`Office.kt`) | Telegram botdagi `bot/harfix_bot/documents.py` bilan bir xil qoidalar |

### Ikki flavor: `direct` va `play`

| Flavor | Qayerga | «Odatiy klaviaturada» (Gboard’da avtomatik o‘girish) |
|---|---|---|
| `direct` | sayt va Telegram’dagi APK | Yo‘q: Google Play Protect fayldan o‘rnatilgan accessibility ilovalarni bloklaydi |
| `play` | Google Play (AAB) | Bor: `src/play/` dagi `WordsService` va `WordsTile` |

Saytdagi 1.4.0 APK «direct» bo‘lgani uchun xizmatning asl kodi tiklanmagan edi, u 1.5.0 da qaytadan
yozildi. Qaror mantig‘i sof Kotlin’da (`WordsLogic.kt`) va testlangan. Play’ga yuklash bo‘yicha
qo‘llanma: [PLAY.md](PLAY.md).

## Yig‘ish

Android Studio’da `android/` papkasini oching, yoki:

```bash
./gradlew testDirectDebugUnitTest testPlayDebugUnitTest   # testlar
./gradlew assemblePlayDebug        # test versiyasi: "Harfix (test)", uz.harfix.app.debug
./gradlew assembleDirectRelease    # saytdagi APK
./gradlew bundlePlayRelease        # Google Play uchun AAB
```

Test versiyasi (`debug`) o‘z nomi va id’si bilan o‘rnatiladi, telefondagi haqiqiy Harfix’ga tegmaydi.

### Imzolash (release)

`android/keystore.properties` faylini yarating (u `.gitignore` da, **hech qachon commit qilmang**):

```properties
storeFile=/yo‘l/harfix.jks
storePassword=...
keyAlias=...
keyPassword=...
```

Bu fayl bo‘lmasa, `assembleRelease` / `bundleRelease` darhol xato bilan to‘xtaydi (aks holda
Google Play qabul qilmaydigan imzosiz fayl chiqardi). Android Studio’dagi «Generate Signed Bundle / APK»
o‘z kalitini beradi, u faylsiz ham ishlaydi.

1.4.0 ning kaliti eski kompyuter bilan yo‘qolgan; 1.5.0 dan boshlab yangi kalit ishlatiladi
(`harfix-release.jks`, alias `harfix`, PKCS12). Yangi kalit bilan imzolangan APK eski APK ustiga
o‘rnatilmaydi: saytdan 1.4.0 ni yuklab olganlar bir marta o‘chirib, qayta o‘rnatishi kerak.
Kalitni **kamida ikki joyda saqlang** (masalan, flesh va bulut): yo‘qolsa, saytdagi ilovani
yangilab bo‘lmaydi. Kalit yaratish: `keytool -genkeypair -v -keystore harfix-release.jks -storetype PKCS12 -alias harfix -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=Harfix, O=Harfix, C=UZ"`.

**GitHub’da yig‘ish** (`.github/workflows/android-release.yml`, Actions → android-release → Run
workflow): repo’ning ikkita secret’i kerak. Ularni kalit egasi o‘zi qo‘yadi:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("harfix-release.jks")) | gh secret set ANDROID_KEYSTORE_BASE64 --repo Abdulla0408/Harfix
gh secret set ANDROID_KEYSTORE_PASSWORD --repo Abdulla0408/Harfix
```

Natija (`Harfix-android-release` artifact): `Harfix.apk` (sayt), `Harfix-play.aab` (Google Play),
`SHA256SUMS.txt` va R8 mapping fayllari.
Google Play’da birinchi chiqarishda «Play App Signing» yoqing: shu kalit faqat *upload key* bo‘ladi,
yo‘qolsa Play orqali almashtirsa bo‘ladi.

## Tuzilishi

| Fayl | Nima |
|---|---|
| `MainActivity.kt` | Butun ilova — `assets/ui` dagi veb-sahifa; telefon ishlari shu yerda |
| `Bridge.kt` | `window.HarfixAndroid`: sahifa chaqiradigan 20 ta funksiya |
| `Converter.kt` | Matn konvertori: yangi lotin, eski lotin, kirill |
| `Office.kt`, `Files.kt`, `ResultsProvider.kt` | Word / Excel / PowerPoint / txt o‘girish va `Downloads/Harfix` ga saqlash |
| `Online.kt`, `News.kt` | Onlayn sozlamalar (Supabase → GitHub Pages), kunlik ping, bildirishnomalar |
| `BannerAds.kt` | Yandex banneri (sahifa onlayn sozlamaga qarab yoqadi) |
| `ProcessTextActivity.kt` | Istalgan ilovada matnni belgilab, menyudan «Harfix» |
| `keyboard/` | Harfix klaviaturasi: ö ğ ş ç qatori, Lotin / Ўзбекча / Русский, o‘ → ö jonli o‘girish va ⌫ bilan qaytarish |
| `Words.kt`, `WordsLogic.kt`, `src/play/…/WordsService.kt`, `WordsTile.kt` | «Odatiy klaviaturada» (faqat `play` flavor’ida, yuqoriga qarang) |

`app/src/test/resources/golden.json` — `harfix.js` natijalari. `harfix.js` o‘zgarsa, uni qayta yarating
va `ConverterTest` ni ishga tushiring.
