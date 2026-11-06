function Get-MySqlRootSecretFile {
    if ([string]::IsNullOrWhiteSpace($env:APPDATA)) {
        throw 'Windows did not provide the current user''s AppData folder.'
    }
    return Join-Path $env:APPDATA 'AdaptiveReadingGame/mysql-root.dpapi'
}

function Set-CurrentUserFileAcl([string]$Path) {
    $Identity = [Security.Principal.WindowsIdentity]::GetCurrent()
    $Acl = Get-Acl -LiteralPath $Path
    $Acl.SetAccessRuleProtection($true, $false)
    foreach ($ExistingRule in @($Acl.Access)) {
        $null = $Acl.RemoveAccessRuleSpecific($ExistingRule)
    }
    $Rule = [Security.AccessControl.FileSystemAccessRule]::new(
        $Identity.User, [Security.AccessControl.FileSystemRights]::FullControl,
        [Security.AccessControl.AccessControlType]::Allow)
    $Acl.SetAccessRule($Rule)
    Set-Acl -LiteralPath $Path -AclObject $Acl
}

function New-MySqlRootPassword {
    $RandomBytes = New-Object byte[] 32
    $Generator = [Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $Generator.GetBytes($RandomBytes)
        return [Convert]::ToBase64String($RandomBytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
    } finally {
        [Array]::Clear($RandomBytes, 0, $RandomBytes.Length)
        $Generator.Dispose()
    }
}

function Save-MySqlRootPassword([string]$Password) {
    $SecretFile = Get-MySqlRootSecretFile
    $SecretDirectory = Split-Path -Parent $SecretFile
    New-Item -ItemType Directory -Path $SecretDirectory -Force | Out-Null
    $SecurePassword = ConvertTo-SecureString -String $Password -AsPlainText -Force
    $EncryptedPassword = ConvertFrom-SecureString -SecureString $SecurePassword
    $TemporaryFile = Join-Path $SecretDirectory ('.mysql-root-' + [guid]::NewGuid().ToString('N') + '.tmp')
    try {
        [IO.File]::WriteAllText($TemporaryFile, $EncryptedPassword, [Text.UTF8Encoding]::new($false))
        Set-CurrentUserFileAcl -Path $TemporaryFile
        Move-Item -LiteralPath $TemporaryFile -Destination $SecretFile -Force
        Set-CurrentUserFileAcl -Path $SecretFile
    } finally {
        if ($SecurePassword) { $SecurePassword.Dispose() }
        if (Test-Path -LiteralPath $TemporaryFile) {
            Remove-Item -LiteralPath $TemporaryFile -Force -ErrorAction SilentlyContinue
        }
        $EncryptedPassword = $null
    }
}

function Read-MySqlRootPassword {
    $SecretFile = Get-MySqlRootSecretFile
    if (-not (Test-Path -LiteralPath $SecretFile -PathType Leaf)) {
        throw 'The dedicated game database service exists, but its saved administrator secret is missing. Setup will not reset it or change another MySQL service.'
    }
    try {
        $EncryptedPassword = [IO.File]::ReadAllText($SecretFile).Trim()
        $SecurePassword = ConvertTo-SecureString -String $EncryptedPassword
        $Pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($SecurePassword)
        try {
            return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($Pointer)
        } finally {
            if ($Pointer -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($Pointer) }
            $SecurePassword.Dispose()
            $EncryptedPassword = $null
        }
    } catch {
        throw 'The saved MySQL administrator secret cannot be opened by this Windows account. Run setup under the Windows account that first configured the game database.'
    }
}
