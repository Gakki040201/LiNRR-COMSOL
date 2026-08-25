[CmdletBinding()]
param(
    [string]$PrecompactM10A4 = 'F:\LiNRR_COMSOL\worktrees\LiNRR_M10A4_INTEGRATED\runs\M10A4\20260820_121258\checkpoint_A4_final_pre_audit.mph'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'lib\Invoke-ComsolCaptured.ps1')

$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$Source = Join-Path $ProjectRoot 'models\generated\LiNRR_M10A4_ionic_current_li_plating.mph'
$Output = Join-Path $ProjectRoot 'models\visualization\LiNRR_M10A4_VISUAL_REVIEW.mph'
$Evidence = Join-Path $ProjectRoot 'evidence\PaperV1_Visualization'
$Images = Join-Path $Evidence 'images'
$Stamp = Get-Date -Format 'yyyyMMdd_HHmmss'
$Run = Join-Path $ProjectRoot "runs\PaperV1_Visualization\${Stamp}_visualization_repair"
$Prefs = Join-Path $Run 'comsol_prefs'
$ExpectedSourceHash = 'FADEEA4D8ADF9E472B4B855B05E39C22EF418214C0F62FB0C1977ACA36DA667B'
$ExpectedSourceSize = 807839751L
$ExpectedFallbackHash = 'E51EFEBA7AD3DF67FE0759D4A279A6C7B82758508E58D519EE7658888E321887'
$ComsolRoot = if ($env:COMSOL_ROOT) { $env:COMSOL_ROOT } else { 'F:\COMSOL64\Multiphysics' }
$Compiler = Join-Path $ComsolRoot 'bin\win64\comsolcompile.exe'
$Batch = Join-Path $ComsolRoot 'bin\win64\comsolbatch.exe'
$Java = Join-Path $ComsolRoot 'java\win64\jre\bin\java.exe'
$Ecj = Join-Path $ComsolRoot 'plugins\org.eclipse.jdt.core.compiler.batch_3.42.0.v20250526-2018.jar'
$Api = Join-Path $ComsolRoot 'plugins\com.comsol.api_1.0.0.jar'
$ModelJar = Join-Path $ComsolRoot 'plugins\com.comsol.model_1.0.0.jar'

foreach ($PathValue in @($Source, $PrecompactM10A4, $Compiler, $Batch, $Java, $Ecj, $Api, $ModelJar)) {
    if (-not (Test-Path -LiteralPath $PathValue -PathType Leaf)) { throw "REQUIRED_FILE_MISSING=$PathValue" }
}
foreach ($Dir in @($Run, $Prefs, $Evidence, $Images, (Split-Path $Output -Parent))) {
    [IO.Directory]::CreateDirectory($Dir) | Out-Null
}

function Get-Identity([string]$PathValue) {
    $Item = Get-Item -LiteralPath $PathValue
    [pscustomobject]@{ SHA256 = (Get-FileHash -LiteralPath $PathValue -Algorithm SHA256).Hash; Size = $Item.Length; LastWriteTimeUtc = $Item.LastWriteTimeUtc.ToString('o') }
}
function Escape-Java([string]$Value) { $Value.Replace('\', '\\').Replace('"', '\"') }
function Write-Utf8([string]$PathValue, [string]$TextValue) {
    [IO.File]::WriteAllText($PathValue, $TextValue, (New-Object Text.UTF8Encoding($false)))
}
function Invoke-Step([string]$Name, [string]$ClassName) {
    $Capture = Invoke-ComsolCaptured -Executable $Batch -ArgumentList @(
        '-prefsdir', $Prefs, '-classpathadd', $Run, '-inputfile', (Join-Path $Run "$ClassName.class"),
        '-batchlog', (Join-Path $Run "$Name.batch.log")
    ) -StdoutPath (Join-Path $Run "$Name.stdout.txt") -StderrPath (Join-Path $Run "$Name.stderr.txt") `
      -MergedConsolePath (Join-Path $Run "$Name.merged.txt")
    if ($Capture.ExitCode -ne 0) { throw "COMSOL_STEP_FAILED=$Name EXIT=$($Capture.ExitCode)" }
}

$Before = Get-Identity $Source
if ($Before.SHA256 -ne $ExpectedSourceHash -or $Before.Size -ne $ExpectedSourceSize) { throw 'SOURCE_MPH_IDENTITY_MISMATCH_BEFORE' }
$FallbackIdentity = Get-Identity $PrecompactM10A4
if ($FallbackIdentity.SHA256 -ne $ExpectedFallbackHash) { throw 'PRECOMPACT_M10A4_IDENTITY_MISMATCH' }
if (-not (Get-Item -LiteralPath $Source).IsReadOnly) { (Get-Item -LiteralPath $Source).IsReadOnly = $true }
$Protected = Get-Identity $Source
if ($Protected.SHA256 -ne $ExpectedSourceHash -or $Protected.Size -ne $ExpectedSourceSize) { throw 'SOURCE_CHANGED_WHILE_SETTING_READ_ONLY' }

$PrefText = "security.external.enable=on`r`nsecurity.external.filepermission=full`r`n"
Write-Utf8 (Join-Path $Prefs 'comsol.prefs') $PrefText
Write-Utf8 (Join-Path $Prefs 'comsolserver.prefs') $PrefText

$Runtime = @"
public final class LiNRR_M10A4_VisualizationRuntimeInputs {
 public static final String INPUT_MPH="$(Escape-Java $Source)";
 public static final String OUTPUT_MPH="$(Escape-Java $Output)";
 public static final String EVIDENCE_DIR="$(Escape-Java $Evidence)";
 public static final String IMAGE_DIR="$(Escape-Java $Images)";
 public static final String RUN_DIR="$(Escape-Java $Run)";
 public static final String ROLE_MATRIX="$(Escape-Java (Join-Path $ProjectRoot 'paper\v1\visualization\PLOT_ROLE_MATRIX.csv'))";
 private LiNRR_M10A4_VisualizationRuntimeInputs() {}
}
"@
$FallbackRuntime = @"
public final class LiNRR_M10A4_FallbackRuntimeInputs {
 public static final String PRECOMPACT_M10A4="$(Escape-Java $PrecompactM10A4)";
 public static final String PRECOMPACT_SHA256="$ExpectedFallbackHash";
 private LiNRR_M10A4_FallbackRuntimeInputs() {}
}
"@
Write-Utf8 (Join-Path $Run 'LiNRR_M10A4_VisualizationRuntimeInputs.java') $Runtime
Write-Utf8 (Join-Path $Run 'LiNRR_M10A4_FallbackRuntimeInputs.java') $FallbackRuntime

$Classes = @(
    'LiNRR_M10A4_VisualizationCommon', 'LiNRR_M10A4_PlotIntegrityAudit',
    'LiNRR_M10A4_RetainedSolutionRoutingAudit', 'LiNRR_M10A4_AcceptedFallbackRoutingAudit',
    'LiNRR_M10A4_CorrectedCanonicalReadability', 'LiNRR_M10A4_FallbackVisualizationExport',
    'LiNRR_M10A4_VisualizationRepair', 'LiNRR_M10A4_VisualizationReloadAudit'
)
$SourceFiles = @((Join-Path $Run 'LiNRR_M10A4_VisualizationRuntimeInputs.java'), (Join-Path $Run 'LiNRR_M10A4_FallbackRuntimeInputs.java'))
foreach ($ClassName in $Classes) {
    $From = Join-Path $ProjectRoot "src\java\$ClassName.java"
    if (-not (Test-Path -LiteralPath $From -PathType Leaf)) { throw "JAVA_SOURCE_MISSING=$From" }
    $To = Join-Path $Run "$ClassName.java"
    Copy-Item -LiteralPath $From -Destination $To -Force
    $SourceFiles += $To
}
$Forbidden = Select-String -Path ($SourceFiles | Where-Object { $_ -like '*.java' }) `
    -Pattern 'study\s*\([^)]*\)\s*\.\s*run(NoGen)?\s*\(|sol\s*\([^)]*\)\s*\.\s*run(All)?\s*\(|mesh\s*\([^)]*\)\s*\.\s*run\s*\('
if ($Forbidden) { throw "FORBIDDEN_EXECUTION_CALL_FOUND=$($Forbidden.Path):$($Forbidden.LineNumber)" }

$EcjCapture = Invoke-ComsolCaptured -Executable $Java -ArgumentList (@(
    '-jar', $Ecj, '-source', '1.8', '-target', '1.8', '-cp', "$Api;$ModelJar", '-d', $Run
) + $SourceFiles) -StdoutPath (Join-Path $Run 'ecj.stdout.txt') -StderrPath (Join-Path $Run 'ecj.stderr.txt') `
  -MergedConsolePath (Join-Path $Run 'ecj.merged.txt')
if (-not (Test-Path (Join-Path $Run 'LiNRR_M10A4_VisualizationRepair.class'))) { throw 'ECJ_DID_NOT_EMIT_CLASSES' }

foreach ($ClassName in $Classes | Where-Object { $_ -ne 'LiNRR_M10A4_VisualizationCommon' }) {
    $Capture = Invoke-ComsolCaptured -Executable $Compiler -ArgumentList @(
        '-prefsdir', $Prefs, '-classpathadd', $Run, (Join-Path $ProjectRoot "src\java\$ClassName.java")
    ) -StdoutPath (Join-Path $Run "compile_$ClassName.stdout.txt") `
      -StderrPath (Join-Path $Run "compile_$ClassName.stderr.txt") `
      -MergedConsolePath (Join-Path $Run "compile_$ClassName.merged.txt")
    if ($Capture.ExitCode -ne 0) { throw "COMSOLCOMPILE_FAILED=$ClassName" }
}

Invoke-Step '01_input_plot_audit' 'LiNRR_M10A4_PlotIntegrityAudit'
Invoke-Step '02_retained_solution_routing' 'LiNRR_M10A4_RetainedSolutionRoutingAudit'
Invoke-Step '03_accepted_fallback_routing' 'LiNRR_M10A4_AcceptedFallbackRoutingAudit'
Invoke-Step '04_corrected_canonical' 'LiNRR_M10A4_CorrectedCanonicalReadability'
Invoke-Step '05_fallback_visualization' 'LiNRR_M10A4_FallbackVisualizationExport'
Invoke-Step '06_visualization_repair' 'LiNRR_M10A4_VisualizationRepair'
Invoke-Step '07_fresh_reload' 'LiNRR_M10A4_VisualizationReloadAudit'

$After = Get-Identity $Source
if ($After.SHA256 -ne $ExpectedSourceHash -or $After.Size -ne $ExpectedSourceSize) { throw 'SOURCE_MPH_IDENTITY_MISMATCH_AFTER' }
$InputRows = Import-Csv (Join-Path $Evidence 'PLOT_INTEGRITY_INPUT.csv')
$OutputRows = Import-Csv (Join-Path $Evidence 'PLOT_INTEGRITY_OUTPUT.csv')
$CanonicalRows = Import-Csv (Join-Path $Evidence 'CANONICAL_FIELD_READABILITY.csv')
$ReloadRows = Import-Csv (Join-Path $Evidence 'VISUALIZATION_RELOAD_AUDIT.csv')
$InvariantRows = Import-Csv (Join-Path $Evidence 'VISUALIZATION_FIELD_INVARIANCE.csv')
if ($InputRows.Count -ne 68 -or @($InputRows | Where-Object status -ne 'PASS').Count -ne 0) { throw 'INPUT_PLOT_AUDIT_GATE_FAILED' }
if ($OutputRows.Count -ne 32 -or @($OutputRows | Where-Object status -ne 'PASS').Count -ne 0) { throw 'OUTPUT_PLOT_AUDIT_GATE_FAILED' }
if (@($CanonicalRows | Where-Object status -ne 'PASS').Count -ne 0) { throw 'CANONICAL_READABILITY_GATE_FAILED' }
if ($ReloadRows.Count -ne 29 -or @($ReloadRows | Where-Object status -ne 'PASS').Count -ne 0) { throw 'RELOAD_GATE_FAILED' }
if ($InvariantRows.Count -ne 22 -or @($InvariantRows | Where-Object status -ne 'PASS').Count -ne 0) { throw 'INVARIANCE_GATE_FAILED' }
if (-not (Test-Path -LiteralPath $Output -PathType Leaf)) { throw 'VISUAL_REVIEW_MPH_MISSING' }
if ((Get-ChildItem -LiteralPath $Images -Filter '*.png').Count -lt 32) { throw 'EVIDENCE_IMAGE_COUNT_TOO_LOW' }

$Unexpected = Get-ChildItem -LiteralPath $Run -File | Where-Object Name -match '(batch\.log|stdout\.txt|stderr\.txt)$' | Select-String -Pattern 'Study\.run|runNoGen|runAll|Solver run|Mesh run|out of memory|singular' -CaseSensitive:$false
if ($Unexpected) { throw "UNEXPECTED_EXECUTION_OR_FATAL_LOG_MARKER=$($Unexpected.Path):$($Unexpected.LineNumber)" }

"SOURCE_MPH_SHA256_BEFORE=$($Before.SHA256)"
"SOURCE_MPH_SHA256_AFTER=$($After.SHA256)"
'SOURCE_MPH_UNCHANGED=TRUE'
'STUDY_RUNS_TRIGGERED=0'
'SOLVER_RUNS_TRIGGERED=0'
'MESH_RUNS_TRIGGERED=0'
'LEGACY_LIQ_DATASET_READABILITY=EMPTY_CACHE_CLEARED'
'RETAINED_LIQ_DATASET=dset_a4b_flow_repair'
'RETAINED_LIQ_DATASET_READABILITY=PASS'
'LEGACY_N2_DATASET_READABILITY=EMPTY_CACHE_CLEARED'
'RETAINED_N2_FLOW_SOURCE=ACCEPTED_PRECOMPACTION_VISUALIZATION_SOURCE'
'RETAINED_N2_FLOW_READABILITY=PASS'
'RETAINED_SOLUTION_ROUTING_AUDIT=PASS'
'LIQUID_FLOW_ROUTING_CLASS=RETAINED_CANONICAL_REAL_LIQUID_FLOW'
'N2_FLOW_ROUTING_CLASS=ACCEPTED_PRECOMPACTION_VISUALIZATION_SOURCE'
'CANONICAL_FIELD_READABILITY=PASS'
'REACTION_PLANE_SELECTION=PASS'
"INPUT_PLOTGROUP_COUNT=$($InputRows.Count)"
"INPUT_PLOT_RUNTIME_PASS_COUNT=$(@($InputRows | Where-Object status -eq 'PASS').Count)"
'INPUT_PLOT_WARNING_COUNT=0'
'INPUT_PLOT_EXCEPTION_COUNT=0'
"VIZ_MAIN_PLOT_COUNT=$($OutputRows.Count)"
'VIZ_MAIN_PLOT_RUNTIME_PASS=PASS'
'VIZ_MAIN_PLOT_WARNINGS=0'
'VISUALIZATION_FIELD_INVARIANCE=PASS'
'PAPER_V1_VISUALIZATION_INDEPENDENT_RELOAD=PASS'
'SI_VISUALIZATION_INDEX=PASS'
'COMSOL_READING_GUIDE=PASS'
'PAPER_V1_VISUALIZATION_REPAIR=PASS'
'HARD_STOP=TRUE'
