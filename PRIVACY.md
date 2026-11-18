# Harfix — maxfiylik siyosati

Kuchga kirgan sana: 17.11.2026

Bu siyosat Harfix ilovalariga tegishli: Android ilova (`uz.harfix.app`) va Harfix klaviaturasi,
hamda Windows uchun Harfix dasturi. Unda ilova qanday maʼlumotlar bilan ishlashi, nimani
internetga yuborishi va nimani yubormasligi yozilgan.

## Qisqacha

- Yozgan matnlaringiz va oʻgirgan fayllaringiz **hech qayerga yuborilmaydi** — hammasi
  telefon yoki kompyuterning oʻzida bajariladi.
- Internet boʻlsa, ilova yangiliklar va sozlamalarni yuklab oladi hamda kuniga bir marta
  **anonim** «Harfix ishlatildi» belgisini yuboradi (sozlamalarda oʻchiriladi).
- Android ilovada **Yandex reklamasi** koʻrsatilishi mumkin; reklama xizmati qurilma
  haqidagi baʼzi maʼlumotlarni oladi (pastda batafsil).
- Ism, telefon raqami, e-mail, kontaktlar, joylashuv, rasmlar soʻralmaydi va toʻplanmaydi.
  Roʻyxatdan oʻtish (akkaunt) yoʻq.

## 1. Qurilmada qoladigan maʼlumotlar

**Matn va fayllar.** Matnni oʻgirish, Word / Excel / PowerPoint / PDF / txt fayllarni oʻgirish
qurilmaning oʻzida bajariladi. Oʻgirilgan fayl siz tanlagan joyga saqlanadi (Android’da
`Downloads/Harfix`, Windows’da fayl yonidagi `Harfix` papkasi yoki siz tanlagan papka).
Asl fayllar oʻzgartirilmaydi.

**Harfix klaviaturasi (Android).** Klaviatura bilan yozilgan matn saqlanmaydi va yuborilmaydi.
Parol, e-mail va sayt manzili maydonlarida yozayotganda oʻgirish ishlamaydi.

**«Yozayotganda oʻgirish» (Windows).** Dastur oʻ, gʻ, sh, ch harflarini almashtirish uchun
bosilgan tugmalarni faqat xotirada, oʻsha zahoti koʻrib chiqadi. Bosilgan tugmalar faylga
yozilmaydi va yuborilmaydi. Parol maydonlarida, administrator huquqi bilan ishlayotgan
dasturlarda, toʻliq ekranli oʻyin va taqdimotlarda hamda siz roʻyxatga qoʻshgan dasturlarda
bu funksiya oʻchadi. Uni istalgan payt oʻchirish mumkin (Ctrl+Shift+Y).

**Sozlamalar** (tanlangan alifbo, test natijasi va hokazo) faqat qurilmaning oʻzida saqlanadi.

## 2. Internetga yuboriladigan maʼlumotlar

**Onlayn sozlamalar.** Ilova vaqti-vaqti bilan eʼlonlar, yangi versiya haqidagi xabar va
reklama sozlamalarini yuklab oladi: avval Harfix serveridan (Supabase, `supabase.co`), u
ishlamasa zaxira nusxadan (GitHub Pages, `github.io`). Bu soʻrovda sizga oid maʼlumot
yuborilmaydi; har qanday internet soʻrovidagi kabi server IP manzilni koʻradi.

**Anonim statistika.** Kuniga bir marta Harfix serveriga quyidagilar yuboriladi:
- ilova birinchi marta yaratgan tasodifiy raqam (oʻrnatish identifikatori). U ismingiz,
  telefon raqamingiz yoki qurilmaning oʻz identifikatorlariga bogʻlanmagan;
- platforma (`android` yoki `windows`);
- ilova versiyasi.

Bu faqat Harfix’dan nechta odam foydalanayotganini bilish uchun kerak. Sozlamalardagi
«Anonim statistika» tugmasi bilan oʻchiriladi.

**Reklama (Android).** Ilovaning asosiy oynasida Yandex Mobile Ads (Yandex) banneri
koʻrsatilishi mumkin; klaviaturada reklama yoʻq. Reklama koʻrsatilganda Yandex reklama
xizmati qurilmadan reklama identifikatori, IP manzil, qurilma modeli, tizim versiyasi,
til kabi maʼlumotlarni oladi va ularni oʻz maxfiylik siyosati asosida ishlatadi:
<https://yandex.com/legal/confidential/>. Reklama identifikatorini Android sozlamalarida
(odatda Google → Reklama) qayta oʻrnatish yoki oʻchirish mumkin.

**Homiy kartochkalari va eʼlonlar.** Ilovada «Reklama» deb belgilangan homiy kartochkalari va
eʼlonlar koʻrsatilishi mumkin. Kartochkadagi rasm homiy koʻrsatgan saytdan yuklanadi (u sayt
IP manzilni koʻradi). Havolani bossangiz, u brauzerda ochiladi.

**Bildirishnomalar (Android).** Eʼlonlar va yangi versiya haqidagi bildirishnomalar yuqoridagi
onlayn sozlamalardan olinadi; ular uchun alohida maʼlumot yuborilmaydi. Ularni ilova
sozlamalarida oʻchirish mumkin.

## 3. Android ruxsatlari

| Ruxsat | Nima uchun |
|---|---|
| Internet | onlayn sozlamalar, anonim statistika, reklama |
| Bildirishnomalar | eʼlonlar va yangi versiya haqida xabar |
| Telefon yonganda ishga tushish | kuniga ikki marta yangiliklarni tekshirish telefon qayta yongandan keyin ham davom etishi uchun |

Kontaktlar, joylashuv, kamera, mikrofon yoki barcha fayllarga ruxsat soʻralmaydi; ilova faqat
siz tanlagan yoki ulashgan fayllarni ochadi.

## 4. Maʼlumotlarni boshqalarga berish

Maʼlumotlar sotilmaydi. Ular faqat yuqorida aytilgan xizmatlarga (Supabase, GitHub Pages,
Android’da Yandex reklamasi) yuboriladi.

## 5. Maʼlumotlarni oʻchirish

- «Anonim statistika» ni oʻchirsangiz, belgi boshqa yuborilmaydi.
- Ilovani oʻchirsangiz (yoki Android’da ilova maʼlumotlarini tozalasangiz), qurilmadagi
  sozlamalar va oʻrnatish identifikatori oʻchadi. Windows’da ular `%APPDATA%\Harfix`
  papkasida turadi.
- Statistikadagi yozuvlar shaxsga bogʻlanmagani uchun ularni alohida odamga qarab topib
  boʻlmaydi.

## 6. Oʻzgarishlar

Siyosat oʻzgarsa, yangi matn shu manzilda yangi sana bilan eʼlon qilinadi.

## 7. Aloqa

Savol va takliflar: <https://github.com/Abdulla0408/Harfix/issues>
