#!/usr/bin/env python3
"""Generate Woods Reader Pro ECDSA licenses using a local, non-APK private key."""

import argparse
import base64
import os
import re
import sys
from pathlib import Path

try:
    from cryptography.hazmat.primitives import hashes, serialization
    from cryptography.hazmat.primitives.asymmetric import ec
except ImportError as exc:
    raise SystemExit(
        "Falta la dependencia 'cryptography'. Instálala con: "
        "python -m pip install cryptography"
    ) from exc


PUBLIC_KEY_BASE64 = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAElRoZUIgehBObRgMCce5GE4UjPZoRyL08s3Zp2ctoc4yhdOW/Smk6y2EYuGSbeHrCH59AbVt+EirwoVIU3MFM/A=="
INSTALL_CODE_PATTERN = re.compile(r"^WR-[A-F0-9]{4}-[A-F0-9]{4}$")
SCRIPT_DIR = Path(__file__).resolve().parent
PRIVATE_KEY_PATH = SCRIPT_DIR / "woods_pro_private.pem"


def normalized_install_code(value: str) -> str:
    code = value.strip().upper()
    if not INSTALL_CODE_PATTERN.fullmatch(code):
        raise ValueError("El código debe tener el formato WR-XXXX-XXXX en hexadecimal.")
    return code


def public_key_base64(private_key) -> str:
    encoded = private_key.public_key().public_bytes(
        encoding=serialization.Encoding.DER,
        format=serialization.PublicFormat.SubjectPublicKeyInfo,
    )
    return base64.b64encode(encoded).decode("ascii")


def initialize_key() -> None:
    if PRIVATE_KEY_PATH.exists():
        raise SystemExit(
            f"Ya existe una clave privada en {PRIVATE_KEY_PATH}. "
            "No se sobrescribió."
        )

    private_key = ec.generate_private_key(ec.SECP256R1())
    exported_public_key = public_key_base64(private_key)
    if PUBLIC_KEY_BASE64 and exported_public_key != PUBLIC_KEY_BASE64:
        raise SystemExit(
            "La clave pública del APK ya está fijada. No se puede crear otra clave "
            "privada; restaura la copia de seguridad de woods_pro_private.pem."
        )

    pem = private_key.private_bytes(
        encoding=serialization.Encoding.PEM,
        format=serialization.PrivateFormat.PKCS8,
        encryption_algorithm=serialization.NoEncryption(),
    )
    descriptor = os.open(
        PRIVATE_KEY_PATH,
        os.O_WRONLY | os.O_CREAT | os.O_EXCL,
        0o600,
    )
    with os.fdopen(descriptor, "wb") as private_file:
        private_file.write(pem)
    try:
        PRIVATE_KEY_PATH.chmod(0o600)
    except OSError:
        pass

    print(f"Clave privada creada localmente: {PRIVATE_KEY_PATH}")
    print(
        "El archivo no está cifrado: restringe su acceso local, no lo compartas ni lo subas, "
        "y guarda una copia de seguridad segura."
    )
    print("Clave pública X.509 en Base64 para fijar en la app:")
    print(exported_public_key)
    print("Fija la clave pública en PUBLIC_KEY_BASE64 y ProLicenseManager antes de firmar.")


def load_private_key(path: Path):
    if not path.is_file():
        raise SystemExit(
            f"No se encontró la clave privada: {path}. "
            "No generes otra si el APK ya está publicado; restaura la copia de seguridad."
        )
    try:
        key = serialization.load_pem_private_key(path.read_bytes(), password=None)
    except (ValueError, TypeError) as exc:
        raise SystemExit(f"No se pudo leer la clave privada: {exc}") from exc
    if not isinstance(key, ec.EllipticCurvePrivateKey) or not isinstance(
        key.curve, ec.SECP256R1
    ):
        raise SystemExit("La clave privada debe ser ECDSA sobre la curva P-256.")
    if public_key_base64(key) != PUBLIC_KEY_BASE64:
        raise SystemExit(
            "La clave privada no corresponde a la clave pública fijada en la app."
        )
    return key


def sign_license(install_code: str, key_path: Path = PRIVATE_KEY_PATH) -> str:
    code = normalized_install_code(install_code)
    private_key = load_private_key(key_path)
    signature = private_key.sign(code.encode("ascii"), ec.ECDSA(hashes.SHA256()))
    encoded_signature = base64.b32encode(signature).decode("ascii").rstrip("=")
    return f"PRO-{encoded_signature}"


def generate_license(install_code: str, key_path: Path = PRIVATE_KEY_PATH) -> str:
    """Alias compatible con bots y servicios automáticos."""
    return sign_license(install_code, key_path)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    subparsers = parser.add_subparsers(dest="command", required=True)
    subparsers.add_parser("init", help="Crear la clave privada local una sola vez")
    sign_parser = subparsers.add_parser("sign", help="Firmar un código de instalación")
    sign_parser.add_argument("install_code", help="Código WR-XXXX-XXXX")
    sign_parser.add_argument(
        "--private-key",
        type=Path,
        default=PRIVATE_KEY_PATH,
        help=f"Ruta de la clave privada (predeterminada: {PRIVATE_KEY_PATH})",
    )

    args = parser.parse_args()
    if args.command == "init":
        initialize_key()
    elif args.command == "sign":
        try:
            print(sign_license(args.install_code, args.private_key))
        except ValueError as exc:
            raise SystemExit(str(exc)) from exc


if __name__ == "__main__":
    if sys.platform == "win32" and hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    main()
