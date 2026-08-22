[CmdletBinding()]
param([string]$RepoRoot)
$ErrorActionPreference='Stop'
if(-not $RepoRoot){$RepoRoot=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path}
Set-Location $RepoRoot
$fail=New-Object System.Collections.Generic.List[string]
function Gate([bool]$ok,[string]$name,[string]$detail=''){if($ok){"$name=PASS"}else{$script:fail.Add("$name`: $detail");"$name=FAIL"}}
function Hash([string]$rel){if(-not(Test-Path -LiteralPath $rel)){return ''};(Get-FileHash -Algorithm SHA256 -LiteralPath $rel).Hash}

$branch=(git branch --show-current).Trim(); Gate ($branch-eq'paper/v1-transport-current-freeze') 'BRANCH_GATE' $branch
$head=(git rev-parse HEAD).Trim(); git merge-base --is-ancestor a9314f89ee4a79492b88948dff912dc885feb7d6 HEAD; Gate ($LASTEXITCODE-eq0) 'BASE_ANCESTRY' $head
$remoteMain=(git rev-parse origin/main).Trim(); Gate ($remoteMain-eq'a9314f89ee4a79492b88948dff912dc885feb7d6') 'REMOTE_MAIN_IDENTITY' $remoteMain
$tagTarget=(git rev-parse 'm10a4-realcell-electrochemistry-v1^{}').Trim(); Gate ($tagTarget-eq'a9314f89ee4a79492b88948dff912dc885feb7d6') 'M10A4_TAG' $tagTarget

$mph='models/generated/LiNRR_M10A4_ionic_current_li_plating.mph'; $a3='models/generated/LiNRR_M10A3_real_species_transport.mph'
Gate ((Hash $mph)-eq'FADEEA4D8ADF9E472B4B855B05E39C22EF418214C0F62FB0C1977ACA36DA667B') 'M10A4_FINAL_MPH_HASH' (Hash $mph)
Gate ((Get-Item $mph).Length-eq807839751) 'M10A4_FINAL_MPH_SIZE' ((Get-Item $mph).Length)
Gate ((Hash $a3)-eq'03612FDB08D993595ABDA41D5873CBBCAA97580DB432ADCD2FBCD2CA06260C00') 'M10A3_HASH' (Hash $a3)
$collector='cad/raw/M10A0_2/block_currentCollector_flowField_v03.2.1_20210525_STEP-AP203.STEP';$chamber='cad/raw/M10A0_2/electrolyteChamber_rectangularMask_v02.2.2_20210521_STEP-AP203.STEP'
Gate ((Hash $collector)-eq'0091777D10ECA620E08780381438F3522B3BE31FEE307F9DDCF074A1FACDDFE9') 'COLLECTOR_STEP_HASH' (Hash $collector)
Gate ((Hash $chamber)-eq'AE350E006228A10EEC4476F79CAFCE6D63F18F10F58F0A8B3559EED0C37FF12C') 'CHAMBER_STEP_HASH' (Hash $chamber)
$report=Get-Content 'evidence/M10A4/M10A4_final_report.txt'; Gate ([bool]($report-match'^M10A4_OVERALL=PASS$')) 'M10A4_ACCEPTANCE' 'final report marker absent'

$required=@('paper/v1/MODEL_SCOPE.md','paper/v1/MODEL_FREEZE_MANIFEST.csv','paper/v1/PARAMETER_PROVENANCE.csv','paper/v1/CLAIM_EVIDENCE_MATRIX.csv','paper/v1/REGRESSION_EVIDENCE.csv','paper/v1/MODEL_LIMITATIONS.csv','paper/v1/SOURCE_ARTIFACT_INDEX.csv','paper/v1/MODEL_FREEZE_REPORT.md','paper/v1/figures/figure_manifest.csv','paper/v1/figures/FIGURE_QA.csv')
foreach($p in $required){Gate (Test-Path -LiteralPath $p) ('REQUIRED_'+[IO.Path]::GetFileName($p).ToUpperInvariant()) $p}

$manifest=Import-Csv 'paper/v1/MODEL_FREEZE_MANIFEST.csv'; $manifestBad=@()
foreach($r in $manifest){if(-not(Test-Path -LiteralPath $r.path)){$manifestBad+="missing:$($r.path)";continue};$h=Hash $r.path;if($h-ne$r.SHA256){$manifestBad+="hash:$($r.path)"};if([string](Get-Item -LiteralPath $r.path).Length-ne[string]$r.size_bytes){$manifestBad+="size:$($r.path)"}}
Gate ($manifestBad.Count-eq0) 'MODEL_FREEZE_MANIFEST' ($manifestBad-join';')
Gate (@($manifest|Where-Object path -eq $mph).Count-eq1) 'MANIFEST_FINAL_MPH_UNIQUE' 'missing or duplicate final MPH row'
$paperFiles=@(Get-ChildItem paper/v1 -Recurse -File|ForEach-Object{$_.FullName.Substring($RepoRoot.Length+1).Replace('\','/')}|Where-Object{$_-ne'paper/v1/MODEL_FREEZE_MANIFEST.csv'});$unmanifested=@($paperFiles|Where-Object{$p=$_;@($manifest|Where-Object path -eq $p).Count-ne1})
Gate ($unmanifested.Count-eq0) 'ALL_PAPER_ARTIFACTS_MANIFESTED' ($unmanifested-join',')

$prov=Import-Csv 'paper/v1/PARAMETER_PROVENANCE.csv';$classes=@('LAB_MEASURED','LAB_MANUAL','DERIVED_FROM_LAB_MANUAL','LITERATURE_REPORTED','LITERATURE_SAME_PLATFORM','LITERATURE_ESTIMATE','PROVISIONAL_SENSITIVITY','CALIBRATION_REQUIRED','NUMERICAL_VERIFICATION_ONLY','DERIVED','DERIVED_DIAGNOSTIC','REAL_CAD')
$provBad=@($prov|Where-Object{[string]::IsNullOrWhiteSpace($_.source_class)-or$classes-notcontains$_.source_class-or[string]::IsNullOrWhiteSpace($_.allowed_interpretation)-or[string]::IsNullOrWhiteSpace($_.forbidden_interpretation)})
Gate ($provBad.Count-eq0) 'PARAMETER_PROVENANCE' (($provBad.parameter)-join',')
Gate (@($prov|Where-Object parameter -eq 'Electrolyte conductivity').calibration_status-eq'CALIBRATION_REQUIRED') 'KAPPA_CLASSIFICATION' 'conductivity upgraded'
Gate (@($prov|Where-Object parameter -eq 'Generic donor diffusivity').allowed_interpretation-eq'generic donor transport diagnostic') 'GENERIC_DONOR_CLASSIFICATION' 'donor semantics drifted'

$claims=Import-Csv 'paper/v1/CLAIM_EVIDENCE_MATRIX.csv';$claimBad=@($claims|Where-Object{[string]::IsNullOrWhiteSpace($_.authority_level)-or[string]::IsNullOrWhiteSpace($_.current_support)-or[string]::IsNullOrWhiteSpace($_.model_artifact)})
Gate ($claimBad.Count-eq0) 'CLAIM_EVIDENCE_MATRIX' (($claimBad.claim_id)-join',')
$mechBad=@($claims|Where-Object{$_.claim_type-eq'MECHANISTIC_CLAIM'-and$_.current_support-ne'UNSUPPORTED_IN_PAPER_V1_MODEL'})
Gate ($mechBad.Count-eq0) 'UNSUPPORTED_MECHANISTIC_CLAIMS' (($mechBad.claim_id)-join',')
$reg=Import-Csv 'paper/v1/REGRESSION_EVIDENCE.csv';Gate (@($reg|Where-Object{$_.status-notmatch'^PASS'}).Count-eq0) 'REGRESSION_EVIDENCE' 'non-accepted regression row'
$lim=Import-Csv 'paper/v1/MODEL_LIMITATIONS.csv';Gate ($lim.Count-ge15) 'MODEL_LIMITATIONS' "rows=$($lim.Count)"
$src=Import-Csv 'paper/v1/SOURCE_ARTIFACT_INDEX.csv';$srcBad=@($src|Where-Object{[string]::IsNullOrWhiteSpace($_.repository_path)-or-not(Test-Path -LiteralPath $_.repository_path)-or((Hash $_.repository_path)-ne$_.SHA256)-or[string]::IsNullOrWhiteSpace($_.classification)})
Gate ($srcBad.Count-eq0) 'SOURCE_ARTIFACT_INDEX' (($srcBad.artifact_id)-join',')

$fm=Import-Csv 'paper/v1/figures/figure_manifest.csv';$fmbad=@($fm|Where-Object{[string]::IsNullOrWhiteSpace($_.source_artifact)-or-not(Test-Path -LiteralPath $_.source_artifact)-or-not(Test-Path -LiteralPath $_.source_script)-or-not(Test-Path -LiteralPath $_.export_file)-or$_.reproducible-ne'TRUE'})
Gate ($fm.Count-eq7-and$fmbad.Count-eq0) 'FIGURE_MANIFEST' "rows=$($fm.Count);bad=$($fmbad.figure_id-join',')"
$qa=Import-Csv 'paper/v1/figures/FIGURE_QA.csv';$qabad=@($qa|Where-Object{$_.status-ne'PASS'-or$_.manual_numeric_edit-ne'FALSE'-or$_.units_ok-ne'TRUE'-or$_.source_linked-ne'TRUE'-or$_.wording_ok-ne'TRUE'-or$_.classification_ok-ne'TRUE'-or$_.reproducible-ne'TRUE'})
Gate ($qa.Count-eq7-and$qabad.Count-eq0) 'FIGURE_QA' "rows=$($qa.Count);bad=$($qabad.figure_id-join',')"
Gate (-not(Test-Path 'paper/v1/manuscript_v1.md')) 'NO_MANUSCRIPT' 'manuscript_v1.md exists'

# Diff-level freeze boundaries: source physics, MPH, CAD, and run outputs may not change relative to base.
$protected=@(git diff --name-only a9314f89ee4a79492b88948dff912dc885feb7d6 -- src/java models/generated cad/raw runs)
Gate ($protected.Count-eq0) 'NO_ACCEPTED_MODEL_MUTATION' ($protected-join',')
$newMph=@(Get-ChildItem -Recurse -File paper -Filter *.mph -ErrorAction SilentlyContinue);Gate ($newMph.Count-eq0) 'NO_DUPLICATE_MPH' (($newMph.FullName)-join',')

# Context-aware wording audit: scan affirmative prose, not forbidden-wording or limitation columns.
$prose=@('paper/v1/MODEL_SCOPE.md','paper/v1/MODEL_FREEZE_REPORT.md')
$badPhrases='validated real-cell prediction|predicted FE|FE map|NH3 rate map|NH3 production map|real Li thickness|predicted Li thickness|SEI mechanism confirmed|Li3N mechanism confirmed|HER selectivity prediction|HOR kinetics prediction|full-cell voltage prediction'
$wordBad=@();foreach($p in $prose){$n=0;foreach($line in Get-Content $p){$n++;if($line-match$badPhrases-and$line-notmatch'(?i)does not|not |no |forbidden|outside|cannot|do not'){$wordBad+="${p}:${n}:$line"}}}
Gate ($wordBad.Count-eq0) 'PROHIBITED_WORDING' ($wordBad-join';')

# Scan only new freeze text/scripts for credential or license material; allow variable names in this audit's own pattern list.
$scanFiles=@(Get-ChildItem paper/v1 -Recurse -File|Where-Object Extension -in '.md','.csv','.ps1','.txt')+@(Get-Item scripts/windows/Build_PaperV1_*.ps1)
$secretPattern='-----BEGIN (RSA |OPENSSH |EC )?PRIVATE KEY-----|ghp_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{40,}|AKIA[0-9A-Z]{16}'
$secretBad=@();foreach($f in $scanFiles){if($f.Name-eq'PaperV1_ModelFreezeAudit.ps1'){continue};$hit=Select-String -LiteralPath $f.FullName -Pattern $secretPattern;if($hit){$secretBad+=$f.FullName}}
Gate ($secretBad.Count-eq0) 'CREDENTIAL_AUDIT' ($secretBad-join',')

$diffCheck=git diff --check 2>&1;Gate ($LASTEXITCODE-eq0) 'GIT_DIFF_CHECK' ($diffCheck-join';')
Gate ($fail.Count-eq0) 'PAPER_V1_MODEL_FREEZE' ($fail-join' | ')
if($fail.Count){'PAPER_V1_MODEL_FREEZE=BLOCKED';$fail|ForEach-Object{"BLOCKER=$_"};exit 1}
'PAPER_V1_BASE_MAIN=a9314f89ee4a79492b88948dff912dc885feb7d6'
'PAPER_V1_SOURCE_TAG=m10a4-realcell-electrochemistry-v1'
'PAPER_V1_BRANCH=paper/v1-transport-current-freeze'
'M10A4_FINAL_MPH=models/generated/LiNRR_M10A4_ionic_current_li_plating.mph'
'M10A4_FINAL_MPH_SHA256=FADEEA4D8ADF9E472B4B855B05E39C22EF418214C0F62FB0C1977ACA36DA667B'
'M10A3_SHA256=03612FDB08D993595ABDA41D5873CBBCAA97580DB432ADCD2FBCD2CA06260C00'
'MODEL_FREEZE_MANIFEST=paper/v1/MODEL_FREEZE_MANIFEST.csv'
'PARAMETER_PROVENANCE=paper/v1/PARAMETER_PROVENANCE.csv'
'CLAIM_EVIDENCE_MATRIX=paper/v1/CLAIM_EVIDENCE_MATRIX.csv'
'REGRESSION_EVIDENCE=paper/v1/REGRESSION_EVIDENCE.csv'
'MODEL_LIMITATIONS=paper/v1/MODEL_LIMITATIONS.csv'
'MAIN_FIGURE_COUNT=7'
'SUPPLEMENT_FIGURE_COUNT=0'
'FIGURE_MANIFEST=paper/v1/figures/figure_manifest.csv'
'FIGURE_QA=paper/v1/figures/FIGURE_QA.csv'
'NEW_COMSOL_SOLVES=0'
'NEW_PHYSICS_CREATED=FALSE'
'SEI_CREATED=FALSE'
'LI3N_KINETICS_CREATED=FALSE'
'LINRR_KINETICS_CREATED=FALSE'
'HER_CREATED=FALSE'
'HOR_CREATED=FALSE'
'FE_PREDICTED=FALSE'
'NH3_KINETICS_PREDICTED=FALSE'
'ACTUAL_LI_THICKNESS_PREDICTED=FALSE'
'FULL_CELL_VOLTAGE_PREDICTED=FALSE'
'EXPERIMENTAL_VALIDATION_CLAIMED=FALSE'
'PAPER_V1_MODEL_FREEZE=PASS'
