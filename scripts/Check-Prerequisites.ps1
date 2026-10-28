param(
    [switch]$CheckOnly
)

$ErrorActionPreference = 'Stop'
$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path

function Stop-PrerequisiteCheck([string]$Message) {
    Write-Error "Prerequisite check failed: $Message"
    throw "Prerequisite check failed: $Message"
}

if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    Stop-PrerequisiteCheck 'Install JDK 21 or newer and add java to PATH.'
}
$JavaInfo = (& java -version 2>&1 | Out-String)
if ($LASTEXITCODE -ne 0 -or $JavaInfo -notmatch 'version "([0-9]+)') {
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
$JavacInfo = (& javac -version 2>&1 | Out-String)
if ($LASTEXITCODE -ne 0 -or $JavacInfo -notmatch 'javac ([0-9]+)') {
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
$MavenInfo = (& mvn -version 2>&1 | Out-String)
if ($LASTEXITCODE -ne 0) {
    Stop-PrerequisiteCheck 'Maven could not start. Check its Java setup.'
}
$MavenVersionLine = ($MavenInfo -split '\r?\n')[0]
Write-Host "OK: $MavenVersionLine"

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

$DbVariables = @($env:ARG_DB_URL, $env:ARG_DB_USER, $env:ARG_DB_PASSWORD)
$DbSetCount = @($DbVariables | Where-Object { -not [string]::IsNullOrWhiteSpace($_) }).Count
if ($DbSetCount -eq 0) {
    Write-Host 'OK: No MySQL settings found; the game will use temporary in-memory storage.'
} elseif ($DbSetCount -eq 3) {
    Write-Host 'OK: MySQL settings are present. Confirm migrations V001 through V005 have been applied before launching.'
} else {
    Stop-PrerequisiteCheck 'Set all three variables ARG_DB_URL, ARG_DB_USER, and ARG_DB_PASSWORD, or clear all three to use temporary storage.'
}

if ($CheckOnly) {
    Write-Host 'All startup prerequisites passed.'
    return
}
