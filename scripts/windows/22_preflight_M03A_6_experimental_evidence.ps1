param([Parameter(Mandatory=$true)][string]$PackageRoot,[string]$HashLedgerPath)

Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
$ProjectRoot=(Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
Import-Module (Join-Path $ProjectRoot 'scripts\windows\modules\LiNRR_M03A6_Acquisition.psm1') -Force -DisableNameChecking
$Result=Invoke-M03A6Preflight -PackageRoot $PackageRoot -HashLedgerPath $HashLedgerPath
$Result.Status
