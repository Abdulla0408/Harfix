import asyncio
import hashlib
import html
import io
import logging
import os
import pathlib

from aiogram import Bot, Dispatcher, F, Router
from aiogram.client.default import DefaultBotProperties
from aiogram.filters import Command, CommandStart
from aiogram.types import (BufferedInputFile, InlineQuery, InlineQueryResultArticle,
                           InputTextMessageContent, Message)
from yangi_alifbo import convert

from .documents import UnsupportedFile, convert_file
from .texts import T

MAX_FILE = 20 * 1024 * 1024  # Bot API download limit
# Privacy: we never log message text or file contents, only counts/errors.
log = logging.getLogger("harfix")
router = Router()


def _fmt(key: str, bot_username: str) -> str:
    return T[key].replace("{username}", bot_username)


@router.message(CommandStart())
async def start(msg: Message, bot: Bot):
    me = await bot.me()
    await msg.answer(_fmt("start", me.username))


@router.message(Command("help"))
async def help_(msg: Message, bot: Bot):
    me = await bot.me()
    await msg.answer(_fmt("help", me.username))


@router.message(F.document)
async def document(msg: Message, bot: Bot):
    doc = msg.document
    name = doc.file_name or "fayl"
    ext = name.rsplit(".", 1)[-1].lower() if "." in name else ""
    if ext == "pdf":
        await msg.reply(T["pdf_soon"])
        return
    if doc.file_size and doc.file_size > MAX_FILE:
        await msg.reply(T["too_big"])
        return
    status = await msg.reply(T["working"])
    buf = io.BytesIO()
    await bot.download(doc, destination=buf)
    try:
        result = await asyncio.to_thread(convert_file, buf.getvalue(), name)
    except UnsupportedFile:
        await status.edit_text(T["unsupported"])
        return
    except Exception:
        log.exception("file conversion failed (ext=%s)", ext)
        await status.edit_text(T["failed"])
        return
    stem, _, suffix = name.rpartition(".")
    out_name = f"{convert(stem)}.{suffix}" if stem else convert(name)
    await msg.reply_document(BufferedInputFile(result, filename=out_name), caption=T["done"])
    await status.delete()


@router.message(F.text)
async def text(msg: Message):
    new = convert(msg.text)
    if new == msg.text:
        await msg.reply(T["same"])
    else:
        await msg.reply(html.escape(new))


@router.message(F.caption)
async def caption(msg: Message):
    await msg.reply(html.escape(convert(msg.caption)))


@router.inline_query()
async def inline(q: InlineQuery):
    src = q.query.strip()
    if not src:
        await q.answer([], switch_pm_text=T["inline_hint"], switch_pm_parameter="inline",
                       cache_time=300, is_personal=False)
        return
    new = convert(src)[:4096]
    rid = hashlib.md5(new.encode()).hexdigest()
    await q.answer([InlineQueryResultArticle(
        id=rid, title=T["inline_title"], description=new[:200],
        input_message_content=InputTextMessageContent(message_text=new, parse_mode=None),
    )], cache_time=0, is_personal=True)


def _load_env():
    env = pathlib.Path(__file__).resolve().parents[1] / ".env"
    if env.exists():
        for line in env.read_text(encoding="utf-8").splitlines():
            if "=" in line and not line.lstrip().startswith("#"):
                k, v = line.split("=", 1)
                os.environ.setdefault(k.strip(), v.strip())


async def main():
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")
    _load_env()
    token = os.environ.get("BOT_TOKEN")
    if not token:
        raise SystemExit("BOT_TOKEN is not set (put it in bot/.env)")
    bot = Bot(token, default=DefaultBotProperties(parse_mode="HTML"))
    dp = Dispatcher()
    dp.include_router(router)
    await dp.start_polling(bot)
