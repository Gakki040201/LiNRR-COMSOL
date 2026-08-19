[CmdletBinding()]
param()
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
$ProjectRoot=(Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
. (Join-Path $PSScriptRoot 'lib\Invoke-ComsolCaptured.ps1')
$Bin='F:\COMSOL64\Multiphysics\bin\win64';$Compiler=Join-Path $Bin 'comsolcompile.exe';$Batch=Join-Path $Bin 'comsolbatch.exe'
$Baseline=Join-Path $ProjectRoot 'models\generated\LiNRR_M10A2R_manual_reconciled.mph';$Output=Join-Path $ProjectRoot 'models\generated\LiNRR_M10A3_real_species_transport.mph';$Java=Join-Path $ProjectRoot 'src\java\LiNRR_M10A3_RealSpeciesTransport.java'
$Collector=Join-Path $ProjectRoot 'cad\raw\M10A0_2\block_currentCollector_flowField_v03.2.1_20210525_STEP-AP203.STEP';$Chamber=Join-Path $ProjectRoot 'cad\raw\M10A0_2\electrolyteChamber_rectangularMask_v02.2.2_20210521_STEP-AP203.STEP'
$Expected=@{$Baseline='B105EBEAF85582E63389427DFAD7320187FA4A472043E207B5363E258E94CEA7';$Collector='0091777D10ECA620E08780381438F3522B3BE31FEE307F9DDCF074A1FACDDFE9';$Chamber='AE350E006228A10EEC4476F79CAFCE6D63F18F10F58F0A8B3559EED0C37FF12C'}
foreach($x in $Expected.GetEnumerator()){if((Get-FileHash -LiteralPath $x.Key -Algorithm SHA256).Hash -cne $x.Value){throw "IMMUTABLE_HASH_MISMATCH: $($x.Key)"}}
$Resume06=@(Get-ChildItem -Directory (Join-Path $ProjectRoot 'runs\M10A3')|Sort-Object Name -Descending|Where-Object {Test-Path (Join-Path $_.FullName 'checkpoint_06_nh3.mph')}|Select-Object -First 1)
$Resume04=@(Get-ChildItem -Directory (Join-Path $ProjectRoot 'runs\M10A3')|Sort-Object Name -Descending|Where-Object {Test-Path (Join-Path $_.FullName 'checkpoint_04_h2_scaffold.mph')}|Select-Object -First 1)
$Resume03=@(Get-ChildItem -Directory (Join-Path $ProjectRoot 'runs\M10A3')|Sort-Object Name -Descending|Where-Object {Test-Path (Join-Path $_.FullName 'checkpoint_03_real_n2.mph')}|Select-Object -First 1)
if($Resume06.Count-eq1){$Input=Join-Path $Resume06[0].FullName 'checkpoint_06_nh3.mph'}elseif($Resume04.Count-eq1){$Input=Join-Path $Resume04[0].FullName 'checkpoint_04_h2_scaffold.mph'}elseif($Resume03.Count-eq1){$Input=Join-Path $Resume03[0].FullName 'checkpoint_03_real_n2.mph'}else{$Precheck=@(Get-ChildItem -Directory (Join-Path $ProjectRoot 'runs\M10A3')|Sort-Object Name -Descending|Where-Object {Test-Path (Join-Path $_.FullName 'checkpoint_02_gas_symmetry.mph')}|Select-Object -First 1);if($Precheck.Count-ne1){throw 'VALID_PRECHECK_CHECKPOINT_NOT_FOUND'};$Input=Join-Path $Precheck[0].FullName 'checkpoint_02_gas_symmetry.mph'}
$Stamp=Get-Date -Format 'yyyyMMdd_HHmmss';$Run=Join-Path $ProjectRoot "runs\M10A3\${Stamp}_real_species";$Evidence=Join-Path $ProjectRoot 'evidence\M10A3';$Tables=Join-Path $ProjectRoot 'results\tables';foreach($d in @($Run,$Evidence,$Tables)){[IO.Directory]::CreateDirectory($d)|Out-Null}
if(Test-Path -LiteralPath $Output -PathType Leaf){Move-Item -LiteralPath $Output -Destination (Join-Path $Run 'preexisting_LiNRR_M10A3_real_species_transport.mph')}
$Prefs=Join-Path $Run 'isolated_comsol_preferences';[IO.Directory]::CreateDirectory($Prefs)|Out-Null;$pt="security.external.enable=on`r`nsecurity.external.filepermission=full`r`n";[IO.File]::WriteAllText((Join-Path $Prefs 'comsol.prefs'),$pt,(New-Object Text.UTF8Encoding($false)));[IO.File]::WriteAllText((Join-Path $Prefs 'comsolserver.prefs'),$pt,(New-Object Text.UTF8Encoding($false)))
function J([string]$x){$x.Replace('\','\\').Replace('"','\"')}
$Runtime=Join-Path $Run 'LiNRR_M10A3_RuntimeInputs.java';$Text=@"
public final class LiNRR_M10A3_RuntimeInputs {
 public static final String INPUT_MPH="$(J $Input)";
 public static final String OUTPUT_MPH="$(J $Output)";
 public static final String RUN_DIR="$(J $Run)";
 public static final String EVIDENCE_DIR="$(J $Evidence)";
 public static final String TABLE_DIR="$(J $Tables)";
}
"@;[IO.File]::WriteAllText($Runtime,$Text,(New-Object Text.UTF8Encoding($false)))
foreach($item in @($Runtime,$Java)){$co=& $Compiler $item 2>&1;$ce=$LASTEXITCODE;$co|Set-Content -LiteralPath (Join-Path $Run ((Split-Path $item -Leaf)+'.compile.txt'));if($ce-ne0){throw "COMPILE_FAILED: $item"}}
$Out=Join-Path $Run 'M10A3_stdout.txt';$Err=Join-Path $Run 'M10A3_stderr.txt';$Log=Join-Path $Run 'M10A3.log';$cap=Invoke-ComsolCaptured -Executable $Batch -ArgumentList @('-prefsdir',$Prefs,'-classpathadd',$Run,'-inputfile',([IO.Path]::ChangeExtension($Java,'.class')),'-batchlog',$Log) -StdoutPath $Out -StderrPath $Err -MergedConsolePath (Join-Path $Run 'M10A3_merged.txt')
if($cap.ExitCode-ne0){throw "M10A3_BATCH_EXIT=$($cap.ExitCode)"}
foreach($marker in @('M10A3A_REAL_N2_TRANSPORT=PASS','M10A3A_H2_TRANSPORT_SCAFFOLD=PASS','M10A3B_PROTON_DONOR_TRANSPORT=PASS','M10A3C_NH3_TRANSPORT=PASS')){if(-not(Select-String -LiteralPath $Out -SimpleMatch $marker -Quiet)){throw "M10A3_MARKER_MISSING: $marker"}}
if(-not(Test-Path -LiteralPath $Output -PathType Leaf)){throw 'M10A3_OUTPUT_MISSING'}
foreach($x in $Expected.GetEnumerator()){if((Get-FileHash -LiteralPath $x.Key -Algorithm SHA256).Hash -cne $x.Value){throw "IMMUTABLE_INPUT_CHANGED: $($x.Key)"}}
Write-Host "M10A3_STATIONARY=PASS`nRUN_DIR=$Run`nFINAL_MPH=$Output"
