param(
    [switch]$InstallOnly,
    [switch]$ReconfigureDatabase
)

$ErrorActionPreference = 'Stop'
$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path

function Invoke-NativeCapture([string]$CommandName, [string[]]$Arguments) {
    $ErrorActionPreference = 'Continue'
    $PSNativeCommandUseErrorActionPreference = $false
    $output = (& $CommandName @Arguments 2>&1 | Out-String)
    [pscustomobject]@{ Output = $output; ExitCode = $LASTEXITCODE }
}

function Get-JavaMajor([string]$CommandName) {
    $command = Get-Command $CommandName -ErrorAction SilentlyContinue
    if (-not $command) { return 0 }
    $version = Invoke-NativeCapture $CommandName @('-version')
    if ($version.ExitCode -ne 0) { return 0 }
    $versionText = $version.Output
    if ($CommandName -eq 'java' -and $versionText -match 'version "([0-9]+)') { return [int]$Matches[1] }
    if ($CommandName -eq 'javac' -and $versionText -match 'javac ([0-9]+)') { return [int]$Matches[1] }
    return 0
}

function Get-JavaHome {
    $properties = Invoke-NativeCapture 'java' @('-XshowSettings:properties', '-version')
    if ($properties.ExitCode -ne 0) { return $null }
    if ($properties.Output -match '(?m)^\s*java\.home\s*=\s*(.+?)\s*$') {
        return $Matches[1].Trim()
    }
    return $null
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

$DetectedJavaHome = Get-JavaHome
if (-not [string]::IsNullOrWhiteSpace($DetectedJavaHome)) {
    $JavacExe = Join-Path $DetectedJavaHome 'bin/javac.exe'
    $JavacCommand = Join-Path $DetectedJavaHome 'bin/javac'
    if ((Test-Path -LiteralPath $JavacExe -PathType Leaf) -or
            (Test-Path -LiteralPath $JavacCommand -PathType Leaf)) {
        $env:JAVA_HOME = $DetectedJavaHome
        $env:Path = "$DetectedJavaHome\bin;$env:Path"
    }
}

$CheckScript = Join-Path $PSScriptRoot 'Check-Prerequisites.ps1'
$DatabaseScript = Join-Path $PSScriptRoot 'Setup-Local-Database.ps1'
if ($ReconfigureDatabase) { & $DatabaseScript -Reconfigure }
else { & $DatabaseScript }
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

& $CheckScript -CheckOnly
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

try {
    $ModelSetupScript = Join-Path $PSScriptRoot 'Setup-Model-Helper.ps1'
    $ModelSetupResult = & $ModelSetupScript
    if ($ModelSetupResult -ne 0) {
        Write-Warning 'The model helper is unavailable; the game will use Java difficulty fallback.'
    }
} catch {
    Write-Warning 'The model helper could not be prepared; the game will use Java difficulty fallback.'
}

if ($InstallOnly) {
    Write-Host 'Setup complete. Run scripts/Run-Game.ps1 to launch the game.'
    exit 0
}

Set-Location $ProjectRoot
& (Join-Path $PSScriptRoot 'Run-Game.ps1')
exit $LASTEXITCODE
