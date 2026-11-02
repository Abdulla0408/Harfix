"""Bot messages. Written in the current Latin alphabet and converted to the
new alphabet at startup, so the bot itself always "speaks" the new alphabet."""
from yangi_alifbo import convert

_RAW = {
    "start": (
        "Assalomu alaykum! Men <b>Harfix</b> — yangi oʻzbek alifbosi yordamchisiman.\n\n"
        "{law} qonuniga koʻra yangi alifboda: {rules}.\n\n"
        "✍️ Menga istalgan matnni yozing (lotin yoki kirill) — yangi alifboda qaytaraman.\n"
        "📄 Word, Excel, PowerPoint yoki .txt fayl yuboring — shrift va bezaklarni "
        "oʻzgartirmasdan faqat yozuvni almashtiraman.\n"
        "💬 Istalgan chatda <code>@{username} matn</code> deb yozing — darhol oʻgiraman."
    ),
    "help": (
        "Qanday ishlatiladi:\n"
        "• Matn yuboring — yangi alifboda qaytaraman.\n"
        "• Fayl yuboring: .docx, .xlsx, .pptx, .txt (20 MB gacha).\n"
        "• Istalgan chatda: <code>@{username} matn</code>\n\n"
        "Maxfiylik: yuborilgan matn va fayllar saqlanmaydi, faqat oʻgirish uchun ishlatiladi."
    ),
    "unsupported": (
        "Bu fayl turini hozircha qoʻllab-quvvatlamayman.\n"
        "Qabul qilinadi: .docx, .xlsx, .pptx, .txt. Eski .doc/.xls/.ppt faylni "
        "Word/Excel/PowerPoint’da yangi formatda saqlab yuboring."
    ),
    "pdf_soon": "PDF fayllarni oʻgirish tez orada qoʻshiladi. Hozircha Word, Excel, PowerPoint yoki .txt yuboring.",
    "too_big": "Fayl juda katta. Telegram botlar 20 MB gacha faylni qabul qila oladi.",
    "working": "⏳ Fayl oʻgirilmoqda...",
    "done": "✅ Tayyor! Yangi alifboga oʻgirildi.",
    "failed": "Faylni oʻgirib boʻlmadi. Fayl shikastlangan yoki parol bilan himoyalangan boʻlishi mumkin.",
    "same": "Bu matn allaqachon yangi alifboda ✅",
    "inline_title": "Yangi alifboda yuborish",
    "inline_hint": "Matn yozing — yangi alifboga oʻgiraman",
}

# Inserted after conversion so they stay as written.
_KEEP = {"{law}": "O‘RQ-1184", "{rules}": "oʻ → ö, gʻ → ğ, sh → ş, ch → ç"}


def _build(text: str) -> str:
    text = convert(text)
    for k, v in _KEEP.items():
        text = text.replace(k, v)
    return text


T = {k: _build(v) for k, v in _RAW.items()}
