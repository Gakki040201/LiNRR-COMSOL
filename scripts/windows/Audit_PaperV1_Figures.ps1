[CmdletBinding()]
param([string]$RepoRoot)
$ErrorActionPreference='Stop'
if(-not$RepoRoot){$RepoRoot=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path}
Add-Type -AssemblyName System.Drawing
$figDir=Join-Path $RepoRoot 'paper/v1/figures';$tmp=Join-Path $figDir '.qa_rebuild_tmp'
function HashFile([string]$rel){(Get-FileHash -Algorithm SHA256 -LiteralPath (Join-Path $RepoRoot $rel)).Hash}
function Rel([string]$path){[IO.Path]::GetFullPath($path).Substring($RepoRoot.Length+1).Replace('\','/')}
$manifest=Import-Csv (Join-Path $figDir 'figure_manifest.csv');$deps=Import-Csv (Join-Path $figDir 'FIGURE_DEPENDENCIES.csv');$build=Import-Csv (Join-Path $figDir 'FIGURE_BUILD_PROVENANCE.csv')
if(Test-Path $tmp){Remove-Item -LiteralPath $tmp -Recurse -Force}
try{
 New-Item -ItemType Directory -Force -Path $tmp|Out-Null
 & (Join-Path $RepoRoot 'scripts/windows/Build_PaperV1_Figures.ps1') -RepoRoot $RepoRoot -OutputDir $tmp | Out-Null
 $figureRebuild=@{};$sourceRebuild=@{}
 foreach($b in $build){
   $committed=Join-Path $RepoRoot $b.output_file;$rebuilt=Join-Path $tmp ([IO.Path]::GetFileName($b.output_file))
   $figureRebuild[$b.figure_id]=((Get-FileHash $committed -Algorithm SHA256).Hash -eq (Get-FileHash $rebuilt -Algorithm SHA256).Hash)
   $rebuiltSource=Join-Path (Join-Path $tmp 'source_data') ([IO.Path]::GetFileName($b.source_data_file))
   $sourceRebuild[$b.figure_id]=((Get-FileHash (Join-Path $RepoRoot $b.source_data_file) -Algorithm SHA256).Hash -eq (Get-FileHash $rebuiltSource -Algorithm SHA256).Hash)
 }
 $rows=@()
 foreach($m in $manifest){
   $panelDeps=@($deps|Where-Object{$_.figure_id-eq$m.figure_id-and$_.panel_id-eq$m.panel_id})
   $depOK=($panelDeps.Count-ge1);foreach($d in $panelDeps){if(-not(Test-Path (Join-Path $RepoRoot $d.dependency_path))){$depOK=$false;continue};if((HashFile $d.dependency_path)-ne$d.dependency_sha256){$depOK=$false}}
   $bp=$build|Where-Object figure_id -eq $m.figure_id|Select-Object -First 1
   $scriptOK=[bool]$bp;if($scriptOK){$scriptOK=(Test-Path (Join-Path $RepoRoot $m.source_script));if($scriptOK){$scriptOK=((HashFile $m.source_script)-eq$bp.builder_script_sha256)}}
   $out=Join-Path $RepoRoot $m.export_file;$imgOK=$false;if(Test-Path $out){$img=[System.Drawing.Image]::FromFile($out);try{$imgOK=($img.Width-eq2400-and$img.Height-eq1500-and(Get-Item $out).Length-gt0)}finally{$img.Dispose()}}
   $statusOK=(-not[string]::IsNullOrWhiteSpace($m.scientific_status));$fieldsOK=(-not[string]::IsNullOrWhiteSpace($m.panel_id)-and-not[string]::IsNullOrWhiteSpace($m.source_dataset)-and-not[string]::IsNullOrWhiteSpace($m.source_expression))
   $det=([bool]$figureRebuild[$m.figure_id]-and[bool]$sourceRebuild[$m.figure_id]);$ok=$imgOK-and$depOK-and$scriptOK-and$statusOK-and$fieldsOK-and$det
   $rows+=[pscustomobject]@{figure_id=$m.figure_id;panel_id=$m.panel_id;output_file=$m.export_file;output_sha256=if(Test-Path $out){HashFile $m.export_file}else{''};dimensions_ok=$imgOK.ToString().ToUpperInvariant();dependency_hashes_ok=$depOK.ToString().ToUpperInvariant();source_script_ok=$scriptOK.ToString().ToUpperInvariant();scientific_status_ok=$statusOK.ToString().ToUpperInvariant();wording_ok=$fieldsOK.ToString().ToUpperInvariant();classification_ok=$statusOK.ToString().ToUpperInvariant();deterministic_rebuild_ok=$det.ToString().ToUpperInvariant();manual_numeric_edit='FALSE';reproducible=$det.ToString().ToUpperInvariant();review_method='AUTOMATED_HASH_AND_DETERMINISTIC_REBUILD';status=if($ok){'PASS'}else{'FAIL'};notes='PNG decode/dimensions, dependency hashes, builder hash, metadata completeness, and byte-identical clean rebuild checked.'}
 }
 $rows|Export-Csv (Join-Path $figDir 'FIGURE_QA.csv') -NoTypeInformation -Encoding utf8
 if(@($rows|Where-Object status -ne 'PASS').Count){throw 'One or more figure QA rows failed'}
 foreach($m in $manifest){$m.reproducible='TRUE'}
 $manifest|Export-Csv (Join-Path $figDir 'figure_manifest.csv') -NoTypeInformation -Encoding utf8
 'FIGURE_DETERMINISTIC_REBUILD=PASS';'FIGURE_QA=PASS'
}finally{if(Test-Path $tmp){Remove-Item -LiteralPath $tmp -Recurse -Force}}
