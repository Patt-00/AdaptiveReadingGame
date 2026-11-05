param(
    [switch]$InstallOnly,
    [switch]$ReconfigureDatabase,
    [switch]$Help
)

$ErrorActionPreference = 'Stop'
if ($Help) {
    Write-Output 'Usage: scripts/setup-windows.ps1 [-InstallOnly] [-ReconfigureDatabase]'
    Write-Output 'Checks prerequisites, configures local database storage, prepares the optional model helper, and starts the game.'
    exit 0
}

$SetupScript = Join-Path $PSScriptRoot 'Setup-And-Run.ps1'
if ($InstallOnly -and $ReconfigureDatabase) { & $SetupScript -InstallOnly -ReconfigureDatabase }
elseif ($InstallOnly) { & $SetupScript -InstallOnly }
elseif ($ReconfigureDatabase) { & $SetupScript -ReconfigureDatabase }
else { & $SetupScript }
exit $LASTEXITCODE
