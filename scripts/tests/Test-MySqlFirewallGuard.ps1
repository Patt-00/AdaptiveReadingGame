$ErrorActionPreference = 'Stop'
$script:MockFirewallRules = @()
$script:MockFirewallEnabled = $true

function Get-NetFirewallProfile {
    param([string[]]$Profile)
    foreach ($Name in $Profile) { [pscustomobject]@{ Name = $Name; Enabled = $script:MockFirewallEnabled } }
}

function Get-NetFirewallRule {
    [CmdletBinding()]
    param([string]$DisplayName, [string]$PolicyStore)
    @($script:MockFirewallRules | Where-Object DisplayName -eq $DisplayName)
}

function New-NetFirewallRule {
    param([string]$DisplayName, [string]$Group, [string]$Description, [string]$Direction,
        [string]$Action, [string]$Protocol, [string]$LocalPort, [string]$Profile, [string]$Enabled)
    $Rule = [pscustomobject]@{ DisplayName = $DisplayName; Group = $Group; Description = $Description;
        Direction = $Direction; Action = $Action; Protocol = $Protocol; LocalPort = $LocalPort;
        Profile = $Profile; Enabled = $Enabled }
    $script:MockFirewallRules += $Rule
    $Rule
}

function Get-NetFirewallPortFilter {
    param([object]$AssociatedNetFirewallRule)
    [pscustomobject]@{ Protocol = $AssociatedNetFirewallRule.Protocol; LocalPort = $AssociatedNetFirewallRule.LocalPort }
}

function Remove-NetFirewallRule {
    [CmdletBinding()]
    param([Parameter(ValueFromPipelineByPropertyName = $true)][string]$DisplayName)
    process { $script:MockFirewallRules = @($script:MockFirewallRules | Where-Object DisplayName -ne $DisplayName) }
}

. (Join-Path (Split-Path $PSScriptRoot -Parent) 'MySqlFirewallGuard.ps1')

Add-MySqlSetupFirewallGuard -Ports @(3306, 33060) | Out-Null
if ($script:MockFirewallRules.Count -ne 2) { throw 'Expected inbound block rules for both MySQL ports.' }
if (@($script:MockFirewallRules | Where-Object { $_.Action -ne 'Block' -or $_.Direction -ne 'Inbound' }).Count -gt 0) {
    throw 'The setup firewall rules must block inbound traffic.'
}

Add-MySqlSetupFirewallGuard -Ports @(3306, 33060) | Out-Null
if ($script:MockFirewallRules.Count -ne 2) { throw 'Repeated setup must reuse the temporary block rules.' }

Remove-MySqlSetupFirewallGuard
if ($script:MockFirewallRules.Count -ne 0) { throw 'The temporary block rules were not removed.' }

$script:MockFirewallEnabled = $false
$RejectedDisabledFirewall = $false
try { Add-MySqlSetupFirewallGuard -Ports @(3306) | Out-Null }
catch { $RejectedDisabledFirewall = $true }
if (-not $RejectedDisabledFirewall) { throw 'Setup must stop when the firewall is disabled.' }

Write-Output 'MySQL firewall guard test passed.'
