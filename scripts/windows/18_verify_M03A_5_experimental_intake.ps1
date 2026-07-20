param([switch]$CreateTimestampedRun)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$BaselineCommit = 'af99111f7f33a3f0698dcb84500398e1e11386a7'
$OriginAuditRef = 'refs/remotes/origin/m03a4-calibration-intake-and-transfer-dry-run'
$env:GIT_PAGER = 'cat'

function Get-NormalizedRepositoryPath([string]$Path) {
    return [IO.Path]::GetFullPath($Path).Replace('/','\').TrimEnd('\').ToLowerInvariant()
}

function Invoke-Git([string[]]$Arguments) {
    $Output=@(& git -C $ProjectRoot @Arguments 2>&1)
    if($LASTEXITCODE -ne 0){throw "git $($Arguments -join ' ') failed: $($Output -join ' ')"}
    return $Output
}

function Get-GitObjectBytes([string]$ObjectSpec) {
    $Info=New-Object Diagnostics.ProcessStartInfo
    $Info.FileName='git.exe'
    $QuotedRoot=$ProjectRoot.Replace('"','\"')
    $Info.Arguments="-C `"$QuotedRoot`" cat-file blob $ObjectSpec"
    $Info.UseShellExecute=$false;$Info.RedirectStandardOutput=$true;$Info.RedirectStandardError=$true;$Info.CreateNoWindow=$true
    $Process=New-Object Diagnostics.Process;$Process.StartInfo=$Info
    if(-not $Process.Start()){throw 'unable to start git cat-file'}
    $Memory=New-Object IO.MemoryStream
    $Process.StandardOutput.BaseStream.CopyTo($Memory);$ErrorText=$Process.StandardError.ReadToEnd();$Process.WaitForExit()
    if($Process.ExitCode -ne 0){throw "git cat-file failed: $ErrorText"}
    $Bytes=$Memory.ToArray();$Memory.Dispose();$Process.Dispose();return $Bytes
}

function Assert-PortableRepositoryPreflight {
    $GitRoot=([string](Invoke-Git @('rev-parse','--show-toplevel'))).Trim()
    if((Get-NormalizedRepositoryPath $GitRoot) -ne (Get-NormalizedRepositoryPath $ProjectRoot)){throw "repository root mismatch: project=$ProjectRoot git=$GitRoot"}
    [void](Invoke-Git @('cat-file','-e',"$BaselineCommit^{commit}"))
    & git -C $ProjectRoot merge-base --is-ancestor $BaselineCommit HEAD 2>$null
    if($LASTEXITCODE -ne 0){throw "$BaselineCommit is not an ancestor of HEAD"}
    $Head=([string](Invoke-Git @('rev-parse','HEAD'))).Trim()
    $BranchOutput=@(& git -C $ProjectRoot symbolic-ref --short -q HEAD 2>$null)
    $Branch=if($LASTEXITCODE -eq 0 -and $BranchOutput.Count){([string]$BranchOutput[0]).Trim()}else{'DETACHED'}
    & git -C $ProjectRoot show-ref --verify --quiet $OriginAuditRef
    if($LASTEXITCODE -eq 0){$Origin=([string](Invoke-Git @('rev-parse',$OriginAuditRef))).Trim();$OriginState=if($Origin -eq $BaselineCommit){'MATCHES_BASELINE'}else{"RECORDED_DIFFERENT:$Origin"}}else{$OriginState='ABSENT_ALLOWED'}
    "M03A5_HEAD_RECORD=$Head";"M03A5_BRANCH_RECORD=$Branch";"M03A5_ORIGIN_M03A4_RECORD=$OriginState";'M03A5_REPOSITORY_PORTABILITY=PASS';'M03A5_PORTABLE_REPOSITORY_PREFLIGHT=PASS'
}

function Assert-FrozenBaseline {
    $BaselinePaths=@(Invoke-Git @('ls-tree','-r','--name-only',$BaselineCommit))
    if($BaselinePaths.Count -ne 259){throw "baseline tracked path count changed: $($BaselinePaths.Count)"}
    foreach($Path in $BaselinePaths){
        if($Path -eq 'docs/DECISIONS.md'){continue}
        & git -C $ProjectRoot diff --quiet --no-ext-diff $BaselineCommit -- $Path
        if($LASTEXITCODE -ne 0){throw "frozen baseline path changed, deleted, or changed type: $Path"}
    }
    'M03A5_BASELINE_TRACKED_PATH_COUNT=259';'M03A5_FROZEN_BASELINE=PASS'
}

function Assert-D0016AppendOnly {
    $BaselineBytes=Get-GitObjectBytes "$BaselineCommit`:docs/DECISIONS.md"
    $CurrentPath=Join-Path $ProjectRoot 'docs\DECISIONS.md';$CurrentBytes=[IO.File]::ReadAllBytes($CurrentPath)
    if($CurrentBytes.Length -le $BaselineBytes.Length){throw 'D0016 append block is missing'}
    for($Index=0;$Index -lt $BaselineBytes.Length;$Index++){if($CurrentBytes[$Index] -ne $BaselineBytes[$Index]){throw "DECISIONS.md differs before D0016 at byte $Index"}}
    $SuffixBytes=New-Object byte[] ($CurrentBytes.Length-$BaselineBytes.Length);[Array]::Copy($CurrentBytes,$BaselineBytes.Length,$SuffixBytes,0,$SuffixBytes.Length)
    $Suffix=[Text.UTF8Encoding]::new($false,$true).GetString($SuffixBytes)
    $Heading="## D0016 $([char]0x2014) M03A.5 experimental calibration acquisition and intake readiness"
    $HeadingLines=@($Suffix -split "`r?`n"|Where-Object{$_ -ceq $Heading})
    if($HeadingLines.Count -ne 1){throw "D0016 heading must occur exactly once in appended bytes; observed=$($HeadingLines.Count)"}
    if(([regex]::Matches($Suffix,'(?m)^## ')).Count -ne 1 -or -not $Suffix.TrimStart("`r","`n").StartsWith($Heading,[StringComparison]::Ordinal)){throw 'only the D0016 block may follow the baseline bytes'}
    'M03A5_D0016_APPEND_ONLY=PASS'
}

function Assert-PowerShellParsers([string[]]$Paths) {
    foreach($Path in $Paths){$Tokens=$null;$Errors=$null;[Management.Automation.Language.Parser]::ParseFile($Path,[ref]$Tokens,[ref]$Errors)|Out-Null;if($Errors.Count){throw "PowerShell parser failed for $Path : $($Errors[0].Message)"}}
    'M03A5_POWERSHELL_PARSER=PASS'
}

$PreflightOutput=@(Assert-PortableRepositoryPreflight);$PreflightOutput|ForEach-Object{$_}
$FrozenOutput=@(Assert-FrozenBaseline);$FrozenOutput|ForEach-Object{$_}
$D0016Output=@(Assert-D0016AppendOnly);$D0016Output|ForEach-Object{$_}

$ModulePath=Join-Path $ProjectRoot 'scripts\windows\modules\LiNRR_M03A5_ExperimentalIntake.psm1'
$TestScript=Join-Path $ProjectRoot 'tests\powershell\Test_M03A_5_ExperimentalIntake.ps1'
Assert-PowerShellParsers @($PSCommandPath,$ModulePath,$TestScript)|ForEach-Object{$_}
Import-Module $ModulePath -Force

$Paths=@{
    Manifest=Join-Path $ProjectRoot 'config\M03A_5_experimental_manifest.csv'
    Resistance=Join-Path $ProjectRoot 'config\M03A_5_resistance_ledger.csv'
    Area=Join-Path $ProjectRoot 'config\M03A_5_area_measurements.csv'
    Direct=Join-Path $ProjectRoot 'config\M03A_5_direct_conductivity.csv'
    Threshold=Join-Path $ProjectRoot 'config\M03A_5_acceptance_thresholds.csv'
}
$Audit=Invoke-M03A5IntakeAudit -ProjectRoot $ProjectRoot -ManifestPath $Paths.Manifest -ResistancePath $Paths.Resistance -AreaPath $Paths.Area -DirectPath $Paths.Direct -ThresholdPath $Paths.Threshold -ExpectedDataOrigin EXPERIMENTAL
if(-not $Audit.FrameworkAuditPassed){throw 'M03A.5 framework audit did not complete'}
if($Audit.BlockedFindingCount -ne 0){throw "blank intake package produced $($Audit.BlockedFindingCount) blocking findings"}
if($Audit.Completeness.Count -ne 136 -or $Audit.MissingInputs.Count -ne 136){throw "canonical missing-field reconstruction changed: completeness=$($Audit.Completeness.Count) missing=$($Audit.MissingInputs.Count)"}
if($Audit.ExperimentalInputComplete -or $Audit.ParameterTransferEligible -or $Audit.M03BCandidate -or $Audit.M03BReady){throw 'blank intake package was incorrectly promoted'}
'M03A5_CANONICAL_CSV_FRAMEWORK=PASS';'M03A5_INPUT_COMPLETENESS_COUNT=136'

$TablesDir=Join-Path $ProjectRoot 'results\tables';$LatestDir=Join-Path $ProjectRoot 'runs\latest';$ReportsDir=Join-Path $ProjectRoot 'results\reports'
foreach($Directory in @($TablesDir,$LatestDir,$ReportsDir)){if(-not(Test-Path -LiteralPath $Directory)){New-Item -ItemType Directory -Path $Directory -Force|Out-Null}}
$CompletenessPath=Join-Path $TablesDir 'M03A_5_input_completeness.csv';$MissingPath=Join-Path $TablesDir 'M03A_5_missing_inputs.csv';$ProvenancePath=Join-Path $TablesDir 'M03A_5_provenance_audit.csv';$ReadinessPath=Join-Path $TablesDir 'M03A_5_readiness.csv';$RegressionPath=Join-Path $TablesDir 'M03A_5_synthetic_regression.csv'
Write-M03A5Utf8NoBomCsv $Audit.Completeness $CompletenessPath;Write-M03A5Utf8NoBomCsv $Audit.MissingInputs $MissingPath;Write-M03A5Utf8NoBomCsv $Audit.Provenance $ProvenancePath;Write-M03A5Utf8NoBomCsv $Audit.Readiness $ReadinessPath

$TestOutput=@(& $TestScript -OutputPath $RegressionPath);$TestOutput|ForEach-Object{$_}
if(@($TestOutput|Where-Object{$_ -eq 'M03A5_REGRESSION_COUNT=69'}).Count -ne 1 -or @($TestOutput|Where-Object{$_ -eq 'M03A5_REGRESSION_PASS_COUNT=69'}).Count -ne 1 -or @($TestOutput|Where-Object{$_ -eq 'M03A5_REGRESSION_FAIL_COUNT=0'}).Count -ne 1){throw 'self-contained regression count markers are missing'}

$ReportLines=New-Object Collections.Generic.List[string]
$ReportLines.Add('# M03A.5 experimental calibration intake report');$ReportLines.Add('');$ReportLines.Add('Canonical framework audit; no timestamped run was requested by default.');$ReportLines.Add('')
$ReportLines.Add('No real experimental files are present. This is a framework/schema audit, not an experimental calibration PASS. COMSOL was not started, no MPH was created, no parameter was transferred, and the M03A.4 active release was not modified.');$ReportLines.Add('')
$ReportLines.Add('## Enforced readiness');$ReportLines.Add('');foreach($Row in $Audit.Readiness){$ReportLines.Add("- $($Row.item) = $($Row.value) - $($Row.detail)")};$ReportLines.Add('')
$ReportLines.Add('## Data the user must collect or enter next');$ReportLines.Add('')
foreach($Group in @($Audit.MissingInputs|Group-Object source_file)){$ReportLines.Add("### $($Group.Name)");$ReportLines.Add('');foreach($Row in $Group.Group){$ReportLines.Add(('- `{0}` / `{1}`: {2}' -f $Row.record_id,$Row.field_name,$Row.required_action))};$ReportLines.Add('')}
$ReportLines.Add('## Scientific gates');$ReportLines.Add('');$ReportLines.Add('- HFR consensus accepts only reviewed allowlisted statuses and methods after raw provenance, normalized provenance, normalized-row audit, and frequency metadata closure pass for that replicate.');$ReportLines.Add('- Replicate compatibility covers cell, instrument/software, temperature, electrolyte/batch/concentration/water metadata, gas/pressure/flow/stabilization, perturbation, DC/OCP condition and bias, and EIS area basis/value.');$ReportLines.Add('- HFR mean uncertainty uses `u_within_mean^2 = sum(u_i^2)/n^2`, `u_between_mean^2 = s_between^2/n`, and `u_HFR_mean = sqrt(u_within_mean^2 + u_between_mean^2)`.');$ReportLines.Add('- Direct and HFR-derived conductivity are compared only after sample and configured temperature compatibility; conflicts are reported without automatic selection.');$ReportLines.Add('- Monte Carlo uses the configured requested denominator and fixed seed, rejects nonphysical draws, and performs no clipping, repair, or resampling.');$ReportLines.Add('')
$ReportLines.Add('## Framework and provenance audit');$ReportLines.Add('');$ReportLines.Add('- 136 required user inputs were reconstructed from the canonical schemas.');$ReportLines.Add('- Raw and normalized evidence are independent roles with repository-relative path, reparse-point, exact Int64 byte-size, and SHA-256 checks.');$ReportLines.Add('- Normalized frequency, z_real, and z_imag rows are retained; duplicate, inductive, and insufficient-HF diagnostics are not deleted.');$ReportLines.Add('- 69/69 synthetic structural and negative regressions passed; synthetic data cannot open experimental or M03B gates.');$ReportLines.Add('- Portable repository preflight, 259-path frozen baseline audit, and byte-prefix D0016 append-only audit passed.');$ReportLines.Add('')
$ReportLines.Add('## Remaining uncertainty');$ReportLines.Add('');$ReportLines.Add('Every experimental value, evidence hash, replicate consensus, de-embedded resistance, conductivity comparison, uncertainty result, and transfer authorization remains unknown until controlled experimental evidence is supplied.')
$ReportText=($ReportLines -join "`r`n")+"`r`n";$LatestReport=Join-Path $LatestDir 'M03A_5_report.md';$DurableReport=Join-Path $ReportsDir 'M03A_5_report.md';Write-M03A5Utf8NoBomText $LatestReport $ReportText;Write-M03A5Utf8NoBomText $DurableReport $ReportText
if((Get-M03A5Sha256 $LatestReport) -ne (Get-M03A5Sha256 $DurableReport)){throw 'latest and durable reports differ'}

if($CreateTimestampedRun){$RunId=(Get-Date).ToUniversalTime().ToString('yyyyMMdd_HHmmss_fff')+'_'+[guid]::NewGuid().ToString('N').Substring(0,8)+'_M03A_5';$RunDir=Join-Path $ProjectRoot "runs\$RunId";$RunTables=Join-Path $RunDir 'tables';New-Item -ItemType Directory -Path $RunTables -Force|Out-Null;Write-M03A5Utf8NoBomText (Join-Path $RunDir 'M03A_5_report.md') $ReportText;foreach($Path in @($CompletenessPath,$MissingPath,$ProvenancePath,$ReadinessPath,$RegressionPath)){Copy-Item -LiteralPath $Path -Destination (Join-Path $RunTables (Split-Path -Leaf $Path))};"M03A5_TIMESTAMPED_RUN=$RunId"}else{'M03A5_TIMESTAMPED_RUN=NOT_CREATED'}

foreach($Path in @($CompletenessPath,$MissingPath,$ProvenancePath,$ReadinessPath,$RegressionPath,$LatestReport,$DurableReport)){if(-not(Test-Path -LiteralPath $Path -PathType Leaf)){throw "required output missing: $Path"}}
$ForbiddenMph=@(Get-ChildItem -LiteralPath $ProjectRoot -Recurse -File -Filter '*.mph'|Where-Object{$_.Name -match 'M03A[._]5'});if($ForbiddenMph.Count){throw 'M03A.5 derivative MPH is forbidden'}
'M03A5_HFR_ELIGIBILITY=PASS';'M03A5_REPLICATE_COMPATIBILITY=PASS';'M03A5_FREQUENCY_METADATA_CLOSURE=PASS';'M03A5_DIRECT_TEMPERATURE_GATE=PASS';'M03A5_EXACT_INT64_PROVENANCE=PASS';'M03A5_DURABLE_REPORT=PASS';'M03A5_REGRESSIONS=PASS'
'SUCCESS|M03A.5 experimental calibration intake framework complete|RUN_STATE=EXPERIMENTAL_INPUT_WAIT|CALIBRATION_MODE=PROVISIONAL|EXPERIMENTAL_INPUT_COMPLETE=FALSE|PARAMETER_TRANSFER_MODE=NOT_RUN|M03B_CANDIDATE=FALSE|M03B_READY=FALSE'
