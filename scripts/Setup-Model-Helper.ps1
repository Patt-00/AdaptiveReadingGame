param([switch]$CheckOnly)

$ErrorActionPreference = 'Stop'
$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$Venv = Join-Path $ProjectRoot 'model-tool/.venv'
$VenvPython = Join-Path $Venv 'Scripts/python.exe'

function Invoke-NativeCapture([string]$Executable, [string[]]$Arguments) {
    $ErrorActionPreference = 'Continue'
    $PSNativeCommandUseErrorActionPreference = $false
    $Output = (& $Executable @Arguments 2>&1 | Out-String)
    [pscustomobject]@{ Output = $Output; ExitCode = $LASTEXITCODE }
}

function Test-ModelEnvironment {
    if (-not (Test-Path -LiteralPath $VenvPython -PathType Leaf)) { return $false }
    $Result = Invoke-NativeCapture $VenvPython @('-c', 'import torch, transformers, huggingface_hub')
    return $Result.ExitCode -eq 0
}

if ($env:ARG_DISABLE_MODEL_HELPER -eq '1') {
    Write-Host 'Model helper setup skipped by ARG_DISABLE_MODEL_HELPER.'
    return 1
}

if (Test-ModelEnvironment) {
    Write-Host 'Model helper dependencies are ready.'
    return 0
}
if ($CheckOnly) {
    Write-Host 'Model helper dependencies are not ready.'
    return 1
}

$PythonCommand = $null
$PythonPrefix = @()
$PythonLauncher = Get-Command py -ErrorAction SilentlyContinue
if ($PythonLauncher) {
    foreach ($Version in @('-3.14', '-3')) {
        $Candidate = Invoke-NativeCapture $PythonLauncher.Source @($Version, '-c', 'import sys; print(sys.executable)')
        if ($Candidate.ExitCode -eq 0 -and -not [string]::IsNullOrWhiteSpace($Candidate.Output)) {
            $PythonCommand = $PythonLauncher.Source
            $PythonPrefix = @($Version)
            break
        }
    }
}
if (-not $PythonCommand) {
    $Python = Get-Command python -ErrorAction SilentlyContinue
    if ($Python) { $PythonCommand = $Python.Source }
}

if (-not $PythonCommand -and (Get-Command winget -ErrorAction SilentlyContinue)) {
    Write-Host 'Installing Python 3.14 for the model helper.'
    & winget install --id Python.Python.3.14 --exact --accept-source-agreements --accept-package-agreements
    if ($LASTEXITCODE -eq 0) {
        $MachinePath = [Environment]::GetEnvironmentVariable('Path', 'Machine')
        $UserPath = [Environment]::GetEnvironmentVariable('Path', 'User')
        $env:Path = "$MachinePath;$UserPath"
        $PythonLauncher = Get-Command py -ErrorAction SilentlyContinue
        if ($PythonLauncher) {
            $PythonCommand = $PythonLauncher.Source
            $PythonPrefix = @('-3.14')
        } else {
            $Python = Get-Command python -ErrorAction SilentlyContinue
            if ($Python) { $PythonCommand = $Python.Source }
        }
    }
}

if (-not $PythonCommand) {
    Write-Host 'Python is unavailable; the game will use Java difficulty fallback.'
    return 1
}

New-Item -ItemType Directory -Force -Path (Split-Path -Parent $Venv) | Out-Null
$Create = Invoke-NativeCapture $PythonCommand ($PythonPrefix + @('-m', 'venv', $Venv))
if ($Create.ExitCode -ne 0 -or -not (Test-Path -LiteralPath $VenvPython -PathType Leaf)) {
    Write-Host 'Could not create the model helper environment; Java fallback remains available.'
    return 1
}

$RequirementsCpu = Join-Path $ProjectRoot 'model-tool/requirements-cpu.txt'
$Requirements = Join-Path $ProjectRoot 'model-tool/requirements.txt'
$TorchInstall = Invoke-NativeCapture $VenvPython @('-m', 'pip', 'install', '--disable-pip-version-check', '--no-cache-dir', '--index-url', 'https://download.pytorch.org/whl/cpu', '-r', $RequirementsCpu)
if ($TorchInstall.ExitCode -ne 0) {
    Write-Host 'CPU PyTorch installation failed; the game will use Java difficulty fallback.'
    return 1
}
$OtherInstall = Invoke-NativeCapture $VenvPython @('-m', 'pip', 'install', '--disable-pip-version-check', '--no-cache-dir', '-r', $Requirements)
if ($OtherInstall.ExitCode -ne 0 -or -not (Test-ModelEnvironment)) {
    Write-Host 'Model helper dependencies could not be verified; the game will use Java difficulty fallback.'
    return 1
}

Write-Host 'Model helper dependencies are ready.'
return 0
