# Builds the Play Store listing images from raw phone captures (1080 x 2160, demo-mode status bar):
# captioned 1080 x 1920 screenshots and the 1024 x 500 feature graphic, in the app's colours and font.
# Usage: powershell -File render-store.ps1 -Shots <folder with air/dpad/touch/type/disc.png> -Out <docs/store>
param([string]$Shots, [string]$Out)
Add-Type -AssemblyName System.Drawing
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)

function Col([string]$hex, [int]$a = 255) { $c = [Drawing.ColorTranslator]::FromHtml($hex); [Drawing.Color]::FromArgb($a, $c) }
$charcoal = Col '#161A1F'; $teal = Col '#00C4CC'; $white = Col '#FFFFFF'; $grey = Col '#BFBFBF'; $hair = Col '#3A4049'

$fonts = New-Object Drawing.Text.PrivateFontCollection
$fonts.AddFontFile((Join-Path $root 'app\src\main\res\font\space_grotesk.ttf'))
$family = $fonts.Families[0]

function RoundRect([float]$x, [float]$y, [float]$w, [float]$h, [float]$r) {
    $p = New-Object Drawing.Drawing2D.GraphicsPath
    $d = 2 * $r
    $p.AddArc($x, $y, $d, $d, 180, 90); $p.AddArc($x + $w - $d, $y, $d, $d, 270, 90)
    $p.AddArc($x + $w - $d, $y + $h - $d, $d, $d, 0, 90); $p.AddArc($x, $y + $h - $d, $d, $d, 90, 90)
    $p.CloseFigure(); $p
}

function NewCanvas([int]$w, [int]$h) {
    # 24-bit: Play rejects a feature graphic with alpha, and screenshots don't need it
    $bmp = New-Object Drawing.Bitmap($w, $h, [Drawing.Imaging.PixelFormat]::Format24bppRgb)
    $g = [Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = 'AntiAlias'; $g.InterpolationMode = 'HighQualityBicubic'
    $g.PixelOffsetMode = 'HighQuality'; $g.TextRenderingHint = 'AntiAliasGridFit'
    $g.Clear($charcoal)
    return @($bmp, $g)
}

# A soft teal glow so the plain charcoal doesn't read as an empty frame
function Glow($g, [float]$cx, [float]$cy, [float]$r, [int]$alpha) {
    $p = New-Object Drawing.Drawing2D.GraphicsPath
    $p.AddEllipse($cx - $r, $cy - $r, 2 * $r, 2 * $r)
    $b = New-Object Drawing.Drawing2D.PathGradientBrush($p)
    $b.CenterColor = (Col '#00C4CC' $alpha); $b.SurroundColors = @((Col '#00C4CC' 0))
    $g.FillPath($b, $p)
}

# The capture with rounded corners and a hairline, like the phone's own screen
function Phone($g, [Drawing.Image]$img, [float]$x, [float]$y, [float]$w, [float]$h, [float]$r) {
    $path = RoundRect $x $y $w $h $r
    $state = $g.Save(); $g.SetClip($path)
    $g.DrawImage($img, (New-Object Drawing.RectangleF($x, $y, $w, $h)))
    $g.Restore($state)
    $g.DrawPath((New-Object Drawing.Pen($hair, 4)), $path)
}

function Centered($g, [string]$text, $font, $brush, [float]$y, [float]$width, [float]$height) {
    $fmt = New-Object Drawing.StringFormat; $fmt.Alignment = 'Center'; $fmt.LineAlignment = 'Near'
    $g.DrawString($text, $font, $brush, (New-Object Drawing.RectangleF(60, $y, ($width - 120), $height)), $fmt)
}

$items = @(
    @{ f = 'air';   n = '01-air-mouse'; k = 'AIR MOUSE'; c = 'Point your phone to move the pointer' },
    @{ f = 'dpad';  n = '02-remote';    k = 'REMOTE';    c = 'A full remote for Android TV' },
    @{ f = 'touch'; n = '03-touchpad';  k = 'TOUCHPAD';  c = 'Or swipe like a touchpad' },
    @{ f = 'type';  n = '04-typing';    k = 'TYPING';    c = "Type with your phone's keyboard" },
    @{ f = 'disc';  n = '05-discovery'; k = 'SET UP';    c = "Finds your TV`non the Wi-Fi" }
)
$kicker = New-Object Drawing.Font($family, 34, [Drawing.FontStyle]::Bold, [Drawing.GraphicsUnit]::Pixel)
$caption = New-Object Drawing.Font($family, 76, [Drawing.FontStyle]::Bold, [Drawing.GraphicsUnit]::Pixel)
foreach ($it in $items) {
    $W = 1080; $H = 1920
    $bmp, $g = NewCanvas $W $H
    Glow $g 540 260 760 46
    Centered $g $it.k $kicker (New-Object Drawing.SolidBrush $teal) 110 $W 50
    Centered $g $it.c $caption (New-Object Drawing.SolidBrush $white) 166 $W 200
    $img = [Drawing.Image]::FromFile((Join-Path $Shots "$($it.f).png"))
    # Whole screen visible, 2:1 like the capture
    $ph = 1440; $pw = $ph * $img.Width / $img.Height
    Phone $g $img (($W - $pw) / 2) 420 $pw $ph 44
    $img.Dispose()
    $bmp.Save((Join-Path $Out "screenshot-$($it.n).png"), [Drawing.Imaging.ImageFormat]::Png)
    $g.Dispose(); $bmp.Dispose()
}

# Feature graphic: icon, name and line on the left; the air mouse pad on the right, cut by the bottom edge
$W = 1024; $H = 500
$bmp, $g = NewCanvas $W $H
Glow $g 800 250 420 60
$icon = [Drawing.Image]::FromFile((Join-Path $Out 'icon-512.png'))
$state = $g.Save(); $g.SetClip((RoundRect 64 92 104 104 26))
$g.DrawImage($icon, 64, 92, 104, 104); $g.Restore($state); $icon.Dispose()
$title = New-Object Drawing.Font($family, 54, [Drawing.FontStyle]::Bold, [Drawing.GraphicsUnit]::Pixel)
$line = New-Object Drawing.Font($family, 28, [Drawing.FontStyle]::Regular, [Drawing.GraphicsUnit]::Pixel)
$g.DrawString("Air Mouse`nTV Remote", $title, (New-Object Drawing.SolidBrush $white), 56, 218)
$g.DrawString("Your phone becomes a TV remote`nand an air mouse", $line, (New-Object Drawing.SolidBrush $grey), 62, 362)
$img = [Drawing.Image]::FromFile((Join-Path $Shots 'air.png'))
$pw = 330; $ph = $pw * $img.Height / $img.Width
Phone $g $img 630 56 $pw $ph 30
$img.Dispose()
$bmp.Save((Join-Path $Out 'feature-graphic.png'), [Drawing.Imaging.ImageFormat]::Png)
$g.Dispose(); $bmp.Dispose()
