# Google Play’ga chiqarish: qadamma-qadam

Bu papkada Play’ga kerak bo‘ladigan hamma narsa bor:
- [listing.md](listing.md): sahifa matnlari, testerlarga xabar, production arizasi qoralamasi;
- `images/`: ikona, feature graphic, skrinshotlar.

Formalar (Data safety, Content rating, deklaratsiyalar) javoblari [../PLAY.md](../PLAY.md) da.

## Oldindan tayyorlab qo‘ying

- **Google akkaunt** (Gmail). Dasturchi akkaunti shunga bog‘lanadi, keyin boshqasiga ko‘chirib bo‘lmaydi.
- **Xalqaro to‘lov kartasi**: Visa yoki Mastercard, 25 $ bir martalik to‘lov uchun. Humo/Uzcard o‘tmaydi.
  Virtual Visa ham bo‘ladi (Uzum, TBC, Kapitalbank va boshqalar).
- **Pasport yoki ID-karta**: Google shaxsni tasdiqlashni so‘raydi. Ismingiz kartadagi va to‘lov profilidagi
  bilan bir xil bo‘lsin.
- **Android telefon** va **telefon raqami**: Google akkauntni Play Console ilovasi orqali haqiqiy telefonda
  va SMS bilan tasdiqlashni so‘raydi.
- **AAB fayli**: GitHub → Actions → `android-release` → oxirgi muvaffaqiyatli ishga tushirish → Artifacts →
  `Harfix-android-release` → ichidagi **`Harfix-play.aab`**.

## 1. Dasturchi akkaunti (to‘lov shu yerda)

1. <https://play.google.com/console/signup> → Google akkaunt bilan kiring.
2. **Yourself** (shaxsiy akkaunt) ni tanlang.
3. Ma’lumotlar:
   - *Developer name* — Play’da ko‘rinadigan nom: **Harfix**.
   - Aloqa uchun pochta va telefon.
   - Tajriba haqidagi savollar.
4. **25 $ to‘lov**.
5. **Shaxsni tasdiqlash**: hujjat rasmi. Odatda 1–3 kun ketadi, natijasi pochtaga keladi.
6. **Telefon tasdiqlash**: Play Console ilovasini telefonga o‘rnatib, shu akkaunt bilan kiring va SMS kodni kiriting.

## 2. Ilovani yaratish

**Create app**:
- App name: `Harfix — yangi oʻzbek alifbosi`;
- Default language: **Uzbek – uz**;
- **App**, **Free**;
- ikkita belgini qo‘ying (qoidalar va AQSh eksport qonunlari).

## 3. Dashboard → «Set up your app»

Har bir bandga javoblar [../PLAY.md](../PLAY.md), 3-bo‘limda:
1. **Privacy policy**: `https://harfix.uz/maxfiylik/`.
2. **App access**: hamma narsa akkauntsiz ishlaydi.
3. **Ads**: Yandex reklamasi yoqiladigan bo‘lsa — *Yes*. Hozir o‘chiq, lekin SDK ilovada bor.
4. **Content rating**: IARC so‘rovnomasi. Kategoriya *Utility/Productivity*, savollarga *No*.
5. **Target audience**: 13+ (13 yoshdan kichiklarni belgilamang).
6. **Data safety**: PLAY.md dagi jadval. Rasmlar (skrinshotlar) faqat telefonda ishlanadi, shuning uchun
   «yig‘iladi» deb belgilanmaydi.
7. **Government apps, Financial features, Health, News**: hammasiga *No*.
8. **Store listing**: [listing.md](listing.md) va `images/`.

## 4. Yopiq test (Closed testing)

1. **Testing → Closed testing → Create track** (yoki tayyor *Alpha*).
2. **Countries**: Uzbekistan va testerlaringiz yashaydigan boshqa davlatlar.
3. **Testers**: *Email list* yarating va 15–20 ta Gmail manzilni qo‘shing. 12 emas: kimdir chiqib ketsa ham
   son 12 dan tushmasin. *Feedback URL*: `https://t.me/a33208`.
4. **Create new release**:
   - **Play App Signing** → *Use Google-generated key* (tavsiya). Bizning `harfix-release.jks` faqat
     *upload key* bo‘ladi.
   - `Harfix-play.aab` ni yuklang.
   - *Release notes*: [listing.md](listing.md) dagi matn.
5. Yuklagach, Play ikkita ruxsat uchun **deklaratsiya** so‘raydi (*App content → Sensitive permissions and APIs*).
   Matnlari PLAY.md da:
   - **Accessibility API** («Odatiy klaviaturada»): matn va **video** (30–60 soniya, ssenariy PLAY.md, 4-bo‘lim).
     Videoni telefonning ekran yozuvi bilan olib, YouTube’ga *Unlisted* qilib yuklang.
   - **Photo and video permissions** (skrinshotlar buferda): matn.
6. **Send for review**. Birinchi tekshiruv bir necha kundan bir haftagacha davom etadi.

> Google «Odatiy klaviaturada» ni rad etsa, xizmatsiz AAB yuboriladi. Uni men tayyorlab beraman:
> `play` flavor’idan xizmatni olib tashlash va qayta yig‘ish kifoya.

## 5. 14 kun

1. Tekshiruvdan o‘tgach, **opt-in havolasini** nusxalang (*Closed testing → Testers*). Uni testerlarga
   [listing.md](listing.md) dagi xabar bilan yuboring.
2. Play Console’da nechta tester qo‘shilgani va 14 kundan qanchasi o‘tgani ko‘rinib turadi.
3. Fikrlar asosida 1–2 ta yangilanish chiqaring:
   - `versionCode` ni oshiring;
   - `android-release` ni ishga tushiring;
   - yangi AAB’ni o‘sha track’ka yuklang.
4. Hech kimni testdan chiqarmang.

## 6. Production

14 kun va 12+ tester bo‘lgach, **Dashboard → Apply for production**. Javoblar qoralamasi [listing.md](listing.md)
da: test paytida bo‘lgan haqiqiy fikr va o‘zgarishlarni yozing. Ruxsat kelgach, *Production* track’ida reliz
yarating. Odatda bir necha kun ichida Play’da ko‘rinadi.
