# Harfix — konvertor yadrosi

O‘RQ-1184 (21.10.2026) qonuni bo‘yicha eski lotin va kirill matnini yangi alifboga o‘giradi:
oʻ→ö, gʻ→ğ, sh→ş, ch→ç, tutuq belgisi → ʼ. URL, e-mail, @mention, #teg va `kod` o‘zgarmaydi.

```python
from yangi_alifbo import convert
convert("Oʻzbekiston shahri")  # "Özbekiston şahri"
convert("Тошкент шаҳри")       # "Toşkent şahri"
```

Testlar: `python3 tests/test_converter.py`. `tests/cases.json` umumiy test to‘plami:
Android (Kotlin) va Windows (C#) versiyalari ham shu testlardan o‘tishi shart.
