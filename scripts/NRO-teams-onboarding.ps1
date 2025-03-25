<#

 - if required install powershell on mac by running:

brew install powershell/tap/powershell

verfify by running:

pwsh

More info here: https://learn.microsoft.com/en-us/powershell/scripting/install/installing-powershell-on-macos?view=powershell-7.5

 - install microsoft teams module by running:

install-Module -Name MicrosoftTeams

verify by running:

Connect-MicrosoftTeams

 - Update the script as instructed in the comments

 - Ensure you have been made an owner of the private channel in production - HMCTS Pre-recorded Evidence Production, or are the owner of the channel you are testing

 - When ready run pwsh NRO-teams-onboarding.ps1

#>

# Connect to Microsoft Teams
Connect-MicrosoftTeams

$CSVPath = "" #add your path to CSV file containing user emails to be onboarded, if using the main onboarding test csv add a real justice user as this will fail for fake emails
$TeamID = "" # change to demo or prod: in microsoft teams click three dots next to team, select get link, copy the group id from url
$TenantId = "531ff96d-0ae9-462a-8d2d-bec7c0b42082"
$TeamDisplayName = "HMCTS PRE-NLE" #set to demo "HMCTS Pre-Demo" or prod "HMCTS Pre-recorded Evidence"
$ChannelName = "NRO-test" #this should be the name of the private channel in prod, or channel being tested

#add relevant paths to log files
$LogPathMatches = ""
$LogPathAddedMembers = ""
$LogPathGetAllMembers = ""
$LogPathAddChannelMembers = ""
$LogPathGetAllChannelMembers = ""


$TeamUsersToAdd = Import-Csv -Path $CSVPath
$CSVUsers = $TeamUsersToAdd | ForEach-Object { ($_.'Email' -split '@')[0].ToLower() }

Clear-Content -Path $LogPathMatches -Force
Clear-Content -Path $LogPathAddedMembers -Force
Clear-Content -Path $LogPathGetAllMembers -Force
Clear-Content -Path $LogPathAddChannelMembers -Force
Clear-Content -Path $LogPathGetAllChannelMembers -Force

# Add users to the team
$TeamUsersToAdd | ForEach-Object {
    Try {
        Add-TeamUser -GroupId $TeamID -User $_.Email -Role "Member"
        Write-Host "Added User to the team: $($_.Email)" -ForegroundColor Green
        Add-Content -Path $LogPathAddedMembers -Value "Added User to the team: $($_.Email)"
    }
    Catch {
        Write-Host "Error Adding User to team: $($_.Exception.Message)" -ForegroundColor Red
        Add-Content -Path $LogPathAddedMembers -Value "Error Adding User to team: $($_.Exception.Message)"
    }
}

Write-Host "Added team users saved to: $LogPathAddedMembers"

#should be "HMCTS Pre-recorded Evidence" for running in prod, or team containing private channel for test
if($TeamDisplayName -eq "HMCTS PRE-NLE"){
$TeamUsers = Get-TeamUser -GroupId $TeamID

# Matches guest users in csv to those in teams to extract their UserId (required to add guest user to private channel)
$UserMatches = @()

$TeamUsers | ForEach-Object {
    $teamEmail = $_.User.ToLower()
    $teamUsername = ($teamEmail -split '@')[0]

    $normalizedTeamUsername = $teamUsername -replace '_.*?#EXT#', ''

    if ($normalizedTeamUsername -in $CSVUsers) {
        $UserMatches += [PSCustomObject]@{
            Email   = $_.User
            UserId  = $_.UserId
        }
    }
}

if ($UserMatches.Count -gt 0) {
    Write-Host "Matching Users with UserIDs Found:" -ForegroundColor Green
    $UserMatches | Format-Table -AutoSize
    $UserMatches | Export-Csv -Path $LogPathMatches -NoTypeInformation
    Write-Host "Matches saved to: $LogPathMatches"
} else {
    Write-Host "No Matches Found." -ForegroundColor Yellow
}

# Adds users to the private channel in prod
Try {
    if ($UserMatches) {
        $UserMatches | ForEach-Object {
            Add-TeamChannelUser -GroupId $TeamID -DisplayName $ChannelName -User $_.UserId -TenantId $TenantId
            Write-Host "Added User to the channel: $($_.Email)" -ForegroundColor Green
            Add-Content -Path $LogPathAddChannelMembers -Value "Added User to the channel: $($_.Email)"
        }
        Write-Host "Added channel users saved to: $LogPathAddChannelMembers"
    }
}
Catch {
    Write-Host "Error Adding User to channel: $($_.Exception.Message)" -ForegroundColor Red
    Add-Content -Path $LogPathAddChannelMembers -Value "Error Adding User to channel: $($_.Exception.Message)"
    Write-Host "Error saved to: $LogPathAddChannelMembers"
}

# Retrieves all team and channel members
Try {
    $channelMembers = Get-TeamChannelUser -GroupId $TeamID -DisplayName $ChannelName
    if ($channelMembers) {
        Add-Content -Path $LogPathGetAllChannelMembers -Value "`nChannel members for '$ChannelName':`n"
        $channelMembers | Format-Table -AutoSize | Out-String | Add-Content -Path $LogPathGetAllChannelMembers
        Write-Host "All channel users saved to: $LogPathGetAllChannelMembers"
    } else {
        Add-Content -Path $LogPathGetAllChannelMembers -Value "`nNo members found in the channel '$ChannelName'.`n"
    }
}

Catch {
    Write-Host "Error retrieving members: $_" -ForegroundColor Red
}
}
Try {
    $teamMembers = Get-TeamUser -GroupId $TeamID
    if ($teamMembers) {
        Add-Content -Path $LogPathGetAllMembers -Value "`nTeam members for '$TeamDisplayName':`n"
        $teamMembers | Format-Table -AutoSize | Out-String | Add-Content -Path $LogPathGetAllMembers
        Write-Host "All team users saved to: $LogPathGetAllMembers"
    } else {
        Add-Content -Path $LogPathGetAllMembers -Value "`nNo members found in the Team '$TeamID'.`n"
    }
}

Catch {
    Write-Host "Error retrieving members: $_" -ForegroundColor Red
}
