# Harfix — Google Play

Paket nomi: **`uz.harfix.app`**. Play uchun **`play`** flavor’i yig‘iladi. Saytdagi APK — **`direct`** flavor’i.

| | Play (`play`) | Sayt (`direct`) |
|---|---|---|
| Rasmlar ruxsati, «Skrinshotlar buferda» | yo‘q | bor |
| Yandex reklama SDK’si, homiy kartasi | yo‘q | bor (onlayn sozlama yoqsa) |
| «Odatiy klaviaturada» (accessibility xizmati) | yo‘q | yo‘q |
| «Rasm qo‘shish», klaviatura foni (tizim rasm tanlagichi) | bor | bor |

## 0. Bilish kerak bo‘lgan narsalar

- **Yangi shaxsiy akkaunt bo‘lsa, yopiq test majburiy.** 2023-yil noyabridan keyin ochilgan shaxsiy
  (Personal) dasturchi akkauntlarida Production’ga chiqishdan oldin **kamida 12 tester bilan 14 kun
  yopiq test (Closed testing)** o‘tkazish kerak. Tashkilot (Organization) akkauntlariga bu talab qo‘yilmaydi.
- **Play versiyasi ataylab sodda (2026-11-20).** Birinchi tekshiruv tez va rad etilmasdan o‘tishi uchun
  unda rasmlar ruxsati (`READ_MEDIA_IMAGES`), reklama SDK’si va accessibility xizmati yo‘q. Shuning uchun
  *Photo and video permissions* va *Accessibility API* deklaratsiyalari kerak emas, *Ads* — **No**.
  `android-release` workflow’i AAB’da bular yo‘qligini tekshiradi.
- **Reklamani keyin Play’da yoqish** — yangilanish bilan: `"directImplementation"` → `implementation`
  (`app/build.gradle.kts`), `src/direct` dagi `BannerAds.kt` va provayderlarni `main` ga, workflow
  tekshiruvidan `com.yandex` va `AD_ID` ni olib tashlang. **O‘sha yangilanish bilan birga** Play Console’da:
  *Ads* → Yes, *Advertising ID* → Yes, Data safety’ga Yandex qatorlari (pastdagi «Reklama qaytsa»),
  akkaunt sozlamalarida **trader status** → trader (manzil Yevropa foydalanuvchilariga ochiq ko‘rinadi).
  Reklamani onlayn sozlama bilan, formalarsiz yoqib bo‘lmaydi: Play versiyasi `ads: false` deydi va
  banner ham, homiy kartasi ham ko‘rsatmaydi.
- **«Odatiy klaviaturada»** (accessibility) uchun Google deklaratsiya va video so‘raydi va ko‘pincha rad
  etadi (4-bo‘lim). Xizmat fayllari git tarixida (`src/play`, 2026-11-20 gacha); qaytarish kerak bo‘lsa, 4-bo‘lim.

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
| Ads | **No** — Play versiyasida reklama SDK’si yo‘q (0-bo‘lim) |
| Content rating | IARC so‘rovnomasi: Utility/Productivity, hamma savolga *No* |
| Target audience | **13+** (13–15, 16–17, 18+). 13 yoshdan kichiklarni belgilamang: Families siyosati qo‘shimcha talablar qo‘yadi, reklama qaytsa SDK ham Families dasturida emas |
| News app | No |
| Data safety | Pastdagi jadval |
| Advertising ID | **No** — Play AAB’da `AD_ID` ruxsati yo‘q (workflow tekshiradi) |
| Accessibility API | Kerak emas: Play versiyasida xizmat yo‘q |
| Government / Financial / Health | No |

**Data safety** (Play versiyasi uchun):

| Ma’lumot | Yig‘iladimi | Kim | Nima uchun |
|---|---|---|---|
| Yozilgan matn, fayllar, klaviatura kiritmasi | **Yo‘q** — hammasi telefonda | — | — |
| Shaxsiy lug‘at, o‘rganilgan so‘zlar, bufer | **Yo‘q** — faqat telefonda | — | — |
| Rasmlar («Rasm qo‘shish», klaviatura foni) | **Yo‘q** — faqat telefonda ishlanadi, yuborilmaydi | — | — |
| Device or other IDs | Ha | Harfix: tasodifiy o‘rnatish raqami (kunlik anonim hisob, Sozlamalarda o‘chiriladi) | Analytics |

- Ma’lumot uchinchi tomonga beriladimi: **Yo‘q**.
- Ma’lumot uzatishda shifrlanadimi: **Ha** (HTTPS).
- O‘chirishni so‘rash mumkinmi: anonim hisob Sozlamalarda o‘chiriladi, bog‘lanish Telegram bot `/fikr` orqali.

**Reklama qaytsa** (0-bo‘lim), jadvalga Yandex qo‘shiladi (Yandex Mobile Ads hujjatidagi «Google Play
Data safety» bo‘limi bilan solishtiring): *Device or other IDs* — reklama identifikatori (Advertising),
*App interactions* — reklama ko‘rish/bosish (Advertising), *Crash logs* — Yandex hujjatiga qarang;
uchinchi tomonga beriladimi — **Ha**, Yandex.

### Rasmlar ruxsati (`READ_MEDIA_IMAGES`) — faqat saytdagi APK’da

Play versiyasida bu ruxsat va «Skrinshotlar buferda» yo‘q (0-bo‘lim), shuning uchun deklaratsiya kerak emas.
Keyin Play’ga qo‘shilsa: ruxsat qatorlarini `src/direct/AndroidManifest.xml` dan `src/main` ga ko‘chiring
va workflow tekshiruvidan olib tashlang. Google bu ruxsatni faqat rasmlar asosiy vazifa bo‘lgan ilovalarga
beradi, shuning uchun rad etilishi ehtimoli katta. Play Console → App content → **Photo and video
permissions** → *Declare*. Inglizcha matn:

> Harfix Keyboard shows the screenshot the user has just taken in its clipboard panel and on the
> clipboard, so it can be pasted into a chat in one tap (like the clipboard of other keyboards).
> The permission is asked only when the user switches "Skrinshotlar buferda" on; the keyboard reads
> only images in the Screenshots folder added in the last minute, keeps copies on the phone and
> never uploads them. The system photo picker cannot do this, because it needs a user action for
> every picture, and the point of the feature is that a new screenshot is ready without one.

Data safety’da rasmlar **yigʻilmaydi** (faqat telefonda ishlanadi).

## 4. Accessibility API deklaratsiyasi — hozir kerak emas

Play versiyasida «Odatiy klaviaturada» yo‘q (0-bo‘lim). Qaytarish uchun: `WordsService.kt`, `WordsTile.kt`
va `src/play/AndroidManifest.xml` ni git tarixidan tiklang (2026-11-20 gacha bo‘lgan `main`), `PlayBuildTest`
ga xizmat testini qaytaring, workflow tekshiruvidan `BIND_ACCESSIBILITY_SERVICE` ni olib tashlang. Keyin
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
| Accessibility API policy | Play versiyasida xizmat yo‘q. Qaytarilsa: deklaratsiya va video (4-bo‘lim) |
| Photo and video permissions | Play versiyasida ruxsat yo‘q. Qo‘shilsa: deklaratsiya (3-bo‘lim) |
| Ads policy / Data safety | Play versiyasida reklama yo‘q. Qaytarilsa: Ads, Data safety, trader status birga (0-bo‘lim) |
| Device and Network Abuse (o‘zini yangilash) | Play versiyasi yangilanishni faqat Play orqali ko‘rsatadi (`Online.kt`: `fromPlay` → Play havolasi). Saytdan APK yuklashni taklif qilmang |
| Data safety mos emas | 3-bo‘limdagi jadvalni SDK’lar bilan solishtiring |
| 16 KB page size | CI’dagi 16 KB tekshiruvi qizil bo‘lsa, native kutubxonali SDK’ni (saytdagi APK’da Yandex) yangilang |
| Metadata / screenshots | Faqat ilovaning o‘zidan olingan skrinshotlar |
| Families / bolalar | Target audience’da 13 yoshdan kichiklarni belgilamang |
