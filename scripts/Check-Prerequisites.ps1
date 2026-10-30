param(
    [switch]$CheckOnly
)

$ErrorActionPreference = 'Stop'
$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path

function Stop-PrerequisiteCheck([string]$Message) {
    Write-Error "Prerequisite check failed: $Message"
    throw "Prerequisite check failed: $Message"
}

function Get-NativeOutput([string]$Executable, [string[]]$Arguments) {
    $PreviousPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    $PSNativeCommandUseErrorActionPreference = $false
    try {
        $Output = (& $Executable @Arguments 2>&1 | Out-String)
        $script:NativeExitCode = $LASTEXITCODE
        return $Output
    } finally {
        $ErrorActionPreference = $PreviousPreference
    }
}

if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    Stop-PrerequisiteCheck 'Install JDK 21 or newer and add java to PATH.'
}
$JavaInfo = Get-NativeOutput 'java' @('-version')
if ($NativeExitCode -ne 0 -or $JavaInfo -notmatch 'version "([0-9]+)') {
    Stop-PrerequisiteCheck 'Could not read the Java version. Run java -version to check your JDK.'
}
$JavaMajor = [int]$Matches[1]
if ($JavaMajor -lt 21) {
    Stop-PrerequisiteCheck "JDK 21 or newer is required; found Java $JavaMajor."
}
Write-Host "OK: Java $JavaMajor"

if (-not (Get-Command javac -ErrorAction SilentlyContinue)) {
    Stop-PrerequisiteCheck 'A JDK compiler is required. Install JDK 21 or newer; a JRE alone is not enough.'
}
$JavacInfo = Get-NativeOutput 'javac' @('-version')
if ($NativeExitCode -ne 0 -or $JavacInfo -notmatch 'javac ([0-9]+)') {
    Stop-PrerequisiteCheck 'Could not read the Java compiler version. Run javac -version to check your JDK.'
}
$JavacMajor = [int]$Matches[1]
if ($JavacMajor -lt 21) {
    Stop-PrerequisiteCheck "JDK 21 or newer is required; found javac $JavacMajor."
}
Write-Host "OK: javac $JavacMajor"

if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
    Stop-PrerequisiteCheck 'Install Maven and add mvn to PATH.'
}
$MavenInfo = Get-NativeOutput 'mvn' @('-version')
if ($NativeExitCode -ne 0) {
    Stop-PrerequisiteCheck 'Maven could not start. Check its Java setup.'
}
if ($MavenInfo -notmatch '(?m)^Java version: ([0-9]+).*runtime: (.+)\s*$') {
    Stop-PrerequisiteCheck 'Could not identify the Java runtime Maven uses. Run mvn -version.'
}
$MavenJava = [int]$Matches[1]
$MavenRuntime = $Matches[2].Trim()
if ($MavenJava -lt 21) {
    Stop-PrerequisiteCheck "Maven uses Java $MavenJava; configure JAVA_HOME to a JDK 21 or newer."
}
if (-not (Test-Path -LiteralPath (Join-Path $MavenRuntime 'bin/javac.exe') -PathType Leaf) -and
    -not (Test-Path -LiteralPath (Join-Path $MavenRuntime 'bin/javac') -PathType Leaf)) {
    Stop-PrerequisiteCheck 'Maven must use a JDK with javac. Check JAVA_HOME and mvn -version.'
}
$MavenVersionLine = ($MavenInfo -split '\r?\n' | Where-Object { $_ -match '^Apache Maven ' } | Select-Object -First 1)
Write-Host "OK: $MavenVersionLine (Maven uses JDK $MavenJava)"

if (-not (Test-Path -LiteralPath (Join-Path $ProjectRoot 'pom.xml') -PathType Leaf)) {
    Stop-PrerequisiteCheck 'pom.xml is missing. Run this script from a project checkout.'
}

if (-not [string]::IsNullOrWhiteSpace($env:ARG_CONTENT_PACK)) {
    if ([IO.Path]::IsPathRooted($env:ARG_CONTENT_PACK)) {
        $StoryPack = $env:ARG_CONTENT_PACK
    } else {
        $StoryPack = Join-Path $ProjectRoot $env:ARG_CONTENT_PACK
    }
    if (-not (Test-Path -LiteralPath $StoryPack -PathType Leaf)) {
        Stop-PrerequisiteCheck "ARG_CONTENT_PACK does not point to a file: $StoryPack"
    }
    Write-Host "OK: Story pack: $StoryPack"
} else {
    $StoryPack = Join-Path $ProjectRoot 'assets/content/hailey-investigation-complete.json'
    if (-not (Test-Path -LiteralPath $StoryPack -PathType Leaf)) {
        Stop-PrerequisiteCheck "Default story pack is missing: $StoryPack. Restore it or set ARG_CONTENT_PACK."
    }
    Write-Host "OK: Default story pack: $StoryPack"
}

$PasswordSet = [Environment]::GetEnvironmentVariables().Contains('ARG_DB_PASSWORD')
if ([string]::IsNullOrWhiteSpace($env:ARG_DB_URL) -and [string]::IsNullOrWhiteSpace($env:ARG_DB_USER) -and -not $PasswordSet) {
    Write-Host 'OK: No MySQL settings found; the game will use temporary in-memory storage.'
} else {
    if ($env:ARG_DB_URL -cnotlike 'jdbc:mysql://*') { Stop-PrerequisiteCheck 'ARG_DB_URL must be a MySQL JDBC URL beginning jdbc:mysql://.' }
    if ([string]::IsNullOrWhiteSpace($env:ARG_DB_USER)) { Stop-PrerequisiteCheck 'ARG_DB_USER is required when MySQL is configured.' }
    if (-not $PasswordSet) { Stop-PrerequisiteCheck 'ARG_DB_PASSWORD must be set when MySQL is configured.' }
    Write-Host 'OK: MySQL settings are present. Connectivity and migrations were not checked; apply V001 through V005 before launching.'
}

$AssetRoot = if ([string]::IsNullOrWhiteSpace($env:ARG_ASSET_DIR)) { Join-Path $ProjectRoot 'assets' }
    elseif ([IO.Path]::IsPathRooted($env:ARG_ASSET_DIR)) { $env:ARG_ASSET_DIR }
    else { Join-Path $ProjectRoot $env:ARG_ASSET_DIR }
$AssetPaths = @((Join-Path $AssetRoot 'characters'), (Join-Path $AssetRoot 'scenes'), (Join-Path $AssetRoot 'cg'))
$AssetFiles = foreach ($Path in $AssetPaths) {
    if (Test-Path -LiteralPath $Path -PathType Container) { Get-ChildItem -LiteralPath $Path -File }
}
$MusicFile = Join-Path $ProjectRoot 'src/main/resources/com/adaptivereadinggame/audio/comfortable-mystery.wav'
if (Test-Path -LiteralPath $MusicFile -PathType Leaf) { $AssetFiles = @($AssetFiles) + (Get-Item -LiteralPath $MusicFile) }
$PointerCount = @($AssetFiles | Where-Object { $_.Length -le 512 -and (Get-Content -LiteralPath $_.FullName -TotalCount 1) -eq 'version https://git-lfs.github.com/spec/v1' }).Count
if ($PointerCount -gt 0) { Write-Warning "$PointerCount artwork or music files are Git LFS pointers; fetch game assets with Git LFS for the full presentation." }
foreach ($Track in @((Join-Path $AssetRoot 'local-music/investigation.wav'), (Join-Path $AssetRoot 'local-music/tense.wav'))) {
    if (-not (Test-Path -LiteralPath $Track -PathType Leaf)) { Write-Host "Info: Optional story music is missing: $Track" }
}
$ModelWeights = Join-Path $ProjectRoot 'model-tool/models/Qwen2.5-0.5B-Instruct/model.safetensors'
if (-not (Test-Path -LiteralPath $ModelWeights -PathType Leaf) -or (Get-Item -LiteralPath $ModelWeights).Length -le 512) {
    Write-Host 'Info: Optional Qwen weights are unavailable locally; Java keeps its fallback unless the helper can load the pinned model.'
}
Write-Host 'Info: The optional model helper is started separately; see model-tool/README.md.'

if ($CheckOnly) {
    Write-Host 'All startup prerequisites passed.'
    return
}
