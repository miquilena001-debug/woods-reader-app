$ErrorActionPreference = "Stop"

$AppDir = "app"
$LibsDir = "$AppDir\libs"
$AssetsDir = "$AppDir\src\main\assets\tts"

# Crear directorios si no existen
New-Item -ItemType Directory -Force -Path $LibsDir | Out-Null
New-Item -ItemType Directory -Force -Path $AssetsDir | Out-Null

# 1. Descargar sherpa-onnx.aar
$AarUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.10.45/sherpa-onnx-1.10.45.aar"
$AarDest = "$LibsDir\sherpa-onnx.aar"

if (-not (Test-Path $AarDest)) {
    Write-Host "Descargando sherpa-onnx.aar (11MB)..."
    Invoke-WebRequest -Uri $AarUrl -OutFile $AarDest
} else {
    Write-Host "sherpa-onnx.aar ya existe."
}

# 2. Descargar Modelo Acústico en Español (Davefx)
$ModelUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-es_ES-davefx-medium.tar.bz2"
$ModelTar = "vits-piper-es_ES-davefx-medium.tar.bz2"
$ModelName = "vits-piper-es_ES-davefx-medium"
$ModelDestPath = "$AssetsDir\$ModelName"

if (-not (Test-Path $ModelDestPath)) {
    Write-Host "Descargando Modelo VITS es_ES-davefx-medium (~35MB)..."
    Invoke-WebRequest -Uri $ModelUrl -OutFile $ModelTar
    
    Write-Host "Extrayendo modelo..."
    # 'tar' viene incluido en Windows 10/11
    tar -xjf $ModelTar -C $AssetsDir
    
    Write-Host "Limpiando archivos temporales..."
    Remove-Item $ModelTar -Force
} else {
    Write-Host "El modelo $ModelName ya existe en assets."
}

Write-Host "¡Descarga y configuración completada con éxito!"
