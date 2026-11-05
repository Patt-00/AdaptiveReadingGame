param(
    [switch]$Reconfigure,
    [switch]$CheckOnly
)

$ErrorActionPreference = 'Stop'
$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
. (Join-Path $PSScriptRoot 'Set-MySqlLoopbackAddress.ps1')

function Invoke-DatabaseWizard([string[]]$ExtraArguments) {
    $Arguments = @('-q', '-Dexec.mainClass=com.adaptivereadinggame.repository.mysql.MySqlLocalDatabaseSetup')
    if ($ExtraArguments.Count -gt 0) {
        $Arguments += "-Dexec.args=$($ExtraArguments -join ' ')"
    }
    $Arguments += 'exec:java'
    Push-Location $ProjectRoot
    try {
        & mvn @Arguments
        if ($LASTEXITCODE -ne 0) { throw 'The local database wizard failed. Review the message above, then rerun setup.' }
    } finally {
        Pop-Location
    }
}

if ($CheckOnly) {
    Invoke-DatabaseWizard @('--check-only')
    return
}
if ($Reconfigure) {
    if ($env:ARG_DB_URL -or $env:ARG_DB_USER -or [Environment]::GetEnvironmentVariables().Contains('ARG_DB_PASSWORD')) {
        throw 'Unset ARG_DB_URL, ARG_DB_USER, and ARG_DB_PASSWORD before reconfiguring; they override saved settings.'
    }
}

$CurrentIdentity = [Security.Principal.WindowsIdentity]::GetCurrent()
$CurrentPrincipal = [Security.Principal.WindowsPrincipal]::new($CurrentIdentity)
if (-not $CurrentPrincipal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    Write-Host 'Requesting administrator permission to configure and start the local MySQL service.'
    $ElevatedArguments = @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', ('"{0}"' -f $PSCommandPath))
    if ($Reconfigure) { $ElevatedArguments += '-Reconfigure' }
    try {
        $PowerShellExecutable = (Get-Process -Id $PID).Path
        $ElevatedProcess = Start-Process -FilePath $PowerShellExecutable `
            -Verb RunAs -ArgumentList $ElevatedArguments -Wait -PassThru
    } catch {
        throw 'Administrator permission is required to configure and restart the local MySQL service.'
    }
    if ($ElevatedProcess.ExitCode -ne 0) {
        throw "Elevated database setup exited with code $($ElevatedProcess.ExitCode)."
    }
    $global:LASTEXITCODE = 0
    return
}

$Services = @(Get-Service -Name 'MySQL*' -ErrorAction SilentlyContinue | Sort-Object Name -Descending)
if ($Services.Count -eq 0) {
    if (-not (Get-Command winget -ErrorAction SilentlyContinue)) {
        throw 'Install MySQL Server 8.4 with its MySQL Configurator, then rerun this script.'
    }
    Write-Host 'Installing MySQL Server. Complete the vendor configuration window and set a local root password when prompted.'
    & winget install --id Oracle.MySQL --exact --accept-source-agreements --accept-package-agreements --interactive
    if ($LASTEXITCODE -ne 0) { throw 'WinGet could not complete MySQL installation.' }
    $Services = @(Get-Service -Name 'MySQL*' -ErrorAction SilentlyContinue | Sort-Object Name -Descending)
}
if ($Services.Count -eq 0) {
    throw 'No MySQL Windows service was found. Finish MySQL Configurator, then rerun this script.'
}

$Service = $Services | Where-Object Status -eq 'Running' | Select-Object -First 1
if (-not $Service) { $Service = $Services | Select-Object -First 1 }
$ServiceDetails = Get-CimInstance Win32_Service -Filter ("Name='{0}'" -f $Service.Name)
$OptionFile = $null
if ($ServiceDetails.PathName -match '(?i)--defaults-file=(?:"([^"]+)"|([^\s]+))') {
    $OptionFile = if ($Matches[1]) { $Matches[1] } else { $Matches[2] }
}
if ([string]::IsNullOrWhiteSpace($OptionFile) -or -not (Test-Path -LiteralPath $OptionFile -PathType Leaf)) {
    $VersionedOptionFile = Join-Path $env:ProgramData 'MySQL/MySQL Server 8.4/my.ini'
    if (Test-Path -LiteralPath $VersionedOptionFile -PathType Leaf) { $OptionFile = $VersionedOptionFile }
}
if ([string]::IsNullOrWhiteSpace($OptionFile) -or -not (Test-Path -LiteralPath $OptionFile -PathType Leaf)) {
    throw "Could not locate the MySQL option file used by $($Service.Name). Finish MySQL Configurator, then rerun setup."
}

$BindConfigurationChanged = Set-MySqlLoopbackAddress -ConfigFile $OptionFile -ServiceName $Service.Name
if ($Service.Status -eq 'Running' -and $BindConfigurationChanged) {
    try {
        Restart-Service -Name $Service.Name
        (Get-Service -Name $Service.Name).WaitForStatus('Running', [TimeSpan]::FromSeconds(45))
    } catch {
        throw "Could not restart $($Service.Name) after limiting MySQL to this device. Open PowerShell as Administrator and rerun setup."
    }
} elseif ($Service.Status -ne 'Running') {
    try {
        Start-Service -Name $Service.Name
        (Get-Service -Name $Service.Name).WaitForStatus('Running', [TimeSpan]::FromSeconds(45))
    } catch {
        throw "Could not start $($Service.Name). Open PowerShell as Administrator, start the MySQL service, then rerun this script."
    }
}

$WizardArguments = @()
if ($Reconfigure) { $WizardArguments += '--reconfigure' }
Write-Host 'Create the game database user below. The MySQL administrator password is entered without showing it.'
Invoke-DatabaseWizard $WizardArguments
