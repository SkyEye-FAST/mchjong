param(
    [Parameter(Mandatory)][string]$Evidence,
    [Parameter(Mandatory)][string]$Pattern,
    [Parameter(Mandatory)][datetime]$Since,
    [Parameter(Mandatory)][string]$Name
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$source = Join-Path $Evidence 'screenshots'
$images = @(Get-ChildItem $source -Filter $Pattern -File | Sort-Object Name)
if ($images.Count -eq 0) { throw "No screenshots match $Pattern" }
foreach ($image in $images) {
    if ($image.LastWriteTime -lt $Since) { throw "Stale evidence: $($image.FullName)" }
}
$destination = Join-Path $Evidence 'review'
[IO.Directory]::CreateDirectory($destination) | Out-Null
$font = [Drawing.Font]::new('Consolas', 10)
try {
    for ($page = 0; $page * 4 -lt $images.Count; $page++) {
        $sheet = [Drawing.Bitmap]::new(1280, 1008)
        $canvas = [Drawing.Graphics]::FromImage($sheet)
        try {
            $canvas.Clear([Drawing.Color]::Black)
            for ($cell = 0; $cell -lt 4 -and $page * 4 + $cell -lt $images.Count; $cell++) {
                $file = $images[$page * 4 + $cell]
                $image = [Drawing.Image]::FromFile($file.FullName)
                try {
                    $x = ($cell % 2) * 640
                    $y = [Math]::Floor($cell / 2) * 504
                    $ratio = [Math]::Min(640.0 / $image.Width, 480.0 / $image.Height)
                    $canvas.DrawString($file.Name, $font, [Drawing.Brushes]::White, $x + 4, $y + 4)
                    $canvas.DrawImage($image, [int]$x, [int]($y + 24), [int]($image.Width * $ratio), [int]($image.Height * $ratio))
                } finally { $image.Dispose() }
            }
            $path = Join-Path $destination ("{0}-{1:00}.png" -f $Name, $page)
            $sheet.Save($path, [Drawing.Imaging.ImageFormat]::Png)
            Write-Output $path
        } finally { $canvas.Dispose(); $sheet.Dispose() }
    }
} finally { $font.Dispose() }
