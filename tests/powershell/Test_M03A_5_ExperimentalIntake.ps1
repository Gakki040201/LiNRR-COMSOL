param([string]$OutputPath='')

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$ModulePath = Join-Path $ProjectRoot 'scripts\windows\modules\LiNRR_M03A5_ExperimentalIntake.psm1'
$FixtureRoot = Join-Path $ProjectRoot 'tests\fixtures\M03A_5'
Import-Module $ModulePath -Force
$ThresholdDefaults=@{}
foreach($ThresholdRow in @(Import-Csv -LiteralPath (Join-Path $ProjectRoot 'config\M03A_5_acceptance_thresholds.csv') -Encoding UTF8)){$ThresholdDefaults[[string]$ThresholdRow.threshold_name]=[string]$ThresholdRow.value}

$Results = New-Object Collections.Generic.List[object]
function Add-Result([string]$Id,[string]$Name,[string]$Expected,[string]$Actual,[string]$Status,[string]$Reason) {
    $Results.Add([pscustomobject][ordered]@{case_id=$Id;case_name=$Name;expected_status=$Expected;actual_status=$Actual;status=$Status;failure_reason=$Reason;data_origin='SYNTHETIC'})
}
function Clone-Rows([object[]]$Rows) { return @($Rows | ForEach-Object { $_ | Select-Object * }) }

function New-SyntheticPackage([string]$Root) {
    New-Item -ItemType Directory -Path (Join-Path $Root 'evidence') -Force | Out-Null
    $Evidence = @{}
    foreach ($Index in 1..3) {
        $RawName = "base_raw_rep$Index.txt"; $NormName = "base_normalized_rep$Index.csv"
        $RawDest = Join-Path $Root "evidence\$RawName"; $NormDest = Join-Path $Root "evidence\$NormName"
        Copy-Item -LiteralPath (Join-Path $FixtureRoot $RawName) -Destination $RawDest
        $NormRows = @(Import-Csv -LiteralPath (Join-Path $FixtureRoot $NormName) -Encoding UTF8)
        Write-M03A5Utf8NoBomCsv $NormRows $NormDest
        $Evidence["raw$Index"] = [pscustomobject]@{relative="evidence/$RawName";size=(Get-Item $RawDest).Length;hash=Get-M03A5Sha256 $RawDest}
        $Evidence["norm$Index"] = [pscustomobject]@{relative="evidence/$NormName";size=(Get-Item $NormDest).Length;hash=Get-M03A5Sha256 $NormDest}
    }
    $Hfr = @(5.00,5.02,4.98)
    $Manifest = foreach ($Index in 1..3) {
        [pscustomobject][ordered]@{
            sample_id='SYN_BASE';cell_id='CELL_SYN';replicate_id="R$Index";operator='SYNTHETIC_OPERATOR';measurement_datetime="2026-01-0${Index}T00:00:00Z";instrument='SYNTHETIC_EIS';instrument_software='SYNTHETIC_SOFTWARE_1';raw_file_path=$Evidence["raw$Index"].relative;raw_file_size_bytes=$Evidence["raw$Index"].size;raw_file_sha256=$Evidence["raw$Index"].hash;normalized_table_path=$Evidence["norm$Index"].relative;normalized_table_size_bytes=$Evidence["norm$Index"].size;normalized_table_sha256=$Evidence["norm$Index"].hash;frequency_unit='Hz';impedance_unit='ohm';frequency_min_Hz='1000';frequency_max_Hz='100000';points_per_decade='3';perturbation_amplitude_V='0.01';dc_condition='OCP';dc_bias_V='';temperature_K='298.15';electrolyte_composition='SYNTHETIC_ELECTROLYTE';electrolyte_batch='SYN_BATCH_1';salt_concentration_mol_L='1';water_content='12';water_content_unit='ppm';water_content_method='SYNTHETIC_METHOD';gas_atmosphere='SYNTHETIC_N2';pressure_Pa='101325';flow_rate_m3_s='0';stabilization_time_s='600';eis_area_basis='EIS_area';eis_area_m2='0.001';source_notebook_reference="SYNTHETIC_FIXTURE_PAGE_$Index";hfr_method='HIGH_FREQUENCY_INTERCEPT_REVIEWED';hfr_ohm=Format-M03A5Number $Hfr[$Index-1];hfr_uncertainty_ohm='0.01';hfr_status='ACCEPTED_SYNTHETIC';hfr_source_reference="SYNTHETIC_HFR_$Index";conductivity_transfer_selection='HFR_DERIVED';conductivity_transfer_authorization_reference='SYNTHETIC_REGRESSION_ONLY';data_origin='SYNTHETIC';status='MEASURED';notes='not experimental evidence'
        }
    }
    $Resistance = foreach ($Name in @('fixture','contact','membrane','other_series')) {
        [pscustomobject][ordered]@{sample_id='SYN_BASE';component_name=$Name;value_ohm='0.1';uncertainty_ohm='0.005';unit='ohm';method='SYNTHETIC_FIXED';replicate_count='3';temperature_K='298.15';source_reference="SYNTHETIC_$Name";raw_evidence_path=$Evidence.raw1.relative;raw_evidence_size_bytes=$Evidence.raw1.size;raw_evidence_sha256=$Evidence.raw1.hash;data_origin='SYNTHETIC';status='MEASURED';notes='not experimental'}
    }
    $AreaValues = [ordered]@{electrode_spacing=@('0.004','0.00002','m');out_of_plane_depth=@('0.05','0.0001','m');geometric_electrode_area=@('0.0012','0.00001','m^2');EIS_area=@('0.001','0.00001','m^2');current_density_reporting_area=@('0.0009','0.00001','m^2')}
    $Areas = foreach ($Name in $AreaValues.Keys) {
        [pscustomobject][ordered]@{sample_id='SYN_BASE';quantity_name=$Name;value_SI=$AreaValues[$Name][0];uncertainty_SI=$AreaValues[$Name][1];unit=$AreaValues[$Name][2];measurement_method='SYNTHETIC_FIXED';source_reference="SYNTHETIC_$Name";raw_evidence_path=$Evidence.raw1.relative;raw_evidence_size_bytes=$Evidence.raw1.size;raw_evidence_sha256=$Evidence.raw1.hash;data_origin='SYNTHETIC';status='MEASURED';definition="synthetic definition for $Name";notes='not experimental'}
    }
    $Direct = @([pscustomobject][ordered]@{sample_id='SYN_BASE';conductivity_S_m='0.8695652173913043';uncertainty_S_m='0.01';unit='S/m';temperature_K='298.15';method='SYNTHETIC_FIXED';cell_constant='1';cell_constant_unit='1/m';calibration_standard='SYNTHETIC_STANDARD';instrument='SYNTHETIC_CONDUCTIVITY';raw_evidence_path=$Evidence.raw1.relative;raw_evidence_size_bytes=$Evidence.raw1.size;raw_evidence_sha256=$Evidence.raw1.hash;source_reference='SYNTHETIC_DIRECT';data_origin='SYNTHETIC';status='MEASURED';notes='not experimental'})
    return @{Manifest=@($Manifest);Resistance=@($Resistance);Areas=@($Areas);Direct=@($Direct)}
}

function Write-Package([string]$Root,[hashtable]$Package) {
    $Paths = @{Manifest=(Join-Path $Root 'manifest.csv');Resistance=(Join-Path $Root 'resistance.csv');Area=(Join-Path $Root 'area.csv');Direct=(Join-Path $Root 'direct.csv');Threshold=(Join-Path $Root 'thresholds.csv')}
    Write-M03A5Utf8NoBomCsv $Package.Manifest $Paths.Manifest
    Write-M03A5Utf8NoBomCsv $Package.Resistance $Paths.Resistance
    Write-M03A5Utf8NoBomCsv $Package.Areas $Paths.Area
    Write-M03A5Utf8NoBomCsv $Package.Direct $Paths.Direct
    $ThresholdRows = @(Import-Csv -LiteralPath (Join-Path $ProjectRoot 'config\M03A_5_acceptance_thresholds.csv') -Encoding UTF8)
    Write-M03A5Utf8NoBomCsv $ThresholdRows $Paths.Threshold
    return $Paths
}

function Invoke-Package([string]$Root,[hashtable]$Package) {
    $Paths=Write-Package $Root $Package
    return Invoke-M03A5IntakeAudit -ProjectRoot $Root -ManifestPath $Paths.Manifest -ResistancePath $Paths.Resistance -AreaPath $Paths.Area -DirectPath $Paths.Direct -ThresholdPath $Paths.Threshold -ExpectedDataOrigin SYNTHETIC
}

$TempRoot = Join-Path ([IO.Path]::GetTempPath()) ('LiNRR_M03A5_tests_' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $TempRoot | Out-Null
try {
    $BaseRoot=Join-Path $TempRoot 'base';New-Item -ItemType Directory -Path $BaseRoot|Out-Null
    $Base=New-SyntheticPackage $BaseRoot
    try {
        $Audit=Invoke-Package $BaseRoot $Base
        if($Audit.BlockedFindingCount -ne 0){throw "base synthetic contract has $($Audit.BlockedFindingCount) blocked findings"}
        if($Audit.ExperimentalInputComplete -or $Audit.M03BCandidate -or $Audit.M03BReady){throw 'synthetic package promoted through experimental/M03B gate'}
        Add-Result 'C000' 'complete synthetic structure remains non-experimental' 'PASS' 'PASS' 'PASS' ''
    } catch { Add-Result 'C000' 'complete synthetic structure remains non-experimental' 'PASS' 'UNEXPECTED' 'FAIL' $_.Exception.Message }

    $Catalog=@(Import-Csv -LiteralPath (Join-Path $FixtureRoot 'negative_cases.csv') -Encoding UTF8)
    foreach($Case in $Catalog){
        $CaseRoot=Join-Path $TempRoot $Case.case_id;New-Item -ItemType Directory -Path $CaseRoot|Out-Null
        $Package=New-SyntheticPackage $CaseRoot
        try {
            $SkipAudit=$false
            switch([string]$Case.mutation){
                'MISSING_RAW_FILE' {$Package.Manifest[0].raw_file_path='evidence/not_present.raw'}
                'RAW_HASH_MISMATCH' {$Package.Manifest[0].raw_file_sha256=('b'*64)}
                'DUPLICATE_REPLICATE_ID' {$Package.Manifest[1].replicate_id='R1'}
                'INSUFFICIENT_REPLICATES' {$Package.Manifest=@($Package.Manifest|Select-Object -First 2)}
                'INCOMPATIBLE_TEMPERATURES' {$Package.Manifest[2].temperature_K='300.15'}
                'INCOMPATIBLE_ELECTROLYTE_BATCHES' {$Package.Manifest[2].electrolyte_batch='SYN_BATCH_2'}
                'MISSING_WATER_CONTENT' {$Package.Manifest[0].water_content=''}
                'INVALID_FREQUENCY_UNIT' {$Package.Manifest[0].frequency_unit='rpm'}
                'INVALID_IMPEDANCE_UNIT' {$Package.Manifest[0].impedance_unit='volt'}
                'MISSING_EIS_AREA' {$Package.Manifest[0].eis_area_m2=''}
                'DUPLICATE_FIXTURE_RESISTANCE' {$Package.Resistance+=@($Package.Resistance|Where-Object component_name -eq 'fixture'|Select-Object *)}
                'MISSING_MEMBRANE_RESISTANCE' {$Package.Resistance=@($Package.Resistance|Where-Object component_name -ne 'membrane')}
                'NONPOSITIVE_DEEMBEDDED_RESISTANCE' {(@($Package.Resistance|Where-Object component_name -eq 'fixture')[0]).value_ohm='5'}
                'AREA_BASIS_MISMATCH' {foreach($R in $Package.Manifest){$R.eis_area_basis='geometric_electrode_area'}}
                'MISSING_DIRECT_PROVENANCE' {$Package.Direct[0].raw_evidence_sha256=''}
                'DIRECT_HFR_CONFLICT' {$Package.Direct[0].conductivity_S_m='2'}
                'NAN_NUMERIC' {(@($Package.Areas|Where-Object quantity_name -eq 'EIS_area')[0]).value_SI='NaN'}
                'INFINITY_NUMERIC' {(@($Package.Areas|Where-Object quantity_name -eq 'EIS_area')[0]).value_SI='Infinity'}
                'BLANK_NUMERIC' {(@($Package.Resistance|Where-Object component_name -eq 'contact')[0]).value_ohm=''}
                'NONPOSITIVE_SPACING' {(@($Package.Areas|Where-Object quantity_name -eq 'electrode_spacing')[0]).value_SI='0'}
                'NONPOSITIVE_AREA' {(@($Package.Areas|Where-Object quantity_name -eq 'EIS_area')[0]).value_SI='0'}
                'MC_REJECTION_ABOVE_LIMIT' {
                    $Requested=[int](ConvertTo-M03A5PositiveInt32Exact $ThresholdDefaults.monte_carlo_minimum_samples 'monte_carlo_minimum_samples')
                    $Limit=ConvertTo-M03A5Double $ThresholdDefaults.monte_carlo_nonphysical_rejection_limit 'monte_carlo_nonphysical_rejection_limit'
                    $MC=Invoke-M03A5MonteCarlo -HfrOhm 1 -HfrUOhm 10 -SeriesOhm @(0,0,0,0) -SeriesUOhm @(0,0,0,0) -SpacingM 1 -SpacingUM 0 -AreaM2 1 -AreaUM2 0 -SampleCount $Requested -Seed 50305 -RejectionLimit $Limit
                    if($MC.status -ne $Case.expected_status -or $MC.repair_count -ne 0 -or $MC.resample_count -ne 0){throw 'Monte Carlo rejection was repaired resampled or misclassified'}
                    if($MC.sample_count -ne $Requested -or ($MC.accepted_count+$MC.rejected_count) -ne $Requested -or [double]$MC.rejected_fraction -le $Limit){throw 'Monte Carlo requested denominator or configured rejection gate was not retained'}
                    Add-Result $Case.case_id $Case.case_name $Case.expected_status $MC.status 'PASS' ''
                    $SkipAudit=$true
                }
                'SYNTHETIC_MISLABELED_EXPERIMENTAL' {$Package.Manifest[0].data_origin='EXPERIMENTAL'}
                'EXPERIMENTAL_WITHOUT_SOURCE_REFERENCE' {$Package.Manifest[0].data_origin='EXPERIMENTAL';$Package.Manifest[0].source_notebook_reference=''}
                'DUPLICATE_FREQUENCY_RETAINED' {
                    $NormPath=Join-Path $CaseRoot $Package.Manifest[0].normalized_table_path
                    $Norm=@(Import-Csv $NormPath);$Norm[0].frequency=$Norm[1].frequency;Write-M03A5Utf8NoBomCsv $Norm $NormPath
                    $Package.Manifest[0].normalized_table_size_bytes=(Get-Item $NormPath).Length;$Package.Manifest[0].normalized_table_sha256=Get-M03A5Sha256 $NormPath
                }
                'INDUCTIVE_HIGH_FREQUENCY_RETAINED' {
                    $NormPath=Join-Path $CaseRoot $Package.Manifest[0].normalized_table_path
                    $Norm=@(Import-Csv $NormPath);$Norm[0].z_imag='0.02';Write-M03A5Utf8NoBomCsv $Norm $NormPath
                    $Package.Manifest[0].normalized_table_size_bytes=(Get-Item $NormPath).Length;$Package.Manifest[0].normalized_table_sha256=Get-M03A5Sha256 $NormPath
                }
                'INSUFFICIENT_HIGH_FREQUENCY_INFORMATION' {
                    $NormPath=Join-Path $CaseRoot $Package.Manifest[0].normalized_table_path
                    $Norm=@(Import-Csv $NormPath|Select-Object -First 3);Write-M03A5Utf8NoBomCsv $Norm $NormPath
                    $Package.Manifest[0].normalized_table_size_bytes=(Get-Item $NormPath).Length;$Package.Manifest[0].normalized_table_sha256=Get-M03A5Sha256 $NormPath
                }
                'HFR_STATUS_REJECTED' {$Package.Manifest[0].hfr_status='REJECTED'}
                'HFR_STATUS_WARNING' {$Package.Manifest[0].hfr_status='WARNING'}
                'HFR_STATUS_UNKNOWN' {$Package.Manifest[0].hfr_status='SOMETHING_ELSE'}
                'HFR_METHOD_FIRST_POINT' {$Package.Manifest[0].hfr_method='FIRST_POINT'}
                'HFR_METHOD_UNKNOWN' {$Package.Manifest[0].hfr_method='UNKNOWN_METHOD'}
                'FREQUENCY_MIN_MISMATCH' {$Package.Manifest[0].frequency_min_Hz='900'}
                'FREQUENCY_MAX_MISMATCH' {$Package.Manifest[0].frequency_max_Hz='90000'}
                'POINTS_PER_DECADE_NONINTEGER' {$Package.Manifest[0].points_per_decade='3.5'}
                'POINTS_PER_DECADE_TOO_HIGH' {$Package.Manifest[0].points_per_decade='4'}
                'SOFTWARE_MISMATCH' {$Package.Manifest[2].instrument_software='SYNTHETIC_SOFTWARE_2'}
                'SALT_CONCENTRATION_MISMATCH' {$Package.Manifest[2].salt_concentration_mol_L='1.1'}
                'PRESSURE_MISMATCH' {$Package.Manifest[2].pressure_Pa='110000'}
                'FLOW_MISMATCH' {$Package.Manifest[2].flow_rate_m3_s='1e-7'}
                'STABILIZATION_MISMATCH' {$Package.Manifest[2].stabilization_time_s='601'}
                'PERTURBATION_MISMATCH' {$Package.Manifest[2].perturbation_amplitude_V='0.02'}
                'DC_CONDITION_MISMATCH' {$Package.Manifest[2].dc_condition='BIAS';$Package.Manifest[2].dc_bias_V='0.1'}
                'NONOCP_MISSING_BIAS' {foreach($R in $Package.Manifest){$R.dc_condition='BIAS';$R.dc_bias_V=''} }
                'NONOCP_BIAS_MISMATCH' {foreach($R in $Package.Manifest){$R.dc_condition='BIAS';$R.dc_bias_V='0.1'};$Package.Manifest[2].dc_bias_V='0.2'}
                'HFR_DISAGREEMENT' {$Package.Manifest[2].hfr_ohm='6'}
                'DIRECT_MISSING' {$Package.Direct[0].status='MISSING'}
                'DIRECT_DUPLICATE' {$Package.Direct+=@($Package.Direct[0]|Select-Object *)}
                'DIRECT_TEMPERATURE_MISMATCH' {$Package.Direct[0].temperature_K='300'}
                'DIRECT_CELL_CONSTANT_UNIT_INVALID' {$Package.Direct[0].cell_constant_unit='cm^-1'}
                'DIRECT_CONDUCTIVITY_NONPOSITIVE' {$Package.Direct[0].conductivity_S_m='0'}
                'RAW_NORMALIZED_PATH_COLLISION' {$Package.Manifest[0].normalized_table_path=$Package.Manifest[0].raw_file_path;$Package.Manifest[0].normalized_table_size_bytes=$Package.Manifest[0].raw_file_size_bytes;$Package.Manifest[0].normalized_table_sha256=$Package.Manifest[0].raw_file_sha256}
                'DATETIME_WITHOUT_TIMEZONE' {$Package.Manifest[0].measurement_datetime='2026-01-01T00:00:00'}
                'RESISTANCE_REPLICATE_COUNT_ZERO' {(@($Package.Resistance|Where-Object component_name -eq 'fixture')[0]).replicate_count='0'}
                'EVIDENCE_SIZE_DECIMAL' {$Package.Manifest[0].raw_file_size_bytes='77.0'}
                'ORPHAN_RESISTANCE_SAMPLE' {$Orphan=$Package.Resistance[0]|Select-Object *;$Orphan.sample_id='SYN_ORPHAN';$Package.Resistance+=@($Orphan)}
                'ORPHAN_AREA_SAMPLE' {$Orphan=$Package.Areas[0]|Select-Object *;$Orphan.sample_id='SYN_ORPHAN';$Package.Areas+=@($Orphan)}
                'ORPHAN_DIRECT_SAMPLE' {$Orphan=$Package.Direct[0]|Select-Object *;$Orphan.sample_id='SYN_ORPHAN';$Package.Direct+=@($Orphan)}
                'FREQUENCY_RANGE_REVERSED' {$Package.Manifest[0].frequency_min_Hz='200000';$Package.Manifest[0].frequency_max_Hz='100000'}
                default {throw "unknown fixture mutation: $($Case.mutation)"}
            }
            if($SkipAudit){continue}
            $Audit=Invoke-Package $CaseRoot $Package
            $Codes=@($Audit.Findings|Select-Object -ExpandProperty code)
            if($Codes -notcontains [string]$Case.expected_status){throw "expected $($Case.expected_status), observed codes: $($Codes -join ';')"}
            if($Audit.ExperimentalInputComplete -or $Audit.M03BCandidate -or $Audit.M03BReady){throw 'negative fixture crossed an experimental or M03B gate'}
            Add-Result $Case.case_id $Case.case_name $Case.expected_status $Case.expected_status 'PASS' ''
        } catch { Add-Result $Case.case_id $Case.case_name $Case.expected_status 'UNEXPECTED' 'FAIL' $_.Exception.Message }
    }

    try {
        $Quoted=Join-Path $TempRoot 'quoted.csv';Write-M03A5Utf8NoBomText $Quoted "a,b`r`n1,`"quoted, value`"`r`n";Assert-M03A5Rfc4180Utf8NoBom $Quoted|Out-Null
        $Q=@(Import-Csv $Quoted);if($Q[0].b -ne 'quoted, value'){throw 'quoted comma was not retained'}
        Add-Result 'C025' 'RFC 4180 quoted comma retained' 'PASS' 'PASS' 'PASS' ''
    } catch { Add-Result 'C025' 'RFC 4180 quoted comma retained' 'PASS' 'UNEXPECTED' 'FAIL' $_.Exception.Message }
    try {
        $Bom=Join-Path $TempRoot 'bom.csv';[IO.File]::WriteAllText($Bom,"a,b`r`n1,2`r`n",[Text.UTF8Encoding]::new($true));$Caught=$false;try{Assert-M03A5Rfc4180Utf8NoBom $Bom|Out-Null}catch{$Caught=$true};if(-not $Caught){throw 'UTF-8 BOM accepted'}
        Add-Result 'C026' 'UTF-8 BOM blocked' 'PASS' 'PASS' 'PASS' ''
    } catch { Add-Result 'C026' 'UTF-8 BOM blocked' 'PASS' 'UNEXPECTED' 'FAIL' $_.Exception.Message }
    try {
        $A=ConvertTo-M03A5Int64Exact '9007199254740992' 'exact-A' -Nonnegative;$B=ConvertTo-M03A5Int64Exact '9007199254740993' 'exact-B' -Nonnegative
        if($A -eq $B -or $A -ne 9007199254740992L -or $B -ne 9007199254740993L){throw 'exact Int64 values collapsed or changed'}
        foreach($Bad in @('1.0','1e3','NaN','Infinity','9223372036854775808','-1')){$Caught=$false;try{[void](ConvertTo-M03A5Int64Exact $Bad 'invalid-size' -Nonnegative)}catch{$Caught=$true};if(-not $Caught){throw "invalid Int64 size accepted: $Bad"}}
        Add-Result 'C062' 'exact Int64 provenance distinguishes adjacent values above 2^53' 'PASS' 'PASS' 'PASS' ''
    } catch { Add-Result 'C062' 'exact Int64 provenance distinguishes adjacent values above 2^53' 'PASS' 'UNEXPECTED' 'FAIL' $_.Exception.Message }
    try {
        $Root=Join-Path $TempRoot 'between_component';New-Item -ItemType Directory -Path $Root|Out-Null;$Package=New-SyntheticPackage $Root;$Audit=Invoke-Package $Root $Package
        $Finding=@($Audit.Findings|Where-Object code -eq 'HFR_CONSENSUS_ELIGIBLE')[0];if($null -eq $Finding -or $Finding.detail -notmatch 'u_between=([^;]+)'){throw 'between-replicate component was not reported'}
        if((ConvertTo-M03A5Double $Matches[1].Trim() 'u_between') -le 0){throw 'between-replicate dispersion did not enter HFR mean uncertainty'}
        Add-Result 'C063' 'between-replicate standard error contributes to HFR uncertainty' 'HFR_CONSENSUS_ELIGIBLE' 'HFR_CONSENSUS_ELIGIBLE' 'PASS' ''
    } catch { Add-Result 'C063' 'between-replicate standard error contributes to HFR uncertainty' 'HFR_CONSENSUS_ELIGIBLE' 'UNEXPECTED' 'FAIL' $_.Exception.Message }
    try {
        $MC=Test-M03A5MonteCarloRejection -RejectedCount 201 -SampleCount 20000 -Limit 0.01
        if($MC.status -ne 'FAILED_INPUT_DISTRIBUTION' -or $MC.repair_count -ne 0 -or $MC.resample_count -ne 0){throw 'accounting helper altered the original denominator'}
        Add-Result 'C064' 'Monte Carlo rejection accounting helper retains denominator' 'FAILED_INPUT_DISTRIBUTION' 'FAILED_INPUT_DISTRIBUTION' 'PASS' ''
    } catch { Add-Result 'C064' 'Monte Carlo rejection accounting helper retains denominator' 'FAILED_INPUT_DISTRIBUTION' 'UNEXPECTED' 'FAIL' $_.Exception.Message }
    try {
        $Root=Join-Path $TempRoot 'reparse_point';New-Item -ItemType Directory -Path $Root|Out-Null;$Package=New-SyntheticPackage $Root
        $Link=Join-Path $Root 'evidence_link';New-Item -ItemType Junction -Path $Link -Target (Join-Path $Root 'evidence')|Out-Null
        $Package.Manifest[0].raw_file_path='evidence_link/base_raw_rep1.txt';$Audit=Invoke-Package $Root $Package
        if(@($Audit.Findings.code) -notcontains 'BLOCKED_EVIDENCE_REPARSE_POINT'){throw 'repository evidence reparse point was accepted'}
        Add-Result 'C065' 'evidence junction or symlink path blocked' 'BLOCKED_EVIDENCE_REPARSE_POINT' 'BLOCKED_EVIDENCE_REPARSE_POINT' 'PASS' ''
    } catch { Add-Result 'C065' 'evidence junction or symlink path blocked' 'BLOCKED_EVIDENCE_REPARSE_POINT' 'UNEXPECTED' 'FAIL' $_.Exception.Message }
    try {
        $Root=Join-Path $TempRoot 'diagnostic_allowlist';New-Item -ItemType Directory -Path $Root|Out-Null;$Package=New-SyntheticPackage $Root
        $Package.Manifest[0].hfr_status='ACCEPTED_SYNTHETIC_WITH_RETAINED_DIAGNOSTIC';$Package.Manifest[1].hfr_method='EQUIVALENT_CIRCUIT_FIT_REVIEWED';$Audit=Invoke-Package $Root $Package
        if($Audit.BlockedFindingCount -ne 0){throw 'documented synthetic accepted status or reviewed method was rejected'}
        Add-Result 'C066' 'documented synthetic HFR status and method allowlists accepted' 'PASS' 'PASS' 'PASS' ''
    } catch { Add-Result 'C066' 'documented synthetic HFR status and method allowlists accepted' 'PASS' 'UNEXPECTED' 'FAIL' $_.Exception.Message }
    try {
        $Root=Join-Path $TempRoot 'frequency_closure';New-Item -ItemType Directory -Path $Root|Out-Null;$Package=New-SyntheticPackage $Root;$Audit=Invoke-Package $Root $Package;$N=$Audit.NormalizedAudits['SYN_BASE/R1']
        if($N.actual_row_count -ne 8 -or [double]$N.actual_frequency_min_Hz -ne 1000 -or [double]$N.actual_frequency_max_Hz -ne 100000 -or [Math]::Abs([double]$N.actual_average_points_per_decade-3.5) -gt 1e-12 -or -not $N.metadata_match -or -not $N.hfr_eligible){throw 'normalized frequency metadata closure fields are incorrect'}
        Add-Result 'C067' 'normalized EIS structured frequency metadata closure' 'PASS' 'PASS' 'PASS' ''
    } catch { Add-Result 'C067' 'normalized EIS structured frequency metadata closure' 'PASS' 'UNEXPECTED' 'FAIL' $_.Exception.Message }
    try {
        if([int]$ThresholdDefaults.minimum_accepted_eis_replicates -ne 3 -or [double]$ThresholdDefaults.direct_vs_hfr_relative_difference_limit -ne 0.10 -or [int]$ThresholdDefaults.monte_carlo_minimum_samples -lt 20000 -or [double]$ThresholdDefaults.monte_carlo_nonphysical_rejection_limit -ne 0.01){throw 'central default thresholds violate the phase contract'}
        Add-Result 'C068' 'central threshold defaults satisfy phase contract' 'PASS' 'PASS' 'PASS' ''
    } catch { Add-Result 'C068' 'central threshold defaults satisfy phase contract' 'PASS' 'UNEXPECTED' 'FAIL' $_.Exception.Message }
} finally {
    $Resolved=[IO.Path]::GetFullPath($TempRoot);$TempBase=[IO.Path]::GetFullPath([IO.Path]::GetTempPath())
    if($Resolved.StartsWith($TempBase,[StringComparison]::OrdinalIgnoreCase) -and (Test-Path -LiteralPath $Resolved)){Remove-Item -LiteralPath $Resolved -Recurse -Force}
}

if($OutputPath){Write-M03A5Utf8NoBomCsv @($Results | ForEach-Object { $_ }) $OutputPath}
foreach($R in $Results){"M03A5_REGRESSION|$($R.case_id)|$($R.case_name)|$($R.expected_status)|$($R.actual_status)|$($R.status)|$($R.failure_reason.Replace('|','/'))"}
$Failed=@($Results|Where-Object status -ne 'PASS')
"M03A5_REGRESSION_COUNT=$($Results.Count)"
"M03A5_REGRESSION_PASS_COUNT=$(@($Results|Where-Object status -eq 'PASS').Count)"
"M03A5_REGRESSION_FAIL_COUNT=$($Failed.Count)"
if($Failed.Count){throw "$($Failed.Count) M03A.5 regressions failed"}
"M03A5_TESTS|PASS|$($Results.Count) synthetic contract cases retained"
