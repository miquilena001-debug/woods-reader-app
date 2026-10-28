# tools/check_dona.ps1
# Script de verificacion de sincronizacion entre el codigo fuente (*.kt) y dona.md.
# Uso: .\tools\check_dona.ps1

Write-Host "Verificando sincronizacion de dona.md con archivos Kotlin..." -ForegroundColor Cyan

if (-not (Test-Path "dona.md")) {
    Write-Error "No se encontro el archivo dona.md en el directorio actual."
    exit 1
}

$donaContent = Get-Content -Raw "dona.md" -Encoding UTF8

# Obtener todos los archivos .kt seguidos por Git
$gitFiles = git ls-files "*.kt" | ForEach-Object { $_.Replace('\', '/') }

$missingInDona = @()
$orphansInDona = @()

# Extraer todos los encabezados ### `...` y entradas en tablas | `...`
$headingMatches = [regex]::Matches($donaContent, '### `([^`]+\.kt)`') | ForEach-Object { $_.Groups[1].Value.Replace('\', '/').Trim() }
$tableMatches = [regex]::Matches($donaContent, '\|\s*`([^`\n]+\.kt)`\s*\|') | ForEach-Object { $_.Groups[1].Value.Replace('\', '/').Trim() }

$allDonaRefs = @($headingMatches) + @($tableMatches)

# 1. Comprobar archivos en Git ausentes en dona.md
foreach ($gf in $gitFiles) {
    $baseName = Split-Path $gf -Leaf
    $matched = $false
    foreach ($ref in $allDonaRefs) {
        if ($ref -eq $gf -or $gf.EndsWith("/$ref") -or $gf.EndsWith($ref) -or $ref -eq $baseName) {
            $matched = $true
            break
        }
    }
    if (-not $matched) {
        $missingInDona += $gf
    }
}

# 2. Comprobar entradas en dona.md que no existen en Git
foreach ($ref in $headingMatches) {
    $matched = $false
    foreach ($gf in $gitFiles) {
        if ($ref -eq $gf -or $gf.EndsWith("/$ref") -or $gf.EndsWith($ref)) {
            $matched = $true
            break
        }
    }
    if (-not $matched) {
        $orphansInDona += $ref
    }
}

Write-Host "--------------------------------------------------------"
Write-Host "Total archivos .kt en Git: $($gitFiles.Count)" -ForegroundColor White
Write-Host "Archivos ausentes en dona.md: $($missingInDona.Count)" -ForegroundColor $(if ($missingInDona.Count -eq 0) { "Green" } else { "Red" })
Write-Host "Entradas huerfanas en dona.md: $($orphansInDona.Count)" -ForegroundColor $(if ($orphansInDona.Count -eq 0) { "Green" } else { "Red" })
Write-Host "--------------------------------------------------------"

if ($missingInDona.Count -gt 0) {
    Write-Host "[ALERTA] Archivos presentes en Git pero ausentes en dona.md:" -ForegroundColor Yellow
    foreach ($m in $missingInDona) {
        Write-Host "  + $m" -ForegroundColor Yellow
    }
}

if ($orphansInDona.Count -gt 0) {
    Write-Host "[ALERTA] Entradas en dona.md que no corresponden a archivos existentes:" -ForegroundColor Red
    foreach ($o in $orphansInDona) {
        Write-Host "  - $o" -ForegroundColor Red
    }
}

if ($missingInDona.Count -eq 0 -and $orphansInDona.Count -eq 0) {
    Write-Host "[EXITO] dona.md esta 100% sincronizado con el repositorio!" -ForegroundColor Green
    exit 0
} else {
    Write-Host "[AVISO] Se detectaron desfases en dona.md. Por favor actualiza el indice." -ForegroundColor Yellow
    exit 1
}
