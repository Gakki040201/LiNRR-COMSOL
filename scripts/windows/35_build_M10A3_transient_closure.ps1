[CmdletBinding()]
param()
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
$ProjectRoot=(Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
. (Join-Path $PSScriptRoot 'lib\Invoke-ComsolCaptured.ps1')
$Bin='F:\COMSOL64\Multiphysics\bin\win64';$Compiler=Join-Path $Bin 'comsolcompile.exe';$Batch=Join-Path $Bin 'comsolbatch.exe'
$Output=Join-Path $ProjectRoot 'models\generated\LiNRR_M10A3_real_species_transport.mph';$Java=Join-Path $ProjectRoot 'src\java\LiNRR_M10A3_TransientClosure.java'
$Baseline=Join-Path $ProjectRoot 'models\generated\LiNRR_M10A2R_manual_reconciled.mph';$Collector=Join-Path $ProjectRoot 'cad\raw\M10A0_2\block_currentCollector_flowField_v03.2.1_20210525_STEP-AP203.STEP';$Chamber=Join-Path $ProjectRoot 'cad\raw\M10A0_2\electrolyteChamber_rectangularMask_v02.2.2_20210521_STEP-AP203.STEP'
$Expected=@{$Baseline='B105EBEAF85582E63389427DFAD7320187FA4A472043E207B5363E258E94CEA7';$Collector='0091777D10ECA620E08780381438F3522B3BE31FEE307F9DDCF074A1FACDDFE9';$Chamber='AE350E006228A10EEC4476F79CAFCE6D63F18F10F58F0A8B3559EED0C37FF12C'}
foreach($x in $Expected.GetEnumerator()){if((Get-FileHash -LiteralPath $x.Key -Algorithm SHA256).Hash -cne $x.Value){throw "IMMUTABLE_HASH_MISMATCH: $($x.Key)"}}
$RtdCoarse=@(Get-ChildItem -Directory (Join-Path $ProjectRoot 'runs\M10A3')|Sort-Object Name -Descending|Where-Object {Test-Path (Join-Path $_.FullName 'checkpoint_06_rtd_coarse.mph')}|Select-Object -First 1)
if($RtdCoarse.Count-eq1){$Input=Join-Path $RtdCoarse[0].FullName 'checkpoint_06_rtd_coarse.mph'}else{$Downstream=@(Get-ChildItem -Directory (Join-Path $ProjectRoot 'runs\M10A3')|Sort-Object Name -Descending|Where-Object {Test-Path (Join-Path $_.FullName 'checkpoint_06_nh3_downstream.mph')}|Select-Object -First 1);if($Downstream.Count-eq1){$Input=Join-Path $Downstream[0].FullName 'checkpoint_06_nh3_downstream.mph'}else{$Stationary=@(Get-ChildItem -Directory (Join-Path $ProjectRoot 'runs\M10A3')|Sort-Object Name -Descending|Where-Object {Test-Path (Join-Path $_.FullName 'checkpoint_06_nh3.mph')}|Select-Object -First 1);if($Stationary.Count-ne1){throw 'VALID_CHECKPOINT_06_NOT_FOUND'};$Input=Join-Path $Stationary[0].FullName 'checkpoint_06_nh3.mph'}}
$Stamp=Get-Date -Format 'yyyyMMdd_HHmmss';$Run=Join-Path $ProjectRoot "runs\M10A3\${Stamp}_transient_closure";$Tables=Join-Path $ProjectRoot 'results\tables';foreach($d in @($Run,$Tables)){[IO.Directory]::CreateDirectory($d)|Out-Null}
$ReferenceRtd=Join-Path $Tables 'M10A3_RTD_medium.csv';if(Test-Path -LiteralPath $ReferenceRtd -PathType Leaf){Copy-Item -LiteralPath $ReferenceRtd -Destination (Join-Path $Run 'M10A3_RTD_medium_tau_nom_over_300_reference.csv')}
$Prefs=Join-Path $Run 'isolated_comsol_preferences';[IO.Directory]::CreateDirectory($Prefs)|Out-Null;$pt="security.external.enable=on`r`nsecurity.external.filepermission=full`r`n";[IO.File]::WriteAllText((Join-Path $Prefs 'comsol.prefs'),$pt,(New-Object Text.UTF8Encoding($false)));[IO.File]::WriteAllText((Join-Path $Prefs 'comsolserver.prefs'),$pt,(New-Object Text.UTF8Encoding($false)))
function J([string]$x){$x.Replace('\','\\').Replace('"','\"')}
$Runtime=Join-Path $Run 'LiNRR_M10A3_TransientRuntimeInputs.java';$Text=@"
public final class LiNRR_M10A3_TransientRuntimeInputs {
 public static final String INPUT_MPH="$(J $Input)";
 public static final String OUTPUT_MPH="$(J $Output)";
 public static final String RUN_DIR="$(J $Run)";
 public static final String TABLE_DIR="$(J $Tables)";
 public static final String RTD_REFERENCE_TRACER_MIN="9.672394850124181E-29";
 public static final String RTD_REFERENCE_INSTANTANEOUS_RR="1.0959011172982438E-8";
 public static final String RTD_REFERENCE_CUMULATIVE_RR="3.406701906534549E-6";
}
"@;[IO.File]::WriteAllText($Runtime,$Text,(New-Object Text.UTF8Encoding($false)))
foreach($item in @($Runtime,$Java)){$co=& $Compiler $item 2>&1;$ce=$LASTEXITCODE;$co|Set-Content -LiteralPath (Join-Path $Run ((Split-Path $item -Leaf)+'.compile.txt'));if($ce-ne0){throw "COMPILE_FAILED: $item"}}
$Out=Join-Path $Run 'M10A3_transient_stdout.txt';$Err=Join-Path $Run 'M10A3_transient_stderr.txt';$Log=Join-Path $Run 'M10A3_transient.log';$cap=Invoke-ComsolCaptured -Executable $Batch -ArgumentList @('-prefsdir',$Prefs,'-classpathadd',$Run,'-inputfile',([IO.Path]::ChangeExtension($Java,'.class')),'-batchlog',$Log) -StdoutPath $Out -StderrPath $Err -MergedConsolePath (Join-Path $Run 'M10A3_transient_merged.txt')
if($cap.ExitCode-ne0){throw "M10A3_TRANSIENT_BATCH_EXIT=$($cap.ExitCode)"};foreach($marker in @('M10A3D_RTD_TRANSPORT=PASS','M10A3C_NH3_DOWNSTREAM_CLOSURE=PASS')){if(-not(Select-String -LiteralPath $Out -SimpleMatch $marker -Quiet)){throw "M10A3_TRANSIENT_MARKER_MISSING: $marker"}}
if(-not(Test-Path -LiteralPath $Output -PathType Leaf)){throw 'M10A3_OUTPUT_MISSING'};foreach($x in $Expected.GetEnumerator()){if((Get-FileHash -LiteralPath $x.Key -Algorithm SHA256).Hash -cne $x.Value){throw "IMMUTABLE_INPUT_CHANGED: $($x.Key)"}}
Write-Host "M10A3_TRANSIENT=PASS`nRUN_DIR=$Run`nFINAL_MPH=$Output"
