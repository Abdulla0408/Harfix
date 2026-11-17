# Harfix — Windows ilova

Versiya **1.2.1**. Python 3.12, oynasi pywebview (WebView2) orqali ko‘rsatiladigan veb-sahifa (`harfix_desktop/ui`).

## Bu kod qayerdan keldi

Asl loyiha faqat kompyuterda turgan va Windows qayta o‘rnatilganda yo‘qolgan. 17.11.2026 da
saytdagi `Harfix-Setup.exe` 1.2.1 dan (GitHub release `harfix-web` v1.4.0) tiklandi: o‘rnatuvchi
Python kodini ochiq holda olib yurgani uchun bu papkadagi fayllar **asl fayllarning aynan o‘zi**.

Shu bilan birga `core/yangi_alifbo` ham ilovadagi yangiroq nusxaga yangilandi (eski lotin va kirillga
o‘girish, `convert_to`, `TARGETS`). U `harfix.js` bilan 3 128 ta misolda × 3 yo‘nalishda bir xil natija beradi,
`core` va bot testlari o‘tadi.

### Yo‘q narsalar (o‘rnatuvchiga kirmagan)

| Nima | Vazifasi |
|---|---|
| `installer/make_bundle.py`, `installer/harfix.iss`, `build.bat` | Python runtime + ilovani yig‘ib, Inno Setup o‘rnatuvchisini yasash |
| `msix/` (`AppxManifest`, `listing.py`, `STORE.md`) | Microsoft Store paketi va Store matni |
| `tests/` | desktop testlari |
| `core/tests/cases_old.json`, `cases_cyr.json` | eski lotin / kirill uchun umumiy testlar |

O‘rnatilgan ilovaning tuzilishi (`harfix_desktop/paths.py` dan):

```
<o‘rnatilgan papka>/runtime/pythonw.exe   rasmiy Python 3.12 (embeddable)
<o‘rnatilgan papka>/runtime/Lib/site-packages   requirements.txt dagi paketlar
<o‘rnatilgan papka>/app/harfix_desktop, app/harfix_files, app/yangi_alifbo
<o‘rnatilgan papka>/harfix.ico
```

Microsoft Store: mahsulot ID `9N1B1ZZ8ML2K`. MSIX paketini qayta yig‘ish uchun kerakli Identity
(Name, Publisher) Partner Center → Harfix → **Product identity** sahifasida turadi.

## Manbadan ishga tushirish (Windows)

```bat
cd desktop
py -3.12 -m venv .venv
.venv\Scripts\pip install -r requirements.txt
.venv\Scripts\python -m harfix_desktop
```

Konsol oynasisiz: `.venv\Scripts\pythonw harfix.pyw` («Windows bilan birga ishga tushish» ham manbadan shu faylni ishlatadi).

`--tray` — soat yonida yashirin ishga tushadi.

## Tuzilishi

| Papka / fayl | Nima |
|---|---|
| `harfix_desktop/app.py` | Oyna, tray, sozlamalar, sahifaga hodisalar (`window.harfixEvent`) |
| `harfix_desktop/api.py` | Sahifa chaqiradigan funksiyalar (`window.pywebview.api`) |
| `harfix_desktop/engine/` | «Yozayotganda o‘girish»: klaviatura hook’i, UI Automation, matnni almashtirish |
| `harfix_desktop/batch.py` | Fayllarni navbat bilan o‘girish |
| `harfix_desktop/online.py` | Onlayn sozlamalar va kunlik anonim hisob (Supabase → GitHub Pages) |
| `harfix_desktop/ui/` | Ilova interfeysi (Mini App bilan umumiy uslub) |
| `harfix_files/` | Word / Excel / PowerPoint / PDF / txt va eski .doc/.xls/.ppt (LibreOffice orqali) |
