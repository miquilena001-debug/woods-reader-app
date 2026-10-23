#!/usr/bin/env python3
"""Bot de Telegram para generación instantánea de licencias Woods Reader PRO."""

import json
import os
import re
import sys
import time
import urllib.parse
import urllib.request
from pathlib import Path

# Añadir el directorio tools al path para importar woods_pro_keygen
SCRIPT_DIR = Path(__file__).resolve().parent
sys.path.insert(0, str(SCRIPT_DIR))

try:
    from woods_pro_keygen import generate_license
except ImportError:
    from tools.woods_pro_keygen import generate_license

# 1. Configuración de credenciales (vía variables de entorno o reemplazo directo)
BOT_TOKEN = os.environ.get("WOODS_BOT_TOKEN", "8807302911:AAEiL0JNCmzV8Bk3ohED4QwvYDa5IQ4uCXc").strip()
ADMIN_USER_ID = int(os.environ.get("WOODS_ADMIN_ID", "0"))

DEVICE_ID_REGEX = re.compile(r"\b(WR-[A-F0-9]{4}-[A-F0-9]{4})\b", re.IGNORECASE)


def send_message(chat_id: int, text: str) -> None:
    """Envía un mensaje al chat especificado usando la API HTTP de Telegram."""
    if not BOT_TOKEN or BOT_TOKEN == "PEGA_AQUI_TU_TOKEN_DE_BOTFATHER":
        print(f"[WARN] BOT_TOKEN no configurado. Mensaje simulado a {chat_id}:\n{text}")
        return

    url = f"https://api.telegram.org/bot{BOT_TOKEN}/sendMessage"
    payload = json.dumps({
        "chat_id": chat_id,
        "text": text,
        "parse_mode": "Markdown"
    }).encode("utf-8")

    req = urllib.request.Request(
        url,
        data=payload,
        headers={"Content-Type": "application/json"}
    )
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            pass
    except Exception as exc:
        print(f"[ERROR] Error al enviar mensaje Telegram: {exc}", file=sys.stderr)


def main() -> None:
    if sys.platform == "win32" and hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")

    print("🤖 Bot Generador Woods Reader PRO activo...")
    if not BOT_TOKEN or BOT_TOKEN == "PEGA_AQUI_TU_TOKEN_DE_BOTFATHER":
        print("⚠️ AVISO: Debes configurar la variable de entorno WOODS_BOT_TOKEN o editar BOT_TOKEN en tools/woods_telegram_bot.py.")
    if ADMIN_USER_ID == 0:
        print("⚠️ AVISO: WOODS_ADMIN_ID está en 0 (cualquiera podrá generar claves hasta que configures tu ID).")

    offset = 0
    while True:
        try:
            if not BOT_TOKEN or BOT_TOKEN == "PEGA_AQUI_TU_TOKEN_DE_BOTFATHER":
                time.sleep(10)
                continue

            url = f"https://api.telegram.org/bot{BOT_TOKEN}/getUpdates?timeout=30&offset={offset}"
            req = urllib.request.Request(url)
            with urllib.request.urlopen(req, timeout=35) as resp:
                data = json.loads(resp.read().decode("utf-8"))

            for update in data.get("result", []):
                offset = update["update_id"] + 1
                msg = update.get("message", {})
                chat_id = msg.get("chat", {}).get("id")
                user = msg.get("from", {})
                user_id = user.get("id")
                text = msg.get("text", "")

                if not chat_id:
                    continue

                # Control de autorización
                if ADMIN_USER_ID != 0 and user_id != ADMIN_USER_ID:
                    send_message(chat_id, f"⛔ *No autorizado.* Tu ID de usuario Telegram es: `{user_id}`")
                    continue

                # Búsqueda del código de instalación del cliente
                match = DEVICE_ID_REGEX.search(text)
                if match:
                    device_code = match.group(1).upper()
                    try:
                        pro_key = generate_license(device_code)
                        reply = (
                            f"✅ *Licencia Woods Reader PRO Generada*\n\n"
                            f"📱 *Dispositivo:* `{device_code}`\n"
                            f"🔑 *Clave Vitalicia:* `{pro_key}`\n\n"
                            f"_(Toca la clave para copiarla y enviársela al cliente)_"
                        )
                        send_message(chat_id, reply)
                    except Exception as err:
                        send_message(chat_id, f"❌ Error al generar licencia para `{device_code}`: {err}")
                else:
                    send_message(
                        chat_id,
                        "👋 *Bot Generador Woods Reader PRO*\n\n"
                        "Envíame o reenvíame cualquier mensaje que contenga el código del dispositivo del cliente (ejemplo: `WR-4F9A-82C1`)."
                    )
        except Exception as e:
            # Esperar brevemente ante cortes de red o timeout
            time.sleep(3)


if __name__ == "__main__":
    main()
