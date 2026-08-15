[CmdletBinding()]
param()
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
$ProjectRoot=(Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
. (Join-Path $PSScriptRoot 'lib\Invoke-ComsolCaptured.ps1')
$Bin='F:\COMSOL64\Multiphysics\bin\win64'
$Compiler=Join-Path $Bin 'comsolcompile.exe';$Batch=Join-Path $Bin 'comsolbatch.exe'
$Input=Join-Path $ProjectRoot 'models\generated\LiNRR_M10A2R_manual_reconciled.mph'
$Java=Join-Path $ProjectRoot 'src\java\LiNRR_M10A3_Precheck.java'
if((git -C $ProjectRoot branch --show-current).Trim() -cne 'm10a3-reactive-species-transport'){throw 'M10A3_WRONG_BRANCH'}
if((git -C $ProjectRoot rev-parse HEAD).Trim() -cne '33117715a5c489c2518013c9fa2d157985f3708c'){throw 'M10A3_WRONG_BASELINE_COMMIT'}
if((Get-FileHash -LiteralPath $Input -Algorithm SHA256).Hash -cne 'B105EBEAF85582E63389427DFAD7320187FA4A472043E207B5363E258E94CEA7'){throw 'M10A3_BASELINE_HASH_MISMATCH'}
$Stamp=Get-Date -Format 'yyyyMMdd_HHmmss';$Run=Join-Path $ProjectRoot "runs\M10A3\${Stamp}_precheck"
$Evidence=Join-Path $ProjectRoot 'evidence\M10A3';$Tables=Join-Path $ProjectRoot 'results\tables'
foreach($d in @($Run,$Evidence,$Tables)){[IO.Directory]::CreateDirectory($d)|Out-Null}
$Prefs=Join-Path $Run 'isolated_comsol_preferences';[IO.Directory]::CreateDirectory($Prefs)|Out-Null
$PrefText="security.external.enable=on`r`nsecurity.external.filepermission=full`r`n"
[IO.File]::WriteAllText((Join-Path $Prefs 'comsol.prefs'),$PrefText,(New-Object Text.UTF8Encoding($false)))
[IO.File]::WriteAllText((Join-Path $Prefs 'comsolserver.prefs'),$PrefText,(New-Object Text.UTF8Encoding($false)))
function J([string]$x){$x.Replace('\','\\').Replace('"','\"')}
$Runtime=Join-Path $Run 'LiNRR_M10A3_RuntimeInputs.java'
$Text=@"
public final class LiNRR_M10A3_RuntimeInputs {
 public static final String INPUT_MPH="$(J $Input)";
 public static final String RUN_DIR="$(J $Run)";
 public static final String EVIDENCE_DIR="$(J $Evidence)";
 public static final String TABLE_DIR="$(J $Tables)";
 public static final String MATCHED_INPUT_MPH="$(J (Join-Path $ProjectRoot 'models\archive\LiNRR_M10A1_real_cad_flow_solved.mph'))";
 public static final String MATCHED_OUTPUT_MPH="$(J (Join-Path $ProjectRoot 'models\generated\LiNRR_M10A2_1_matched_flow_control.mph'))";
}
"@
[IO.File]::WriteAllText($Runtime,$Text,(New-Object Text.UTF8Encoding($false)))
foreach($item in @($Runtime,$Java)){$CompileOutput=& $Compiler $item 2>&1;$CompileExit=$LASTEXITCODE;$CompileOutput|Tee-Object -FilePath (Join-Path $Run ((Split-Path $item -Leaf)+'.compile.txt'));if($CompileExit -ne 0){throw "COMPILE_FAILED: $item"}}
$Log=Join-Path $Run 'M10A3_precheck.log';$Out=Join-Path $Run 'M10A3_precheck_stdout.txt';$Err=Join-Path $Run 'M10A3_precheck_stderr.txt'
$Capture=Invoke-ComsolCaptured -Executable $Batch -ArgumentList @('-prefsdir',$Prefs,'-classpathadd',$Run,'-inputfile',([IO.Path]::ChangeExtension($Java,'.class')),'-batchlog',$Log) -StdoutPath $Out -StderrPath $Err -MergedConsolePath (Join-Path $Run 'M10A3_precheck_merged.txt')
if($Capture.ExitCode -ne 0){throw "M10A3_PRECHECK_BATCH_FAILED exit=$($Capture.ExitCode)"}
foreach($marker in @('M10A3_PRECHECK=PASS','INACTIVE_CAVITY_AUDIT=PASS','M10A3_GAS_GEOMETRY_SYMMETRY=PASS')){if(-not(Select-String -LiteralPath $Out -SimpleMatch $marker -Quiet)){throw "MISSING_MARKER: $marker"}}
Write-Host "M10A3_PRECHECK=PASS`nRUN_DIR=$Run"
