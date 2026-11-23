# Builds the source images in mobile/assets that `npm run assets` (@capacitor/assets) turns into
# every app icon and splash screen size: the "J" from the JukeANator wordmark for the icon, the
# whole wordmark for the splash screen, both on black (the Web/Mobile UI's background).
#
# The pixel coordinates below are specific to src/main/resources/static/images/JukeANatorLogo.png
# (1888x560); a different logo image needs new ones. Usage, from the mobile folder:
#   powershell -ExecutionPolicy Bypass -File scripts\make-icon-sources.ps1
param(
  [string]$Logo = (Join-Path $PSScriptRoot '..\..\src\main\resources\static\images\JukeANatorLogo.png'),
  [string]$OutDir = (Join-Path $PSScriptRoot '..\assets'))
$Logo = (Resolve-Path $Logo).Path
Add-Type -AssemblyName System.Drawing
New-Item -ItemType Directory -Force $OutDir | Out-Null

$src = [System.Drawing.Bitmap]::FromFile($Logo)

# 1. Isolate the "J": crop its region, then black out the neighboring "u" to the right of a
#    slanted cut line measured from the gap between the J's stem and the u.
$ox = 60; $oy = 60
$crop = $src.Clone((New-Object System.Drawing.Rectangle $ox, $oy, 460, 480), [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$feather = 18
for ($y = 0; $y -lt $crop.Height; $y++) {
  # Right of the J's top bar (ends near x=325) everything belongs to the next letters. Under the
  # bar the cut runs diagonally down to the gap between the J's stem and the "u" (top at y=225),
  # then follows that slanted gap. No hard steps, so the J's glow fades instead of being clipped.
  $cut = if ($y -lt 150) { 335 } elseif ($y -lt 222) { 335 - ($y - 150) * (335 - 241) / 72.0 } else { 241 - ($y - 225) * 0.12 }
  $cut = [int]$cut
  for ($x = 0; $x -lt $crop.Width; $x++) {
    if ($x -ge $cut) { $crop.SetPixel($x, $y, [System.Drawing.Color]::Black); continue }
    $d = $cut - $x
    if ($d -lt $feather) {
      # Feather only the dim glow; the letter's bright body and outline are never dimmed.
      $p = $crop.GetPixel($x, $y)
      if ($p.R -lt 110) {
        $f = $d / [double]$feather
        $crop.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(255, [int]($p.R * $f), [int]($p.G * $f), [int]($p.B * $f)))
      }
    }
  }
}
# Bounding box of the J (including its glow).
$minX = $crop.Width; $minY = $crop.Height; $maxX = 0; $maxY = 0
for ($y = 0; $y -lt $crop.Height; $y++) { for ($x = 0; $x -lt $crop.Width; $x++) {
  if ($crop.GetPixel($x, $y).R -gt 40) {
    if ($x -lt $minX) { $minX = $x }; if ($x -gt $maxX) { $maxX = $x }
    if ($y -lt $minY) { $minY = $y }; if ($y -gt $maxY) { $maxY = $y }
  } } }
$jRect = New-Object System.Drawing.Rectangle $minX, $minY, ($maxX - $minX + 1), ($maxY - $minY + 1)
$j = $crop.Clone($jRect, $crop.PixelFormat)
"J bounding box: $jRect"

function New-Canvas([int]$w, [int]$h, [bool]$transparent) {
  $bmp = New-Object System.Drawing.Bitmap $w, $h, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
  $g = [System.Drawing.Graphics]::FromImage($bmp)
  $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
  $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
  $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
  $g.Clear($(if ($transparent) { [System.Drawing.Color]::Transparent } else { [System.Drawing.Color]::Black }))
  return @($bmp, $g)
}

# Draws $img centered on a $size canvas, scaled so its larger dimension is $fraction of $size.
function Save-Centered($img, [int]$w, [int]$h, [double]$fraction, [string]$path) {
  $bmp, $g = New-Canvas $w $h $false
  $scale = [Math]::Min($w * $fraction / $img.Width, $h * $fraction / $img.Height)
  $dw = [int]($img.Width * $scale); $dh = [int]($img.Height * $scale)
  $g.DrawImage($img, [int](($w - $dw) / 2), [int](($h - $dh) / 2), $dw, $dh)
  $g.Dispose(); $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png); $bmp.Dispose()
  "wrote $path"
}

# 2. icon-only.png: the full-bleed icon (iOS, legacy Android, store listings). J fills ~72%.
Save-Centered $j 1024 1024 0.72 (Join-Path $OutDir 'icon-only.png')
# 3. icon-foreground.png: Android adaptive icon layer. @capacitor/assets already insets this layer
#    by 16.7% per side (the part launchers crop away), so this canvas is the visible icon area;
#    at 68% the J's corners stay inside the circular mask some launchers use.
Save-Centered $j 1024 1024 0.68 (Join-Path $OutDir 'icon-foreground.png')
# 4. icon-background.png: plain black adaptive icon background.
$bmp, $g = New-Canvas 1024 1024 $false; $g.Dispose()
$bmp.Save((Join-Path $OutDir 'icon-background.png'), [System.Drawing.Imaging.ImageFormat]::Png); $bmp.Dispose()
"wrote icon-background.png"
# 5. splash.png / splash-dark.png: the JukeANator wordmark on black. The tool crops this square to
#    each screen's shape, so the wordmark stays within the central ~40% width to survive a
#    portrait phone crop.
Save-Centered $src 2732 2732 0.40 (Join-Path $OutDir 'splash.png')
Copy-Item (Join-Path $OutDir 'splash.png') (Join-Path $OutDir 'splash-dark.png')
"wrote splash-dark.png"

$j.Dispose(); $crop.Dispose(); $src.Dispose()
