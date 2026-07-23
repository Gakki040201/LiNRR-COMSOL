param(
    [Parameter(Mandatory=$true)][string]$InputPath,
    [Parameter(Mandatory=$true)][string]$OutputPath,
    [Parameter(Mandatory=$true)][string]$ParserProfile,
    [string]$FrequencyColumn,
    [string]$ZRealColumn,
    [string]$ZImagColumn,
    [string]$RowMappingPath,
    [string]$ProvenancePath
)

Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
$ProjectRoot=(Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
Import-Module (Join-Path $ProjectRoot 'scripts\windows\modules\LiNRR_M03A6_Acquisition.psm1') -Force -DisableNameChecking
$Result=Prepare-M03A6NormalizedEis @PSBoundParameters
"NORMALIZED_EIS=$($Result.OutputPath)"
"ROW_MAPPING=$($Result.RowMappingPath)"
"CONVERSION_PROVENANCE=$($Result.ProvenancePath)"
"ROWS_RETAINED=$($Result.RowCount)"
"M03A6_RAW_BYTES_UNCHANGED=$(if($Result.RawBytesUnchanged){'PASS'}else{'FAIL'})"
"M03A6_RAW_TIMESTAMP_UNCHANGED=$(if($Result.RawTimestampUnchanged){'PASS'}else{'FAIL'})"
