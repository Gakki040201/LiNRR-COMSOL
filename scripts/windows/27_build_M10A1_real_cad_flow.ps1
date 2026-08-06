[CmdletBinding()]param()
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
$ProjectRoot=(Resolve-Path(Join-Path $PSScriptRoot '..\..')).Path
.(Join-Path $PSScriptRoot 'lib\Invoke-ComsolCaptured.ps1')

$ComsolBin='F:\COMSOL64\Multiphysics\bin\win64'
$Compiler=Join-Path $ComsolBin 'comsolcompile.exe';$Batch=Join-Path $ComsolBin 'comsolbatch.exe'
$Java=Join-Path $ProjectRoot 'src\java\LiNRR_M10A1_RealCAD_Flow.java';$ClassFile=Join-Path $ProjectRoot 'src\java\LiNRR_M10A1_RealCAD_Flow.class'
$InputMph=Join-Path $ProjectRoot 'models\generated\LiNRR_M10A0_4_real_cad_fluid_domains.mph'
$OutputMph=Join-Path $ProjectRoot 'models\generated\LiNRR_M10A1_real_cad_flow_solved.mph'
$CcStep=Join-Path $ProjectRoot 'cad\raw\M10A0_2\block_currentCollector_flowField_v03.2.1_20210525_STEP-AP203.STEP'
$ChamberStep=Join-Path $ProjectRoot 'cad\raw\M10A0_2\electrolyteChamber_rectangularMask_v02.2.2_20210521_STEP-AP203.STEP'
$ExpectedCcSha='0091777D10ECA620E08780381438F3522B3BE31FEE307F9DDCF074A1FACDDFE9';$ExpectedChamberSha='AE350E006228A10EEC4476F79CAFCE6D63F18F10F58F0A8B3559EED0C37FF12C'
foreach($x in @($Compiler,$Batch,$Java,$InputMph,$CcStep,$ChamberStep)){if(-not(Test-Path -LiteralPath $x -PathType Leaf)){throw "M10A1_REQUIRED_INPUT_MISSING: $x"}}
$CcSha=(Get-FileHash -LiteralPath $CcStep -Algorithm SHA256).Hash.ToUpperInvariant();$ChamberSha=(Get-FileHash -LiteralPath $ChamberStep -Algorithm SHA256).Hash.ToUpperInvariant()
if($CcSha -cne $ExpectedCcSha -or $ChamberSha -cne $ExpectedChamberSha){throw "M10A1_SOURCE_STEP_HASH_MISMATCH: cc=$CcSha chamber=$ChamberSha"}

$Stamp=Get-Date -Format 'yyyyMMdd_HHmmss';$RunDir=Join-Path $ProjectRoot "runs\M10A1\$Stamp`_real_cad_flow";$FigureDir=Join-Path $RunDir 'figures'
[System.IO.Directory]::CreateDirectory($RunDir)|Out-Null;[System.IO.Directory]::CreateDirectory($FigureDir)|Out-Null;[System.IO.Directory]::CreateDirectory((Split-Path $OutputMph -Parent))|Out-Null
$RuntimeJava=Join-Path $RunDir 'LiNRR_M10A1_RuntimeInputs.java';$RuntimeClass=Join-Path $RunDir 'LiNRR_M10A1_RuntimeInputs.class'
$CompileOut=Join-Path $RunDir 'M10A1_compile_stdout.txt';$CompileErr=Join-Path $RunDir 'M10A1_compile_stderr.txt';$CompileMerged=Join-Path $RunDir 'M10A1_compile_console_merged.txt'
$Stdout=Join-Path $RunDir 'M10A1_stdout.txt';$Stderr=Join-Path $RunDir 'M10A1_stderr.txt';$Merged=Join-Path $RunDir 'M10A1_console_merged.txt';$BatchLog=Join-Path $RunDir 'M10A1_real_cad_flow.log'
$FlowCsv=Join-Path $RunDir 'M10A1_flow_metrics.csv';$GeometryCsv=Join-Path $RunDir 'M10A1_geometry_metrics.csv';$MeshCsv=Join-Path $RunDir 'M10A1_mesh_metrics.csv';$SolverSummary=Join-Path $RunDir 'M10A1_solver_summary.txt'
if(Test-Path -LiteralPath $OutputMph -PathType Leaf){Move-Item -LiteralPath $OutputMph -Destination(Join-Path $RunDir('preexisting_'+(Split-Path $OutputMph -Leaf)))}
function J([string]$v){$v.Replace('\','\\').Replace('"','\"')}
$Runtime=@"
public final class LiNRR_M10A1_RuntimeInputs {
 private LiNRR_M10A1_RuntimeInputs() {}
 public static final String INPUT_MPH="$(J $InputMph)";
 public static final String OUTPUT_MPH="$(J $OutputMph)";
 public static final String FIGURE_DIR="$(J $FigureDir)";
 public static final String CC_SHA256="$CcSha";
 public static final String CHAMBER_SHA256="$ChamberSha";
}
"@
[System.IO.File]::WriteAllText($RuntimeJava,$Runtime,(New-Object System.Text.UTF8Encoding($false)))
$RuntimeCompileStarted=[DateTime]::UtcNow
$Rc=Invoke-ComsolCaptured -Executable $Compiler -ArgumentList @($RuntimeJava) -StdoutPath(Join-Path $RunDir 'M10A1_runtime_compile_stdout.txt') -StderrPath(Join-Path $RunDir 'M10A1_runtime_compile_stderr.txt') -MergedConsolePath(Join-Path $RunDir 'M10A1_runtime_compile_console_merged.txt')
$RuntimeCompileText=Get-Content -LiteralPath(Join-Path $RunDir 'M10A1_runtime_compile_console_merged.txt') -Raw
if($Rc.ExitCode -ne 0 -or -not(Test-Path -LiteralPath $RuntimeClass) -or (Get-Item -LiteralPath $RuntimeClass).LastWriteTimeUtc -lt $RuntimeCompileStarted.AddSeconds(-2) -or $RuntimeCompileText -match '(?i)compilation failed|\berror\s*:'){throw "M10A1_RUNTIME_COMPILE_FAILED: exit=$($Rc.ExitCode)"}
$JavaCompileStarted=[DateTime]::UtcNow
$Jc=Invoke-ComsolCaptured -Executable $Compiler -ArgumentList @($Java) -StdoutPath $CompileOut -StderrPath $CompileErr -MergedConsolePath $CompileMerged
$JavaCompileText=Get-Content -LiteralPath $CompileMerged -Raw
if($Jc.ExitCode -ne 0 -or -not(Test-Path -LiteralPath $ClassFile) -or (Get-Item -LiteralPath $ClassFile).LastWriteTimeUtc -lt $JavaCompileStarted.AddSeconds(-2) -or $JavaCompileText -match '(?i)compilation failed|\berror\s*:'){Get-Content $CompileMerged|ForEach-Object{Write-Host $_};throw "M10A1_JAVA_COMPILE_FAILED: exit=$($Jc.ExitCode)"}

$Capture=Invoke-ComsolCaptured -Executable $Batch -ArgumentList @('-classpathadd',$RunDir,'-inputfile',$ClassFile,'-outputfile',$OutputMph,'-batchlog',$BatchLog) -StdoutPath $Stdout -StderrPath $Stderr -MergedConsolePath $Merged
Write-Host 'M10A1_CAPTURED_STDOUT_BEGIN';Get-Content -LiteralPath $Stdout|ForEach-Object{Write-Host $_};Write-Host 'M10A1_CAPTURED_STDOUT_END'
Write-Host 'M10A1_CAPTURED_STDERR_BEGIN';Get-Content -LiteralPath $Stderr|ForEach-Object{Write-Host $_};Write-Host 'M10A1_CAPTURED_STDERR_END'
if($Capture.ExitCode -ne 0){throw "M10A1_COMSOLBATCH_NONZERO: exit=$($Capture.ExitCode)"}
foreach($x in @($Stdout,$Stderr,$Merged,$BatchLog,$OutputMph)){if(-not(Test-Path -LiteralPath $x -PathType Leaf)){throw "M10A1_EVIDENCE_MISSING: $x"}}
foreach($x in @($Stdout,$Merged,$BatchLog,$OutputMph)){if((Get-Item -LiteralPath $x).Length -eq 0){throw "M10A1_EVIDENCE_EMPTY: $x"}}
$Markers=@('M10A1_NUMERICAL_TEST_PARAMETERS_PASS','M10A1_FLOW_COMPONENTS_PASS','M10A1_PHYSICS_CREATE_PASS','M10A1_COARSE_MESH_PASS','M10A1_LIQUID_COARSE_SOLVE_PASS','M10A1_N2_COARSE_SOLVE_PASS','M10A1_MEDIUM_MESH_REVIEW_PASS','M10A1_RESULT_NODES_PASS','M10A1_SOLVED_MODEL_SAVE_PASS','M10A1_FLOW_FIELDS_FINITE_PASS','M10A1_MASS_CONSERVATION_PASS','M10A1_REAL_CAD_FLOW_SOLVED=PASS')
foreach($m in $Markers){if(-not(Select-String -LiteralPath $Stdout -SimpleMatch $m -Quiet)){throw "M10A1_REQUIRED_MARKER_MISSING: $m"}}
$AllowedFallback='M10A1_(BOUNDARY_LAYER|DIRECT_SOLVE|HNLIN_RAMP|NS_FROM_STOKES)_UNAVAILABLE|IMAGE_EXPORT_UNAVAILABLE'
$Fatal=Select-String -Path @($BatchLog,$Stdout,$Stderr) -Pattern @('\berrors?\b','\bfailed\b','\bundefined\b','\bsingular\b','\bout of memory\b','\bexception\b','FIRST_FAILED_API','FAILURE_CONTEXT_BEGIN') -CaseSensitive:$false -ErrorAction SilentlyContinue|Where-Object{$_.Line -notmatch $AllowedFallback}
if($Fatal){$Fatal|ForEach-Object{Write-Host "FATAL|$($_.Path)|$($_.LineNumber)|$($_.Line)"};throw 'M10A1_FATAL_EVIDENCE_FOUND'}
$Warnings=Select-String -Path @($BatchLog,$Stdout,$Stderr) -Pattern @('\bwarning\b','\bwarn:') -CaseSensitive:$false -ErrorAction SilentlyContinue

function Write-PrefixedCsv([string]$Prefix,[string]$Path,[int]$Expected){$lines=Get-Content -LiteralPath $Stdout|Where-Object{$_ -like "$Prefix*"}|ForEach-Object{$_.Substring($Prefix.Length)}|Select-Object -Unique;[System.IO.File]::WriteAllLines($Path,[string[]]$lines,(New-Object System.Text.UTF8Encoding($false)));if(@($lines).Count -ne $Expected){throw "M10A1_CSV_INVALID: $Path records=$(@($lines).Count) expected=$Expected"}}
Write-PrefixedCsv 'M10A1_FLOW_CSV|' $FlowCsv 5;Write-PrefixedCsv 'M10A1_GEOMETRY_CSV|' $GeometryCsv 3;Write-PrefixedCsv 'M10A1_MESH_CSV|' $MeshCsv 5
$SummaryLines=Get-Content -LiteralPath $Stdout|Where-Object{$_ -match '^M10A1_(SOLVER|DIRECT_SOLVE|FLOW_RAMP|MESH_REVIEW|MESH_AUDIT|BOUNDARY_LAYER|IMAGE_EXPORT|REAL_CAD_FLOW_SOLVED)'}
[System.IO.File]::WriteAllLines($SolverSummary,[string[]]$SummaryLines,(New-Object System.Text.UTF8Encoding($false)))
$CcAfter=(Get-FileHash -LiteralPath $CcStep -Algorithm SHA256).Hash.ToUpperInvariant();$ChamberAfter=(Get-FileHash -LiteralPath $ChamberStep -Algorithm SHA256).Hash.ToUpperInvariant()
if($CcAfter -cne $ExpectedCcSha -or $ChamberAfter -cne $ExpectedChamberSha){throw "M10A1_SOURCE_STEP_HASH_CHANGED: cc=$CcAfter chamber=$ChamberAfter"}
$Mph=Get-Item -LiteralPath $OutputMph;$Images=Get-ChildItem -LiteralPath $FigureDir -Filter '*.png' -File -ErrorAction SilentlyContinue
Write-Host 'M10A1_REAL_CAD_FLOW_SOLVED=PASS';Write-Host "RUNTIME_COMPILE_EXIT=$($Rc.ExitCode)";Write-Host "JAVA_COMPILE_EXIT=$($Jc.ExitCode)";Write-Host "COMSOLBATCH_EXIT=$($Capture.ExitCode)";Write-Host "WARNING_HITS=$(@($Warnings).Count)";Write-Host "IMAGE_COUNT=$(@($Images).Count)";Write-Host "MPH_PATH=$($Mph.FullName)";Write-Host "MPH_BYTES=$($Mph.Length)";Write-Host "MPH_SHA256=$((Get-FileHash -LiteralPath $OutputMph -Algorithm SHA256).Hash.ToUpperInvariant())";Write-Host "FLOW_CSV=$FlowCsv";Write-Host "GEOMETRY_CSV=$GeometryCsv";Write-Host "MESH_CSV=$MeshCsv";Write-Host "SOLVER_SUMMARY=$SolverSummary";Write-Host "FIGURE_DIR=$FigureDir";Write-Host "STDOUT_PATH=$Stdout";Write-Host "STDERR_PATH=$Stderr";Write-Host "MERGED_CONSOLE_PATH=$Merged";Write-Host "BATCH_LOG_PATH=$BatchLog";Write-Host "RUN_DIR=$RunDir"
