Add-Type -AssemblyName System.Drawing
$source = "e:\DigiPlot\src\main\resources\icon.png"
$dest = "e:\DigiPlot\src\main\resources\icon.ico"

Write-Host "Converting icon.png to icon.ico..."
$img = [System.Drawing.Image]::FromFile($source)
$bmp = New-Object System.Drawing.Bitmap($img)
$hicon = $bmp.GetHicon()
$icon = [System.Drawing.Icon]::FromHandle($hicon)

$fs = New-Object System.IO.FileStream($dest, [System.IO.FileMode]::Create)
$icon.Save($fs)

$fs.Close()
$fs.Dispose()
$img.Dispose()
$bmp.Dispose()
$icon.Dispose()
[System.Runtime.InteropServices.Marshal]::DestroyIcon($hicon) | Out-Null

Write-Host "Successfully created icon.ico!"
