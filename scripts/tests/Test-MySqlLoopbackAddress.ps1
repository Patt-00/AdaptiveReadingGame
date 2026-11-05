$ErrorActionPreference = 'Stop'
$Helper = Join-Path (Split-Path $PSScriptRoot -Parent) 'Set-MySqlLoopbackAddress.ps1'
. $Helper

$ConfigFile = Join-Path ([IO.Path]::GetTempPath()) ("arg-mysql-loopback-" + [guid]::NewGuid().ToString('N') + '.ini')
try {
    [IO.File]::WriteAllText($ConfigFile, "[mysqld]`r`nport=3306`r`n")

    $Changed = Set-MySqlLoopbackAddress -ConfigFile $ConfigFile -ServiceName 'MySQL84'
    if (-not $Changed) { throw 'The first call should add the loopback setting.' }
    $Configured = [IO.File]::ReadAllText($ConfigFile)
    if ($Configured -notmatch '(?im)^bind-address\s*=\s*127\.0\.0\.1\s*$') {
        throw 'The config file does not bind the main MySQL service to loopback.'
    }
    if ($Configured -notmatch '(?im)^mysqlx-bind-address\s*=\s*127\.0\.0\.1\s*$') {
        throw 'The config file does not bind MySQL X Protocol to loopback.'
    }
    if ($Configured -notmatch '(?im)^skip-networking\s*=\s*OFF\s*$') {
        throw 'The config file does not enable the local TCP listener after safe initial configuration.'
    }
    if ($Configured -notmatch '(?im)^\[MySQL84\]\s*$') {
        throw 'The config file does not add the Windows service option group.'
    }
    if (([regex]::Matches($Configured, '(?im)^bind-address\s*=\s*127\.0\.0\.1\s*$')).Count -lt 2) {
        throw 'Both the default and named service groups must be bound to loopback.'
    }
    if (([regex]::Matches($Configured, '(?im)^mysqlx-bind-address\s*=\s*127\.0\.0\.1\s*$')).Count -lt 2) {
        throw 'Both the default and named service groups must restrict MySQL X Protocol.'
    }
    if (([regex]::Matches($Configured, '(?im)^skip-networking\s*=\s*OFF\s*$')).Count -lt 2) {
        throw 'Both the default and named service groups must allow the local TCP connection.'
    }

    $ChangedAgain = Set-MySqlLoopbackAddress -ConfigFile $ConfigFile -ServiceName 'MySQL84'
    if ($ChangedAgain) { throw 'The second call should not duplicate the loopback settings.' }
    if ([IO.File]::ReadAllText($ConfigFile) -ne $Configured) {
        throw 'The second call changed an already configured option file.'
    }

    Write-Output 'MySQL loopback config test passed.'
} finally {
    Remove-Item -LiteralPath $ConfigFile -Force -ErrorAction SilentlyContinue
}
