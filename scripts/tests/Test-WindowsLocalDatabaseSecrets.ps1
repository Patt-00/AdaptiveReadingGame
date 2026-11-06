$ErrorActionPreference = 'Stop'
. (Join-Path (Split-Path $PSScriptRoot -Parent) 'WindowsLocalDatabaseSecrets.ps1')

$OriginalAppData = $env:APPDATA
$TestAppData = Join-Path ([IO.Path]::GetTempPath()) ('arg-secrets-' + [guid]::NewGuid().ToString('N'))
try {
    $env:APPDATA = $TestAppData
    $Password = New-MySqlRootPassword
    if ($Password -notmatch '^[A-Za-z0-9_-]{43}$') {
        throw 'Generated MySQL administrator passwords must use 32 random bytes encoded as safe text.'
    }

    Save-MySqlRootPassword -Password $Password
    $SecretFile = Get-MySqlRootSecretFile
    $StoredValue = [IO.File]::ReadAllText($SecretFile)
    if ([string]::IsNullOrWhiteSpace($StoredValue) -or $StoredValue.Contains($Password)) {
        throw 'The saved MySQL administrator password must be protected, not stored as plain text.'
    }
    if ((Read-MySqlRootPassword) -cne $Password) {
        throw 'The saved MySQL administrator password could not be recovered for its Windows user.'
    }

    $Acl = Get-Acl -LiteralPath $SecretFile
    if (-not $Acl.AreAccessRulesProtected -or $Acl.Access.Count -ne 1 -or
        $Acl.Access[0].IdentityReference -ne [Security.Principal.WindowsIdentity]::GetCurrent().Name) {
        throw 'The protected password file must have an explicit ACL for the current Windows user only.'
    }
    Write-Output 'Windows local database secret test passed.'
} finally {
    $Password = $null
    $env:APPDATA = $OriginalAppData
    if (Test-Path -LiteralPath $TestAppData) {
        Remove-Item -LiteralPath $TestAppData -Recurse -Force -ErrorAction SilentlyContinue
    }
}
