@echo off
title Bot Generador de Licencias Telegram - Woods Reader PRO
color 0A
cd /d "%~dp0"

echo ========================================================
echo   WOODS READER PRO - BOT DE TELEGRAM AUTOMATICO
echo ========================================================
echo.

if not exist woods_pro_private.pem (
    echo [ERROR] No se encontro la clave privada woods_pro_private.pem.
    echo Asegurate de ejecutar primero woods_pro_keygen.py o Abrir_Keymaker.bat
    pause
    exit /b 1
)

python woods_telegram_bot.py
if errorlevel 1 (
    echo.
    echo [ERROR] Ocurrio un error al ejecutar el bot.
    pause
)
