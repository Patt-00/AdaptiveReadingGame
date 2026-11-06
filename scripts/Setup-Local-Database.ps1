param(
    [switch]$Reconfigure,
    [switch]$CheckOnly
)

$ErrorActionPreference = 'Stop'
$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
. (Join-Path $PSScriptRoot 'Set-MySqlLoopbackAddress.ps1')
. (Join-Path $PSScriptRoot 'MySqlFirewallGuard.ps1')
. (Join-Path $PSScriptRoot 'WindowsLocalDatabaseSecrets.ps1')

function Invoke-DatabaseWizard([string[]]$ExtraArguments) {
    $Arguments = @('-q', '-Dexec.mainClass=com.adaptivereadinggame.repository.mysql.MySqlLocalDatabaseSetup')
    $WizardArguments = @('--automatic') + $ExtraArguments
    $Arguments += "-Dexec.args=$($WizardArguments -join ' ')"
    $Arguments += 'exec:java'
    Push-Location $ProjectRoot
    $PreviousErrorPreference = $ErrorActionPreference
    $PreviousNativeErrorPreference = $PSNativeCommandUseErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $PSNativeCommandUseErrorActionPreference = $false
        $Output = (& mvn @Arguments 2>&1 | Out-String)
        $ExitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $PreviousErrorPreference
        $PSNativeCommandUseErrorActionPreference = $PreviousNativeErrorPreference
        Pop-Location
    }
    if (-not [string]::IsNullOrWhiteSpace($Output)) { Write-Host $Output.TrimEnd() }
    if ($ExitCode -ne 0) { throw 'The local database wizard failed. Review the message above, then rerun setup.' }
}

function Get-MySqlTcpListeners([int[]]$Ports) {
    try {
        $Listeners = @(Get-NetTCPConnection -State Listen -LocalPort $Ports -ErrorAction Stop)
        if ($Listeners.Count -gt 0) { return $Listeners }
    } catch {
        # Use netstat below when NetTCPIP is unavailable in a remote PowerShell session.
    }

    $Netstat = Join-Path $env:SystemRoot 'System32/netstat.exe'
    if (-not (Test-Path -LiteralPath $Netstat -PathType Leaf)) { return @() }
    $Listeners = @()
    foreach ($Line in (& $Netstat -ano -p tcp 2>$null)) {
        if ($Line -match '^\s*TCP\s+(\[[^\]]+\]|[^:\s]+):([0-9]+)\s+\S+\s+LISTENING\s+[0-9]+\s*$') {
            $LocalPort = [int]$Matches[2]
            if ($Ports -contains $LocalPort) {
                $LocalAddress = $Matches[1]
                if ($LocalAddress.StartsWith('[') -and $LocalAddress.EndsWith(']')) {
                    $LocalAddress = $LocalAddress.Substring(1, $LocalAddress.Length - 2)
                }
                $Listeners += [pscustomobject]@{ LocalAddress = $LocalAddress; LocalPort = $LocalPort; State = 'Listen' }
            }
        }
    }
    return $Listeners
}

function Assert-MySqlPortsAvailable([int[]]$Ports) {
    $Listeners = @(Get-MySqlTcpListeners -Ports $Ports)
    if ($Listeners.Count -gt 0) {
        $BusyPorts = ($Listeners | Select-Object -ExpandProperty LocalPort -Unique) -join ', '
        throw "The local game database ports $BusyPorts are already in use. Stop the conflicting service, then rerun setup."
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
        $ClassicListeners = @(Get-MySqlTcpListeners -Ports @($Ports.Classic))
        if ($ClassicListeners.Count -gt 0) { break }
        Start-Sleep -Seconds 1
    }
    if ($ClassicListeners.Count -eq 0) {
        throw "MySQL did not open its configured local TCP port $($Ports.Classic)."
    }

    $AllListeners = @(Get-MySqlTcpListeners -Ports @($Ports.Classic, $Ports.XProtocol))
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

    $GameOptionFile = Join-Path $env:ProgramData 'AdaptiveReadingGame/MySQL Server 8.4/my.ini'
    if (Test-Path -LiteralPath $GameOptionFile -PathType Leaf) { return $GameOptionFile }
    throw "Could not locate the MySQL option file used by $ServiceName. Finish MySQL setup, then rerun this script."
}

function Get-GameDatabaseConfigFile {
    if (-not [string]::IsNullOrWhiteSpace($env:ARG_DB_CONFIG_FILE)) {
        return [IO.Path]::GetFullPath($env:ARG_DB_CONFIG_FILE)
    }
    if ([string]::IsNullOrWhiteSpace($env:APPDATA)) {
        throw 'Windows did not provide the current user''s AppData folder.'
    }
    return Join-Path $env:APPDATA 'AdaptiveReadingGame/database.properties'
}

function ConvertTo-MySqlPasswordLiteral([string]$Password) {
    if ($Password.IndexOf([char]0) -ge 0) {
        throw 'The MySQL administrator password cannot contain a NUL character.'
    }
    $EscapedPassword = $Password.Replace('\', '\\').Replace("'", "\'")
    return "'$EscapedPassword'"
}

function Set-MySqlBootstrapRootPassword([string]$MysqlClient, [int]$Port, [string]$TemporaryPassword, [string]$NewPassword) {
    $HadMysqlPwd = Test-Path Env:MYSQL_PWD
    $PreviousMysqlPwd = $env:MYSQL_PWD
    $PreviousErrorPreference = $ErrorActionPreference
    $PreviousNativeErrorPreference = $PSNativeCommandUseErrorActionPreference
    $Sql = "ALTER USER 'root'@'localhost' IDENTIFIED BY $(ConvertTo-MySqlPasswordLiteral $NewPassword);"
    try {
        $env:MYSQL_PWD = $TemporaryPassword
        $ErrorActionPreference = 'Continue'
        $PSNativeCommandUseErrorActionPreference = $false
        $Output = $Sql | & $MysqlClient --no-defaults --protocol=TCP --host=127.0.0.1 `
            --port=$Port --user=root --connect-expired-password --batch 2>&1 | Out-String
        $ExitCode = $LASTEXITCODE
    } finally {
        if ($HadMysqlPwd) { $env:MYSQL_PWD = $PreviousMysqlPwd }
        else { Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue }
        $ErrorActionPreference = $PreviousErrorPreference
        $PSNativeCommandUseErrorActionPreference = $PreviousNativeErrorPreference
        $Output = $null
        $Sql = $null
    }
    if ($ExitCode -ne 0) {
        throw 'MySQL could not set the administrator password during first-run setup. Setup will attempt to remove the incomplete service and data directory.'
    }
}

function Assert-MySqlRuntimeConfigurationIsLocal(
    [string]$MysqlClient, [string]$RootPassword, $Ports) {
    $HadMysqlPwd = Test-Path Env:MYSQL_PWD
    $PreviousMysqlPwd = $env:MYSQL_PWD
    $PreviousErrorPreference = $ErrorActionPreference
    $PreviousNativeErrorPreference = $PSNativeCommandUseErrorActionPreference
    $Sql = 'SELECT @@global.bind_address, @@global.port, @@global.mysqlx_bind_address, @@global.mysqlx_port;'
    $PortArgument = "--port=$($Ports.Classic)"
    try {
        $env:MYSQL_PWD = $RootPassword
        $ErrorActionPreference = 'Continue'
        $PSNativeCommandUseErrorActionPreference = $false
        $Output = $Sql | & $MysqlClient --no-defaults --protocol=TCP --host=127.0.0.1 `
            $PortArgument --user=root --batch --skip-column-names 2>&1 | Out-String
        $ExitCode = $LASTEXITCODE
    } finally {
        if ($HadMysqlPwd) { $env:MYSQL_PWD = $PreviousMysqlPwd }
        else { Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue }
        $ErrorActionPreference = $PreviousErrorPreference
        $PSNativeCommandUseErrorActionPreference = $PreviousNativeErrorPreference
        $Sql = $null
    }

    if ($ExitCode -ne 0 -or [string]::IsNullOrWhiteSpace($Output)) {
        $Output = $null
        throw 'MySQL did not confirm loopback-only listeners for its classic and X Protocol ports. Inbound firewall blocks remain in place.'
    }
    $Values = @($Output.Trim() -split "`t")
    $Output = $null
    if ($Values.Count -ne 4 -or
        $Values[0] -ne '127.0.0.1' -or [int]$Values[1] -ne $Ports.Classic -or
        $Values[2] -ne '127.0.0.1' -or [int]$Values[3] -ne $Ports.XProtocol) {
        throw 'MySQL did not confirm loopback-only listeners for its classic and X Protocol ports. Inbound firewall blocks remain in place.'
    }
}

function Initialize-NewMySqlServer {
    $BaseDirectory = Join-Path $env:ProgramFiles 'MySQL/MySQL Server 8.4'
    $ServerBinary = Join-Path $BaseDirectory 'bin/mysqld.exe'
    $MysqlClient = Join-Path $BaseDirectory 'bin/mysql.exe'
    $ServerDirectory = Join-Path $env:ProgramData 'AdaptiveReadingGame/MySQL Server 8.4'
    $DataDirectory = Join-Path $ServerDirectory 'Data'
    $OptionFile = Join-Path $ServerDirectory 'my.ini'
    $ServiceName = 'AdaptiveReadingGameMySQL84'
    $ClassicPort = 3307
    $XProtocolPort = 33070
    $RootPassword = New-MySqlRootPassword
    $ServiceInstalled = $false
    $RootPasswordApplied = $false
    $MayRemoveNewData = $false
    $OptionFileCreated = $false
    $TemporaryPassword = $null
    $InitializationOutput = $null
    $PreviousErrorPreference = $ErrorActionPreference
    $PreviousNativeErrorPreference = $PSNativeCommandUseErrorActionPreference

    try {
        if (-not (Test-Path -LiteralPath $ServerBinary -PathType Leaf) -or
            -not (Test-Path -LiteralPath $MysqlClient -PathType Leaf)) {
            throw 'MySQL Server or its command-line client is missing. Rerun setup to install MySQL, then try again.'
        }
        if (Get-Service -Name $ServiceName -ErrorAction SilentlyContinue) {
            throw "$ServiceName already exists. Setup will not initialize over an existing service."
        }
        if (Test-Path -LiteralPath $OptionFile -PathType Leaf) {
            throw "A MySQL option file already exists at $OptionFile. Setup will not overwrite it."
        }
        if (Test-Path -LiteralPath $DataDirectory -PathType Container) {
            $ExistingData = @(Get-ChildItem -LiteralPath $DataDirectory -Force -ErrorAction Stop)
            if ($ExistingData.Count -gt 0) {
                throw "MySQL data already exists at $DataDirectory but no MySQL service is registered. Setup will not initialize over these files."
            }
            $MayRemoveNewData = $true
        } else {
            New-Item -ItemType Directory -Path $DataDirectory -Force | Out-Null
            $MayRemoveNewData = $true
        }

        $ErrorActionPreference = 'Continue'
        $PSNativeCommandUseErrorActionPreference = $false
        $InitializationOutput = & $ServerBinary --no-defaults --initialize --console `
            "--basedir=$BaseDirectory" "--datadir=$DataDirectory" 2>&1 | Out-String -Width 240
        $InitializationExitCode = $LASTEXITCODE
        $TemporaryPasswordMatch = [regex]::Match(
            $InitializationOutput,
            '(?im)temporary password is generated for root@localhost:\s*(.+?)\s*$')
        if ($InitializationExitCode -ne 0 -or -not $TemporaryPasswordMatch.Success) {
            throw 'MySQL could not initialize its new local data directory. Setup stopped before starting the service.'
        }
        $TemporaryPassword = $TemporaryPasswordMatch.Groups[1].Value.Trim()
        $InitializationOutput = $null
        $ErrorActionPreference = $PreviousErrorPreference
        $PSNativeCommandUseErrorActionPreference = $PreviousNativeErrorPreference

        New-Item -ItemType Directory -Path $ServerDirectory -Force | Out-Null
        $BaseOption = $BaseDirectory.Replace('\', '/')
        $DataOption = $DataDirectory.Replace('\', '/')
        $OptionContents = "[mysqld]`r`nbasedir=`"$BaseOption`"`r`ndatadir=`"$DataOption`"`r`nport=$ClassicPort`r`nmysqlx-port=$XProtocolPort`r`n"
        [IO.File]::WriteAllText($OptionFile, $OptionContents, [Text.UTF8Encoding]::new($false))
        $OptionFileCreated = $true
        $null = Set-MySqlLoopbackAddress -ConfigFile $OptionFile -ServiceName $ServiceName

        $DefaultsFileArgument = '--defaults-file="' + $OptionFile + '"'
        $ErrorActionPreference = 'Continue'
        $PSNativeCommandUseErrorActionPreference = $false
        $InstallOutput = & $ServerBinary --install $ServiceName $DefaultsFileArgument 2>&1 | Out-String
        $InstallExitCode = $LASTEXITCODE
        $InstallOutput = $null
        $ErrorActionPreference = $PreviousErrorPreference
        $PSNativeCommandUseErrorActionPreference = $PreviousNativeErrorPreference
        if ($InstallExitCode -ne 0) { throw 'MySQL could not register its local Windows service.' }
        $ServiceInstalled = $true

        Start-Service -Name $ServiceName
        (Get-Service -Name $ServiceName).WaitForStatus('Running', [TimeSpan]::FromSeconds(45))
        $Ports = Get-MySqlConfiguredPorts -ConfigFile $OptionFile -ServiceName $ServiceName
        Set-MySqlBootstrapRootPassword -MysqlClient $MysqlClient -Port $Ports.Classic `
            -TemporaryPassword $TemporaryPassword -NewPassword $RootPassword
        Assert-MySqlRuntimeConfigurationIsLocal -MysqlClient $MysqlClient -RootPassword $RootPassword -Ports $Ports
        $TemporaryPassword = $null
        Save-MySqlRootPassword -Password $RootPassword
        $RootPasswordApplied = $true
        Write-Host 'A dedicated MySQL service is ready for the game. Its administrator password is generated and protected for this Windows account.'
        return $RootPassword
    } catch {
        if (-not $RootPasswordApplied) {
            $CanRemoveFiles = -not $ServiceInstalled
            if ($ServiceInstalled) {
                Stop-Service -Name $ServiceName -Force -ErrorAction SilentlyContinue
                try { (Get-Service -Name $ServiceName).WaitForStatus('Stopped', [TimeSpan]::FromSeconds(20)) } catch { }
                $PreviousErrorPreference = $ErrorActionPreference
                $ErrorActionPreference = 'Continue'
                $PSNativeCommandUseErrorActionPreference = $false
                $RemoveOutput = & sc.exe delete $ServiceName 2>&1 | Out-String
                $RemoveExitCode = $LASTEXITCODE
                $RemoveOutput = $null
                $ErrorActionPreference = $PreviousErrorPreference
                Start-Sleep -Milliseconds 500
                $CanRemoveFiles = ($RemoveExitCode -eq 0 -and -not (Get-Service -Name $ServiceName -ErrorAction SilentlyContinue))
            }
            if ($CanRemoveFiles) {
                if ($OptionFileCreated -and (Test-Path -LiteralPath $OptionFile)) {
                    Remove-Item -LiteralPath $OptionFile -Force -ErrorAction SilentlyContinue
                }
                if ($MayRemoveNewData -and (Test-Path -LiteralPath $DataDirectory)) {
                    Remove-Item -LiteralPath $DataDirectory -Recurse -Force -ErrorAction SilentlyContinue
                }
                if ((Test-Path -LiteralPath $ServerDirectory -PathType Container) -and
                    @(Get-ChildItem -LiteralPath $ServerDirectory -Force -ErrorAction SilentlyContinue).Count -eq 0) {
                    Remove-Item -LiteralPath $ServerDirectory -Force -ErrorAction SilentlyContinue
                }
            } else {
                Write-Warning 'MySQL setup stopped before finishing the dedicated game service. The service is stopped when possible, and inbound firewall blocks remain in place.'
            }
        }
        throw
    } finally {
        $InitializationOutput = $null
        $TemporaryPassword = $null
        $ErrorActionPreference = $PreviousErrorPreference
        $PSNativeCommandUseErrorActionPreference = $PreviousNativeErrorPreference
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

$DatabaseConfigFile = Get-GameDatabaseConfigFile
if (-not $Reconfigure -and
    ($env:ARG_DB_URL -or $env:ARG_DB_USER -or [Environment]::GetEnvironmentVariables().Contains('ARG_DB_PASSWORD'))) {
    Write-Host 'Using the explicit local database environment settings.'
    Invoke-DatabaseWizard @()
    return
}
if (-not $Reconfigure -and (Test-Path -LiteralPath $DatabaseConfigFile -PathType Leaf)) {
    Write-Host 'Using the saved local database settings.'
    Invoke-DatabaseWizard @()
    return
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

$GameServiceName = 'AdaptiveReadingGameMySQL84'
$GameServerDirectory = Join-Path $env:ProgramData 'AdaptiveReadingGame/MySQL Server 8.4'
$GameOptionFile = Join-Path $GameServerDirectory 'my.ini'
$GameServerBinary = Join-Path $env:ProgramFiles 'MySQL/MySQL Server 8.4/bin/mysqld.exe'
$GameMysqlClient = Join-Path $env:ProgramFiles 'MySQL/MySQL Server 8.4/bin/mysql.exe'
$GamePorts = [pscustomobject]@{ Classic = 3307; XProtocol = 33070 }
$RootAdminPassword = $null
$HadSetupUrl = Test-Path Env:ARG_DB_SETUP_URL
$PreviousSetupUrl = $env:ARG_DB_SETUP_URL
$HadAdminUser = Test-Path Env:ARG_DB_ADMIN_USER
$PreviousAdminUser = $env:ARG_DB_ADMIN_USER
$HadAdminPassword = Test-Path Env:ARG_DB_ADMIN_PASSWORD
$PreviousAdminPassword = $env:ARG_DB_ADMIN_PASSWORD
$WizardArguments = @()
if ($Reconfigure) { $WizardArguments += '--reconfigure' }

try {
    if (-not (Test-Path -LiteralPath $GameServerBinary -PathType Leaf) -or
        -not (Test-Path -LiteralPath $GameMysqlClient -PathType Leaf)) {
        if (-not (Get-Command winget -ErrorAction SilentlyContinue)) {
            throw 'Install MySQL Server 8.4, then rerun this script.'
        }
        Write-Host 'Installing MySQL Server 8.4 binaries. Setup will create a separate game-only service and leave existing services alone.'
        & winget install --id Oracle.MySQL --exact --accept-source-agreements --accept-package-agreements --silent
        if ($LASTEXITCODE -ne 0 -and
            (-not (Test-Path -LiteralPath $GameServerBinary -PathType Leaf) -or
             -not (Test-Path -LiteralPath $GameMysqlClient -PathType Leaf))) {
            throw 'WinGet could not install MySQL Server. Check the WinGet message above, then rerun setup.'
        }
    } else {
        Write-Host 'MySQL Server 8.4 is installed. Setup will use its binaries for the separate game-only service.'
    }

    $Service = Get-Service -Name $GameServiceName -ErrorAction SilentlyContinue
    if (-not $Service) {
        Assert-MySqlPortsAvailable -Ports @($GamePorts.Classic, $GamePorts.XProtocol)
        Add-MySqlSetupFirewallGuard -Ports @($GamePorts.Classic, $GamePorts.XProtocol) | Out-Null
        Write-Host 'Creating the dedicated local game database service. Existing MySQL services and databases will not be changed.'
        $RootAdminPassword = Initialize-NewMySqlServer
    } else {
        Add-MySqlSetupFirewallGuard -Ports @($GamePorts.Classic, $GamePorts.XProtocol) | Out-Null
        $OptionFile = Get-MySqlOptionFile -ServiceName $GameServiceName
        if ([IO.Path]::GetFullPath($OptionFile) -ine [IO.Path]::GetFullPath($GameOptionFile)) {
            throw "$GameServiceName does not use the dedicated game option file. Setup will not modify that service."
        }
        $RootAdminPassword = Read-MySqlRootPassword
        $ConfiguredPorts = Get-MySqlConfiguredPorts -ConfigFile $OptionFile -ServiceName $GameServiceName
        if ($ConfiguredPorts.Classic -ne $GamePorts.Classic -or
            $ConfiguredPorts.XProtocol -ne $GamePorts.XProtocol) {
            throw 'The dedicated game database service has unexpected ports. Setup will not change an existing service.'
        }
        $BindConfigurationChanged = Set-MySqlLoopbackAddress -ConfigFile $OptionFile -ServiceName $GameServiceName
        if ($Service.Status -eq 'Running' -and $BindConfigurationChanged) {
            Restart-Service -Name $GameServiceName
            (Get-Service -Name $GameServiceName).WaitForStatus('Running', [TimeSpan]::FromSeconds(45))
        } elseif ($Service.Status -ne 'Running') {
            Start-Service -Name $GameServiceName
            (Get-Service -Name $GameServiceName).WaitForStatus('Running', [TimeSpan]::FromSeconds(45))
        }
        Assert-MySqlRuntimeConfigurationIsLocal -MysqlClient $GameMysqlClient `
            -RootPassword $RootAdminPassword -Ports $GamePorts
    }

    $Service = Get-Service -Name $GameServiceName -ErrorAction Stop
    if ($Service.Status -ne 'Running') {
        Start-Service -Name $GameServiceName
        (Get-Service -Name $GameServiceName).WaitForStatus('Running', [TimeSpan]::FromSeconds(45))
    }
    Assert-MySqlRuntimeConfigurationIsLocal -MysqlClient $GameMysqlClient `
        -RootPassword $RootAdminPassword -Ports $GamePorts
    Remove-MySqlSetupFirewallGuard

    $env:ARG_DB_SETUP_URL = 'jdbc:mysql://127.0.0.1:3307/adaptive_reading'
    $env:ARG_DB_ADMIN_USER = 'root'
    $env:ARG_DB_ADMIN_PASSWORD = $RootAdminPassword
    Write-Host 'The game database login is generated automatically and saved for this Windows user.'
    Invoke-DatabaseWizard $WizardArguments
} finally {
    Remove-MySqlSetupFirewallGuard
    if ($HadSetupUrl) { $env:ARG_DB_SETUP_URL = $PreviousSetupUrl }
    else { Remove-Item Env:ARG_DB_SETUP_URL -ErrorAction SilentlyContinue }
    if ($HadAdminUser) { $env:ARG_DB_ADMIN_USER = $PreviousAdminUser }
    else { Remove-Item Env:ARG_DB_ADMIN_USER -ErrorAction SilentlyContinue }
    if ($HadAdminPassword) { $env:ARG_DB_ADMIN_PASSWORD = $PreviousAdminPassword }
    else { Remove-Item Env:ARG_DB_ADMIN_PASSWORD -ErrorAction SilentlyContinue }
    $RootAdminPassword = $null
}
