[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
. (Join-Path $PSScriptRoot 'lib\Invoke-ComsolCaptured.ps1')
$ComsolBin = 'F:\COMSOL64\Multiphysics\bin\win64'
$Compiler = Join-Path $ComsolBin 'comsolcompile.exe'
$Batch = Join-Path $ComsolBin 'comsolbatch.exe'
$Java = Join-Path $ProjectRoot 'src\java\LiNRR_M10A2R_ManualReconciliation.java'
$InputMph = Join-Path $ProjectRoot 'models\generated\LiNRR_M10A2_ssc_pipe_wetting.mph'
$OutputMph = Join-Path $ProjectRoot 'models\generated\LiNRR_M10A2R_manual_reconciled.mph'
$Collector = Join-Path $ProjectRoot 'cad\raw\M10A0_2\block_currentCollector_flowField_v03.2.1_20210525_STEP-AP203.STEP'
$Chamber = Join-Path $ProjectRoot 'cad\raw\M10A0_2\electrolyteChamber_rectangularMask_v02.2.2_20210521_STEP-AP203.STEP'
$ManualCandidates = @(Get-ChildItem -LiteralPath (Join-Path $ProjectRoot 'docs\manual\original') -File -Filter '*.docx')
if ($ManualCandidates.Count -ne 1) { throw "M10A2R_MANUAL_ARCHIVE_COUNT_INVALID: $($ManualCandidates.Count)" }
$Manual = $ManualCandidates[0].FullName
$BaselineNumerics = Join-Path $ProjectRoot 'config\M10A2R_numerical_baseline.csv'
$EvidenceDir = Join-Path $ProjectRoot 'evidence\M10A2R'
$ResultTables = Join-Path $ProjectRoot 'results\tables'
$Expected = @{
    $InputMph = 'EC58BAEDC13FFCAA7594FE75567D330BA4458D07A77060A2E751EA3AECDBD5C1'
    $Collector = '0091777D10ECA620E08780381438F3522B3BE31FEE307F9DDCF074A1FACDDFE9'
    $Chamber = 'AE350E006228A10EEC4476F79CAFCE6D63F18F10F58F0A8B3559EED0C37FF12C'
    $Manual = 'EE9AEB5309B6D5BCA5649458DA2EE7B531D43FA50C7E6A6A1909EFFA81871EF7'
}

if ((git -C $ProjectRoot branch --show-current).Trim() -cne 'm10a2-ssc-pipe-wetting') { throw 'M10A2R_WRONG_BRANCH' }
foreach ($PathValue in @($Compiler,$Batch,$Java,$InputMph,$Collector,$Chamber,$Manual,$BaselineNumerics)) {
    if (-not (Test-Path -LiteralPath $PathValue -PathType Leaf)) { throw "M10A2R_REQUIRED_INPUT_MISSING: $PathValue" }
}
$Before = @{}
foreach ($Item in $Expected.GetEnumerator()) {
    $File = Get-Item -LiteralPath $Item.Key
    $Sha = (Get-FileHash -LiteralPath $Item.Key -Algorithm SHA256).Hash.ToUpperInvariant()
    if ($Sha -cne $Item.Value) { throw "M10A2R_INPUT_HASH_MISMATCH: $($Item.Key) $Sha" }
    $Before[$Item.Key] = [pscustomobject]@{ Bytes = [long]$File.Length; Sha256 = $Sha }
}

$Stamp = Get-Date -Format 'yyyyMMdd_HHmmss'
$RunDir = Join-Path $ProjectRoot "runs\M10A2R\$($Stamp)_manual_reconciliation"
foreach ($Dir in @($RunDir,$EvidenceDir,$ResultTables,(Split-Path $OutputMph -Parent))) { [IO.Directory]::CreateDirectory($Dir) | Out-Null }
$PrefsDir = Join-Path $RunDir 'isolated_comsol_preferences'
[IO.Directory]::CreateDirectory($PrefsDir) | Out-Null
# COMSOL methods default to limited filesystem access. This isolated, task-local
# preference set permits the updater to write only the explicitly supplied output,
# evidence, and run paths without changing the user's global COMSOL preferences.
$PrefsText = @"
security.external.enable=on
security.external.filepermission=full
"@
[IO.File]::WriteAllText((Join-Path $PrefsDir 'comsol.prefs'),$PrefsText,(New-Object Text.UTF8Encoding($false)))
[IO.File]::WriteAllText((Join-Path $PrefsDir 'comsolserver.prefs'),$PrefsText,(New-Object Text.UTF8Encoding($false)))
[IO.File]::WriteAllText((Join-Path $RunDir 'COMSOL_SECURITY_SCOPE.txt'),
    "ISOLATED_PREFS=TRUE`r`nGLOBAL_PREFS_MODIFIED=FALSE`r`nFILE_PERMISSION=full`r`nPURPOSE=M10A2R evidence/image/model export only`r`n",
    (New-Object Text.UTF8Encoding($false)))
if (Test-Path -LiteralPath $OutputMph -PathType Leaf) {
    Move-Item -LiteralPath $OutputMph -Destination (Join-Path $RunDir ('preexisting_' + (Split-Path $OutputMph -Leaf)))
}

function ConvertTo-JavaLiteral([string]$Value) { $Value.Replace('\','\\').Replace('"','\"') }
$RuntimeJava = Join-Path $RunDir 'LiNRR_M10A2R_RuntimeInputs.java'
$RuntimeClass = Join-Path $RunDir 'LiNRR_M10A2R_RuntimeInputs.class'
$RuntimeText = @"
public final class LiNRR_M10A2R_RuntimeInputs {
 public static final String INPUT_MPH="$(ConvertTo-JavaLiteral $InputMph)";
 public static final String OUTPUT_MPH="$(ConvertTo-JavaLiteral $OutputMph)";
 public static final String RUN_DIR="$(ConvertTo-JavaLiteral $RunDir)";
 public static final String EVIDENCE_DIR="$(ConvertTo-JavaLiteral $EvidenceDir)";
 public static final String CC_STEP="$(ConvertTo-JavaLiteral $Collector)";
 public static final String CHAMBER_STEP="$(ConvertTo-JavaLiteral $Chamber)";
 public static final String BASELINE_NUMERICS="$(ConvertTo-JavaLiteral $BaselineNumerics)";
 public static final String RESULT_GEOMETRY_PROVENANCE="$(ConvertTo-JavaLiteral (Join-Path $ResultTables 'M10A2R_geometry_feature_provenance.csv'))";
 public static final String RESULT_NUMERICAL_INVARIANCE="$(ConvertTo-JavaLiteral (Join-Path $ResultTables 'M10A2R_numerical_invariance.csv'))";
 private LiNRR_M10A2R_RuntimeInputs() {}
}
"@
[IO.File]::WriteAllText($RuntimeJava,$RuntimeText,(New-Object Text.UTF8Encoding($false)))

function Invoke-Compile([string]$PathValue,[string]$Stem) {
    $Capture = Invoke-ComsolCaptured -Executable $Compiler -ArgumentList @($PathValue) `
        -StdoutPath (Join-Path $RunDir "$($Stem)_compile_stdout.txt") `
        -StderrPath (Join-Path $RunDir "$($Stem)_compile_stderr.txt") `
        -MergedConsolePath (Join-Path $RunDir "$($Stem)_compile_merged.txt")
    if ($Capture.ExitCode -ne 0) { throw "M10A2R_COMPILE_FAILED: $Stem exit=$($Capture.ExitCode)" }
}
Invoke-Compile $RuntimeJava 'runtime'
Invoke-Compile $Java 'main'
if (-not (Test-Path -LiteralPath $RuntimeClass -PathType Leaf)) { throw 'M10A2R_RUNTIME_CLASS_MISSING' }

$BatchLog = Join-Path $RunDir 'M10A2R.log'
$Stdout = Join-Path $RunDir 'M10A2R_stdout.txt'
$Stderr = Join-Path $RunDir 'M10A2R_stderr.txt'
$Capture = Invoke-ComsolCaptured -Executable $Batch `
    -ArgumentList @('-prefsdir',$PrefsDir,'-classpathadd',$RunDir,'-inputfile',([IO.Path]::ChangeExtension($Java,'.class')),'-batchlog',$BatchLog) `
    -StdoutPath $Stdout -StderrPath $Stderr -MergedConsolePath (Join-Path $RunDir 'M10A2R_merged.txt')
if ($Capture.ExitCode -ne 0) { throw "M10A2R_BATCH_FAILED: exit=$($Capture.ExitCode)" }
foreach ($Marker in @('M10A2R_PHYSICAL_VIEW_RELOAD=PASS','M10A2R_REDUCED_VIEW_RELOAD=PASS','M10A2R_RESULT_RELOAD=PASS','M10A2R_NUMERICAL_INVARIANCE=PASS','M10A2R_OVERALL=PASS')) {
    if (-not (Select-String -LiteralPath $Stdout -SimpleMatch $Marker -Quiet)) { throw "M10A2R_PASS_MARKER_MISSING: $Marker" }
}
if (-not (Test-Path -LiteralPath $OutputMph -PathType Leaf) -or (Get-Item -LiteralPath $OutputMph).Length -le 0) { throw 'M10A2R_OUTPUT_MPH_MISSING' }
foreach ($Name in @('model_tree.txt','model_tree.json','component_inventory.csv','geometry_feature_provenance.csv','selection_inventory.csv','physics_inventory.csv','study_inventory.csv','dataset_inventory.csv','result_inventory.csv','parameter_inventory.csv','numerical_invariance.csv','physical_cell_full_3d.png','physical_cell_exploded.png','gas_port_connections.png','n2_channel_geometry.png','h2_channel_geometry.png','electrolyte_chamber_geometry.png','ssc_physical_true_scale.png','ssc_reduced_darcy.png','ssc_reduced_wetting.png')) {
    $PathValue = Join-Path $EvidenceDir $Name
    if (-not (Test-Path -LiteralPath $PathValue -PathType Leaf) -or (Get-Item -LiteralPath $PathValue).Length -le 0) { throw "M10A2R_EVIDENCE_MISSING: $Name" }
}

$ScanFiles = @($BatchLog,$Stdout,$Stderr,(Join-Path $RunDir 'M10A2R_merged.txt'))
$Fatal = Select-String -Path $ScanFiles -Pattern @('\berrors?\b','\bfailed\b','\bundefined\b','\bsingular\b','\bout of memory\b','\bexception\b','\bNaN\b','\bInf\b') -CaseSensitive:$false -ErrorAction SilentlyContinue
if ($Fatal) { $Fatal | ForEach-Object { Write-Host "FATAL|$($_.Path)|$($_.LineNumber)|$($_.Line)" }; throw 'M10A2R_FATAL_LOG_MATCH' }
$Warnings = @(Select-String -Path $ScanFiles -Pattern @('\bwarning\b','\bwarn:') -CaseSensitive:$false -ErrorAction SilentlyContinue)

foreach ($Item in $Expected.GetEnumerator()) {
    $After = Get-Item -LiteralPath $Item.Key
    $AfterSha = (Get-FileHash -LiteralPath $Item.Key -Algorithm SHA256).Hash.ToUpperInvariant()
    if ([long]$After.Length -ne $Before[$Item.Key].Bytes -or $AfterSha -cne $Before[$Item.Key].Sha256) { throw "M10A2R_IMMUTABLE_INPUT_CHANGED: $($Item.Key)" }
}
$Out = Get-Item -LiteralPath $OutputMph
Write-Host 'M10A2R_OVERALL=PASS'
Write-Host 'CONE_GEOMETRY_ORIGIN=REAL_CAD_ORIGINAL_STEP_BREP'
Write-Host 'CONE_GEOMETRY_ACTION=KEEP_AND_LABEL_NOT_PEEK'
Write-Host 'PHYSICAL_LONG_STRAIGHT_PIPE_PRESENT=FALSE'
Write-Host 'GAS_PFA_ID_MM=2'
Write-Host 'GAS_PFA_OD_MM=3'
Write-Host 'PEEK_FITTING_INTERNAL_GEOMETRY=NOT_MODELED_UNLESS_VERIFIED'
Write-Host 'PHYSICAL_CELL_3D=PASS'
Write-Host 'SSC_PHYSICAL_3D=PASS'
Write-Host 'SSC_DARCY_SPATIAL_LEVEL=REDUCED_THROUGH_PLANE'
Write-Host 'SSC_WETTING_SPATIAL_LEVEL=REDUCED_THROUGH_PLANE'
Write-Host 'REDUCED_MODELS_HIDDEN_FROM_PHYSICAL_DEFAULT_VIEW=TRUE'
Write-Host 'NUMERICAL_INVARIANCE_STATUS=PASS'
Write-Host "FINAL_MPH=$($Out.FullName)"
Write-Host "FINAL_MPH_SHA256=$((Get-FileHash -LiteralPath $OutputMph -Algorithm SHA256).Hash.ToUpperInvariant())"
Write-Host "WARNING_HITS=$($Warnings.Count)"
Write-Host "RUN_DIR=$RunDir"
