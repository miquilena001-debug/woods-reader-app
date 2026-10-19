import hmac
import hashlib
import sys

if hasattr(sys.stdout, "reconfigure"):
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    except Exception:
        pass

XOR_SEED = [
    0x0D, 0x35, 0x35, 0x3E, 0x29, 0x05, 0x09, 0x2E,
    0x2F, 0x3E, 0x33, 0x35, 0x05, 0x17, 0x3B, 0x29,
    0x2E, 0x3F, 0x28, 0x05, 0x68, 0x6A, 0x68, 0x6C
]

def get_secret() -> bytes:
    return bytes([b ^ 0x5A for b in XOR_SEED])

def generate_license(device_code: str) -> str:
    clean_id = device_code.strip().upper()
    digest = hmac.new(get_secret(), clean_id.encode("utf-8"), hashlib.sha256).hexdigest().upper()[:12]
    return f"PRO-{digest[0:4]}-{digest[4:8]}-{digest[8:12]}"

if __name__ == "__main__":
    print("=== GENERADOR OFICIAL DE LICENCIAS WOODS READER PRO ===")
    code = sys.argv[1] if len(sys.argv) > 1 else input("Ingresa el ID de Dispositivo del cliente (ej. WR-4F9A-82C1): ")
    print("\n[OK] CLAVE DE ACTIVACION VITALICIA:")
    print(generate_license(code))
