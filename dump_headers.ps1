$path = "C:\Users\wglp09\.gemini\antigravity\conversations\4550952e-6a19-44bc-9876-813cfda6b821.pb"
$outputPath = "e:\DigiPlot\hex_dump.txt"

if (Test-Path $path) {
    # Read just the first 16 bytes to check the file headers (Magic Bytes)
    $bytes = [System.IO.File]::ReadAllBytes($path)
    $hex = [System.BitConverter]::ToString($bytes[0..15])
    
    # Save the hex to a text file that my built-in reading tools can access!
    Set-Content -Path $outputPath -Value $hex
    Write-Host "Dumped header hex to $outputPath" -ForegroundColor Green
} else {
    Write-Host "File not found!" -ForegroundColor Red
}
