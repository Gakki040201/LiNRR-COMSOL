param([Parameter(Mandatory=$true)][string]$DestinationRoot)

Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
$ProjectRoot=(Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$ModulePath=Join-Path $ProjectRoot 'scripts\windows\modules\LiNRR_M03A6_Acquisition.psm1'
Import-Module $ModulePath -Force -DisableNameChecking
$Created=Initialize-M03A6AcquisitionPackage -DestinationRoot $DestinationRoot -RepositoryRoot $ProjectRoot
"ACQUISITION_PACKAGE_READY=$Created"
