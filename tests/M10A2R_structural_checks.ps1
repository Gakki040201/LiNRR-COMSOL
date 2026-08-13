[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$JavaPath = Join-Path $ProjectRoot 'src\java\LiNRR_M10A2R_ManualReconciliation.java'
$MphPath = Join-Path $ProjectRoot 'models\generated\LiNRR_M10A2R_manual_reconciled.mph'
$Evidence = Join-Path $ProjectRoot 'evidence\M10A2R'
$ManualCandidates = @(Get-ChildItem -LiteralPath (Join-Path $ProjectRoot 'docs\manual\original') -File -Filter '*.docx')
if ($ManualCandidates.Count -ne 1) { throw "M10A2R_MANUAL_ARCHIVE_COUNT_INVALID: $($ManualCandidates.Count)" }
$Manual = $ManualCandidates[0].FullName
$Extracted = Join-Path $ProjectRoot 'docs\manual\LiNRR_manual_extracted.md'
$Manifest = Join-Path $ProjectRoot 'docs\manual\LiNRR_manual_manifest.csv'

if ((git -C $ProjectRoot branch --show-current).Trim() -cne 'm10a2-ssc-pipe-wetting') { throw 'M10A2R_WRONG_BRANCH' }
foreach ($PathValue in @($JavaPath,$MphPath,$Manual,$Extracted,$Manifest)) {
    if (-not (Test-Path -LiteralPath $PathValue -PathType Leaf) -or (Get-Item -LiteralPath $PathValue).Length -le 0) { throw "M10A2R_REQUIRED_FILE_MISSING: $PathValue" }
}
if ((Get-FileHash -LiteralPath $Manual -Algorithm SHA256).Hash.ToUpperInvariant() -cne 'EE9AEB5309B6D5BCA5649458DA2EE7B531D43FA50C7E6A6A1909EFFA81871EF7') { throw 'M10A2R_MANUAL_HASH_MISMATCH' }

$Java = [IO.File]::ReadAllText($JavaPath,[Text.Encoding]::UTF8)
foreach ($Required in @('comp_cell_physical','L_gas_stub_visual','gas_pfa_id_visual','gas_pfa_od_visual','NOT MODELED UNLESS VERIFIED','REDUCED THROUGH-PLANE MODEL','1D REDUCED MODEL - NOT PHYSICAL ROUTING','REAL_CAD_INTERNAL_PORT_PROFILE','M10A2R_PHYSICAL_VIEW_RELOAD=PASS','M10A2R_REDUCED_VIEW_RELOAD=PASS','M10A2R_RESULT_RELOAD=PASS')) {
    if (-not $Java.Contains($Required)) { throw "M10A2R_REQUIRED_SOURCE_TEXT_MISSING: $Required" }
}
foreach ($Forbidden in @('addSpeciesTransport','ElectrodeReaction','ButlerVolmer','PEEK cone angle','L_gas_stub_visual*Q_')) {
    if ($Java.Contains($Forbidden)) { throw "M10A2R_SCOPE_OR_TRUTH_VIOLATION: $Forbidden" }
}

$RequiredEvidence = @('model_tree.txt','model_tree.json','component_inventory.csv','geometry_feature_provenance.csv','selection_inventory.csv','physics_inventory.csv','study_inventory.csv','dataset_inventory.csv','result_inventory.csv','parameter_inventory.csv','numerical_invariance.csv','README_ACCEPTANCE.md','physical_cell_full_3d.png','physical_cell_exploded.png','gas_port_connections.png','n2_channel_geometry.png','h2_channel_geometry.png','electrolyte_chamber_geometry.png','ssc_physical_true_scale.png','ssc_reduced_darcy.png','ssc_reduced_wetting.png')
foreach ($Name in $RequiredEvidence) {
    $PathValue = Join-Path $Evidence $Name
    if (-not (Test-Path -LiteralPath $PathValue -PathType Leaf) -or (Get-Item -LiteralPath $PathValue).Length -le 0) { throw "M10A2R_EVIDENCE_MISSING: $Name" }
}
$Components = @(Import-Csv -LiteralPath (Join-Path $Evidence 'component_inventory.csv'))
foreach ($Tag in @('comp_cell_physical','comp_pipe_n2','comp_pipe_h2','comp_pipe_liq','comp_ssc_darcy','comp_ssc_wetting','comp_n2_transfer')) {
    if (-not ($Components | Where-Object tag -CEQ $Tag)) { throw "M10A2R_COMPONENT_MISSING: $Tag" }
}
$Results = @(Import-Csv -LiteralPath (Join-Path $Evidence 'result_inventory.csv'))
foreach ($Tag in @('pg00_physical_cell','pg01_physical_exploded','pg02_gas_connections','pg03_n2_channel','pg04_h2_channel','pg05_electrolyte')) {
    if (-not ($Results | Where-Object result_tag -CEQ $Tag)) { throw "M10A2R_RESULT_MISSING: $Tag" }
}
$Invariant = @(Import-Csv -LiteralPath (Join-Path $Evidence 'numerical_invariance.csv'))
if ($Invariant.Count -lt 10 -or @($Invariant | Where-Object { [double]$_.relative_difference -gt 1e-10 -or $_.status -cne 'PASS' }).Count -gt 0) { throw 'M10A2R_NUMERICAL_INVARIANCE_INVALID' }
$Prov = @(Import-Csv -LiteralPath (Join-Path $Evidence 'geometry_feature_provenance.csv'))
if (-not ($Prov | Where-Object { $_.feature_tag -ceq 'REAL_CAD_INTERNAL_PORT_PROFILE' -and $_.source_class -ceq 'REAL_CAD' -and $_.keep_or_remove -ceq 'KEEP' })) { throw 'M10A2R_CONE_PROVENANCE_MISSING' }

$Latest = Get-ChildItem -LiteralPath (Join-Path $ProjectRoot 'runs\M10A2R') -Directory |
    Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName 'M10A2R_stdout.txt') } |
    Sort-Object LastWriteTimeUtc -Descending | Select-Object -First 1
if (-not $Latest) { throw 'M10A2R_FORMAL_RUN_NOT_FOUND' }
$Stdout = Join-Path $Latest.FullName 'M10A2R_stdout.txt'
foreach ($Marker in @('M10A2R_PHYSICAL_VIEW_RELOAD=PASS','M10A2R_REDUCED_VIEW_RELOAD=PASS','M10A2R_RESULT_RELOAD=PASS','M10A2R_OVERALL=PASS')) {
    if (-not (Select-String -LiteralPath $Stdout -SimpleMatch $Marker -Quiet)) { throw "M10A2R_RUN_MARKER_MISSING: $Marker" }
}
Write-Host "M10A2R_FINAL_MPH_SHA256=$((Get-FileHash -LiteralPath $MphPath -Algorithm SHA256).Hash.ToUpperInvariant())"
Write-Host 'M10A2R_STRUCTURAL_CHECKS=PASS'
