param(
    [switch]$InstallOnly
)

$ErrorActionPreference = 'Stop'
$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path

function Get-JavaMajor([string]$CommandName) {
    $command = Get-Command $CommandName -ErrorAction SilentlyContinue
    if (-not $command) { return 0 }
    $versionText = (& $CommandName -version 2>&1 | Out-String)
    if ($LASTEXITCODE -ne 0) { return 0 }
    if ($CommandName -eq 'java' -and $versionText -match 'version "([0-9]+)') { return [int]$Matches[1] }
    if ($CommandName -eq 'javac' -and $versionText -match 'javac ([0-9]+)') { return [int]$Matches[1] }
    return 0
}

$NeedsJdk = (Get-JavaMajor 'java') -lt 21 -or (Get-JavaMajor 'javac') -lt 21
$NeedsMaven = -not (Get-Command mvn -ErrorAction SilentlyContinue)

if ($NeedsJdk -or $NeedsMaven) {
    if (-not (Get-Command winget -ErrorAction SilentlyContinue)) {
        throw 'WinGet is unavailable. Install JDK 21 or newer and Maven manually, then run scripts/Run-Game.ps1.'
    }

    if ($NeedsJdk) {
        & winget install --id EclipseAdoptium.Temurin.21.JDK --exact --accept-source-agreements --accept-package-agreements
        if ($LASTEXITCODE -ne 0) { throw 'WinGet could not install JDK 21. Resolve the installer error and rerun this setup script.' }
    }
    if ($NeedsMaven) {
        & winget install --id Apache.Maven --exact --accept-source-agreements --accept-package-agreements
        if ($LASTEXITCODE -ne 0) { throw 'WinGet could not install Maven. Resolve the installer error and rerun this setup script.' }
    }

    $machinePath = [Environment]::GetEnvironmentVariable('Path', 'Machine')
    $userPath = [Environment]::GetEnvironmentVariable('Path', 'User')
    $env:Path = "$machinePath;$userPath"
    $javaHome = [Environment]::GetEnvironmentVariable('JAVA_HOME', 'Machine')
    if ([string]::IsNullOrWhiteSpace($javaHome)) {
        $javaHome = [Environment]::GetEnvironmentVariable('JAVA_HOME', 'User')
    }
    if ($NeedsJdk) {
        $adoptiumRoot = Join-Path $env:ProgramFiles 'Eclipse Adoptium'
        if (Test-Path -LiteralPath $adoptiumRoot -PathType Container) {
            $installedJdk = Get-ChildItem -LiteralPath $adoptiumRoot -Directory |
                Where-Object { $_.Name -match '^jdk-21([.-]|$)' } |
                Sort-Object Name -Descending |
                Select-Object -First 1
            if ($installedJdk) { $javaHome = $installedJdk.FullName }
        }
    }
    if (-not [string]::IsNullOrWhiteSpace($javaHome)) {
        $env:JAVA_HOME = $javaHome
        $env:Path = "$javaHome\bin;$env:Path"
    }
}

$CheckScript = Join-Path $PSScriptRoot 'Check-Prerequisites.ps1'
& $CheckScript -CheckOnly
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

if ($InstallOnly) {
    Write-Host 'Setup complete. Run scripts/Run-Game.ps1 to launch the game.'
    exit 0
}

Set-Location $ProjectRoot
& (Join-Path $PSScriptRoot 'Run-Game.ps1')
exit $LASTEXITCODE
