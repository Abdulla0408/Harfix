# Harfix — Microsoft Store

Mahsulot ID: **9N1B1ZZ8ML2K** (Partner Center → Apps and games → Harfix).

Store versiyasining afzalligi: paketni Microsoft o‘zi imzolaydi, shuning uchun «Windows protected your PC»
va Smart App Control («An Application Control policy has blocked this file») ogohlantirishlari chiqmaydi,
yangilanishlar Store orqali o‘zi keladi.

**Holati:** 1.2.1.0 18.11.2026 da Store’da chiqdi (<https://apps.microsoft.com/detail/9N1B1ZZ8ML2K>);
1.2.2.0 yangilanish sifatida yuborilgan. Birinchi yuborish 13.11 da
rad etilgan edi (10.1.1.3 Inaccurate Representation): skrinshotlar ilovadan to‘g‘ridan-to‘g‘ri olinmagan,
ya’ni interfeys brauzerda chizilgan edi. Endi [`store/`](store) dagi rasmlar o‘rnatilgan Harfix’ning
haqiqiy oynasidan olingan (pastda, «Skrinshotlar»).

## 1. Bir marta: paket identifikatori

Partner Center → Harfix → **Product management → Product identity** sahifasidagi uchta qiymatni
[`identity.json`](identity.json) ga yozing:

| Partner Center’da | `identity.json` da | Ko‘rinishi |
|---|---|---|
| Package/Identity/Name | `name` | `12345Abdulla.Harfix` |
| Package/Identity/Publisher | `publisher` | `CN=1A2B3C4D-…` |
| Package/Properties/PublisherDisplayName | `publisher_display_name` | `Abdulla` |

Ular bo‘sh bo‘lsa `build_msix.py` **TEST** paket yasaydi (`Harfix-…-TEST.msix`). Store uni qabul qilmaydi
(«package identity doesn’t match» xatosi).

## 2. Paketni olish

**GitHub orqali (Windows kerak emas):** `desktop/` o‘zgargan har bir push’da **msix** workflow ishlaydi:
paketni yig‘adi, ichidagi Python’ni tekshiradi (importlar, .NET/WebView2, Word/Excel/PowerPoint/txt/PDF
o‘girish), paketni Windows’ga o‘rnatib ishga tushiradi va Windows App Certification Kit’dan o‘tkazadi.
Tayyor `.msix` → Actions → msix → oxirgi run → **Artifacts → Harfix-msix**.

WACK natijasi (17.11.2026): umumiy **WARNING**, ya’ni majburiy testlar o‘tgan. «Blocked executables» faqat
Windows S rejimi uchun: Python, lxml, MuPDF kutubxonalari ichida `cmd`, `reg` kabi so‘zlar uchragani
uchun chiqadi; WACK uni majburiy test hisoblamaydi (shuning uchun umumiy natija FAIL emas). «DPIAwareness» — ogohlantirish (DPI’ni pywebview
ishga tushganda o‘zi sozlaydi).

**O‘z kompyuteringizda (Windows, Python 3.12, Windows SDK):**

```bat
py -3.12 desktop\msix\build_msix.py
```

Natija: `desktop\msix\out\Harfix-1.2.2.0.msix`. Versiya `harfix_desktop/__init__.py` dagi `VERSION` dan
olinadi. Har yangi yuborishda versiyani oshiring: Store bir xil yoki eski versiyani qabul qilmaydi.

## 3. Partner Center’da yuborish

Harfix → **Start your submission** (yoki **Update**):

1. **Pricing and availability** — Free; Markets: hammasi (yoki faqat Uzbekistan); Visibility: Public.
2. **Properties** — Category: *Productivity*. Privacy policy URL: `https://harfix.uz/maxfiylik/`.
   Website: `https://harfix.uz/`. Support contact: `https://t.me/` dagi bot havolasi yoki e-mail.
   System requirements: Windows 10 2004+ (x64), Microsoft Edge WebView2 Runtime.
3. **Age ratings** — IARC so‘rovnomasi: «Utility, productivity…» turi, hamma savolga *No* → 3+.
4. **Packages** — `Harfix-<versiya>.msix` ni tashlang. «Windows 10/11 Desktop» belgilangan bo‘lsin.
5. **Store listings** — til: **Uzbek (Latin)** (paket shu tilda: `uz-Latn`). Matnlar pastda, rasmlar
   [`store/`](store) papkasida (1707×960, ilova oynasining o‘zi).
6. **Submission options** — *Restricted capabilities* va *Notes for certification* ga pastdagi
   inglizcha matnni qo‘ying.
7. **Submit to the Store.** Tekshiruv odatda 1–3 kun.

## 4. Store sahifasi matnlari

**Description**

> Harfix — yangi oʻzbek alifbosi yordamchisi. 21.10.2026 dagi OʻRQ-1184-son qonun bilan lotin
> alifbosida 4 ta harf oʻzgardi: oʻ → ö, gʻ → ğ, sh → ş, ch → ç. Harfix yangi alifboda yozishni osonlashtiradi.
>
> • Yozayotganda oʻgirish: Word, brauzer, Telegram — istalgan dasturda odatdagidek yozasiz, sh, ch, oʻ, gʻ
> yozilishi bilan ş, ç, ö, ğ ga almashadi. Kerak boʻlmasa, darhol Backspace bosing — yozganingiz qaytadi.
> Ctrl+Shift+Y — yoqish/oʻchirish.
> • Belgilangan matn: istalgan dasturda matnni belgilab Ctrl+Shift+H bosing — oʻsha joyning oʻzida oʻgiriladi.
> • Matnni oʻgirish: lotin yoki kirill matn darhol yangi lotin, eski lotin yoki kirillda chiqadi.
> • Fayllar: Word, Excel, PowerPoint, PDF va txt, butun papka bir yoʻla. Shrift, rasm, jadval va formatlash
> oʻzgarmaydi, asl fayllar saqlanib qoladi. Eski .doc/.xls/.ppt uchun LibreOffice kerak.
> • Alifbo va test: yangi harflar, qoidalar va 10 savollik mashq.
>
> Maxfiylik: Harfix yozganlaringizni saqlamaydi va hech qayerga yubormaydi. Parol maydonlarida,
> terminal va parol saqlagich dasturlarida ishlamaydi. Internet faqat eʼlonlar va anonim kunlik hisob uchun
> kerak (Sozlamalarda oʻchiriladi). Harfix bepul; unda homiylar kartasi koʻrinishi mumkin.

**What’s new in this version**

> Microsoft Store versiyasi: yangilanishlar Store orqali oʻzi keladi, Smart App Control yoqilgan kompyuterlarda ham ishlaydi.

**Product features** (har biri alohida qator)

- Istalgan dasturda yozayotganda oʻgirish: sh → ş, ch → ç, oʻ → ö, gʻ → ğ
- Ctrl+Shift+H: belgilangan matnni oʻsha joyda oʻgirish
- Lotin va kirill matnni yangi lotin, eski lotin yoki kirillga oʻgirish
- Word, Excel, PowerPoint, PDF va txt fayllar, butun papka bir yoʻla
- Formatlash, shrift va rasmlar oʻzgarmaydi
- Yangi alifbo qoidalari va test
- Internetsiz ishlaydi, yozganlaringiz hech qayerga yuborilmaydi

**Search terms** (7 tagacha): `yangi alifbo`, `oʻzbek lotin`, `kirill lotin`, `ö ğ ş ç`, `uzbek alphabet`,
`transliteratsiya`, `Harfix`

**Screenshots** (izohi bilan):

| Fayl | Izoh |
|---|---|
| `store/1-matn.png` | Matnni oʻgirish: lotin va kirill matn yangi alifboda |
| `store/2-fayllar.png` | Fayllar: Word, Excel, PowerPoint, PDF va txt |
| `store/3-alifbo.png` | Alifbo: yangi harflar va qoidalar |
| `store/4-sozlamalar.png` | Sozlamalar: yozayotganda oʻgirish va tezkor tugmalar |

### Skrinshotlar

Store faqat ilovaning **o‘zidan olingan** rasmlarni qabul qiladi: brauzerda chizilgan interfeys yoki maket
rad etiladi (10.1.1.3). Rasmlarni qayta olish (Windows, Harfix o‘rnatilgan bo‘lsin, Python 3.10+):

```bat
py desktop\msix\capture_screenshots.py
```

Skript ishlab turgan Harfix’ni yopadi, uni WebView2 masofaviy sozlash porti bilan (faqat 127.0.0.1) qayta ochadi,
har bir sahifani to‘ldirib, oynani sarlavha paneli bilan `PrintWindow` orqali rasmga oladi (125% masshtabda
1707×960), so‘ng Harfix’ni yana soat yonida oddiy holda ishga tushiradi. Rasmlar `store/` ga yoziladi.
Kompyuter qaysi mavzuda bo‘lsa (qorong‘i/yorug‘), rasmlar ham shunday chiqadi.

## 5. Tekshiruvchilar uchun (inglizcha)

**Restricted capabilities → runFullTrust:**

Maydon 500 belgigacha qabul qiladi; 18.11.2026 da yuborilgani:

> Harfix is a Win32 desktop app (Python, packaged as MSIX). Full trust is needed to convert text as the user
> types in other programs (low-level keyboard hook + SendInput, switched on by the user), to convert selected
> text on Ctrl+Shift+H (UI Automation/clipboard), to read and write the files the user picks (and start the
> user's own LibreOffice for old .doc/.xls/.ppt), and to start with Windows via its startup task. No sign-in.
> Keystrokes and files are never stored or sent; password fields skipped.

**Notes for certification:**

> Harfix converts Uzbek text to the new Latin alphabet (law O'RQ-1184: oʻ→ö, gʻ→ğ, sh→ş, ch→ç).
> The interface is in Uzbek. How to test: open Notepad and type "shahar" — it becomes "şahar";
> type "choy" — "çoy". Press Backspace right after a change to undo it. Ctrl+Shift+Y turns live conversion
> on/off. Select some text such as "shahar choy" in any app and press Ctrl+Shift+H to convert it in place.
> In the app, "Matnni oʻgirish" converts typed text and "Fayllar" converts Word/Excel/PowerPoint/PDF files.
> Closing the window keeps Harfix in the notification area; right-click its icon → "Chiqish" to exit.
> The app starts with Windows through its startup task; the user can turn this off in the app's settings
> or in Windows Settings → Apps → Startup. No account or login is needed.

## 6. Avtomatik yuborish (ixtiyoriy)

Birinchi yuborish qo‘lda qilinadi. Keyingilarini GitHub’dan bir tugma bilan yuborish uchun:

1. Partner Center → ⚙ Account settings → **User management → Microsoft Entra applications** → yangi ilova
   (rol: Manager). Tenant ID, Client ID va yangi **Key** (client secret) ni oling; Seller ID —
   Account settings → Legal info → Developer.
2. GitHub → Harfix → Settings → Secrets and variables → Actions: `STORE_TENANT_ID`, `STORE_SELLER_ID`,
   `STORE_CLIENT_ID`, `STORE_CLIENT_SECRET`.
3. Actions → **msix** → *Run workflow* → «Also send it to the Microsoft Store» ✓.

## 7. Ko‘p uchraydigan rad etish sabablari

| Xabar | Sabab va yechim |
|---|---|
| Package identity / publisher doesn’t match | `identity.json` bo‘sh yoki xato — 1-bo‘lim |
| Version must be higher | `VERSION` ni oshiring (masalan 1.2.2 → 1.2.3) |
| Listing language not in package | Store listing **Uzbek (Latin)** bo‘lsin |
| Restricted capability runFullTrust | 5-bo‘limdagi izohni yozing |
| 10.1.1.3 Inaccurate Representation (Screenshots) | Rasmlar ilovaning o‘zidan olinmagan — `capture_screenshots.py` bilan qayta oling |
| Privacy policy required | Properties’da `https://harfix.uz/maxfiylik/` |
| App updates outside the Store | Store versiyasi «Yangi versiya — yuklab olish» xabarini ko‘rsatmaydi (`online.py`, `store=True`) |
