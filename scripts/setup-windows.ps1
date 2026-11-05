param(
    [switch]$InstallOnly,
    [switch]$Help
)

$ErrorActionPreference = 'Stop'
if ($Help) {
    Write-Output 'Usage: scripts/setup-windows.ps1 [-InstallOnly]'
    Write-Output 'Checks prerequisites, prepares the optional model helper, and starts the game.'
    exit 0
}

$SetupScript = Join-Path $PSScriptRoot 'Setup-And-Run.ps1'
if ($InstallOnly) { & $SetupScript -InstallOnly }
else { & $SetupScript }
exit $LASTEXITCODE
