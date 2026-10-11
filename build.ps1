param([string]$JavaHome, [string]$Maven)
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
if (-not $JavaHome -and (Test-Path -LiteralPath '.tools\jdk25')) { $JavaHome = (Resolve-Path -LiteralPath '.tools\jdk25').Path }
if ($JavaHome) { $env:JAVA_HOME = $JavaHome }
if (-not $Maven -and (Test-Path -LiteralPath '.tools\apache-maven-3.9.11\bin\mvn.cmd')) { $Maven = (Resolve-Path -LiteralPath '.tools\apache-maven-3.9.11\bin\mvn.cmd').Path }
if (-not $Maven) { $Maven = 'mvn.cmd' }
& $Maven clean verify
if ($LASTEXITCODE -ne 0) { throw "Maven a échoué: $LASTEXITCODE" }
Write-Host 'Prêt: target\PixelPrinter-Reforged-2.0.0.jar'
