param (
    [string]$ImagePath
)

Add-Type -AssemblyName System.Drawing

$img = [System.Drawing.Image]::FromFile($ImagePath)
$basePath = "app\src\main\res"

# Tamaños para ícono legacy (ic_launcher.png y ic_launcher_round.png)
$legacySizes = @(48, 72, 96, 144, 192)
# Tamaños para ícono adaptativo (ic_launcher_foreground.png)
$adaptiveSizes = @(108, 162, 216, 324, 432)
$densities = @("mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi")

for ($i = 0; $i -lt $densities.Length; $i++) {
    $folder = "mipmap-" + $densities[$i]
    $fullDir = Join-Path $basePath $folder
    if (!(Test-Path $fullDir)) {
        New-Item -ItemType Directory -Force -Path $fullDir | Out-Null
    }

    # 1. Legacy Square (Transparente para que el sistema ponga el fondo si quiere)
    $sLeg = $legacySizes[$i]
    $bmpLeg = New-Object System.Drawing.Bitmap($sLeg, $sLeg)
    $gLeg = [System.Drawing.Graphics]::FromImage($bmpLeg)
    $gLeg.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $gLeg.Clear([System.Drawing.Color]::Transparent)
    # Centrar un poco más para que no toque los bordes (80% del tamaño)
    $legPad = [math]::Round($sLeg * 0.1)
    $legSize = $sLeg - (2 * $legPad)
    $gLeg.DrawImage($img, $legPad, $legPad, $legSize, $legSize)
    $gLeg.Dispose()
    $bmpLeg.Save((Join-Path $fullDir "ic_launcher.png"), [System.Drawing.Imaging.ImageFormat]::Png)
    $bmpLeg.Dispose()

    # 2. Legacy Round (Recorte circular sobre transparente)
    $bmpRound = New-Object System.Drawing.Bitmap($sLeg, $sLeg)
    $gRound = [System.Drawing.Graphics]::FromImage($bmpRound)
    $gRound.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $gRound.Clear([System.Drawing.Color]::Transparent)
    $pathRound = New-Object System.Drawing.Drawing2D.GraphicsPath
    $pathRound.AddEllipse(0, 0, $sLeg, $sLeg)
    $gRound.SetClip($pathRound)
    $gRound.DrawImage($img, $legPad, $legPad, $legSize, $legSize)
    $gRound.Dispose()
    $bmpRound.Save((Join-Path $fullDir "ic_launcher_round.png"), [System.Drawing.Imaging.ImageFormat]::Png)
    $bmpRound.Dispose()

    # 3. Adaptive Foreground
    $sAda = $adaptiveSizes[$i]
    $bmpAda = New-Object System.Drawing.Bitmap($sAda, $sAda)
    $gAda = [System.Drawing.Graphics]::FromImage($bmpAda)
    $gAda.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    # Fondo transparente para el foreground (el fondo lo da ic_launcher_background.xml)
    $gAda.Clear([System.Drawing.Color]::Transparent)
    
    # Para asegurar que la imagen (que casi ocupa todo el cuadro original) no se corte 
    # por la máscara circular de Android (safe zone es diámetro 72dp de 108dp = 66%), 
    # la escalaremos a un 66% y la centraremos.
    $drawSize = [math]::Round($sAda * 0.66)
    $offset = [math]::Round(($sAda - $drawSize) / 2)
    $gAda.DrawImage($img, $offset, $offset, $drawSize, $drawSize)
    $gAda.Dispose()
    $bmpAda.Save((Join-Path $fullDir "ic_launcher_foreground.png"), [System.Drawing.Imaging.ImageFormat]::Png)
    $bmpAda.Dispose()
}

$img.Dispose()
Write-Host "Icons generated successfully in res/mipmap-* folders."
