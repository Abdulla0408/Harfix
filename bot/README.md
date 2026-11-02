# Harfix Telegram bot

Nima qiladi:
- Matn (lotin yoki kirill) → yangi alifbo (O‘RQ-1184).
- .docx / .xlsx / .pptx / .txt fayllar → faqat matn almashadi; shrift, o‘lcham, rang, rasm va formulalar o‘zgarmaydi.
- Inline rejim: istalgan chatda `@HarfixBot matn`.
- Maxfiylik: matn va fayllar saqlanmaydi, logga yozilmaydi.

## Ishga tushirish (Windows / Linux)

```bash
cd yangi-alifbo/bot
python -m venv .venv
.venv\Scripts\activate        # Linux: source .venv/bin/activate
pip install -r requirements.txt
copy .env.example .env        # va BOT_TOKEN ni yozing
python -m harfix_bot
```

Inline rejim uchun @BotFather → /setinline → botni tanlang → "Matn yozing" kabi yozuv kiriting.

## Testlar

```bash
python tests/test_documents.py
python ../core/tests/test_converter.py
```

Hali yo‘q: PDF (keyingi bosqich), eski .doc/.xls/.ppt formatlari.
