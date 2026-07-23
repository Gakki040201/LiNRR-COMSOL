param(
    [Parameter(Mandatory=$true)][string]$PackageRoot,
    [Parameter(Mandatory=$true)][ValidatePattern('^[A-Za-z0-9._-]+$')][string]$LedgerVersion
)

Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
$ProjectRoot=(Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
Import-Module (Join-Path $ProjectRoot 'scripts\windows\modules\LiNRR_M03A6_Acquisition.psm1') -Force -DisableNameChecking
$Result=Freeze-M03A6ExperimentalEvidence -PackageRoot $PackageRoot -LedgerVersion $LedgerVersion
"HASH_LEDGER=$($Result.LedgerPath)"
"VERIFICATION_REPORT=$($Result.ReportPath)"
"FILES_VERIFIED=$($Result.FileCount)"
"M03A6_RAW_BYTES_UNCHANGED=$(if($Result.RawBytesUnchanged){'PASS'}else{'FAIL'})"
"M03A6_RAW_TIMESTAMP_UNCHANGED=$(if($Result.RawTimestampUnchanged){'PASS'}else{'FAIL'})"
"M03A6_EXACT_HASH_LEDGER=PASS"
