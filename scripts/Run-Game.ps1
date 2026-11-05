$ErrorActionPreference = 'Stop'
$CheckScript = Join-Path $PSScriptRoot 'Check-Prerequisites.ps1'
& $CheckScript -CheckOnly
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}
$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$ModelProcess = $null
$GameExitCode = 0

try {
    $ModelSetupScript = Join-Path $PSScriptRoot 'Setup-Model-Helper.ps1'
    $ModelReady = & $ModelSetupScript -CheckOnly
    if ($ModelReady -eq 0) {
        $ModelPython = Join-Path $ProjectRoot 'model-tool/.venv/Scripts/python.exe'
        $LogDirectory = Join-Path $ProjectRoot 'target'
        New-Item -ItemType Directory -Force -Path $LogDirectory | Out-Null
        try {
            $ModelProcess = Start-Process -FilePath $ModelPython `
                -ArgumentList 'model-tool/server.py' `
                -WorkingDirectory $ProjectRoot `
                -WindowStyle Hidden `
                -PassThru `
                -RedirectStandardOutput (Join-Path $LogDirectory 'model-helper.log') `
                -RedirectStandardError (Join-Path $LogDirectory 'model-helper-error.log')
            Write-Host 'Started the optional local model helper.'
        } catch {
            Write-Warning 'The model helper could not start. The game will use Java difficulty fallback.'
        }
    } else {
        Write-Host 'Model helper is unavailable. The game will use Java difficulty fallback.'
    }

    Set-Location $ProjectRoot
    & mvn javafx:run
    $GameExitCode = $LASTEXITCODE
} finally {
    if ($ModelProcess -and -not $ModelProcess.HasExited) {
        Stop-Process -Id $ModelProcess.Id -Force -ErrorAction SilentlyContinue
    }
}

exit $GameExitCode
