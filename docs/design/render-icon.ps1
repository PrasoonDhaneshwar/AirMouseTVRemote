# Renders app icon L3b (docs/design/air-mouse.md) to PNGs with GDI+, same geometry as the vector in
# app/src/main/res/drawable/ic_launcher_foreground.xml (design coordinates, 72 x 72 from 18,18).
# Usage: powershell -File render-icon.ps1 -Res <app/src/main/res> -Store <store icon .png>
param([string]$Res, [string]$Store)
Add-Type -AssemblyName System.Drawing
$ErrorActionPreference = 'Stop'

function Col([string]$hex, [int]$a = 255) { $c = [Drawing.ColorTranslator]::FromHtml($hex); [Drawing.Color]::FromArgb($a, $c) }
$bg = Col '#161A1F'; $teal = Col '#00C4CC'; $white = Col '#FFFFFF'; $plate = Col '#2C3038'

function RoundRect([float]$x, [float]$y, [float]$w, [float]$h, [float]$r) {
    $p = New-Object Drawing.Drawing2D.GraphicsPath
    $d = 2 * $r
    $p.AddArc($x, $y, $d, $d, 180, 90); $p.AddArc($x + $w - $d, $y, $d, $d, 270, 90)
    $p.AddArc($x + $w - $d, $y + $h - $d, $d, $d, 0, 90); $p.AddArc($x, $y + $h - $d, $d, $d, 90, 90)
    $p.CloseFigure(); $p
}

# An SVG-style arc of radius r from (x1,y1) to (x2,y2), the short way and clockwise
function ArcPath([float]$x1, [float]$y1, [float]$x2, [float]$y2, [float]$r) {
    $mx = ($x1 + $x2) / 2; $my = ($y1 + $y2) / 2
    $dx = $x2 - $x1; $dy = $y2 - $y1; $len = [Math]::Sqrt($dx * $dx + $dy * $dy)
    $h = [Math]::Sqrt([Math]::Max(0, $r * $r - ($len / 2) * ($len / 2)))
    # Centre on the right of travel (down, for a rightward arc) for a clockwise (screen) short arc
    $cx = $mx - $h * ($dy / $len); $cy = $my + $h * ($dx / $len)
    $a1 = [Math]::Atan2($y1 - $cy, $x1 - $cx) * 180 / [Math]::PI
    $a2 = [Math]::Atan2($y2 - $cy, $x2 - $cx) * 180 / [Math]::PI
    $sweep = $a2 - $a1; if ($sweep -lt 0) { $sweep += 360 }
    $p = New-Object Drawing.Drawing2D.GraphicsPath
    $p.AddArc([float]($cx - $r), [float]($cy - $r), [float](2 * $r), [float](2 * $r), [float]$a1, [float]$sweep); $p
}

function Draw($g, [bool]$withBg) {
    if ($withBg) { $g.Clear($bg) }
    # The TV
    $tv = RoundRect 26 40 42 30 7
    $g.FillPath((New-Object Drawing.SolidBrush $plate), $tv)
    $pen = New-Object Drawing.Pen($white, 5); $pen.LineJoin = 'Round'; $g.DrawPath($pen, $tv)
    # Its pointer
    $ptr = [Drawing.PointF[]]@(
        (New-Object Drawing.PointF 35.5, 46.5), (New-Object Drawing.PointF 35.5, 64), (New-Object Drawing.PointF 40, 59.5),
        (New-Object Drawing.PointF 43.2, 66.3), (New-Object Drawing.PointF 46.4, 64.9), (New-Object Drawing.PointF 43.2, 58.2),
        (New-Object Drawing.PointF 49, 58.2))
    $g.FillPolygon((New-Object Drawing.SolidBrush $teal), $ptr)
    $tp = New-Object Drawing.Pen($teal, 2); $tp.LineJoin = 'Round'; $g.DrawPolygon($tp, $ptr)
    # The remote, tilted 12 degrees about (72, 62), filled with the background to hide the TV's edge
    $state = $g.Save()
    $g.TranslateTransform(72, 62); $g.RotateTransform(12); $g.TranslateTransform(-72, -62)
    $body = RoundRect 65 47 14 30 7
    $g.FillPath((New-Object Drawing.SolidBrush $bg), $body)
    $g.DrawPath((New-Object Drawing.Pen($white, 3.5)), $body)
    $g.FillEllipse((New-Object Drawing.SolidBrush $teal), 68.7, 51.2, 6.6, 6.6)
    $g.FillEllipse((New-Object Drawing.SolidBrush $white), 70, 61.5, 4, 4)
    $g.FillEllipse((New-Object Drawing.SolidBrush $white), 70, 67.5, 4, 4)
    $g.Restore($state)
    # Two waves from the tip
    $w1 = New-Object Drawing.Pen($teal, 4); $w1.StartCap = 'Round'; $w1.EndCap = 'Round'
    $g.DrawPath($w1, (ArcPath 72.03 41.27 80.33 43.03 6))
    $w2 = New-Object Drawing.Pen((Col '#00C4CC' 153), 4); $w2.StartCap = 'Round'; $w2.EndCap = 'Round'
    $g.DrawPath($w2, (ArcPath 69.58 37.49 84.11 40.58 10.5))
}

# size: pixels; shape: square | rounded | round; zoom: how many design units are cropped off each edge
function Render([string]$path, [int]$size, [string]$shape, [float]$crop) {
    $bmp = New-Object Drawing.Bitmap $size, $size, ([Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = 'AntiAlias'; $g.PixelOffsetMode = 'HighQuality'; $g.CompositingQuality = 'HighQuality'
    $clip = New-Object Drawing.Drawing2D.GraphicsPath
    if ($shape -eq 'round') { $clip.AddEllipse(0, 0, $size, $size) }
    elseif ($shape -eq 'rounded') { $clip = RoundRect 0 0 $size $size ($size * 0.22) }
    else { $clip.AddRectangle((New-Object Drawing.RectangleF 0, 0, $size, $size)) }
    $g.SetClip($clip)
    $scale = $size / (72 - 2 * $crop)
    $g.ScaleTransform($scale, $scale); $g.TranslateTransform(-(18 + $crop), -(18 + $crop))
    Draw $g $true
    $g.Dispose()
    New-Item -ItemType Directory -Force (Split-Path $path) | Out-Null
    $bmp.Save($path, [Drawing.Imaging.ImageFormat]::Png); $bmp.Dispose()
}

if ($Store) { Render $Store 512 'square' 0 }
if ($Res) {
    foreach ($d in @(@('mdpi', 48), @('hdpi', 72), @('xhdpi', 96), @('xxhdpi', 144), @('xxxhdpi', 192))) {
        # Before Android 8 the icon is not masked, so the mark is drawn a little larger to fill its own shape
        Render "$Res\mipmap-$($d[0])\ic_launcher.png" $d[1] 'rounded' 6
        Render "$Res\mipmap-$($d[0])\ic_launcher_round.png" $d[1] 'round' 6
    }
}
