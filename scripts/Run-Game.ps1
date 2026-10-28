$ErrorActionPreference = 'Stop'
$CheckScript = Join-Path $PSScriptRoot 'Check-Prerequisites.ps1'
& $CheckScript -CheckOnly
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}
Set-Location (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
& mvn javafx:run
exit $LASTEXITCODE
