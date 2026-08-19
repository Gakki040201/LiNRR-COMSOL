param([string]$ProjectRoot='',[switch]$SkipFixtureGeneration)
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
if([string]::IsNullOrWhiteSpace($ProjectRoot)){$ProjectRoot=(Resolve-Path(Join-Path $PSScriptRoot '..\..')).Path}
$ModulePath=Join-Path $ProjectRoot 'scripts\windows\modules\LiNRR_M03A2_EIS.psm1';$ManifestPath=Join-Path $ProjectRoot 'config\M03A_2_eis_manifest.csv';$AreaPath=Join-Path $ProjectRoot 'config\M03A_2_area_audit.csv'
Import-Module $ModulePath -Force
$AssertionCount=0
function Assert-True{param([bool]$Condition,[string]$Message)$script:AssertionCount++;if(-not$Condition){throw "ASSERTION FAILED: $Message"}}
function Assert-Equal{param([object]$Actual,[object]$Expected,[string]$Message)$script:AssertionCount++;if([string]$Actual-cne[string]$Expected){throw "ASSERTION FAILED: $Message; actual='$Actual'; expected='$Expected'"}}
function Case{param([object[]]$Rows,[string]$Id)$F=@($Rows|Where-Object case_id -eq $Id);if($F.Count-ne1){throw "case lookup: $Id"};return $F[0]}
function Import-Case{param([object]$C)$P=Join-Path $ProjectRoot (([string]$C.file_path).Replace('/','\'));return Import-M03A2Eis -Path $P -CaseId $C.case_id -FrequencyUnit $C.frequency_unit -ImpedanceUnit $C.impedance_unit -SampleId $C.sample_id -ReplicateId $C.replicate_id -CaseType $C.case_type}
function RelErr{param([double]$A,[double]$E)return [Math]::Abs($A-$E)/[Math]::Max([Math]::Abs($E),1e-30)}

$Manifest=@(Import-Csv -LiteralPath $ManifestPath -Encoding UTF8)
$RequiredManifest=@('sample_id','replicate_id','case_id','case_type','file_path','data_origin','frequency_unit','impedance_unit','temperature_K','electrolyte_composition','water_content','instrument','perturbation_amplitude_V','dc_bias_V','measurement_date','area_basis','EIS_area_m2','intercept_method','equivalent_circuit','source_reference','status','notes','expected_outcome','expected_hfr_ohm')
Assert-True ($Manifest.Count-ge30) 'at least 30 predeclared cases'
foreach($Name in $RequiredManifest){Assert-True ($Manifest[0].PSObject.Properties.Name-contains$Name) "manifest field $Name"}
Assert-Equal @($Manifest|Group-Object case_id|Where-Object Count -ne 1).Count 0 'unique case ids'
Assert-Equal @($Manifest|Where-Object{$_.data_origin-notin@('SYNTHETIC','EXPERIMENTAL','LITERATURE','UNKNOWN')}).Count 0 'data_origin enum'
Assert-Equal @($Manifest|Where-Object data_origin -eq SYNTHETIC).Count 32 'all generated fixtures explicitly synthetic'
$C023=Case $Manifest C023;Assert-Equal $C023.data_origin EXPERIMENTAL 'C023 is experimental';Assert-Equal $C023.expected_outcome EXPERIMENTAL_INPUT_INCOMPLETE 'C023 expected gate';Assert-True (-not(Test-Path(Join-Path $ProjectRoot (($C023.file_path).Replace('/','\'))))) 'C023 has no fabricated file'
if(-not$SkipFixtureGeneration){New-M03A2SyntheticFixtures $Manifest $ProjectRoot}

Assert-Equal (Format-M03A2Number(Convert-M03A2FrequencyToHz 1 mHz)) '0.001' 'mHz conversion'
Assert-Equal (Format-M03A2Number(Convert-M03A2FrequencyToHz 1 MHz)) '1000000' 'MHz conversion distinct'
Assert-Equal (Format-M03A2Number(Convert-M03A2ImpedanceToOhm 1000 mohm)) '1' 'mohm means milliohm'
$UnicodeBlocked=$false;try{[void](Convert-M03A2ImpedanceToOhm 1 'mΩ')}catch{$UnicodeBlocked=$_.Exception.Message-like'UNSUPPORTED_IMPEDANCE_UNIT*'};Assert-True $UnicodeBlocked 'ambiguous Unicode impedance unit blocked'

$Imports=@{};$Estimates=@{}
foreach($C in $Manifest|Where-Object data_origin -eq SYNTHETIC){$I=Import-Case $C;$Imports[$C.case_id]=$I;$Contract=if($C.intercept_method-eq'USER_DEFINED_EQUIVALENT_CIRCUIT'){$C}else{$null};$Estimates[$C.case_id]=Get-M03A2HfrEstimate $I $C.intercept_method $Contract}
foreach($Id in @('C001','C002','C003')){$E=$Estimates[$Id];$Limit=[double](Case $Manifest $Id).accuracy_threshold_relative;Assert-True ((RelErr $E.EstimateOhm ([double](Case $Manifest $Id).expected_hfr_ohm))-le$Limit) "$Id quantitative recovery";Assert-True ($E.SelectedPointCount-gt0) "$Id selected rows recorded";Assert-True (-not[string]::IsNullOrWhiteSpace($E.SelectedOriginalRows)) "$Id original row trace";Assert-True ($E.FrequencyMaxHz-ge$E.FrequencyMinHz) "$Id frequency range trace"}
$First=Get-M03A2HfrEstimate $Imports.C002 FIRST_POINT_DIAGNOSTIC;Assert-Equal $First.MethodClass LAB_SCREENING_HEURISTIC 'first point class';Assert-Equal $First.Status DIAGNOSTIC_ONLY_NOT_HFR 'first point diagnostic only'
$BlockedFirst=Get-M03A2HfrEstimate $Imports.C008 FIRST_POINT_DIAGNOSTIC;Assert-Equal $BlockedFirst.MethodClass LAB_SCREENING_HEURISTIC 'blocked first point still heuristic'
foreach($Id in @('C008','C009','C010','C011')){Assert-Equal $Imports[$Id].Status BLOCKED_INVALID_NUMERIC_DATA "$Id invalid input";Assert-Equal $Imports[$Id].AuditRows.Count $Imports[$Id].OriginalRowCount "$Id preserves raw audit"}
Assert-True ($Imports.C007.DuplicateCount-ge2) 'duplicates retained';Assert-Equal $Imports.C006.Status WARNING_HIGH_FREQUENCY_INDUCTIVE_ARTIFACT 'inductive artifact retained'
Assert-Equal $Estimates.C005.Status BLOCKED_INSUFFICIENT_HIGH_FREQUENCY_COVERAGE 'low fmax blocked'
Assert-Equal $Estimates.C030.Status BLOCKED_INSUFFICIENT_HIGH_FREQUENCY_COVERAGE 'high fmax but far from real axis blocked'
Assert-True ($Estimates.C030.FailureReason-like'*min_abs_Zimag*') 'coverage failure evidence'
foreach($Id in @('C024','C028','C029')){Assert-True ((RelErr $Estimates[$Id].EstimateOhm 2)-le.01) "$Id unit normalization"}
Assert-Equal $Imports.C031.Status BLOCKED_UNSUPPORTED_IMPEDANCE_UNIT 'Unicode unit classification'
Assert-Equal $Estimates.C004.Status METHOD_DEPENDENT_PROVISIONAL 'CPE method dependence'
Assert-Equal $Estimates.C025.Status SUPPORTED_SYNTHETIC_RANDLES_ONLY 'synthetic Randles interface';Assert-Equal $Estimates.C025.InterceptModel DECLARED_SYNTHETIC_RANDLES_RS 'declared Rs not first-point callback';Assert-Equal $Estimates.C025.SourceReference M03A2_C025_SYNTHETIC_FIT 'equivalent-circuit source'
Assert-Equal $Estimates.C032.Status BLOCKED_EQUIVALENT_CIRCUIT_CONTRACT 'incomplete equivalent-circuit contract blocked'

$Rep=@();foreach($C in $Manifest|Where-Object fixture_group -in @('AGREE','DISAGREE')){$Rep+=[PSCustomObject]@{sample_id=$C.sample_id;method=$C.intercept_method;replicate_id=$C.replicate_id;case_id=$C.case_id;hfr_ohm=$Estimates[$C.case_id].EstimateOhm}}
$Consensus=@(Get-M03A2ReplicateConsensus $Rep);Assert-Equal $Consensus.Count 2 'one consensus row per sample and method';Assert-Equal @($Consensus|Where-Object sample_id -eq S_DISAGREE)[0].replicate_count 3 'outlier retained in count';Assert-Equal @($Consensus|Where-Object sample_id -eq S_DISAGREE)[0].consensus_status REPLICATE_DISAGREEMENT 'disagreement classified'

$ValidComponents=@{fixture=@{value_ohm=.2;uncertainty_ohm=.002;source='FIXTURE_MEASUREMENT_SYNTHETIC';status='MEASURED'};contact=@{value_ohm=.1;uncertainty_ohm=.002;source='CONTACT_MEASUREMENT_SYNTHETIC';status='MEASURED'};membrane=@{value_ohm=.15;uncertainty_ohm=.002;source='MEMBRANE_MEASUREMENT_SYNTHETIC';status='MEASURED'};other_series=@{value_ohm=.05;uncertainty_ohm=.002;source='OTHER_MEASUREMENT_SYNTHETIC';status='MEASURED'}}
$ValidLedger=Get-M03A2ResistanceLedger LEDGER_OK C001 2 .01 $ValidComponents;Assert-Equal $ValidLedger.status PASS 'complete ledger';Assert-True ([double]$ValidLedger.R_electrolyte_uncertainty_ohm-gt0) 'ledger uncertainty'
$Negative=Get-M03A2ResistanceLedger LEDGER_NEG C018 .4 .01 $ValidComponents;Assert-Equal $Negative.status BLOCKED_NEGATIVE_DEEMBEDDED_RESISTANCE 'negative de-embedded block'
$Zero=Get-M03A2ResistanceLedger LEDGER_ZERO C026 .5 .01 $ValidComponents;Assert-Equal $Zero.status BLOCKED_NONPOSITIVE_DEEMBEDDED_RESISTANCE 'zero de-embedded block'
$Missing=$ValidComponents.Clone();$Missing.Remove('other_series');$MissingLedger=Get-M03A2ResistanceLedger LEDGER_MISSING C022 2 .01 $Missing;Assert-Equal $MissingLedger.status BLOCKED_MISSING_SERIES_COMPONENT 'missing component not zero'

$A=Get-M03A2AnalyticalUncertainty 2 .01 @(.2,.1,.15,.05) @(.002,.002,.002,.002) .004 .00004 .003025 .00003025;Assert-Equal $A.status PASS 'analytical uncertainty'
$Means=@{hfr=2.;fixture=.2;contact=.1;membrane=.15;other=.05;thickness=.004;area=.003025};$U=@{hfr=.01;fixture=.002;contact=.002;membrane=.002;other=.002;thickness=.00004;area=.00003025};$Mc1=Invoke-M03A2MonteCarlo $Means $U 314159 5000;$Mc2=Invoke-M03A2MonteCarlo $Means $U 314159 5000;Assert-Equal (Format-M03A2Number $Mc1.mean) (Format-M03A2Number $Mc2.mean) 'fixed seed mean';Assert-Equal $Mc1.MC_sample_count ($Mc1.MC_accepted_count+$Mc1.MC_rejected_count) 'MC accounting'
$BadMeans=$Means.Clone();$BadMeans.hfr=.55;$BadU=$U.Clone();$BadU.hfr=.1;$Bad=Invoke-M03A2MonteCarlo $BadMeans $BadU 314159 5000;Assert-Equal $Bad.status FAILED_INPUT_DISTRIBUTION 'rejection fraction gate';Assert-True ($Bad.MC_rejected_fraction-gt.01) 'rejected fraction reported without repair'

$Area=@(Invoke-M03A2AreaAudit @(Import-Csv -LiteralPath $AreaPath -Encoding UTF8));Assert-Equal $Area.Count 18 'long-form rows retained';Assert-Equal @($Area|Where-Object case_id -eq C001|Select-Object -First 1).status PASS 'complete mapping passes';Assert-Equal @($Area|Where-Object case_id -eq C019|Select-Object -First 1).status AREA_BASIS_MISMATCH 'missing mapping blocks';Assert-Equal @($Area|Where-Object case_id -eq C023|Select-Object -First 1).status EXPERIMENTAL_INPUT_INCOMPLETE 'experimental area incomplete'

Write-Output ("M03A2_TEST|PASS|assertions={0}" -f $AssertionCount)
