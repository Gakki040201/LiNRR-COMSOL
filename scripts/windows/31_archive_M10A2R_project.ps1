[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$Root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
if ((git -C $Root branch --show-current).Trim() -cne 'm10a2-ssc-pipe-wetting') { throw 'M10A2R_WRONG_BRANCH' }

function Get-Relative([string]$PathValue) {
    $Full = [IO.Path]::GetFullPath($PathValue)
    if (-not $Full.StartsWith($Root + [IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)) {
        throw "ARCHIVE_SOURCE_OUTSIDE_REPOSITORY: $Full"
    }
    return $Full.Substring($Root.Length + 1).Replace('\','/')
}

function Add-ByteExactCopy([string]$Source,[string]$Destination) {
    $Src = [IO.Path]::GetFullPath($Source)
    $Dst = [IO.Path]::GetFullPath($Destination)
    if (-not $Dst.StartsWith($Root + [IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)) {
        throw "ARCHIVE_DESTINATION_OUTSIDE_REPOSITORY: $Dst"
    }
    [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($Dst)) | Out-Null
    if (-not [IO.File]::Exists($Dst)) {
        if ([IO.Path]::GetExtension($Src) -ieq '.mph') {
            New-Item -ItemType HardLink -Path $Dst -Target $Src -ErrorAction Stop | Out-Null
        } else {
            [IO.File]::Copy($Src,$Dst,$false)
        }
    }
    $A = Get-Item -LiteralPath $Src
    $B = Get-Item -LiteralPath $Dst
    $HashA = (Get-FileHash -LiteralPath $Src -Algorithm SHA256).Hash.ToUpperInvariant()
    $HashB = (Get-FileHash -LiteralPath $Dst -Algorithm SHA256).Hash.ToUpperInvariant()
    if ($A.Length -ne $B.Length -or $HashA -cne $HashB) { throw "ARCHIVE_BYTE_IDENTITY_FAILED: $Src" }
    return [pscustomobject]@{Bytes=[long]$A.Length;Sha256=$HashA}
}

$Rows = New-Object Collections.Generic.List[object]
function Add-RunArchive([string]$Stage,[string]$RunRoot) {
    if (-not (Test-Path -LiteralPath $RunRoot -PathType Container)) { return }
    foreach ($File in Get-ChildItem -LiteralPath $RunRoot -Recurse -File) {
        $Relative = $File.FullName.Substring(([IO.Path]::GetFullPath($RunRoot)).Length).TrimStart('\')
        $Original = Get-Relative $File.FullName
        if ($Relative -match '(^|\\)isolated_comsol_preferences\\' -or $File.Extension -ieq '.class' -or $File.Extension -ieq '.prefs' -or $File.Extension -ieq '.recoveries') {
            $Rows.Add([pscustomobject]@{stage=$Stage;original_path=$Original;archive_path='';bytes=[long]$File.Length;sha256=(Get-FileHash -LiteralPath $File.FullName -Algorithm SHA256).Hash.ToUpperInvariant();storage='EXCLUDED';classification='BUILD_OR_SECURITY_CONFIGURATION_NOT_SCIENTIFIC_EVIDENCE'})
            continue
        }
        $Destination = Join-Path $Root (Join-Path "archive\$Stage" $Relative)
        $Identity = Add-ByteExactCopy $File.FullName $Destination
        $Storage = if ($File.Extension -ieq '.mph') { 'GIT_LFS' } else { 'GIT' }
        $Class = if ($File.Extension -ieq '.mph') { 'FAILED_OR_INTERMEDIATE_COMSOL_RUN_EVIDENCE' } elseif ($File.Extension -ieq '.log') { 'FULL_COMSOL_BATCH_LOG' } else { 'RUN_TEXT_OR_SMALL_BINARY_EVIDENCE' }
        $Rows.Add([pscustomobject]@{stage=$Stage;original_path=$Original;archive_path=(Get-Relative $Destination);bytes=$Identity.Bytes;sha256=$Identity.Sha256;storage=$Storage;classification=$Class})
    }
}

[IO.Directory]::CreateDirectory((Join-Path $Root 'archive\M10A0')) | Out-Null
# Remove only previously generated copies of isolated COMSOL preference trees.
# Their source runs remain untouched and every excluded source file is inventoried.
$M10A2RArchiveRoot = [IO.Path]::GetFullPath((Join-Path $Root 'archive\M10A2R'))
if (Test-Path -LiteralPath $M10A2RArchiveRoot) {
    foreach ($PreferencesCopy in Get-ChildItem -LiteralPath $M10A2RArchiveRoot -Directory -Recurse -Filter 'isolated_comsol_preferences') {
        $Resolved = [IO.Path]::GetFullPath($PreferencesCopy.FullName)
        if (-not $Resolved.StartsWith($M10A2RArchiveRoot + [IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase) -or $PreferencesCopy.Name -cne 'isolated_comsol_preferences') {
            throw "UNSAFE_GENERATED_ARCHIVE_CLEANUP_TARGET: $Resolved"
        }
        [IO.Directory]::Delete($Resolved,$true)
    }
}
Add-RunArchive 'M10A1' (Join-Path $Root 'runs\M10A1_1')
Add-RunArchive 'M10A2' (Join-Path $Root 'runs\M10A2')
Add-RunArchive 'M10A2R' (Join-Path $Root 'runs\M10A2R')

$M10A0Readme = Join-Path $Root 'archive\M10A0\README.md'
if (-not (Test-Path -LiteralPath $M10A0Readme)) {
    [IO.File]::WriteAllText($M10A0Readme,"# M10A0 archive`n`nNo timestamped M10A0 run directory exists in this worktree. The immutable M10A0.2 STEP sources remain under `cad/raw/M10A0_2/` and are already tracked in ordinary Git.`n",[Text.UTF8Encoding]::new($false))
}
$M10A0Info = Get-Item $M10A0Readme
$Rows.Add([pscustomobject]@{stage='M10A0';original_path='cad/raw/M10A0_2/';archive_path=(Get-Relative $M10A0Readme);bytes=[long]$M10A0Info.Length;sha256=(Get-FileHash $M10A0Readme -Algorithm SHA256).Hash.ToUpperInvariant();storage='GIT';classification='NO_TIMESTAMPED_RUN_FOUND_REAL_STEP_SOURCES_REMAIN_IN_PLACE'})

$Manifest = Join-Path $Root 'archive\archive_manifest.csv'
$Rows | Sort-Object stage,original_path | Export-Csv -LiteralPath $Manifest -NoTypeInformation -Encoding UTF8

$ModelRows = New-Object Collections.Generic.List[object]
foreach ($Name in @('LiNRR_M10A1_real_cad_flow_solved.mph','LiNRR_M10A2_ssc_pipe_wetting.mph','LiNRR_M10A2R_manual_reconciled.mph')) {
    $Source = Join-Path $Root "models\generated\$Name"
    if (-not (Test-Path -LiteralPath $Source -PathType Leaf)) { throw "MODEL_ARCHIVE_SOURCE_MISSING: $Name" }
    $Destination = Join-Path $Root "models\archive\$Name"
    $Identity = Add-ByteExactCopy $Source $Destination
    $ModelRows.Add([pscustomobject]@{filename=$Name;bytes=$Identity.Bytes;sha256=$Identity.Sha256;source_path=(Get-Relative $Source);archive_path=(Get-Relative $Destination);storage='GIT_LFS'})
}
$ModelRows | Export-Csv -LiteralPath (Join-Path $Root 'models\archive\model_manifest.csv') -NoTypeInformation -Encoding UTF8

$RawDir = Join-Path $Root 'data\raw_experimental'
[IO.Directory]::CreateDirectory($RawDir) | Out-Null
$ExpectedExternal = 'D:\LiNRR_Experimental_Data\M03A_6\EXP001'
$RawStatus = if (Test-Path -LiteralPath $ExpectedExternal) { 'REVIEW_REQUIRED' } else { 'NOT_FOUND' }
[pscustomobject]@{source_path=$ExpectedExternal;archive_path='';bytes='';sha256='';instrument_source='M03A.6 external evidence package';date_if_known='';status=$RawStatus;reason='No authorized byte-preserving package was available at the documented location; no raw file was uploaded.'} |
    Export-Csv -LiteralPath (Join-Path $RawDir 'inventory.csv') -NoTypeInformation -Encoding UTF8
[IO.File]::WriteAllText((Join-Path $RawDir 'README.md'),"# Raw experimental archive status`n`nThe documented M03A.6 package path was not present during M10A2R archival. No raw experimental file was fabricated or uploaded. Status: **$RawStatus**.`n",[Text.UTF8Encoding]::new($false))

Write-Host "ARCHIVE_MANIFEST=$Manifest"
Write-Host "ARCHIVE_ROWS=$($Rows.Count)"
Write-Host "MODEL_ARCHIVE_COUNT=$($ModelRows.Count)"
Write-Host "RAW_EXPERIMENTAL_ARCHIVE=$RawStatus"
Write-Host 'M10A2R_PROJECT_ARCHIVE=PASS'
