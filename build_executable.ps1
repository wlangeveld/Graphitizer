$ErrorActionPreference = "Stop"

$env:JAVA_HOME = "C:\Program Files\Java\jdk-25"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

Write-Host "Cleaning and Packaging with Maven..." -ForegroundColor Cyan
& .\apache-maven-3.9.6\bin\mvn.cmd clean package

if ($LASTEXITCODE -ne 0) {
    Write-Host "Maven build failed!" -ForegroundColor Red
    exit $LASTEXITCODE
}

Write-Host "Converting icon.png to icon.ico using Java..." -ForegroundColor Cyan
& "$env:JAVA_HOME\bin\javac.exe" IconConverter.java
& "$env:JAVA_HOME\bin\java.exe" IconConverter "src\main\resources\icon.png" "src\main\resources\icon.ico"

Write-Host "Cleaning previous release output..." -ForegroundColor Cyan
if (Test-Path "release") {
    Remove-Item -Recurse -Force "release"
}

Write-Host "Building native executable with jpackage..." -ForegroundColor Cyan
jpackage --type app-image --name "Graphitizer 1.0" --input target --main-jar graphitizer-1.0-SNAPSHOT.jar --dest release --icon src/main/resources/icon.ico

if ($LASTEXITCODE -ne 0) {
    Write-Host "jpackage build failed!" -ForegroundColor Red
    exit $LASTEXITCODE
}

Write-Host "Successfully built Graphitizer native application in release/Graphitizer!" -ForegroundColor Green
