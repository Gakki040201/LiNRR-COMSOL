Set-StrictMode -Version 2.0
$ErrorActionPreference = 'Stop'

$M03A2Module = Join-Path $PSScriptRoot 'LiNRR_M03A2_EIS.psm1'
if (-not (Test-Path -LiteralPath $M03A2Module -PathType Leaf)) { throw "Frozen M03A.2 module missing: $M03A2Module" }
Import-Module $M03A2Module -Force -Scope Local

function Test-M03A4Finite {
    param([double]$Value)
    return (-not [double]::IsNaN($Value)) -and (-not [double]::IsInfinity($Value))
}

function ConvertTo-M03A4Double {
    param([object]$Value,[string]$Name)
    if ($null -eq $Value -or [string]::IsNullOrWhiteSpace([string]$Value)) { throw "$Name is blank" }
    $Number = 0.0
    if (-not [double]::TryParse([string]$Value,[Globalization.NumberStyles]::Float,[Globalization.CultureInfo]::InvariantCulture,[ref]$Number)) { throw "$Name is not numeric" }
    if (-not (Test-M03A4Finite $Number)) { throw "$Name is nonfinite" }
    return $Number
}

function Format-M03A4Number {
    param([double]$Value)
    if ([double]::IsNaN($Value)) { return 'NaN' }
    if ([double]::IsPositiveInfinity($Value)) { return 'Infinity' }
    if ([double]::IsNegativeInfinity($Value)) { return '-Infinity' }
    return $Value.ToString('G17',[Globalization.CultureInfo]::InvariantCulture)
}

function Write-M03A4Utf8NoBomText {
    param([string]$Path,[string]$Text)
    [IO.File]::WriteAllText($Path,$Text,(New-Object Text.UTF8Encoding($false)))
}

function Write-M03A4Utf8NoBomCsv {
    param([object[]]$Rows,[string]$Path)
    if (@($Rows).Count -eq 0) { throw "Refusing to write empty CSV: $Path" }
    $Text = (@($Rows) | ConvertTo-Csv -NoTypeInformation) -join "`r`n"
    Write-M03A4Utf8NoBomText $Path ($Text + "`r`n")
}

function Get-M03A4Sha256 {
    param([string]$Path)
    return (Get-FileHash -Algorithm SHA256 -LiteralPath $Path).Hash.ToLowerInvariant()
}

function Import-M03A4Manifest {
    param([string]$Path,[string]$ProjectRoot)
    $Required = @('sample_id','replicate_id','file_path','data_origin','frequency_unit','impedance_unit','temperature_K','electrolyte_composition','water_content','instrument','perturbation_amplitude_V','dc_bias_V','measurement_date','area_basis','EIS_area_m2','intercept_method','equivalent_circuit','fitting_software','fit_file','source_reference','status','notes')
    $Rows = @(Import-Csv -LiteralPath $Path -Encoding UTF8)
    if ($Rows.Count -eq 0) { throw 'M03A.4 manifest is empty' }
    foreach ($Name in $Required) { if (-not ($Rows[0].PSObject.Properties.Name -contains $Name)) { throw "Manifest missing column: $Name" } }
    $Keys = @{}
    $Out = New-Object Collections.Generic.List[object]
    foreach ($Row in $Rows) {
        $Key = "$($Row.sample_id)|$($Row.replicate_id)"
        if ($Keys.ContainsKey($Key)) { throw "Duplicate manifest replicate: $Key" }
        $Keys[$Key] = $true
        if (@('EXPERIMENTAL','SYNTHETIC','LITERATURE','UNKNOWN') -notcontains [string]$Row.data_origin) { throw "Illegal data_origin: $($Row.data_origin)" }
        $Resolved = if ([string]::IsNullOrWhiteSpace([string]$Row.file_path)) { '' } else { Join-Path $ProjectRoot ([string]$Row.file_path) }
        $Missing = New-Object Collections.Generic.List[string]
        foreach ($Name in @('frequency_unit','impedance_unit','temperature_K','electrolyte_composition','water_content','instrument','measurement_date','area_basis','EIS_area_m2','intercept_method','source_reference')) {
            if ([string]::IsNullOrWhiteSpace([string]$Row.$Name)) { $Missing.Add($Name) }
        }
        if ([string]::IsNullOrWhiteSpace([string]$Row.file_path)) { $Missing.Add('file_path') }
        elseif (-not (Test-Path -LiteralPath $Resolved -PathType Leaf)) { $Missing.Add('existing_file') }
        $Status = if ($Missing.Count -gt 0 -and $Row.data_origin -eq 'EXPERIMENTAL') { 'EXPERIMENTAL_INPUT_INCOMPLETE' } elseif ($Missing.Count -gt 0) { 'BLOCKED_MANIFEST_INCOMPLETE' } else { 'PASS' }
        $Out.Add([pscustomobject][ordered]@{sample_id=$Row.sample_id;replicate_id=$Row.replicate_id;file_path=$Row.file_path;resolved_file_path=$Resolved;data_origin=$Row.data_origin;frequency_unit=$Row.frequency_unit;impedance_unit=$Row.impedance_unit;temperature_K=$Row.temperature_K;electrolyte_composition=$Row.electrolyte_composition;water_content=$Row.water_content;instrument=$Row.instrument;perturbation_amplitude_V=$Row.perturbation_amplitude_V;dc_bias_V=$Row.dc_bias_V;measurement_date=$Row.measurement_date;area_basis=$Row.area_basis;EIS_area_m2=$Row.EIS_area_m2;intercept_method=$Row.intercept_method;equivalent_circuit=$Row.equivalent_circuit;fitting_software=$Row.fitting_software;fit_file=$Row.fit_file;source_reference=$Row.source_reference;status=$Status;failure_reason=($Missing -join ';');notes=$Row.notes})
    }
    return @($Out | ForEach-Object { $_ })
}

function Import-M03A4Eis {
    param([string]$Path,[string]$CaseId,[string]$FrequencyUnit='Hz',[string]$ImpedanceUnit='ohm',[string]$SampleId='',[string]$ReplicateId='')
    return Import-M03A2Eis -Path $Path -CaseId $CaseId -FrequencyUnit $FrequencyUnit -ImpedanceUnit $ImpedanceUnit -SampleId $SampleId -ReplicateId $ReplicateId -CaseType 'M03A4_INTAKE'
}

function Get-M03A4HfrEstimate {
    param([object]$ImportResult,[ValidateSet('FIRST_POINT_DIAGNOSTIC','HIGH_FREQUENCY_INTERCEPT','USER_DEFINED_EQUIVALENT_CIRCUIT')][string]$Method='HIGH_FREQUENCY_INTERCEPT',[object]$EquivalentCircuitContract)
    return Get-M03A2HfrEstimate -ImportResult $ImportResult -Method $Method -EquivalentCircuitContract $EquivalentCircuitContract
}

function Get-M03A4ReplicateConsensus {
    param([object[]]$HfrRows,[double]$DisagreementLimit=0.10)
    $Input = foreach ($Row in $HfrRows) { [pscustomobject]@{sample_id=$Row.SampleId;method=$Row.Method;hfr_ohm=$Row.EstimateOhm} }
    return @(Get-M03A2ReplicateConsensus -ReplicateRows $Input -DisagreementLimit $DisagreementLimit)
}

function Get-M03A4ResistanceLedger {
    param([string]$SampleId,[object[]]$Rows,[object]$AcceptedConsensus)
    $Required = @('EIS_HFR_total','fixture','contact','membrane','other_series')
    $Failure = New-Object Collections.Generic.List[string]
    $Values = @{}; $Uncertainties = @{}; $Sources = @{}; $Statuses = @{}
    $Sample = @($Rows | Where-Object sample_id -eq $SampleId)
    foreach ($Name in $Required) {
        $Matches = @($Sample | Where-Object component_name -ceq $Name)
        if ($Matches.Count -ne 1) { $Failure.Add("missing_or_duplicate:$Name"); continue }
        $Row = $Matches[0]; $Status = [string]$Row.status; $Source = [string]$Row.source_reference
        $Statuses[$Name] = $Status
        if ($Status -eq 'NOT_APPLICABLE_WITH_EVIDENCE') {
            if ([string]::IsNullOrWhiteSpace($Source)) { $Failure.Add("not_applicable_without_evidence:$Name") }
            else { $Values[$Name]=0.0; $Uncertainties[$Name]=0.0; $Sources[$Name]=$Source }
            continue
        }
        $Allowed = if ($Name -eq 'EIS_HFR_total') { @('MEASURED','SYNTHETIC_CONSENSUS_ACCEPTED') } else { @('MEASURED','SYNTHETIC_DEFINED') }
        if ($Allowed -notcontains $Status) { $Failure.Add("blocked_status:${Name}:$Status"); continue }
        if ([string]$Row.source_type -eq 'SYNTHETIC' -and $Status -eq 'MEASURED') { $Failure.Add("synthetic_mislabeled_measured:$Name"); continue }
        try { $V=ConvertTo-M03A4Double $Row.value_ohm "$Name value"; $U=ConvertTo-M03A4Double $Row.uncertainty_ohm "$Name uncertainty" } catch { $Failure.Add("invalid:$Name"); continue }
        if ($V -lt 0 -or $U -lt 0 -or [string]::IsNullOrWhiteSpace($Source)) { $Failure.Add("invalid_or_unproven:$Name"); continue }
        $Values[$Name]=$V; $Uncertainties[$Name]=$U; $Sources[$Name]=$Source
    }
    if ($null -ne $AcceptedConsensus -and $Values.ContainsKey('EIS_HFR_total')) {
        if ([string]$AcceptedConsensus.consensus_status -ne 'PASS') { $Failure.Add('replicate_consensus_not_accepted') }
        else {
            $ConsensusValue = ConvertTo-M03A4Double $AcceptedConsensus.mean_HFR_ohm 'consensus HFR'
            $ConsensusSd = ConvertTo-M03A4Double $AcceptedConsensus.standard_deviation_ohm 'consensus standard deviation'
            $ConsensusN = [int](ConvertTo-M03A4Double $AcceptedConsensus.valid_replicate_count 'consensus replicate count')
            $ConsensusU = $ConsensusSd / [Math]::Sqrt($ConsensusN)
            if ([Math]::Abs($Values.EIS_HFR_total-$ConsensusValue) -gt 1e-12*[Math]::Max(1.0,[Math]::Abs($ConsensusValue))) { $Failure.Add('ledger_HFR_disagrees_with_accepted_consensus') }
            if ([Math]::Abs($Uncertainties.EIS_HFR_total-$ConsensusU) -gt 1e-12*[Math]::Max(1.0,[Math]::Abs($ConsensusU))) { $Failure.Add('ledger_HFR_uncertainty_disagrees_with_consensus_SEM') }
        }
    }
    $Total=if($Values.ContainsKey('EIS_HFR_total')){$Values.EIS_HFR_total}else{[double]::NaN}
    $TotalU=if($Uncertainties.ContainsKey('EIS_HFR_total')){$Uncertainties.EIS_HFR_total}else{[double]::NaN}
    $Non=if($Failure.Count -eq 0){$Values.fixture+$Values.contact+$Values.membrane+$Values.other_series}else{[double]::NaN}
    $NonU=if($Failure.Count -eq 0){[Math]::Sqrt($Uncertainties.fixture*$Uncertainties.fixture+$Uncertainties.contact*$Uncertainties.contact+$Uncertainties.membrane*$Uncertainties.membrane+$Uncertainties.other_series*$Uncertainties.other_series)}else{[double]::NaN}
    $Electrolyte=$Total-$Non; $ElectrolyteU=[Math]::Sqrt($TotalU*$TotalU+$NonU*$NonU)
    $Status=if($Failure.Count){'BLOCKED_SERIES_RESISTANCE_LEDGER_INCOMPLETE'}elseif($Electrolyte-lt0){'BLOCKED_NEGATIVE_DEEMBEDDED_RESISTANCE'}elseif($Electrolyte-le0){'BLOCKED_NONPOSITIVE_DEEMBEDDED_RESISTANCE'}else{'PASS'}
    if($Status-ne'PASS' -and $Failure.Count-eq0){$Failure.Add('nonpositive R_electrolyte cannot enter conductivity inversion')}
    return [pscustomobject][ordered]@{sample_id=$SampleId;R_HFR_total_ohm=Format-M03A4Number $Total;R_HFR_total_uncertainty_ohm=Format-M03A4Number $TotalU;R_non_electrolyte_ohm=Format-M03A4Number $Non;R_non_electrolyte_uncertainty_ohm=Format-M03A4Number $NonU;R_electrolyte_ohm=Format-M03A4Number $Electrolyte;R_electrolyte_uncertainty_ohm=Format-M03A4Number $ElectrolyteU;fixture_ohm=if($Values.ContainsKey('fixture')){Format-M03A4Number $Values.fixture}else{''};fixture_uncertainty_ohm=if($Uncertainties.ContainsKey('fixture')){Format-M03A4Number $Uncertainties.fixture}else{''};contact_ohm=if($Values.ContainsKey('contact')){Format-M03A4Number $Values.contact}else{''};contact_uncertainty_ohm=if($Uncertainties.ContainsKey('contact')){Format-M03A4Number $Uncertainties.contact}else{''};membrane_ohm=if($Values.ContainsKey('membrane')){Format-M03A4Number $Values.membrane}else{''};membrane_uncertainty_ohm=if($Uncertainties.ContainsKey('membrane')){Format-M03A4Number $Uncertainties.membrane}else{''};other_series_ohm=if($Values.ContainsKey('other_series')){Format-M03A4Number $Values.other_series}else{''};other_series_uncertainty_ohm=if($Uncertainties.ContainsKey('other_series')){Format-M03A4Number $Uncertainties.other_series}else{''};fixture_source=if($Sources.ContainsKey('fixture')){$Sources.fixture}else{''};contact_source=if($Sources.ContainsKey('contact')){$Sources.contact}else{''};membrane_source=if($Sources.ContainsKey('membrane')){$Sources.membrane}else{''};other_series_source=if($Sources.ContainsKey('other_series')){$Sources.other_series}else{''};status=$Status;failure_reason=($Failure -join ';')}
}

function Invoke-M03A4AreaAudit {
    param([string]$SampleId,[object[]]$Rows)
    $Required=[ordered]@{electrode_spacing='m';out_of_plane_thickness='m';geometric_electrode_area='m^2';actual_wetted_area='m^2';catalytic_layer_projected_area='m^2';GDE_exposed_area='m^2';EIS_area='m^2';current_density_reporting_area='m^2'}
    $Sample=@($Rows|Where-Object sample_id -eq $SampleId); $Overall='PASS'; $Reason=''
    foreach($Name in $Required.Keys){
        $Found=@($Sample|Where-Object quantity_name -ceq $Name)
        if($Found.Count-ne1){$Overall='AREA_MAPPING_INCOMPLETE';$Reason="missing or duplicate $Name";break}
        $R=$Found[0]
        try{
            if([string]$R.unit-ne$Required[$Name]){throw "unit mismatch for $Name"}
            if([string]$R.data_origin-ne'SYNTHETIC'){throw "data_origin must be SYNTHETIC for $Name"}
            if([string]$R.status-ne'SYNTHETIC_DEFINED'){throw "status must be SYNTHETIC_DEFINED for $Name"}
            $V=ConvertTo-M03A4Double $R.value_SI "$Name value";$U=ConvertTo-M03A4Double $R.uncertainty_SI "$Name uncertainty"
            if($V-le0-or$U-lt0){throw 'nonpositive value or negative uncertainty'}
            if([string]::IsNullOrWhiteSpace([string]$R.measurement_method)-or[string]::IsNullOrWhiteSpace([string]$R.source_reference)-or[string]::IsNullOrWhiteSpace([string]$R.definition)){throw 'source evidence incomplete'}
        }catch{$Overall=if($Name-eq'EIS_area'){'AREA_BASIS_MISMATCH'}else{'AREA_MAPPING_INCOMPLETE'};$Reason=$_.Exception.Message;break}
    }
    if($Overall-eq'PASS'){
        foreach($Name in @('electrode_spacing','out_of_plane_thickness','EIS_area','current_density_reporting_area')){
            $R=@($Sample|Where-Object quantity_name -eq $Name)[0]
            if([string]::IsNullOrWhiteSpace([string]$R.mapping_formula)-or[string]::IsNullOrWhiteSpace([string]$R.mapping_reference)-or[string]::IsNullOrWhiteSpace([string]$R.mapping_uncertainty_relative)){$Overall='AREA_BASIS_MISMATCH';$Reason="incomplete mapping evidence for $Name";break}
        }
    }
    return @($Sample|ForEach-Object{[pscustomobject][ordered]@{sample_id=$_.sample_id;quantity_name=$_.quantity_name;value_SI=$_.value_SI;uncertainty_SI=$_.uncertainty_SI;unit=$_.unit;data_origin=$_.data_origin;status=$_.status;measurement_method=$_.measurement_method;source_reference=$_.source_reference;definition=$_.definition;mapping_formula=$_.mapping_formula;mapping_reference=$_.mapping_reference;mapping_uncertainty_relative=$_.mapping_uncertainty_relative;overall_status=$Overall;failure_reason=$Reason;notes=$_.notes}})
}

function Test-M03A4DirectConductivity {
    param([string]$SampleId,[object[]]$Rows)
    $Matches=@($Rows|Where-Object sample_id -eq $SampleId)
    if($Matches.Count-ne1){throw 'direct conductivity row missing or duplicate'}
    $R=$Matches[0]
    if($R.data_origin-ne'SYNTHETIC'-or$R.status-ne'SYNTHETIC_DEFINED'-or$R.unit-ne'S/m'){throw 'direct conductivity origin status or unit invalid'}
    foreach($Name in @('measurement_method','source_reference','temperature_K')){if([string]::IsNullOrWhiteSpace([string]$R.$Name)){throw "direct conductivity $Name missing"}}
    $V=ConvertTo-M03A4Double $R.conductivity_S_m 'direct conductivity';$U=ConvertTo-M03A4Double $R.uncertainty_S_m 'direct conductivity uncertainty';$T=ConvertTo-M03A4Double $R.temperature_K 'direct conductivity temperature'
    if($V-le0-or$U-lt0-or$T-le0){throw 'direct conductivity value uncertainty or temperature invalid'}
    return [pscustomobject][ordered]@{sample_id=$SampleId;conductivity_S_m=Format-M03A4Number $V;uncertainty_S_m=Format-M03A4Number $U;unit=$R.unit;temperature_K=Format-M03A4Number $T;data_origin=$R.data_origin;status='PASS';measurement_method=$R.measurement_method;source_reference=$R.source_reference;failure_reason=''}
}

function Get-M03A4ResolvedValue {
    param([object[]]$Rows,[string]$SampleId,[string]$Quantity,[string]$Unit)
    $M=@($Rows|Where-Object{$_.sample_id-eq$SampleId-and$_.quantity_name-eq$Quantity})
    if($M.Count-ne1){throw "resolved row missing or duplicate: $SampleId/$Quantity"}
    $R=$M[0]
    if($R.unit-ne$Unit-or$R.data_origin-ne'SYNTHETIC'-or$R.status-ne'PASS'-or$R.source_sha256-notmatch'^[0-9a-fA-F]{64}$'){throw "resolved row validation failed: $SampleId/$Quantity"}
    return [pscustomobject]@{Value=(ConvertTo-M03A4Double $R.value_SI "$Quantity value");Uncertainty=(ConvertTo-M03A4Double $R.uncertainty_SI "$Quantity uncertainty");Row=$R}
}

function ConvertTo-M03A4CanonicalField {
    param([AllowNull()][object]$Value)
    return ([string]$Value).Replace('\','\\').Replace('|','\|').Replace("`r",'\r').Replace("`n",'\n')
}

function New-M03A4ParentProvenance {
    param([object[]]$ResolvedRows,[string]$SampleId='SYN_DRY_001',[string]$DerivedQuantity='conductivity')
    $Order=[ordered]@{
        EIS_HFR_total='ohm'
        fixture='ohm'
        contact='ohm'
        membrane='ohm'
        other_series='ohm'
        electrode_spacing='m'
        EIS_area='m^2'
    }
    $Out=New-Object Collections.Generic.List[object]
    foreach($Name in $Order.Keys){
        $Resolved=Get-M03A4ResolvedValue $ResolvedRows $SampleId $Name $Order[$Name]
        $R=$Resolved.Row
        foreach($Required in @('data_origin','evidence_status','source_table','source_rows','source_reference','source_sha256')){
            if([string]::IsNullOrWhiteSpace([string]$R.$Required)){throw "parent provenance incomplete: $SampleId/$Name/$Required"}
        }
        if($R.source_sha256-notmatch'^[0-9a-fA-F]{64}$'){throw "parent source hash invalid: $SampleId/$Name"}
        $Fields=@($DerivedQuantity,$Name,(Format-M03A4Number $Resolved.Value),(Format-M03A4Number $Resolved.Uncertainty),$R.unit,$R.data_origin,$R.source_table,$R.source_rows,$R.source_reference,$R.source_sha256.ToLowerInvariant(),$R.evidence_status)
        $Canonical=($Fields|ForEach-Object{ConvertTo-M03A4CanonicalField $_})-join'|'
        $Out.Add([pscustomobject][ordered]@{
            derived_quantity=$DerivedQuantity
            parent_quantity=$Name
            parent_value=Format-M03A4Number $Resolved.Value
            parent_uncertainty=Format-M03A4Number $Resolved.Uncertainty
            parent_unit=$R.unit
            parent_origin=$R.data_origin
            parent_source_table=$R.source_table
            parent_source_rows=$R.source_rows
            parent_source_reference=$R.source_reference
            parent_source_sha256=$R.source_sha256.ToLowerInvariant()
            canonical_serialization=$Canonical
            status=$R.evidence_status
        })
    }
    if($Out.Count-ne$Order.Count){throw "parent provenance incomplete: expected $($Order.Count), found $($Out.Count)"}
    $Material=(@($Out|ForEach-Object{$_.canonical_serialization})-join"`n")
    $Bytes=[Text.Encoding]::UTF8.GetBytes($Material);$Hasher=[Security.Cryptography.SHA256]::Create()
    try{$Digest=(($Hasher.ComputeHash($Bytes)|ForEach-Object{$_.ToString('x2')})-join'')}finally{$Hasher.Dispose()}
    return [pscustomobject]@{Rows=@($Out|ForEach-Object{$_});Digest=$Digest;CanonicalMaterial=$Material}
}

function Get-M03A4AnalyticalUncertaintyFromResolved {
    param([object[]]$Rows,[string]$SampleId='SYN_DRY_001')
    $H=Get-M03A4ResolvedValue $Rows $SampleId 'EIS_HFR_total' 'ohm';$F=Get-M03A4ResolvedValue $Rows $SampleId 'fixture' 'ohm';$C=Get-M03A4ResolvedValue $Rows $SampleId 'contact' 'ohm';$M=Get-M03A4ResolvedValue $Rows $SampleId 'membrane' 'ohm';$O=Get-M03A4ResolvedValue $Rows $SampleId 'other_series' 'ohm';$S=Get-M03A4ResolvedValue $Rows $SampleId 'electrode_spacing' 'm';$A=Get-M03A4ResolvedValue $Rows $SampleId 'EIS_area' 'm^2'
    $R=$H.Value-$F.Value-$C.Value-$M.Value-$O.Value;if($R-le0){throw 'nonpositive analytical input'}
    $Var=$H.Uncertainty*$H.Uncertainty+$F.Uncertainty*$F.Uncertainty+$C.Uncertainty*$C.Uncertainty+$M.Uncertainty*$M.Uncertainty+$O.Uncertainty*$O.Uncertainty
    $RU=[Math]::Sqrt($Var);$K=$S.Value/($A.Value*$R);$KU=$K*[Math]::Sqrt(($S.Uncertainty/$S.Value)*($S.Uncertainty/$S.Value)+($A.Uncertainty/$A.Value)*($A.Uncertainty/$A.Value)+($RU/$R)*($RU/$R))
    return [pscustomobject][ordered]@{method='FIRST_ORDER_INDEPENDENT';R_electrolyte_ohm=Format-M03A4Number $R;R_electrolyte_uncertainty_ohm=Format-M03A4Number $RU;conductivity_S_m=Format-M03A4Number $K;conductivity_uncertainty_S_m=Format-M03A4Number $KU;accepted_samples='';rejected_samples='';total_generated='';rejected_fraction='';seed='';status='PASS';assumption='inputs independent; covariance=0; simple variance sum forbidden when covariance becomes available';source_artifact='results/tables/M03A_4_resolved_inputs.csv'}
}

function Get-M03A4NextNormal { param([Random]$Random) $U1=[Math]::Max($Random.NextDouble(),1e-15);$U2=$Random.NextDouble();return [Math]::Sqrt(-2*[Math]::Log($U1))*[Math]::Cos(2*[Math]::PI*$U2) }

function Invoke-M03A4MonteCarloFromResolved {
    param([object[]]$Rows,[string]$SampleId='SYN_DRY_001',[int]$SampleCount=20000,[int]$Seed=40304)
    if($SampleCount-lt20000){throw 'Monte Carlo requires at least 20000 samples'}
    $Q=[ordered]@{hfr=@('EIS_HFR_total','ohm');fixture=@('fixture','ohm');contact=@('contact','ohm');membrane=@('membrane','ohm');other_series=@('other_series','ohm');spacing=@('electrode_spacing','m');area=@('EIS_area','m^2')};$Means=@{};$U=@{}
    foreach($N in $Q.Keys){$V=Get-M03A4ResolvedValue $Rows $SampleId $Q[$N][0] $Q[$N][1];$Means[$N]=$V.Value;$U[$N]=$V.Uncertainty}
    $Random=New-Object Random($Seed);$Values=New-Object Collections.Generic.List[double];$Rejected=0
    for($I=0;$I-lt$SampleCount;$I++){$S=@{};foreach($N in $Q.Keys){$S[$N]=[double]$Means[$N]+[double]$U[$N]*(Get-M03A4NextNormal $Random)};$R=$S.hfr-$S.fixture-$S.contact-$S.membrane-$S.other_series;if($S.spacing-le0-or$S.area-le0-or$R-le0){$Rejected++;continue};$K=$S.spacing/($S.area*$R);if($K-le0-or-not(Test-M03A4Finite $K)){$Rejected++;continue};$Values.Add($K)}
    $Fraction=$Rejected/[double]$SampleCount;$Mean=if($Values.Count){[double](($Values|Measure-Object -Average).Average)}else{[double]::NaN};$SS=0.0;foreach($V in $Values){$SS+=($V-$Mean)*($V-$Mean)};$Sd=if($Values.Count-gt1){[Math]::Sqrt($SS/($Values.Count-1))}else{[double]::NaN}
    return [pscustomobject][ordered]@{method='FIXED_SEED_MONTE_CARLO';R_electrolyte_ohm='';R_electrolyte_uncertainty_ohm='';conductivity_S_m=Format-M03A4Number $Mean;conductivity_uncertainty_S_m=Format-M03A4Number $Sd;accepted_samples=$Values.Count;rejected_samples=$Rejected;total_generated=$SampleCount;rejected_fraction=Format-M03A4Number $Fraction;seed=$Seed;status=if($Fraction-gt0.01){'FAILED_INPUT_DISTRIBUTION'}else{'PASS'};assumption='inputs independent; covariance=0; rejected unchanged; no clipping or repair';source_artifact='results/tables/M03A_4_resolved_inputs.csv'}
}

function Compare-M03A4Conductivity {
    param([double]$DirectSPerM,[double]$DirectUncertaintySPerM,[double]$HfrSPerM,[double]$HfrUncertaintySPerM,[double]$Limit=0.10)
    foreach($V in @($DirectSPerM,$HfrSPerM)){if(-not(Test-M03A4Finite $V)-or$V-le0){throw 'conductivity inputs must be finite and positive'}};$D=[Math]::Abs($DirectSPerM-$HfrSPerM)/[Math]::Max(0.5*([Math]::Abs($DirectSPerM)+[Math]::Abs($HfrSPerM)),1e-30)
    return [pscustomobject][ordered]@{sample_id='SYN_DRY_001';direct_conductivity_S_m=Format-M03A4Number $DirectSPerM;direct_uncertainty_S_m=Format-M03A4Number $DirectUncertaintySPerM;HFR_conductivity_S_m=Format-M03A4Number $HfrSPerM;HFR_uncertainty_S_m=Format-M03A4Number $HfrUncertaintySPerM;relative_difference=Format-M03A4Number $D;threshold=Format-M03A4Number $Limit;status=if($D-gt$Limit){'CALIBRATION_CONFLICT'}else{'PASS'};selection='NONE_AUTOMATIC';failure_reason=if($D-gt$Limit){'difference exceeds threshold; no truth selected'}else{''}}
}

function Invoke-M03A4ParameterMappingAudit {
    param([object[]]$Rows)
    $Expected=[ordered]@{conductivity=@('kappa_dry_run','kappa_dry_run=conductivity','S/m','S/m');electrode_spacing=@('Hcell_dry_run','Hcell_dry_run=electrode_spacing','m','m');out_of_plane_thickness=@('depth_dry_run','depth_dry_run=out_of_plane_thickness','m','m');EIS_area=@('A_EIS_dry_run','A_EIS_dry_run=EIS_area','m^2','m^2');current_density_reporting_area=@('A_j_report_dry_run','A_j_report_dry_run=current_density_reporting_area','m^2','m^2')};$Out=New-Object Collections.Generic.List[object]
    foreach($Source in $Expected.Keys){$Matches=@($Rows|Where-Object source_quantity -ceq $Source);$Found=$null;$Status='PASS';$Reason='';if($Matches.Count-ne1){$Status='PARAMETER_TARGET_MISSING';$Reason='missing or duplicate mapping'}else{$Found=$Matches[0];$E=$Expected[$Source];if($Found.target_model_parameter-ne$E[0]){$Status='PARAMETER_TARGET_MISSING';$Reason='unexpected target'}elseif($Found.conversion_formula-cne$E[1]){$Status='PARAMETER_FORMULA_NOT_WHITELISTED';$Reason='formula must exactly match fixed whitelist'}elseif($Found.source_unit-ne$E[2]-or$Found.target_unit-ne$E[3]){$Status='PARAMETER_UNIT_MISMATCH';$Reason='source or target unit mismatch'}elseif([string]::IsNullOrWhiteSpace([string]$Found.source_reference)){$Status='PARAMETER_MAPPING_INCOMPLETE';$Reason='provenance missing'}};$Out.Add([pscustomobject][ordered]@{source_quantity=$Source;target_model_parameter=if($null-eq$Found){''}else{$Found.target_model_parameter};conversion_formula=if($null-eq$Found){''}else{$Found.conversion_formula};source_unit=if($null-eq$Found){''}else{$Found.source_unit};target_unit=if($null-eq$Found){''}else{$Found.target_unit};uncertainty_method=if($null-eq$Found){''}else{$Found.uncertainty_method};required_status=if($null-eq$Found){''}else{$Found.required_status};source_reference=if($null-eq$Found){''}else{$Found.source_reference};status=$Status;failure_reason=$Reason})}
    return @($Out | ForEach-Object { $_ })
}

function New-M03A4CompletenessGate {
    param([bool]$SyntheticTransferPass)
    $Items=[ordered]@{SYNTHETIC_PARAMETER_TRANSFER_PASS=@($(if($SyntheticTransferPass){'TRUE'}else{'FALSE'}),$(if($SyntheticTransferPass){'PASS'}else{'FAIL'}),'actual combined COMSOL integration result');EXPERIMENTAL_EIS_COMPLETE=@('FALSE','EXPERIMENTAL_INPUT_INCOMPLETE','no real EIS file supplied');REPLICATE_CONSENSUS_ACCEPTED=@('FALSE','EXPERIMENTAL_INPUT_INCOMPLETE','synthetic consensus is not experimental evidence');SERIES_RESISTANCE_LEDGER_COMPLETE=@('FALSE','EXPERIMENTAL_INPUT_INCOMPLETE','experimental series ledger missing');DEEMBEDDED_RESISTANCE_POSITIVE=@('FALSE','EXPERIMENTAL_INPUT_INCOMPLETE','experimental de-embedding unavailable');AREA_MAPPING_COMPLETE=@('FALSE','EXPERIMENTAL_INPUT_INCOMPLETE','experimental area mapping unavailable');ELECTRODE_SPACING_MEASURED=@('FALSE','EXPERIMENTAL_INPUT_INCOMPLETE','experimental spacing absent');DIRECT_CONDUCTIVITY_AVAILABLE=@('FALSE','EXPERIMENTAL_INPUT_INCOMPLETE','experimental direct conductivity absent');CONDUCTIVITY_CONFLICT_RESOLVED=@('FALSE','EXPERIMENTAL_INPUT_INCOMPLETE','no experimental comparison');UNCERTAINTY_PROPAGATION_COMPLETE=@('FALSE','EXPERIMENTAL_INPUT_INCOMPLETE','experimental uncertainties absent');EXPERIMENTAL_PARAMETER_TRANSFER_PASS=@('FALSE','EXPERIMENTAL_INPUT_INCOMPLETE','synthetic dry-run cannot satisfy experimental gate');M03B_READY=@('FALSE','PASS','M03A.4 does not authorize M03B')}
    return @($Items.Keys|ForEach-Object{[pscustomobject][ordered]@{item=$_;value=$Items[$_][0];status=$Items[$_][1];reason=$Items[$_][2]}})
}

Export-ModuleMember -Function @('Test-M03A4Finite','ConvertTo-M03A4Double','Format-M03A4Number','Write-M03A4Utf8NoBomText','Write-M03A4Utf8NoBomCsv','Get-M03A4Sha256','Import-M03A4Manifest','Import-M03A4Eis','Get-M03A4HfrEstimate','Get-M03A4ReplicateConsensus','Get-M03A4ResistanceLedger','Invoke-M03A4AreaAudit','Test-M03A4DirectConductivity','Get-M03A4ResolvedValue','New-M03A4ParentProvenance','Get-M03A4AnalyticalUncertaintyFromResolved','Invoke-M03A4MonteCarloFromResolved','Compare-M03A4Conductivity','Invoke-M03A4ParameterMappingAudit','New-M03A4CompletenessGate')
