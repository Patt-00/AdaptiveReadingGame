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

function Assert-MySqlDefaultPortsAvailable {
    $Listeners = @(Get-MySqlTcpListeners -Ports @(3306, 33060))
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

    $VersionedOptionFile = Join-Path $env:ProgramData 'MySQL/MySQL Server 8.4/my.ini'
    if (Test-Path -LiteralPath $VersionedOptionFile -PathType Leaf) { return $VersionedOptionFile }
    throw "Could not locate the MySQL option file used by $ServiceName. Finish MySQL setup, then rerun this script."
}

function Read-NewMySqlRootPassword {
    Write-Host 'Choose a MySQL administrator password. This password is not saved by the game.'
    $FirstSecure = Read-Host 'New MySQL root password' -AsSecureString
    $SecondSecure = Read-Host 'Confirm MySQL root password' -AsSecureString
    $FirstPointer = [IntPtr]::Zero
    $SecondPointer = [IntPtr]::Zero
    try {
        $FirstPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($FirstSecure)
        $SecondPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($SecondSecure)
        $RootPassword = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($FirstPointer)
        $ConfirmedPassword = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($SecondPointer)
    } finally {
        if ($FirstPointer -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($FirstPointer) }
        if ($SecondPointer -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($SecondPointer) }
        if ($FirstSecure) { $FirstSecure.Dispose() }
        if ($SecondSecure) { $SecondSecure.Dispose() }
    }
    if ([string]::IsNullOrWhiteSpace($RootPassword) -or $RootPassword -cne $ConfirmedPassword) {
        $RootPassword = $null
        $ConfirmedPassword = $null
        throw 'The MySQL root password must be non-empty and match its confirmation.'
    }
    $ConfirmedPassword = $null
    return $RootPassword
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

function Initialize-NewMySqlServer([string]$RootPassword) {
    $BaseDirectory = Join-Path $env:ProgramFiles 'MySQL/MySQL Server 8.4'
    $ServerBinary = Join-Path $BaseDirectory 'bin/mysqld.exe'
    $MysqlClient = Join-Path $BaseDirectory 'bin/mysql.exe'
    $ServerDirectory = Join-Path $env:ProgramData 'MySQL/MySQL Server 8.4'
    $DataDirectory = Join-Path $ServerDirectory 'Data'
    $OptionFile = Join-Path $ServerDirectory 'my.ini'
    $ServiceName = 'MySQL84'
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
            throw "$ServiceName already exists. Rerun setup so it can use the existing MySQL service."
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
        $OptionContents = "[mysqld]`r`nbasedir=`"$BaseOption`"`r`ndatadir=`"$DataOption`"`r`nport=3306`r`nmysqlx-port=33060`r`n"
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
        $RootPasswordApplied = $true
        $TemporaryPassword = $null
        Write-Host 'MySQL is initialized as a local service and only accepts connections from this device.'
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
                Write-Warning 'MySQL setup stopped before setting the administrator password. The service is stopped when possible, and inbound firewall blocks remain in place.'
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
    $MySqlServerDirectory = Join-Path $env:ProgramFiles 'MySQL/MySQL Server 8.4'
    $ExistingServerBinary = Join-Path $MySqlServerDirectory 'bin/mysqld.exe'
    $ExistingServerIsInstalled = Test-Path -LiteralPath $ExistingServerBinary -PathType Leaf

    if (-not $ExistingServerIsInstalled) {
        if (-not (Get-Command winget -ErrorAction SilentlyContinue)) {
            throw 'Install MySQL Server 8.4, then rerun this script.'
        }
        Write-Host 'Installing MySQL Server silently so no setup window opens. Setup will initialize it with loopback-only access.'
        & winget install --id Oracle.MySQL --exact --accept-source-agreements --accept-package-agreements --silent
        if ($LASTEXITCODE -ne 0 -and -not (Test-Path -LiteralPath $ExistingServerBinary -PathType Leaf)) {
            throw 'WinGet could not complete MySQL installation. Check the WinGet message above, then rerun setup.'
        }
    } else {
        Write-Host 'An existing MySQL Server 8.4 installation was found. Reusing it without asking WinGet to upgrade it.'
    }
    Assert-MySqlDefaultPortsAvailable
    Add-MySqlSetupFirewallGuard -Ports @(3306, 33060) | Out-Null
    $RootAdminPassword = Read-NewMySqlRootPassword
    Initialize-NewMySqlServer -RootPassword $RootAdminPassword
    $Services = @(Get-Service -Name 'MySQL*' -ErrorAction SilentlyContinue | Sort-Object Name -Descending)
}
if ($Services.Count -eq 0) { throw 'MySQL setup did not register a Windows service. Resolve the displayed issue, then rerun setup.' }

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

if ($RootAdminPassword) {
    $MysqlClient = Join-Path $env:ProgramFiles 'MySQL/MySQL Server 8.4/bin/mysql.exe'
    Assert-MySqlRuntimeConfigurationIsLocal -MysqlClient $MysqlClient `
        -RootPassword $RootAdminPassword -Ports $MySqlPorts
} else {
    Assert-MySqlListenersAreLocal $MySqlPorts
}
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
