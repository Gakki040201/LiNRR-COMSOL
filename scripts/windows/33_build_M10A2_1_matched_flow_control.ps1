[CmdletBinding()]
param()
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
$ProjectRoot=(Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
. (Join-Path $PSScriptRoot 'lib\Invoke-ComsolCaptured.ps1')
$Bin='F:\COMSOL64\Multiphysics\bin\win64';$Compiler=Join-Path $Bin 'comsolcompile.exe';$Batch=Join-Path $Bin 'comsolbatch.exe'
$Input=Join-Path $ProjectRoot 'models\archive\LiNRR_M10A1_real_cad_flow_solved.mph';$Output=Join-Path $ProjectRoot 'models\generated\LiNRR_M10A2_1_matched_flow_control.mph';$Java=Join-Path $ProjectRoot 'src\java\LiNRR_M10A2_1_MatchedFlowControl.java'
$Stamp=Get-Date -Format 'yyyyMMdd_HHmmss';$Run=Join-Path $ProjectRoot "runs\M10A3\${Stamp}_matched_flow";[IO.Directory]::CreateDirectory($Run)|Out-Null
$Prefs=Join-Path $Run 'isolated_comsol_preferences';[IO.Directory]::CreateDirectory($Prefs)|Out-Null;$pt="security.external.enable=on`r`nsecurity.external.filepermission=full`r`n";[IO.File]::WriteAllText((Join-Path $Prefs 'comsol.prefs'),$pt,(New-Object Text.UTF8Encoding($false)));[IO.File]::WriteAllText((Join-Path $Prefs 'comsolserver.prefs'),$pt,(New-Object Text.UTF8Encoding($false)))
function J([string]$x){$x.Replace('\','\\').Replace('"','\"')}
$Runtime=Join-Path $Run 'LiNRR_M10A3_RuntimeInputs.java';$Text=@"
public final class LiNRR_M10A3_RuntimeInputs {
 public static final String MATCHED_INPUT_MPH="$(J $Input)";
 public static final String MATCHED_OUTPUT_MPH="$(J $Output)";
}
"@;[IO.File]::WriteAllText($Runtime,$Text,(New-Object Text.UTF8Encoding($false)))
foreach($item in @($Runtime,$Java)){$co=& $Compiler $item 2>&1;$ce=$LASTEXITCODE;$co|Set-Content -LiteralPath (Join-Path $Run ((Split-Path $item -Leaf)+'.compile.txt'));if($ce -ne 0){throw "COMPILE_FAILED: $item"}}
$Out=Join-Path $Run 'matched_stdout.txt';$Err=Join-Path $Run 'matched_stderr.txt';$Log=Join-Path $Run 'matched.log'
$cap=Invoke-ComsolCaptured -Executable $Batch -ArgumentList @('-prefsdir',$Prefs,'-classpathadd',$Run,'-inputfile',([IO.Path]::ChangeExtension($Java,'.class')),'-batchlog',$Log) -StdoutPath $Out -StderrPath $Err -MergedConsolePath (Join-Path $Run 'matched_merged.txt')
if($cap.ExitCode -ne 0 -or -not(Select-String -LiteralPath $Out -SimpleMatch 'M10A2_1_MATCHED_FLOW_CONTROL=PASS' -Quiet)){throw 'M10A2_1_MATCHED_FLOW_CONTROL_FAILED'}
if(-not(Test-Path -LiteralPath $Output -PathType Leaf)){throw 'MATCHED_OUTPUT_MISSING'}
Write-Host "M10A2_1_MATCHED_FLOW_CONTROL=PASS`nRUN_DIR=$Run"
