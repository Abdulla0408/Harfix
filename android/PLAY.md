# Harfix — Google Play

Paket nomi: **`uz.harfix.app`**. Play uchun **`play`** flavor’i yig‘iladi: unda «Odatiy klaviaturada»
(accessibility xizmati) bor. Saytdagi APK — **`direct`** flavor’i, unda bu xizmat yo‘q.

## 0. Bilish kerak bo‘lgan ikki narsa

- **Yangi shaxsiy akkaunt bo‘lsa, yopiq test majburiy.** 2023-yil noyabridan keyin ochilgan shaxsiy
  (Personal) dasturchi akkauntlarida Production’ga chiqishdan oldin **kamida 12 tester bilan 14 kun
  yopiq test (Closed testing)** o‘tkazish kerak. Tashkilot (Organization) akkauntlariga bu talab qo‘yilmaydi.
- **«Odatiy klaviaturada» Play tekshiruvidan o‘tmasligi mumkin.** U Accessibility API’dan foydalanadi,
  Harfix esa nogironligi bor foydalanuvchilar uchun vosita emas (`isAccessibilityTool="false"`).
  Shuning uchun Play Console’da deklaratsiya va video kerak (4-bo‘lim). Google uni rad etishi mumkin.
  Unda shu xizmatsiz versiya yuboriladi: `play` flavor’idan xizmatni olib tashlash bir qatorlik ish,
  yoki `direct` AAB yig‘iladi. Advanced Protection yoqilgan telefonlarda bu xizmat umuman ishlamaydi.

## 1. Imzo kaliti

Kalit 2026-11-19 da yaratilgan: `harfix-release.jks` (alias `harfix`). U GitHub secret’larida turadi, imzolangan
fayllarni `android-release` workflow yig‘adi ([README.md](README.md), «Imzolash»). Play’da shu kalit *upload key*
bo‘ladi: birinchi yuklashda **Play App Signing → Google-generated key** ni tanlang. Upload key yo‘qolsa, Play
orqali almashtirsa bo‘ladi. Saytdagi APK uchun esa almashtirib bo‘lmaydi.

## 2. Yig‘ish

```bash
cd android
./gradlew testPlayReleaseUnitTest bundlePlayRelease   # → app/build/outputs/bundle/playRelease/app-play-release.aab
./gradlew assembleDirectRelease                       # saytdagi APK → app/build/outputs/apk/direct/release/
```

Har yangi yuborishda `versionCode` ni oshiring (`app/build.gradle.kts`). Hozir: 1.5.0, code 9.
Imzolangan AAB va APK: GitHub → Actions → `android-release` → *Run workflow* → artifact `Harfix-android-release`.

**GitHub orqali:** `android` workflow har push’da ikkala flavor’ni testlaydi va test APK’larini yig‘adi
(Actions → android → Artifacts). Tekshiradi:
- native kutubxonalar 16 KB sahifaga moslangani (Play buni 2025-yil noyabrdan talab qiladi);
- ilova so‘raydigan ruxsatlar ro‘yxati.

## 3. Play Console: ilova yaratish va «App content»

**Create app**
- Nomi: Harfix. Standart til: **Uzbek (uz)**. App. Free.

**App content** bo‘limlari:

| Bo‘lim | Javob |
|---|---|
| Privacy policy | `https://harfix.uz/maxfiylik/` |
| App access | Hamma funksiyalar akkauntsiz ishlaydi |
| Ads | **Yes** — Yandex Mobile Ads banneri (onlayn sozlama yoqsa) |
| Content rating | IARC so‘rovnomasi: Utility/Productivity, hamma savolga *No* |
| Target audience | **13+** (13–15, 16–17, 18+). 13 yoshdan kichiklarni belgilamang: reklama SDK’si Families dasturida emas |
| News app | No |
| Data safety | Pastdagi jadval |
| Advertising ID | **Yes**, reklama uchun. CI’dagi ruxsatlar ro‘yxatida `com.google.android.gms.permission.AD_ID` bo‘lsa, albatta *Yes* |
| Accessibility API | 4-bo‘lim |
| Government / Financial / Health | No |

**Data safety** (Yandex SDK qismini Yandex Mobile Ads hujjatlaridagi «Google Play Data safety» bo‘limi
bilan solishtiring, SDK versiyasi o‘zgarganda ham):

| Ma’lumot | Yig‘iladimi | Kim | Nima uchun |
|---|---|---|---|
| Yozilgan matn, fayllar, klaviatura kiritmasi | **Yo‘q** — hammasi telefonda | — | — |
| Shaxsiy lug‘at, o‘rganilgan so‘zlar, bufer | **Yo‘q** — faqat telefonda | — | — |
| Rasmlar (skrinshotlar buferda, «Rasm qo‘shish», klaviatura foni) | **Yo‘q** — faqat telefonda ishlanadi, yuborilmaydi | — | — |
| Device or other IDs | Ha | Harfix: tasodifiy o‘rnatish raqami (kunlik anonim hisob, Sozlamalarda o‘chiriladi). Yandex: reklama identifikatori | Analytics; Advertising |
| App interactions (reklama ko‘rish/bosish) | Ha | Yandex | Advertising |
| Crash logs / diagnostics | Yandex hujjatiga qarang | Yandex | — |

- Ma’lumot uchinchi tomonga beriladimi: **Ha** — Yandex (reklama).
- Ma’lumot uzatishda shifrlanadimi: **Ha** (HTTPS).
- O‘chirishni so‘rash mumkinmi: anonim hisob Sozlamalarda o‘chiriladi, bog‘lanish Telegram bot `/fikr` orqali.

### Rasmlar ruxsati (`READ_MEDIA_IMAGES`)

«Skrinshotlar buferda» uchun klaviatura yangi skrinshotlarni koʻrishi kerak. Play Console →
App content → **Photo and video permissions** → *Declare*. Inglizcha matn:

> Harfix Keyboard shows the screenshot the user has just taken in its clipboard panel and on the
> clipboard, so it can be pasted into a chat in one tap (like the clipboard of other keyboards).
> The permission is asked only when the user switches "Skrinshotlar buferda" on; the keyboard reads
> only images in the Screenshots folder added in the last minute, keeps copies on the phone and
> never uploads them. The system photo picker cannot do this, because it needs a user action for
> every picture, and the point of the feature is that a new screenshot is ready without one.

Data safety’da rasmlar **yigʻilmaydi** (faqat telefonda ishlanadi). Rad etilsa: ikkala
`uses-permission` qatorini `src/main/AndroidManifest.xml` dan `src/direct/AndroidManifest.xml` ga
koʻchiring. Play versiyasida «Rasm qoʻshish» (tizim rasm tanlagichi, ruxsatsiz) qoladi.

## 4. Accessibility API deklaratsiyasi (`play` flavor)

Play Console → App content → **Accessibility API** → *Declare*. Inglizcha matn:

> Harfix converts Uzbek text to the new Latin alphabet (law O'RQ-1184: oʻ→ö, gʻ→ğ, sh→ş, ch→ç). The
> optional "Harfix: odatiy klaviaturada" accessibility service lets users keep their usual keyboard
> (Gboard, Samsung Keyboard): when the user types the second letter of oʻ, gʻ, sh or ch in a text
> field, the service replaces the pair with the new letter in that field (Backspace undoes it). It
> only reads TYPE_VIEW_TEXT_CHANGED events of the field being edited, skips password, e-mail, URL
> and number fields, never stores or transmits any text, and takes no other actions. Before the user
> is sent to Accessibility settings, the app shows a full-screen disclosure explaining this and asks
> for consent ("Roziman, davom etish"). Users can turn it off from the app, a Quick Settings tile,
> or Android settings.

**Video ssenariysi** (30–60 soniya, YouTube’ga «Unlisted» qilib yuklang va havolasini kiriting):
1. Harfix → «Klaviatura» bo‘limi → «Yoqish».
2. Ogohlantirish oynasi (*«Odatiy klaviaturada» xizmati*) ko‘rinadi → «Roziman, davom etish».
3. Android Maxsus imkoniyatlar → «Harfix: odatiy klaviaturada» → yoqish.
4. Telegram yoki Messages’da Gboard bilan «Toshkent» deb yozish → «Toşkent» bo‘ladi. ⌫ bosib, qaytishini ko‘rsatish.
5. Parol maydonida hech narsa o‘zgarmasligini ko‘rsatish.

## 5. Store listing (Uzbek)

Tayyor matnlar va rasmlar: [play/listing.md](play/listing.md), `play/images/`. Qadamma-qadam yo‘riqnoma:
[play/README.md](play/README.md).

  Microsoft Store’dagidek, skrinshotlar **ilovaning o‘zidan** olinishi kerak: maket yoki brauzerda
  chizilgan interfeys rad etiladi.

## 6. Chiqarish

1. **Internal testing** → AAB’ni yuklang, o‘zingiz o‘rnatib sinab ko‘ring.
2. **Closed testing**: shaxsiy akkaunt bo‘lsa, 12+ tester, 14 kun (0-bo‘lim).
3. **Production** → «Apply for production» → ko‘rib chiqish odatda 1–7 kun.

## 7. Ko‘p uchraydigan rad etish sabablari

| Xabar | Yechim |
|---|---|
| Accessibility API policy | Deklaratsiya va video (4-bo‘lim). Rad etilsa, xizmatsiz AAB yuboring |
| Device and Network Abuse (o‘zini yangilash) | Play versiyasi yangilanishni faqat Play orqali ko‘rsatadi (`Online.kt`: `fromPlay` → Play havolasi). Saytdan APK yuklashni taklif qilmang |
| Data safety mos emas | 3-bo‘limdagi jadvalni SDK’lar bilan solishtiring |
| 16 KB page size | CI’dagi 16 KB tekshiruvi qizil bo‘lsa, Yandex SDK’ni yangilang |
| Metadata / screenshots | Faqat ilovaning o‘zidan olingan skrinshotlar |
| Families / bolalar | Target audience’da 13 yoshdan kichiklarni belgilamang |
