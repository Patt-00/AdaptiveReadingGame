$script:MySqlTemporaryFirewallRuleNames = @()

function Add-MySqlSetupFirewallGuard {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)]
        [int[]]$Ports
    )

    $UniquePorts = @($Ports | Sort-Object -Unique)
    if ($UniquePorts.Count -eq 0 -or @($UniquePorts | Where-Object { $_ -lt 1 -or $_ -gt 65535 }).Count -gt 0) {
        throw 'The MySQL firewall guard needs valid TCP port numbers.'
    }

    $Profiles = @(Get-NetFirewallProfile -Profile @('Domain', 'Private', 'Public'))
    if ($Profiles.Count -lt 3 -or @($Profiles | Where-Object { -not $_.Enabled }).Count -gt 0) {
        throw 'Enable Windows Defender Firewall for the Domain, Private, and Public profiles before installing MySQL.'
    }

    foreach ($Port in $UniquePorts) {
        $RuleName = "Adaptive Reading Game MySQL Setup Block $Port"
        $ExistingRules = @(Get-NetFirewallRule -DisplayName $RuleName -ErrorAction SilentlyContinue)
        if ($ExistingRules.Count -gt 1) { throw "More than one firewall rule uses the reserved name $RuleName." }

        if ($ExistingRules.Count -eq 0) {
            New-NetFirewallRule -DisplayName $RuleName `
                -Group 'Adaptive Reading Game' `
                -Description 'Temporary inbound block while the local MySQL listener is restricted to loopback.' `
                -Direction Inbound -Action Block -Protocol TCP -LocalPort $Port -Profile Any -Enabled True `
                | Out-Null
        } else {
            $Existing = $ExistingRules[0]
            $Filters = @(Get-NetFirewallPortFilter -AssociatedNetFirewallRule $Existing)
            $MatchingPort = @($Filters | Where-Object {
                @('TCP', '6') -contains [string]$_.Protocol -and [string]$_.LocalPort -eq [string]$Port
            }).Count -gt 0
            if (([string]$Existing.Action -ne 'Block') -or ([string]$Existing.Direction -ne 'Inbound') -or ([string]$Existing.Enabled -ne 'True') -or (-not $MatchingPort)) {
                throw "A firewall rule named $RuleName exists but is not a matching inbound TCP block. Resolve it before setup."
            }
        }

        $ActiveRules = @(Get-NetFirewallRule -PolicyStore ActiveStore -DisplayName $RuleName -ErrorAction SilentlyContinue)
        if ($ActiveRules.Count -eq 0) {
            throw "The Windows firewall is not applying the temporary MySQL block for port $Port."
        }
        if ($script:MySqlTemporaryFirewallRuleNames -notcontains $RuleName) {
            $script:MySqlTemporaryFirewallRuleNames += $RuleName
        }
    }

    return $script:MySqlTemporaryFirewallRuleNames
}

function Remove-MySqlSetupFirewallGuard {
    foreach ($RuleName in $script:MySqlTemporaryFirewallRuleNames) {
        Remove-NetFirewallRule -DisplayName $RuleName -ErrorAction SilentlyContinue
    }
    $script:MySqlTemporaryFirewallRuleNames = @()
}
