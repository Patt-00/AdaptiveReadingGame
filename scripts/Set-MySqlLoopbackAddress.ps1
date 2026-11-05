function Set-MySqlLoopbackAddress {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)]
        [string]$ConfigFile,

        [Parameter(Mandatory = $true)]
        [string]$ServiceName
    )

    if (-not (Test-Path -LiteralPath $ConfigFile -PathType Leaf)) {
        throw "MySQL option file was not found: $ConfigFile"
    }
    if ($ServiceName -notmatch '^[A-Za-z0-9_-]+$') {
        throw 'The MySQL service name contains characters that cannot be used as an option-file group.'
    }

    $Contents = [IO.File]::ReadAllText($ConfigFile)
    $Marker = '# AdaptiveReadingGame managed local bind'
    if ($Contents.Contains($Marker)) {
        $ManagedSection = $Contents.Substring($Contents.IndexOf($Marker, [System.StringComparison]::Ordinal))
        $HasDefaultGroup = $ManagedSection -match '(?ims)^\[mysqld\]\s*\r?\n(?:(?!^\[).)*?^\s*bind-address\s*=\s*127\.0\.0\.1\s*$'
        $HasDefaultXGroup = $ManagedSection -match '(?ims)^\[mysqld\]\s*\r?\n(?:(?!^\[).)*?^\s*mysqlx-bind-address\s*=\s*127\.0\.0\.1\s*$'
        $ServicePattern = '(?ims)^\[' + [regex]::Escape($ServiceName) + '\]\s*\r?\n(?:(?!^\[).)*?^\s*bind-address\s*=\s*127\.0\.0\.1\s*$'
        $ServiceXPattern = '(?ims)^\[' + [regex]::Escape($ServiceName) + '\]\s*\r?\n(?:(?!^\[).)*?^\s*mysqlx-bind-address\s*=\s*127\.0\.0\.1\s*$'
        if ($HasDefaultGroup -and $HasDefaultXGroup -and $ManagedSection -match $ServicePattern -and $ManagedSection -match $ServiceXPattern) { return $false }
        throw 'The existing Adaptive Reading Game MySQL bind settings were changed; restore loopback settings before rerunning setup.'
    }

    $LineEnding = "`r`n"
    if ($Contents.Length -gt 0 -and -not $Contents.EndsWith("`n")) { $Contents += $LineEnding }
    $Block = $Marker + $LineEnding + '[mysqld]' + $LineEnding
    $Block += 'bind-address=127.0.0.1' + $LineEnding + 'mysqlx-bind-address=127.0.0.1' + $LineEnding
    $Block += '[' + $ServiceName + ']' + $LineEnding
    $Block += 'bind-address=127.0.0.1' + $LineEnding + 'mysqlx-bind-address=127.0.0.1' + $LineEnding
    [IO.File]::WriteAllText($ConfigFile, $Contents + $LineEnding + $Block, [Text.UTF8Encoding]::new($false))
    return $true
}
