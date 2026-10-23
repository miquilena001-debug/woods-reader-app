#!/usr/bin/env python3
"""
Woods Keymaker — App de escritorio para emitir licencias Pro ECDSA.
Requiere: pip install customtkinter cryptography

Uso:
    python tools/woods_keymaker_gui.py
"""

import base64
import json
import re
import sys
import threading
import tkinter as tk
from datetime import datetime
from pathlib import Path

try:
    import customtkinter as ctk
except ImportError:
    raise SystemExit(
        "Falta 'customtkinter'. Instálala con: python -m pip install customtkinter"
    )

try:
    from cryptography.hazmat.primitives import hashes, serialization
    from cryptography.hazmat.primitives.asymmetric import ec
except ImportError:
    raise SystemExit(
        "Falta 'cryptography'. Instálala con: python -m pip install cryptography"
    )

# ─── Constantes ───────────────────────────────────────────────────────────────
SCRIPT_DIR = Path(__file__).resolve().parent
PRIVATE_KEY_PATH = SCRIPT_DIR / "woods_pro_private.pem"
HISTORY_PATH = SCRIPT_DIR / "license_history.json"

PUBLIC_KEY_BASE64 = (
    "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAElRoZUIgehBObRgMCce5GE4UjPZoR"
    "yL08s3Zp2ctoc4yhdOW/Smk6y2EYuGSbeHrCH59AbVt+EirwoVIU3MFM/A=="
)

INSTALL_CODE_RE = re.compile(r"^WR-[A-F0-9]{4}-[A-F0-9]{4}$")
WR_TOKEN_RE = re.compile(r"WR-[A-F0-9]{4}-[A-F0-9]{4}", re.IGNORECASE)

# Paleta de colores Woods
C_BG = "#0F1F14"
C_SURFACE = "#162318"
C_SURFACE2 = "#1D2E20"
C_GOLD = "#C8943A"
C_GOLD_BRIGHT = "#F0B850"
C_EMERALD = "#3CB371"
C_TEXT = "#E8EDE8"
C_TEXT_DIM = "#8AAA8A"
C_RED = "#EF5350"
C_BORDER = "#2A3D2A"


# ─── Criptografía ─────────────────────────────────────────────────────────────

def _pub_b64(private_key) -> str:
    return base64.b64encode(
        private_key.public_key().public_bytes(
            serialization.Encoding.DER,
            serialization.PublicFormat.SubjectPublicKeyInfo,
        )
    ).decode("ascii")


def load_private_key() -> ec.EllipticCurvePrivateKey:
    if not PRIVATE_KEY_PATH.is_file():
        raise FileNotFoundError(f"Clave privada no encontrada: {PRIVATE_KEY_PATH}")
    key = serialization.load_pem_private_key(PRIVATE_KEY_PATH.read_bytes(), password=None)
    if not isinstance(key, ec.EllipticCurvePrivateKey):
        raise ValueError("El archivo no contiene una clave ECDSA.")
    if _pub_b64(key).replace("\n", "") != PUBLIC_KEY_BASE64.replace("\n", ""):
        raise ValueError("La clave privada no coincide con la clave pública fijada en la app.")
    return key


def sign_install_code(code: str) -> str:
    """Devuelve la licencia PRO-<Base32> para el código WR-XXXX-XXXX dado."""
    normalized = code.strip().upper()
    if not INSTALL_CODE_RE.fullmatch(normalized):
        raise ValueError(f"Formato inválido: '{code}'. Debe ser WR-XXXX-XXXX.")
    key = load_private_key()
    sig = key.sign(normalized.encode("ascii"), ec.ECDSA(hashes.SHA256()))
    encoded = base64.b32encode(sig).decode("ascii").rstrip("=")
    return f"PRO-{encoded}"


def verify_license(install_code: str, license_key: str) -> bool:
    """Verifica una licencia con la clave pública."""
    try:
        normalized_code = install_code.strip().upper()
        if not INSTALL_CODE_RE.fullmatch(normalized_code):
            return False
        normalized_key = license_key.strip().upper()
        if not normalized_key.startswith("PRO-"):
            return False
        encoded = normalized_key[4:].replace("-", "").replace(" ", "")
        # Re-añadir padding Base32
        pad = (8 - len(encoded) % 8) % 8
        sig_bytes = base64.b32decode(encoded + "=" * pad)

        raw = base64.b64decode(PUBLIC_KEY_BASE64)
        from cryptography.hazmat.primitives.asymmetric import utils as asym_utils
        from cryptography.hazmat.backends import default_backend
        pub_key = serialization.load_der_public_key(raw, backend=default_backend())
        pub_key.verify(sig_bytes, normalized_code.encode("ascii"), ec.ECDSA(hashes.SHA256()))
        return True
    except Exception:
        return False


def extract_code_from_text(text: str) -> str:
    """Extrae WR-XXXX-XXXX de texto libre."""
    m = WR_TOKEN_RE.search(text)
    return m.group(0).upper() if m else ""


# ─── Historial ────────────────────────────────────────────────────────────────

def load_history() -> list:
    if HISTORY_PATH.exists():
        try:
            return json.loads(HISTORY_PATH.read_text(encoding="utf-8"))
        except Exception:
            return []
    return []


def save_history(entries: list) -> None:
    HISTORY_PATH.write_text(json.dumps(entries, ensure_ascii=False, indent=2), encoding="utf-8")


def add_history_entry(install_code: str, license_key: str, note: str) -> list:
    entries = load_history()
    entries.insert(0, {
        "id": f"{datetime.now().timestamp():.0f}",
        "install_code": install_code,
        "license_key": license_key,
        "note": note.strip() or "Sin nota",
        "date": datetime.now().strftime("%d/%m/%Y %H:%M"),
    })
    save_history(entries)
    return entries


# ─── Widgets auxiliares ───────────────────────────────────────────────────────

def copy_to_clipboard(root: tk.Tk, text: str) -> None:
    root.clipboard_clear()
    root.clipboard_append(text)
    root.update()


class StatusBadge(ctk.CTkLabel):
    """Etiqueta de estado con colores semáforo."""

    def set_ok(self, msg: str):
        self.configure(text=f"✓  {msg}", text_color=C_EMERALD)

    def set_error(self, msg: str):
        self.configure(text=f"✕  {msg}", text_color=C_RED)

    def set_warn(self, msg: str):
        self.configure(text=f"⚠  {msg}", text_color=C_GOLD)

    def set_neutral(self, msg: str = ""):
        self.configure(text=msg, text_color=C_TEXT_DIM)


# ─── Pantalla: Emitir licencias ───────────────────────────────────────────────

class IssuerTab(ctk.CTkFrame):
    def __init__(self, master, root_window, **kw):
        super().__init__(master, fg_color="transparent", **kw)
        self.root = root_window
        self._build()

    def _build(self):
        self.columnconfigure(0, weight=1)

        # ── Tarjeta de estado de clave ──────────────────────────────────────
        key_card = ctk.CTkFrame(self, fg_color=C_SURFACE, corner_radius=12)
        key_card.grid(row=0, column=0, sticky="ew", padx=20, pady=(20, 10))
        key_card.columnconfigure(0, weight=1)

        ctk.CTkLabel(
            key_card, text="Estado de la clave privada",
            font=ctk.CTkFont(size=14, weight="bold"), text_color=C_TEXT
        ).grid(row=0, column=0, sticky="w", padx=16, pady=(14, 4))

        self._key_status = StatusBadge(key_card, text="", font=ctk.CTkFont(size=13))
        self._key_status.grid(row=1, column=0, sticky="w", padx=16, pady=(0, 14))

        ctk.CTkButton(
            key_card, text="🔄  Recargar clave", width=160, height=32,
            fg_color=C_SURFACE2, hover_color=C_BORDER, text_color=C_TEXT_DIM,
            font=ctk.CTkFont(size=12), command=self._check_key
        ).grid(row=1, column=1, padx=12, pady=(0, 14), sticky="e")

        self._check_key()

        # ── Tarjeta de emisión ───────────────────────────────────────────────
        issue_card = ctk.CTkFrame(self, fg_color=C_SURFACE, corner_radius=12)
        issue_card.grid(row=1, column=0, sticky="ew", padx=20, pady=10)
        issue_card.columnconfigure(0, weight=1)

        ctk.CTkLabel(
            issue_card, text="Código de instalación del cliente",
            font=ctk.CTkFont(size=14, weight="bold"), text_color=C_TEXT
        ).grid(row=0, column=0, columnspan=2, sticky="w", padx=16, pady=(14, 6))

        # Fila campo + botón pegar
        code_row = ctk.CTkFrame(issue_card, fg_color="transparent")
        code_row.grid(row=1, column=0, columnspan=2, sticky="ew", padx=16)
        code_row.columnconfigure(0, weight=1)

        self._code_entry = ctk.CTkEntry(
            code_row, placeholder_text="Ej: WR-A1B2-C3D4",
            font=ctk.CTkFont(family="Courier New", size=16, weight="bold"),
            height=44, corner_radius=10,
            fg_color=C_SURFACE2, border_color=C_BORDER, text_color=C_TEXT
        )
        self._code_entry.grid(row=0, column=0, sticky="ew", padx=(0, 8))
        self._code_entry.bind("<KeyRelease>", lambda e: self._on_code_change())

        ctk.CTkButton(
            code_row, text="📋 Pegar", width=90, height=44,
            fg_color=C_SURFACE2, hover_color=C_BORDER, text_color=C_GOLD,
            font=ctk.CTkFont(size=13), command=self._paste_code
        ).grid(row=0, column=1)

        self._code_status = StatusBadge(
            issue_card, text="Introduce o pega el código WR-XXXX-XXXX del cliente.",
            font=ctk.CTkFont(size=12), text_color=C_TEXT_DIM
        )
        self._code_status.grid(row=2, column=0, columnspan=2, sticky="w", padx=16, pady=(6, 8))

        # Nota opcional
        ctk.CTkLabel(
            issue_card, text="Nota del cliente (opcional)",
            font=ctk.CTkFont(size=12), text_color=C_TEXT_DIM
        ).grid(row=3, column=0, columnspan=2, sticky="w", padx=16, pady=(4, 2))

        self._note_entry = ctk.CTkEntry(
            issue_card, placeholder_text="Ej: Juan García — compra 22/10/2026",
            height=36, corner_radius=10,
            fg_color=C_SURFACE2, border_color=C_BORDER, text_color=C_TEXT,
            font=ctk.CTkFont(size=13)
        )
        self._note_entry.grid(row=4, column=0, columnspan=2, sticky="ew", padx=16, pady=(0, 14))

        # Botón firmar
        self._sign_btn = ctk.CTkButton(
            issue_card, text="⚡  Firmar y emitir licencia Pro",
            height=48, corner_radius=10,
            fg_color=C_GOLD, hover_color=C_GOLD_BRIGHT, text_color="#000000",
            font=ctk.CTkFont(size=15, weight="bold"),
            command=self._sign
        )
        self._sign_btn.grid(row=5, column=0, columnspan=2, sticky="ew", padx=16, pady=(0, 16))

        # ── Tarjeta de resultado ─────────────────────────────────────────────
        self._result_card = ctk.CTkFrame(self, fg_color=C_SURFACE, corner_radius=12)
        self._result_card.grid(row=2, column=0, sticky="ew", padx=20, pady=(0, 20))
        self._result_card.columnconfigure(0, weight=1)
        self._result_card.grid_remove()  # oculta hasta firmar

        ctk.CTkLabel(
            self._result_card, text="✓  LICENCIA EMITIDA",
            font=ctk.CTkFont(size=15, weight="bold"), text_color=C_EMERALD
        ).grid(row=0, column=0, columnspan=2, sticky="w", padx=16, pady=(14, 4))

        self._result_label = ctk.CTkLabel(
            self._result_card, text="",
            font=ctk.CTkFont(family="Courier New", size=13, weight="bold"),
            text_color=C_GOLD_BRIGHT, wraplength=540, justify="left"
        )
        self._result_label.grid(row=1, column=0, columnspan=2, sticky="w", padx=16, pady=(0, 8))

        self._copy_btn = ctk.CTkButton(
            self._result_card, text="📋  Copiar licencia", height=38, corner_radius=8,
            fg_color=C_EMERALD, hover_color="#2E8B57", text_color="#000000",
            font=ctk.CTkFont(size=13, weight="bold"),
            command=self._copy_result
        )
        self._copy_btn.grid(row=2, column=0, sticky="ew", padx=16, pady=(0, 4))

        self._share_label = ctk.CTkLabel(
            self._result_card, text="",
            font=ctk.CTkFont(size=12), text_color=C_TEXT_DIM,
            wraplength=540, justify="left"
        )
        self._share_label.grid(row=3, column=0, columnspan=2, sticky="w", padx=16, pady=(4, 14))

        self._current_license = ""

    # ── Lógica ─────────────────────────────────────────────────────────────

    def _check_key(self):
        try:
            load_private_key()
            self._key_status.set_ok(f"Clave P-256 cargada  ·  {PRIVATE_KEY_PATH.name}")
        except FileNotFoundError:
            self._key_status.set_error(
                f"No encontrada: {PRIVATE_KEY_PATH}\n"
                "Crea o restaura woods_pro_private.pem en la carpeta tools/."
            )
        except Exception as exc:
            self._key_status.set_error(str(exc))

    def _on_code_change(self):
        raw = self._code_entry.get()
        extracted = extract_code_from_text(raw)
        if extracted:
            self._code_status.set_ok(f"Código válido: {extracted}")
        elif raw.strip():
            self._code_status.set_error("Formato inválido — debe ser WR-XXXX-XXXX")
        else:
            self._code_status.set_neutral("Introduce o pega el código WR-XXXX-XXXX del cliente.")

    def _paste_code(self):
        try:
            text = self.root.clipboard_get()
            extracted = extract_code_from_text(text)
            if extracted:
                self._code_entry.delete(0, "end")
                self._code_entry.insert(0, extracted)
                self._on_code_change()
            else:
                self._code_status.set_warn("No se detectó un código WR-XXXX-XXXX en el portapapeles.")
        except tk.TclError:
            self._code_status.set_warn("El portapapeles está vacío.")

    def _sign(self):
        raw = self._code_entry.get()
        code = extract_code_from_text(raw) or raw.strip().upper()

        self._sign_btn.configure(state="disabled", text="Firmando…")
        self._result_card.grid_remove()

        def worker():
            try:
                license_key = sign_install_code(code)
                note = self._note_entry.get()
                add_history_entry(code, license_key, note)
                self.after(0, lambda: self._show_result(code, license_key, note))
            except Exception as exc:
                self.after(0, lambda: self._show_error(str(exc)))

        threading.Thread(target=worker, daemon=True).start()

    def _show_result(self, code: str, license_key: str, note: str):
        self._current_license = license_key
        self._result_label.configure(text=license_key)
        share_msg = (
            f"Tu licencia Woods Reader Pro:\n"
            f"Código de instalación: {code}\n"
            f"Clave de activación: {license_key}\n"
            f"Introduce la clave en Configuración → Woods Pro → Activar."
        )
        self._share_label.configure(text=share_msg)
        self._result_card.grid()
        self._sign_btn.configure(state="normal", text="⚡  Firmar y emitir licencia Pro")
        copy_to_clipboard(self.root, license_key)
        self._code_status.set_ok(f"Licencia emitida y copiada para {code}")

    def _show_error(self, msg: str):
        self._code_status.set_error(msg)
        self._sign_btn.configure(state="normal", text="⚡  Firmar y emitir licencia Pro")

    def _copy_result(self):
        if self._current_license:
            copy_to_clipboard(self.root, self._current_license)
            self._copy_btn.configure(text="✓  ¡Copiado!")
            self.after(2000, lambda: self._copy_btn.configure(text="📋  Copiar licencia"))


# ─── Pantalla: Verificador ─────────────────────────────────────────────────────

class VerifierTab(ctk.CTkFrame):
    def __init__(self, master, root_window, **kw):
        super().__init__(master, fg_color="transparent", **kw)
        self.root = root_window
        self._build()

    def _build(self):
        self.columnconfigure(0, weight=1)

        card = ctk.CTkFrame(self, fg_color=C_SURFACE, corner_radius=12)
        card.grid(row=0, column=0, sticky="ew", padx=20, pady=20)
        card.columnconfigure(0, weight=1)

        ctk.CTkLabel(
            card, text="Verificador Criptográfico",
            font=ctk.CTkFont(size=16, weight="bold"), text_color=C_TEXT
        ).grid(row=0, column=0, sticky="w", padx=16, pady=(14, 2))

        ctk.CTkLabel(
            card,
            text="Comprueba si una clave es auténtica para un dispositivo antes de dar soporte.",
            font=ctk.CTkFont(size=12), text_color=C_TEXT_DIM
        ).grid(row=1, column=0, sticky="w", padx=16, pady=(0, 12))

        ctk.CTkLabel(card, text="Código de instalación", font=ctk.CTkFont(size=12), text_color=C_TEXT_DIM).grid(
            row=2, column=0, sticky="w", padx=16, pady=(0, 2))
        self._code_entry = ctk.CTkEntry(
            card, placeholder_text="WR-XXXX-XXXX", height=40, corner_radius=10,
            fg_color=C_SURFACE2, border_color=C_BORDER, text_color=C_TEXT,
            font=ctk.CTkFont(family="Courier New", size=14)
        )
        self._code_entry.grid(row=3, column=0, sticky="ew", padx=16, pady=(0, 10))

        ctk.CTkLabel(card, text="Clave Pro firmada", font=ctk.CTkFont(size=12), text_color=C_TEXT_DIM).grid(
            row=4, column=0, sticky="w", padx=16, pady=(0, 2))
        self._key_entry = ctk.CTkEntry(
            card, placeholder_text="PRO-...", height=40, corner_radius=10,
            fg_color=C_SURFACE2, border_color=C_BORDER, text_color=C_TEXT,
            font=ctk.CTkFont(family="Courier New", size=13)
        )
        self._key_entry.grid(row=5, column=0, sticky="ew", padx=16, pady=(0, 12))

        ctk.CTkButton(
            card, text="Verificar autenticidad", height=44, corner_radius=10,
            fg_color=C_GOLD, hover_color=C_GOLD_BRIGHT, text_color="#000000",
            font=ctk.CTkFont(size=14, weight="bold"),
            command=self._verify
        ).grid(row=6, column=0, sticky="ew", padx=16, pady=(0, 16))

        self._result_frame = ctk.CTkFrame(self, fg_color=C_SURFACE, corner_radius=12)
        self._result_frame.grid(row=1, column=0, sticky="ew", padx=20, pady=(0, 20))
        self._result_frame.columnconfigure(0, weight=1)
        self._result_frame.grid_remove()

        self._result_icon = ctk.CTkLabel(self._result_frame, text="", font=ctk.CTkFont(size=36))
        self._result_icon.grid(row=0, column=0, pady=(16, 4))

        self._result_title = ctk.CTkLabel(self._result_frame, text="", font=ctk.CTkFont(size=16, weight="bold"))
        self._result_title.grid(row=1, column=0)

        self._result_body = ctk.CTkLabel(
            self._result_frame, text="", font=ctk.CTkFont(size=13),
            text_color=C_TEXT_DIM, wraplength=500
        )
        self._result_body.grid(row=2, column=0, pady=(4, 16))

    def _verify(self):
        code = self._code_entry.get().strip()
        key = self._key_entry.get().strip()
        if not code or not key:
            return
        valid = verify_license(code, key)
        self._result_frame.grid()
        if valid:
            self._result_icon.configure(text="✅", text_color=C_EMERALD)
            self._result_title.configure(text="LICENCIA AUTÉNTICA", text_color=C_EMERALD)
            self._result_body.configure(
                text="La firma ECDSA coincide con la clave pública oficial y este dispositivo."
            )
            self._result_frame.configure(fg_color="#132A17")
        else:
            self._result_icon.configure(text="❌", text_color=C_RED)
            self._result_title.configure(text="LICENCIA INVÁLIDA", text_color=C_RED)
            self._result_body.configure(
                text="Esta clave no corresponde al código de instalación o ha sido alterada."
            )
            self._result_frame.configure(fg_color="#331616")


# ─── Pantalla: Historial ───────────────────────────────────────────────────────

class HistoryTab(ctk.CTkFrame):
    def __init__(self, master, root_window, **kw):
        super().__init__(master, fg_color="transparent", **kw)
        self.root = root_window
        self._build()

    def _build(self):
        self.columnconfigure(0, weight=1)
        self.rowconfigure(1, weight=1)

        # Barra superior
        top = ctk.CTkFrame(self, fg_color="transparent")
        top.grid(row=0, column=0, sticky="ew", padx=20, pady=(16, 8))
        top.columnconfigure(0, weight=1)

        ctk.CTkLabel(top, text="Historial de licencias emitidas",
                     font=ctk.CTkFont(size=15, weight="bold"), text_color=C_TEXT
                     ).grid(row=0, column=0, sticky="w")

        btn_row = ctk.CTkFrame(top, fg_color="transparent")
        btn_row.grid(row=0, column=1, sticky="e")

        ctk.CTkButton(
            btn_row, text="🔄 Actualizar", width=110, height=32,
            fg_color=C_SURFACE2, hover_color=C_BORDER, text_color=C_TEXT_DIM,
            font=ctk.CTkFont(size=12), command=self.refresh
        ).grid(row=0, column=0, padx=(0, 8))

        ctk.CTkButton(
            btn_row, text="🗑 Vaciar todo", width=110, height=32,
            fg_color=C_SURFACE2, hover_color="#4A1010", text_color=C_RED,
            font=ctk.CTkFont(size=12), command=self._clear_all
        ).grid(row=0, column=1)

        # Scroll frame
        self._scroll = ctk.CTkScrollableFrame(self, fg_color="transparent")
        self._scroll.grid(row=1, column=0, sticky="nsew", padx=20, pady=(0, 16))
        self._scroll.columnconfigure(0, weight=1)

        self.refresh()

    def refresh(self):
        for widget in self._scroll.winfo_children():
            widget.destroy()

        entries = load_history()
        if not entries:
            ctk.CTkLabel(
                self._scroll, text="No hay licencias en el historial.",
                font=ctk.CTkFont(size=14), text_color=C_TEXT_DIM
            ).grid(row=0, column=0, pady=40)
            return

        for i, entry in enumerate(entries):
            self._entry_card(entry, row=i)

    def _entry_card(self, entry: dict, row: int):
        card = ctk.CTkFrame(self._scroll, fg_color=C_SURFACE, corner_radius=12)
        card.grid(row=row, column=0, sticky="ew", pady=(0, 10))
        card.columnconfigure(0, weight=1)

        top_row = ctk.CTkFrame(card, fg_color="transparent")
        top_row.grid(row=0, column=0, sticky="ew", padx=14, pady=(12, 4))
        top_row.columnconfigure(0, weight=1)

        ctk.CTkLabel(
            top_row, text=entry.get("note", "Sin nota"),
            font=ctk.CTkFont(size=14, weight="bold"), text_color=C_TEXT
        ).grid(row=0, column=0, sticky="w")

        ctk.CTkLabel(
            top_row, text=entry.get("date", ""),
            font=ctk.CTkFont(size=11), text_color=C_TEXT_DIM
        ).grid(row=0, column=1, sticky="e")

        ctk.CTkLabel(
            card,
            text=f"Dispositivo: {entry.get('install_code', '—')}",
            font=ctk.CTkFont(size=12), text_color=C_TEXT_DIM
        ).grid(row=1, column=0, sticky="w", padx=14, pady=(0, 6))

        key_box = ctk.CTkFrame(card, fg_color=C_SURFACE2, corner_radius=8)
        key_box.grid(row=2, column=0, sticky="ew", padx=14, pady=(0, 8))
        key_box.columnconfigure(0, weight=1)

        ctk.CTkLabel(
            key_box, text=entry.get("license_key", ""),
            font=ctk.CTkFont(family="Courier New", size=12, weight="bold"),
            text_color=C_GOLD_BRIGHT, wraplength=520, justify="left"
        ).grid(row=0, column=0, sticky="w", padx=10, pady=8)

        btn_row = ctk.CTkFrame(card, fg_color="transparent")
        btn_row.grid(row=3, column=0, sticky="e", padx=14, pady=(0, 10))

        lic = entry.get("license_key", "")
        code = entry.get("install_code", "")

        ctk.CTkButton(
            btn_row, text="📋 Copiar clave", width=120, height=30,
            fg_color=C_SURFACE2, hover_color=C_BORDER, text_color=C_GOLD,
            font=ctk.CTkFont(size=12),
            command=lambda k=lic: copy_to_clipboard(self.root, k)
        ).grid(row=0, column=0, padx=(0, 6))

        share_text = (
            f"Tu licencia Woods Reader Pro:\n"
            f"Código de instalación: {code}\n"
            f"Clave de activación: {lic}\n"
            f"Introduce la clave en Configuración → Woods Pro → Activar."
        )
        ctk.CTkButton(
            btn_row, text="💬 Copiar mensaje", width=130, height=30,
            fg_color=C_SURFACE2, hover_color=C_BORDER, text_color=C_TEXT_DIM,
            font=ctk.CTkFont(size=12),
            command=lambda m=share_text: copy_to_clipboard(self.root, m)
        ).grid(row=0, column=1, padx=(0, 6))

        ctk.CTkButton(
            btn_row, text="🗑", width=32, height=30,
            fg_color=C_SURFACE2, hover_color="#4A1010", text_color=C_RED,
            font=ctk.CTkFont(size=14),
            command=lambda eid=entry.get("id"): self._delete(eid)
        ).grid(row=0, column=2)

    def _delete(self, entry_id: str):
        entries = [e for e in load_history() if e.get("id") != entry_id]
        save_history(entries)
        self.refresh()

    def _clear_all(self):
        save_history([])
        self.refresh()


# ─── Ventana principal ─────────────────────────────────────────────────────────

class WoodsKeymakerApp(ctk.CTk):
    def __init__(self):
        super().__init__()
        ctk.set_appearance_mode("dark")
        ctk.set_default_color_theme("dark-blue")

        self.title("🌲 Woods Keymaker  —  Admin de Licencias Pro")
        self.geometry("640x700")
        self.minsize(560, 540)
        self.configure(fg_color=C_BG)

        self._build_header()
        self._build_tabs()

    def _build_header(self):
        header = ctk.CTkFrame(self, fg_color=C_SURFACE, corner_radius=0, height=56)
        header.pack(fill="x")
        header.pack_propagate(False)

        ctk.CTkLabel(
            header, text="🌲 Woods Keymaker",
            font=ctk.CTkFont(size=18, weight="bold"), text_color=C_TEXT
        ).pack(side="left", padx=20, pady=12)

        badge = ctk.CTkLabel(
            header, text="PRO ADMIN",
            font=ctk.CTkFont(size=10, weight="bold"),
            text_color=C_GOLD_BRIGHT,
            fg_color=C_SURFACE2, corner_radius=6,
            padx=8, pady=2
        )
        badge.pack(side="left", pady=16)

        ctk.CTkLabel(
            header, text="ECDSA P-256  ·  Clave privada local",
            font=ctk.CTkFont(size=11), text_color=C_TEXT_DIM
        ).pack(side="right", padx=20)

    def _build_tabs(self):
        tabview = ctk.CTkTabview(
            self, fg_color=C_BG,
            segmented_button_fg_color=C_SURFACE,
            segmented_button_selected_color=C_GOLD,
            segmented_button_selected_hover_color=C_GOLD_BRIGHT,
            segmented_button_unselected_color=C_SURFACE,
            segmented_button_unselected_hover_color=C_SURFACE2,
            text_color=C_TEXT,
            text_color_disabled=C_TEXT_DIM
        )
        tabview.pack(fill="both", expand=True, padx=0, pady=0)

        tabview.add("⚡ Emitir")
        tabview.add("✔ Verificar")
        tabview.add("📋 Historial")

        IssuerTab(tabview.tab("⚡ Emitir"), self).pack(fill="both", expand=True)
        VerifierTab(tabview.tab("✔ Verificar"), self).pack(fill="both", expand=True)

        self._history_tab = HistoryTab(tabview.tab("📋 Historial"), self)
        self._history_tab.pack(fill="both", expand=True)

        # Refrescar historial al cambiar a esa pestaña
        tabview._segmented_button.configure(
            command=lambda tab: self._history_tab.refresh() if tab == "📋 Historial" else None
        )


# ─── Entrada ──────────────────────────────────────────────────────────────────

if __name__ == "__main__":
    if sys.platform == "win32" and hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    app = WoodsKeymakerApp()
    app.mainloop()
