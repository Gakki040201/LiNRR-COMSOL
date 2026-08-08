[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$CsvPath = Join-Path $ProjectRoot 'config\M10A2_lab_hardware_parameters.csv'
$JavaPath = Join-Path $ProjectRoot 'src\java\LiNRR_M10A2_SSC_Pipe_Wetting.java'
$FinalMph = Join-Path $ProjectRoot 'models\generated\LiNRR_M10A2_ssc_pipe_wetting.mph'
$BaselineMph = Join-Path $ProjectRoot 'models\generated\LiNRR_M10A1_real_cad_flow_solved.mph'
$Collector = Join-Path $ProjectRoot 'cad\raw\M10A0_2\block_currentCollector_flowField_v03.2.1_20210525_STEP-AP203.STEP'
$Chamber = Join-Path $ProjectRoot 'cad\raw\M10A0_2\electrolyteChamber_rectangularMask_v02.2.2_20210521_STEP-AP203.STEP'

$Expected = @{
    $BaselineMph = '535F90E93B42B6D69A31A635C429B01B0B36D3EA8FE39B5645352FCBDF3D43C5'
    $Collector = '0091777D10ECA620E08780381438F3522B3BE31FEE307F9DDCF074A1FACDDFE9'
    $Chamber = 'AE350E006228A10EEC4476F79CAFCE6D63F18F10F58F0A8B3559EED0C37FF12C'
}
foreach ($Item in $Expected.GetEnumerator()) {
    if (-not (Test-Path -LiteralPath $Item.Key -PathType Leaf)) { throw "M10A2_REQUIRED_FILE_MISSING: $($Item.Key)" }
    $Actual = (Get-FileHash -LiteralPath $Item.Key -Algorithm SHA256).Hash.ToUpperInvariant()
    if ($Actual -cne $Item.Value) { throw "M10A2_IMMUTABLE_HASH_MISMATCH: $($Item.Key) $Actual" }
}

if ((git -C $ProjectRoot branch --show-current).Trim() -cne 'm10a2-ssc-pipe-wetting') { throw 'M10A2_WRONG_BRANCH' }
if (-not (Test-Path -LiteralPath $FinalMph -PathType Leaf) -or (Get-Item -LiteralPath $FinalMph).Length -le 0) { throw 'M10A2_FINAL_MPH_MISSING' }

$Rows = @(Import-Csv -LiteralPath $CsvPath)
if ($Rows.Count -lt 1) { throw 'M10A2_PARAMETER_TABLE_EMPTY' }
$ExpectedColumns = @('parameter','value','unit','source_class','source_reference','model_role','notes')
$ActualColumns = @($Rows[0].PSObject.Properties.Name)
if (($ExpectedColumns -join '|') -cne ($ActualColumns -join '|')) { throw 'M10A2_PARAMETER_COLUMNS_INVALID' }
$AllowedClasses = @('LAB_MANUAL','REAL_CAD','LITERATURE_REPORTED','LITERATURE_SAME_PLATFORM','DERIVED','PROVISIONAL','CALIBRATION_REQUIRED')
$BadClasses = @($Rows | Where-Object { $_.source_class -cnotin $AllowedClasses })
if ($BadClasses.Count -gt 0) { throw "M10A2_SOURCE_CLASS_INVALID: $($BadClasses.source_class -join ',')" }
if (@($Rows | Where-Object { $_.source_class -ceq 'EXPERIMENTAL' }).Count -gt 0) { throw 'M10A2_EXPERIMENTAL_CLASS_FORBIDDEN' }
$Liquid = $Rows | Where-Object { $_.parameter -ceq 'Q_liquid_lab' }
if (-not $Liquid -or $Liquid.value -or $Liquid.source_class -cne 'CALIBRATION_REQUIRED') { throw 'M10A2_LIQUID_FLOW_GATE_INVALID' }

$Java = [System.IO.File]::ReadAllText($JavaPath, [System.Text.Encoding]::UTF8)
foreach ($RequiredText in @('PipeFlow','PorousMediaFlowDarcy','PhaseTransportPorous','MultiphaseFlowInPorousMedia','DilutedSpecies','M10A2_INDEPENDENT_MPH_RELOAD=PASS')) {
    if (-not $Java.Contains($RequiredText)) { throw "M10A2_REQUIRED_MODEL_ELEMENT_MISSING: $RequiredText" }
}
foreach ($ForbiddenText in @('ButlerVolmer','ElectrodeReaction')) {
    if ($Java.Contains($ForbiddenText)) { throw "M10A2_SCOPE_VIOLATION: $ForbiddenText" }
}

$Latest = Get-ChildItem -LiteralPath (Join-Path $ProjectRoot 'runs\M10A2') -Directory |
    Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName 'M10A2_stdout.txt') } |
    Sort-Object LastWriteTimeUtc -Descending | Select-Object -First 1
if (-not $Latest) { throw 'M10A2_FORMAL_RUN_NOT_FOUND' }
$Stdout = Join-Path $Latest.FullName 'M10A2_stdout.txt'
foreach ($Marker in @('M10A2A_HYDRAULIC_NETWORK=PASS','M10A2B_POROUS_SSC=PASS','M10A2C_WETTING=PASS','M10A2D_N2_TRANSFER=PASS','M10A2_INDEPENDENT_MPH_RELOAD=PASS')) {
    if (-not (Select-String -LiteralPath $Stdout -SimpleMatch $Marker -Quiet)) { throw "M10A2_RUN_MARKER_MISSING: $Marker" }
}

Write-Host "M10A2_PARAMETER_ROWS=$($Rows.Count)"
Write-Host "M10A2_FINAL_MPH_SHA256=$((Get-FileHash -LiteralPath $FinalMph -Algorithm SHA256).Hash.ToUpperInvariant())"
Write-Host "M10A2_STRUCTURAL_CHECKS=PASS"
