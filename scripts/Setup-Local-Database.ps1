param(
    [switch]$Reconfigure,
    [switch]$CheckOnly
)

$ErrorActionPreference = 'Stop'
$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
. (Join-Path $PSScriptRoot 'Set-MySqlLoopbackAddress.ps1')
. (Join-Path $PSScriptRoot 'MySqlFirewallGuard.ps1')

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

function Assert-MySqlDefaultPortsAvailable {
    $Listeners = @(Get-NetTCPConnection -State Listen -LocalPort @(3306, 33060) -ErrorAction SilentlyContinue)
    if ($Listeners.Count -gt 0) {
        $BusyPorts = ($Listeners | Select-Object -ExpandProperty LocalPort -Unique) -join ', '
        throw "MySQL default ports $BusyPorts are already in use. Stop the listener before installing the local server."
    }
}

function Get-MySqlConfiguredPorts([string]$ConfigFile, [string]$ServiceName) {
    $ClassicPort = 3306
    $XProtocolPort = 33060
    $ServerGroups = @('mysqld', $ServiceName.ToLowerInvariant())
    $CurrentGroup = ''
    foreach ($Line in [IO.File]::ReadLines($ConfigFile)) {
        if ($Line -match '^\s*\[([^\]]+)\]\s*(?:[#;].*)?$') {
            $CurrentGroup = $Matches[1].ToLowerInvariant()
            continue
        }
        if ($ServerGroups -notcontains $CurrentGroup) { continue }
        if ($Line -match '^\s*port\s*=\s*([0-9]+)\s*(?:[#;].*)?$') {
            $ClassicPort = [int]$Matches[1]
        } elseif ($Line -match '^\s*mysqlx[-_]port\s*=\s*([0-9]+)\s*(?:[#;].*)?$') {
            $XProtocolPort = [int]$Matches[1]
        }
    }
    [pscustomobject]@{ Classic = $ClassicPort; XProtocol = $XProtocolPort }
}

function Assert-MySqlListenersAreLocal($Ports) {
    $ClassicListeners = @()
    for ($Attempt = 0; $Attempt -lt 30; $Attempt++) {
        $ClassicListeners = @(Get-NetTCPConnection -State Listen -LocalPort $Ports.Classic -ErrorAction SilentlyContinue)
        if ($ClassicListeners.Count -gt 0) { break }
        Start-Sleep -Seconds 1
    }
    if ($ClassicListeners.Count -eq 0) {
        throw "MySQL did not open its configured local TCP port $($Ports.Classic)."
    }

    $AllListeners = @(Get-NetTCPConnection -State Listen -LocalPort @($Ports.Classic, $Ports.XProtocol) -ErrorAction SilentlyContinue)
    $LoopbackAddresses = @('127.0.0.1', '::1', '::ffff:127.0.0.1')
    $RemoteListeners = @($AllListeners | Where-Object { $LoopbackAddresses -notcontains $_.LocalAddress })
    if ($RemoteListeners.Count -gt 0) {
        $Addresses = ($RemoteListeners | Select-Object -ExpandProperty LocalAddress -Unique) -join ', '
        throw "MySQL is still listening outside loopback ($Addresses); inbound firewall blocks remain in place."
    }
}

function Get-MySqlOptionFile([string]$ServiceName) {
    $ServiceDetails = Get-CimInstance Win32_Service -Filter ("Name='{0}'" -f $ServiceName)
    if ($ServiceDetails.PathName -match '(?i)--defaults-file=(?:"([^"]+)"|([^\s]+))') {
        $Candidate = if ($Matches[1]) { $Matches[1] } else { $Matches[2] }
        if (Test-Path -LiteralPath $Candidate -PathType Leaf) { return $Candidate }
    }

    $VersionedOptionFile = Join-Path $env:ProgramData 'MySQL/MySQL Server 8.4/my.ini'
    if (Test-Path -LiteralPath $VersionedOptionFile -PathType Leaf) { return $VersionedOptionFile }
    throw "Could not locate the MySQL option file used by $ServiceName. Finish MySQL Configurator, then rerun setup."
}

function Invoke-NewMySqlConfigurator {
    $ConfiguratorCandidates = @(
        (Join-Path $env:ProgramFiles 'MySQL/MySQL Server 8.4/bin/mysql_configurator.exe'),
        (Join-Path $env:ProgramFiles 'MySQL/MySQL Server 8.4/bin/mysql-configurator.exe')
    )
    $Configurator = $ConfiguratorCandidates | Where-Object { Test-Path -LiteralPath $_ -PathType Leaf } | Select-Object -First 1
    if (-not $Configurator) {
        throw 'MySQL Configurator was not found. Complete MySQL Configurator with TCP disabled, then rerun setup.'
    }

    Write-Host 'Set the MySQL administrator password. This password is not saved by the game.'
    $FirstSecure = Read-Host 'New MySQL root password' -AsSecureString
    $SecondSecure = Read-Host 'Confirm MySQL root password' -AsSecureString
    $FirstPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($FirstSecure)
    $SecondPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($SecondSecure)
    try {
        $RootPassword = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($FirstPointer)
        $ConfirmedPassword = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($SecondPointer)
    } finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($FirstPointer)
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($SecondPointer)
        $FirstSecure.Dispose()
        $SecondSecure.Dispose()
    }
    if ([string]::IsNullOrWhiteSpace($RootPassword) -or $RootPassword -cne $ConfirmedPassword) {
        $RootPassword = $null
        $ConfirmedPassword = $null
        throw 'The MySQL root password must be non-empty and match its confirmation.'
    }

    $HadMysqlPwd = Test-Path Env:MYSQL_PWD
    $PreviousMysqlPwd = $env:MYSQL_PWD
    try {
        $env:MYSQL_PWD = $RootPassword
        & $Configurator --console --action=configure --enable-tcp-ip=false --open-win-firewall=false
        $ConfiguratorExitCode = $LASTEXITCODE
    } finally {
        if ($HadMysqlPwd) { $env:MYSQL_PWD = $PreviousMysqlPwd }
        else { Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue }
        $ConfirmedPassword = $null
    }
    if ($ConfiguratorExitCode -ne 0) { throw 'MySQL Configurator could not initialize the server with TCP disabled.' }
    return $RootPassword
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

$RootAdminPassword = $null
$Services = @(Get-Service -Name 'MySQL*' -ErrorAction SilentlyContinue | Sort-Object Name -Descending)
if ($Services.Count -eq 0) {
    if (-not (Get-Command winget -ErrorAction SilentlyContinue)) {
        throw 'Install MySQL Server 8.4 with its MySQL Configurator, then rerun this script.'
    }
    Assert-MySqlDefaultPortsAvailable
    Add-MySqlSetupFirewallGuard -Ports @(3306, 33060) | Out-Null
    Write-Host 'Installing MySQL Server. When the MSI asks, choose Configure later; setup will initialize it with TCP disabled before enabling loopback-only access. Keep the default ports if prompted.'
    & winget install --id Oracle.MySQL --exact --accept-source-agreements --accept-package-agreements --interactive
    if ($LASTEXITCODE -ne 0) { throw 'WinGet could not complete MySQL installation.' }
    $Services = @(Get-Service -Name 'MySQL*' -ErrorAction SilentlyContinue | Sort-Object Name -Descending)
    if ($Services.Count -eq 0) {
        $RootAdminPassword = Invoke-NewMySqlConfigurator
        $Services = @(Get-Service -Name 'MySQL*' -ErrorAction SilentlyContinue | Sort-Object Name -Descending)
    }
}
if ($Services.Count -eq 0) { throw 'No MySQL Windows service was found. Finish MySQL Configurator, then rerun this script.' }

$Service = $Services | Where-Object Status -eq 'Running' | Select-Object -First 1
if (-not $Service) { $Service = $Services | Select-Object -First 1 }
$OptionFile = Get-MySqlOptionFile -ServiceName $Service.Name
$MySqlPorts = Get-MySqlConfiguredPorts -ConfigFile $OptionFile -ServiceName $Service.Name
Add-MySqlSetupFirewallGuard -Ports @($MySqlPorts.Classic, $MySqlPorts.XProtocol) | Out-Null

try {
    $BindConfigurationChanged = Set-MySqlLoopbackAddress -ConfigFile $OptionFile -ServiceName $Service.Name
} catch {
    throw "Could not limit MySQL to local connections by updating $OptionFile. Verify the file is writable, then rerun setup. Details: $($_.Exception.Message)"
}
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

Assert-MySqlListenersAreLocal $MySqlPorts
Remove-MySqlSetupFirewallGuard

$WizardArguments = @()
if ($Reconfigure) { $WizardArguments += '--reconfigure' }
Write-Host 'Create the game database user below. The MySQL administrator password is entered without showing it.'
$HadAdminUser = Test-Path Env:ARG_DB_ADMIN_USER
$PreviousAdminUser = $env:ARG_DB_ADMIN_USER
$HadAdminPassword = Test-Path Env:ARG_DB_ADMIN_PASSWORD
$PreviousAdminPassword = $env:ARG_DB_ADMIN_PASSWORD
try {
    if ($RootAdminPassword) {
        $env:ARG_DB_ADMIN_USER = 'root'
        $env:ARG_DB_ADMIN_PASSWORD = $RootAdminPassword
    }
    Invoke-DatabaseWizard $WizardArguments
} finally {
    if ($HadAdminUser) { $env:ARG_DB_ADMIN_USER = $PreviousAdminUser }
    else { Remove-Item Env:ARG_DB_ADMIN_USER -ErrorAction SilentlyContinue }
    if ($HadAdminPassword) { $env:ARG_DB_ADMIN_PASSWORD = $PreviousAdminPassword }
    else { Remove-Item Env:ARG_DB_ADMIN_PASSWORD -ErrorAction SilentlyContinue }
    $RootAdminPassword = $null
}
