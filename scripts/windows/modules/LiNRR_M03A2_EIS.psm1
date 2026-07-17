Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$script:Invariant = [Globalization.CultureInfo]::InvariantCulture

function Test-M03A2Finite { param([double]$Value) return (-not [double]::IsNaN($Value))-and(-not [double]::IsInfinity($Value)) }

function ConvertTo-M03A2Double {
    param([object]$Value,[string]$FieldName='value')
    $Text=[string]$Value;if([string]::IsNullOrWhiteSpace($Text)){throw "Blank numeric value for $FieldName"}
    $Number=0.0;$Ok=[double]::TryParse($Text,[Globalization.NumberStyles]::Float,$script:Invariant,[ref]$Number)
    if(-not$Ok-or-not(Test-M03A2Finite $Number)){throw "Invalid nonfinite or nonnumeric value for ${FieldName}: '$Text'"};return $Number
}

function Format-M03A2Number {
    param([double]$Value)
    if(-not(Test-M03A2Finite $Value)){if([double]::IsNaN($Value)){return'NaN'};if($Value-gt0){return'Infinity'};return'-Infinity'}
    return $Value.ToString('G17',$script:Invariant)
}

function Write-M03A2Utf8NoBomText { param([string]$Path,[string]$Text) [IO.File]::WriteAllText($Path,$Text,(New-Object Text.UTF8Encoding($false))) }
function Write-M03A2Utf8NoBomCsv {
    param([object[]]$Rows,[string]$Path)
    @($Rows)|Export-Csv -LiteralPath $Path -NoTypeInformation -Encoding UTF8
    $Text=[IO.File]::ReadAllText($Path,[Text.Encoding]::UTF8)-replace"`r`n","`n";Write-M03A2Utf8NoBomText $Path $Text
}

function Convert-M03A2FrequencyToHz {
    param([double]$Value,[string]$Unit)
    $Token=$Unit.Trim()
    switch -CaseSensitive ($Token){'Hz'{return $Value};'kHz'{return $Value*1e3};'mHz'{return $Value*1e-3};'MHz'{return $Value*1e6};default{throw "UNSUPPORTED_FREQUENCY_UNIT: '$Unit'; allowed: Hz, kHz, mHz, MHz"}}
}

function Convert-M03A2ImpedanceToOhm {
    param([double]$Value,[string]$Unit)
    $Token=$Unit.Trim();if($Token-match'[^\x00-\x7F]'){throw "UNSUPPORTED_IMPEDANCE_UNIT: ambiguous Unicode token '$Unit'"}
    switch($Token.ToLowerInvariant()){'ohm'{return $Value};'mohm'{return $Value*1e-3};'kohm'{return $Value*1e3};default{throw "UNSUPPORTED_IMPEDANCE_UNIT: '$Unit'; allowed: ohm, mohm, kohm"}}
}

function Get-M03A2NextNormal { param([Random]$Random) $U1=[Math]::Max($Random.NextDouble(),1e-15);$U2=$Random.NextDouble();return [Math]::Sqrt(-2*[Math]::Log($U1))*[Math]::Cos(2*[Math]::PI*$U2) }

function New-M03A2SyntheticFixtures {
    param([object[]]$ManifestRows,[string]$ProjectRoot)
    foreach($Case in @($ManifestRows)){
        if(([string]$Case.data_origin)-cne'SYNTHETIC'){continue}
        $Path=Join-Path $ProjectRoot (([string]$Case.file_path).Replace('/','\'));$Dir=Split-Path -Parent $Path;if(-not(Test-Path $Dir)){New-Item -ItemType Directory -Force -Path $Dir|Out-Null}
        $Count=[int](ConvertTo-M03A2Double $Case.point_count point_count);$Fmax=ConvertTo-M03A2Double $Case.max_frequency_Hz max_frequency_Hz
        $Rs=ConvertTo-M03A2Double $Case.rs_fixture_ohm rs_fixture_ohm;$Rct=ConvertTo-M03A2Double $Case.rct_ohm rct_ohm;$C=ConvertTo-M03A2Double $Case.capacitance_F capacitance_F
        $Random=$null;$Sigma=0.0;if(-not[string]::IsNullOrWhiteSpace([string]$Case.noise_seed)){$Random=New-Object Random([int](ConvertTo-M03A2Double $Case.noise_seed noise_seed));$Sigma=ConvertTo-M03A2Double $Case.noise_sigma_ohm noise_sigma_ohm}
        $Generated=New-Object Collections.Generic.List[object];$LogMax=[Math]::Log10($Fmax)
        for($I=0;$I-lt$Count;$I++){
            $Fraction=if($Count-eq1){0}else{$I/[double]($Count-1)};$F=[Math]::Pow(10,$LogMax-($LogMax+1)*$Fraction);$W=2*[Math]::PI*$F;$Real=$Rs;$Imag=0.0
            if($Rct-gt0-and$C-gt0){
                if($Case.case_type-eq'RS_RCT_CPE'){$Alpha=.82;$Magnitude=$C*[Math]::Pow($W,$Alpha);$Yr=1/$Rct+$Magnitude*[Math]::Cos($Alpha*[Math]::PI/2);$Yi=$Magnitude*[Math]::Sin($Alpha*[Math]::PI/2);$D=$Yr*$Yr+$Yi*$Yi;$Real+=$Yr/$D;$Imag=-$Yi/$D}
                else{$X=$W*$Rct*$C;$Real+=$Rct/(1+$X*$X);$Imag=-$Rct*$X/(1+$X*$X)}
            }
            if($null-ne$Random){$Real+=$Sigma*(Get-M03A2NextNormal $Random);$Imag+=$Sigma*(Get-M03A2NextNormal $Random)}
            if($Case.case_type-eq'HIGH_FREQUENCY_INDUCTIVE_ARTIFACT'-and$I-lt3){$Imag=.06-.02*$I}
            $OutF=switch -CaseSensitive ([string]$Case.frequency_unit){'kHz'{$F/1e3};'mHz'{$F*1e3};'MHz'{$F/1e6};default{$F}}
            $Unit=[string]$Case.impedance_unit;$OutR=switch($Unit.ToLowerInvariant()){'mohm'{$Real*1e3};'kohm'{$Real/1e3};default{$Real}};$OutI=switch($Unit.ToLowerInvariant()){'mohm'{$Imag*1e3};'kohm'{$Imag/1e3};default{$Imag}}
            $Generated.Add([PSCustomObject][ordered]@{frequency=Format-M03A2Number $OutF;z_real=Format-M03A2Number $OutR;z_imag=Format-M03A2Number $OutI})
        }
        $Rows=@($Generated|ForEach-Object{$_})
        if($Case.case_type-in@('DUPLICATE_FREQUENCY','DUPLICATE_AND_UNSORTED')){$Rows=@($Rows[0..10]+$Rows[10]+$Rows[11..($Rows.Count-1)])}
        if($Case.case_type-in@('UNSORTED_FREQUENCY','DUPLICATE_AND_UNSORTED')){$Even=@();$Odd=@();for($I=0;$I-lt$Rows.Count;$I++){if($I%2-eq0){$Even+=$Rows[$I]}else{$Odd+=$Rows[$I]}};$Rows=@($Odd+$Even)}
        if(([string]$Case.case_type)-like'INVALID_NUMERIC_DATA_*'){$Bad=[Math]::Min(5,$Rows.Count-1);switch($Case.case_type){'INVALID_NUMERIC_DATA_NAN'{$Rows[$Bad].z_real='NaN'};'INVALID_NUMERIC_DATA_INF'{$Rows[$Bad].z_imag='Infinity'};'INVALID_NUMERIC_DATA_EMPTY'{$Rows[$Bad].frequency=''};'INVALID_NUMERIC_DATA_STRING'{$Rows[$Bad].z_real='not_a_number'}}}
        Write-M03A2Utf8NoBomCsv $Rows $Path
    }
}

function Import-M03A2Eis {
    param([string]$Path,[string]$CaseId,[string]$FrequencyUnit,[string]$ImpedanceUnit,[string]$SampleId='',[string]$ReplicateId='',[string]$CaseType='')
    if(-not(Test-Path -LiteralPath $Path -PathType Leaf)){throw "EIS input missing: $Path"};$Raw=@(Import-Csv -LiteralPath $Path -Encoding UTF8);if($Raw.Count-eq0){throw "EIS input has no rows: $Path"}
    foreach($Name in @('frequency','z_real','z_imag')){if(-not($Raw[0].PSObject.Properties.Name-contains$Name)){throw "EIS input missing column '$Name': $Path"}}
    $UnitStatus='PASS';$UnitDetail='';try{[void](Convert-M03A2FrequencyToHz 1 $FrequencyUnit);[void](Convert-M03A2ImpedanceToOhm 1 $ImpedanceUnit)}catch{$UnitDetail=$_.Exception.Message;$UnitStatus=if($UnitDetail-like'UNSUPPORTED_FREQUENCY_UNIT*'){'BLOCKED_UNSUPPORTED_FREQUENCY_UNIT'}else{'BLOCKED_UNSUPPORTED_IMPEDANCE_UNIT'}}
    $Parsed=New-Object Collections.Generic.List[object];$Audit=New-Object Collections.Generic.List[object];$Invalid=0
    for($I=0;$I-lt$Raw.Count;$I++){
        $R=$Raw[$I];$F=[double]::NaN;$Zr=[double]::NaN;$Zi=[double]::NaN;$Status=$UnitStatus;$Detail=$UnitDetail
        if($Status-eq'PASS'){try{$F=Convert-M03A2FrequencyToHz (ConvertTo-M03A2Double $R.frequency frequency) $FrequencyUnit;$Zr=Convert-M03A2ImpedanceToOhm (ConvertTo-M03A2Double $R.z_real z_real) $ImpedanceUnit;$Zi=Convert-M03A2ImpedanceToOhm (ConvertTo-M03A2Double $R.z_imag z_imag) $ImpedanceUnit;if($F-le0){throw'Frequency must be positive'}}catch{$Status='BLOCKED_INVALID_NUMERIC_DATA';$Detail=$_.Exception.Message;$Invalid++}}
        $Row=[PSCustomObject][ordered]@{SampleId=$SampleId;ReplicateId=$ReplicateId;CaseId=$CaseId;CaseType=$CaseType;SourceFile=$Path;OriginalRowNumber=$I+2;RawFrequency=[string]$R.frequency;RawZReal=[string]$R.z_real;RawZImag=[string]$R.z_imag;FrequencyHz=$F;ZRealOhm=$Zr;ZImagOhm=$Zi;NormalizedOrder='';DuplicateFrequency=$false;HighFrequencyInductive=$false;ParseStatus=$Status;Detail=$Detail};$Audit.Add($Row);if($Status-eq'PASS'){$Parsed.Add($Row)}
    }
    if (($UnitStatus -ne 'PASS') -or ($Invalid -gt 0)) {
        $Final = if ($UnitStatus -ne 'PASS') { $UnitStatus } else { 'BLOCKED_INVALID_NUMERIC_DATA' }
        $FinalDetail = if ($UnitStatus -ne 'PASS') { $UnitDetail } else { "$Invalid invalid row(s); entire case blocked" }
        return [PSCustomObject][ordered]@{SampleId=$SampleId;ReplicateId=$ReplicateId;CaseId=$CaseId;CaseType=$CaseType;SourceFile=$Path;Status=$Final;Rows=@();AuditRows=@($Audit|ForEach-Object{$_});DuplicateCount=0;InductiveHighFrequencyCount=0;OriginalRowCount=$Raw.Count;Detail=$FinalDetail}
    }
    $Counts=@{};foreach($Row in $Parsed){$Key=Format-M03A2Number $Row.FrequencyHz;if(-not$Counts.ContainsKey($Key)){$Counts[$Key]=0};$Counts[$Key]++}
    $Sorted=@($Parsed|Sort-Object @{Expression={[double]$_.FrequencyHz};Descending=$true},@{Expression={[int]$_.OriginalRowNumber};Ascending=$true});$Fmax=[double]$Sorted[0].FrequencyHz;$Scale=[double](@($Sorted|ForEach-Object{[Math]::Abs([double]$_.ZRealOhm)}|Measure-Object -Maximum).Maximum);$Threshold=[Math]::Max(.01,.005*$Scale);$Dup=0;$Ind=0
    for($I=0;$I-lt$Sorted.Count;$I++){$Row=$Sorted[$I];$Row.NormalizedOrder=$I+1;$Key=Format-M03A2Number $Row.FrequencyHz;$Row.DuplicateFrequency=$Counts[$Key]-gt1;$Row.HighFrequencyInductive=$Row.FrequencyHz-ge.1*$Fmax-and$Row.ZImagOhm-gt$Threshold;if($Row.DuplicateFrequency){$Dup++};if($Row.HighFrequencyInductive){$Ind++}}
    $Status=if($Ind-gt0){'WARNING_HIGH_FREQUENCY_INDUCTIVE_ARTIFACT'}elseif($Dup-gt0){'WARNING_DUPLICATE_FREQUENCY'}else{'PASS'}
    return [PSCustomObject][ordered]@{SampleId=$SampleId;ReplicateId=$ReplicateId;CaseId=$CaseId;CaseType=$CaseType;SourceFile=$Path;Status=$Status;Rows=$Sorted;AuditRows=$Sorted;DuplicateCount=$Dup;InductiveHighFrequencyCount=$Ind;OriginalRowCount=$Raw.Count;Detail="all rows retained; inductive threshold=$(Format-M03A2Number $Threshold) ohm"}
}

function Solve-M03A2Linear3 {
    param([double[]]$Matrix,[double[]]$Vector)
    $A=New-Object 'double[]' 12;for($I=0;$I-lt3;$I++){for($J=0;$J-lt3;$J++){$A[4*$I+$J]=$Matrix[3*$I+$J]};$A[4*$I+3]=$Vector[$I]}
    for($C=0;$C-lt3;$C++){$P=$C;for($R=$C+1;$R-lt3;$R++){if([Math]::Abs($A[4*$R+$C]) -gt [Math]::Abs($A[4*$P+$C])){$P=$R}};if([Math]::Abs($A[4*$P+$C])-lt1e-20){throw'Singular circle-fit normal matrix'};if($P-ne$C){for($J=$C;$J-lt4;$J++){$T=$A[4*$C+$J];$A[4*$C+$J]=$A[4*$P+$J];$A[4*$P+$J]=$T}};$D=$A[4*$C+$C];for($J=$C;$J-lt4;$J++){$A[4*$C+$J]/=$D};for($R=0;$R-lt3;$R++){if($R-eq$C){continue};$K=$A[4*$R+$C];for($J=$C;$J-lt4;$J++){$A[4*$R+$J]-=$K*$A[4*$C+$J]}}};return [double[]]@($A[3],$A[7],$A[11])
}

function Get-M03A2CircleIntercept {
    param([object[]]$Rows)
    $FitRows=@($Rows|Where-Object{[double]$_.ZImagOhm-le0});if($FitRows.Count-lt6){throw'Fewer than 6 noninductive fit rows'};$X=@($FitRows|ForEach-Object{[double]$_.ZRealOhm});$Y=@($FitRows|ForEach-Object{-[double]$_.ZImagOhm});$Xrange=($X|Measure-Object -Maximum).Maximum-($X|Measure-Object -Minimum).Minimum;$Yrange=($Y|Measure-Object -Maximum).Maximum-($Y|Measure-Object -Minimum).Minimum
    if([Math]::Abs($Yrange)-lt1e-12-and[Math]::Abs($Xrange)-lt1e-9){$Mean=[double](($X|Measure-Object -Average).Average);return [PSCustomObject]@{EstimateOhm=$Mean;StandardUncertaintyOhm=0.0;FitType='RESISTIVE_LEVEL';FitRows=$FitRows;ResidualMetric=0.0;FitQualityStatus='PASS';RequiresCompleteSemicircle=$false}}
    $N=New-Object 'double[]' 9;$B=New-Object 'double[]' 3;foreach($Row in $FitRows){$A=[double]$Row.ZRealOhm;$C=-[double]$Row.ZImagOhm;$D=-($A*$A+$C*$C);$V=@($A,$C,1.0);for($I=0;$I-lt3;$I++){$B[$I]+=$V[$I]*$D;for($J=0;$J-lt3;$J++){$N[3*$I+$J]+=$V[$I]*$V[$J]}}};$S=Solve-M03A2Linear3 $N $B;$Cx=-.5*$S[0];$Cy=-.5*$S[1];$R2=$Cx*$Cx+$Cy*$Cy-$S[2];if($R2-le0){throw'Nonpositive fitted circle radius'};$Radius=[Math]::Sqrt($R2);$Intercept=$Cx-$Radius;if(-not(Test-M03A2Finite $Intercept)-or$Intercept-le0){throw'Nonpositive fitted intercept'};$SS=0.0;foreach($Row in $FitRows){$D=[Math]::Sqrt(([double]$Row.ZRealOhm-$Cx)*([double]$Row.ZRealOhm-$Cx)+(-[double]$Row.ZImagOhm-$Cy)*(-[double]$Row.ZImagOhm-$Cy));$SS+=($D-$Radius)*($D-$Radius)};$Rms=[Math]::Sqrt($SS/[Math]::Max(1,$FitRows.Count-3));$Norm=$Rms/[Math]::Max($Radius,1e-30);$U=$Rms/[Math]::Sqrt($FitRows.Count);return [PSCustomObject]@{EstimateOhm=$Intercept;StandardUncertaintyOhm=$U;FitType='FULL_ARC_CIRCLE_LEFT_REAL_INTERCEPT';FitRows=$FitRows;ResidualMetric=$Norm;FitQualityStatus=if($Norm-le.05){'PASS'}else{'FAILED_FIT_QUALITY'};RequiresCompleteSemicircle=$true}
}

function Test-M03A2HighFrequencyCoverage {
    param([object[]]$Rows)
    if (@($Rows).Count -lt 8) {
        return [PSCustomObject]@{Status='BLOCKED_INSUFFICIENT_HIGH_FREQUENCY_COVERAGE';CandidateRows=@();CandidatePointCount=0;FrequencyMinHz=[double]::NaN;FrequencyMaxHz=[double]::NaN;FrequencySpanRatio=0;NearAxisStatus='FAIL';RealAxisSupportStatus='FAIL';InductiveArtifactStatus='NOT_ASSESSED';DuplicateFrequencyStatus='NOT_ASSESSED';FitQualityStatus='NOT_ASSESSED';ResidualMetric=[double]::NaN;CompleteSemicircleDependence='NOT_ASSESSED';Detail='fewer than 8 finite points'}
    }
    $Max=[double](($Rows|Measure-Object FrequencyHz -Maximum).Maximum);$Candidates=@($Rows|Where-Object{[double]$_.FrequencyHz-ge.1*$Max});$Min=if($Candidates.Count){[double](($Candidates|Measure-Object FrequencyHz -Minimum).Minimum)}else{[double]::NaN};$Span=if($Min-gt0){$Max/$Min}else{0};$Scale=[double](@($Candidates|ForEach-Object{[Math]::Abs([double]$_.ZRealOhm)}|Measure-Object -Average).Average);$AxisTol=[Math]::Max(.02,.02*$Scale);$MinImag=[double](@($Candidates|ForEach-Object{[Math]::Abs([double]$_.ZImagOhm)}|Measure-Object -Minimum).Minimum);$Near=$MinImag-le$AxisTol;$Cross=$false;for($I=0;$I-lt$Candidates.Count-1;$I++){if(([double]$Candidates[$I].ZImagOhm)*([double]$Candidates[$I+1].ZImagOhm)-le0){$Cross=$true}}
    $Fit=$null;try{$Fit=Get-M03A2CircleIntercept $Rows}catch{};$FitSupport=$null-ne$Fit-and$Fit.FitQualityStatus-eq'PASS';$Residual=if($null-ne$Fit){[double]$Fit.ResidualMetric}else{[double]::NaN};$FitQuality=if($null-ne$Fit){$Fit.FitQualityStatus}else{'FAILED_FIT_QUALITY'};$Sufficient=$Candidates.Count-ge5-and$Span-ge5-and$Near-and($Cross-or$FitSupport)-and$FitQuality-eq'PASS';$Status=if($Sufficient){'PASS'}else{'BLOCKED_INSUFFICIENT_HIGH_FREQUENCY_COVERAGE'}
    return [PSCustomObject]@{Status=$Status;CandidateRows=$Candidates;CandidatePointCount=$Candidates.Count;FrequencyMinHz=$Min;FrequencyMaxHz=$Max;FrequencySpanRatio=$Span;NearAxisStatus=if($Near){'PASS'}else{'FAIL_FAR_FROM_REAL_AXIS'};RealAxisSupportStatus=if($Cross){'REAL_AXIS_CROSSING'}elseif($FitSupport){'SUPPORTED_BY_DECLARED_FIT'}else{'NO_CROSSING_OR_FIT_SUPPORT'};InductiveArtifactStatus=if(@($Candidates|Where-Object{$_.HighFrequencyInductive}).Count){'WARNING_HIGH_FREQUENCY_INDUCTIVE_ARTIFACT'}else{'NONE'};DuplicateFrequencyStatus=if(@($Rows|Where-Object{$_.DuplicateFrequency}).Count){'WARNING_DUPLICATE_FREQUENCY'}else{'NONE'};FitQualityStatus=$FitQuality;ResidualMetric=$Residual;CompleteSemicircleDependence=if($null-ne$Fit-and$Fit.RequiresCompleteSemicircle){'YES_PROVISIONAL'}else{'NO'};Detail="candidate=highest frequency decade; points=$($Candidates.Count); span=$(Format-M03A2Number $Span); min_abs_Zimag=$(Format-M03A2Number $MinImag); axis_tolerance=$(Format-M03A2Number $AxisTol); no rows silently deleted"}
}

function New-M03A2HfrResult {
    param($Import,$Method,$Class,$Estimate,$U,$Rows,$Model,$Residual,$Coverage,$FitQuality,$Status,$Reason,$Detail,$Ec)
    $Selected=@($Rows);$Fmin=if($Selected.Count){[double](($Selected|Measure-Object FrequencyHz -Minimum).Minimum)}else{[double]::NaN};$Fmax=if($Selected.Count){[double](($Selected|Measure-Object FrequencyHz -Maximum).Maximum)}else{[double]::NaN};$Original=($Selected|ForEach-Object{[string]$_.OriginalRowNumber})-join';'
    return [PSCustomObject][ordered]@{CaseId=$Import.CaseId;SampleId=$Import.SampleId;ReplicateId=$Import.ReplicateId;Method=$Method;MethodClass=$Class;EstimateOhm=$Estimate;StandardUncertaintyOhm=$U;SelectedPointCount=$Selected.Count;SelectedOriginalRows=$Original;FrequencyMinHz=$Fmin;FrequencyMaxHz=$Fmax;InterceptModel=$Model;ResidualMetric=$Residual;HighFrequencyCoverageStatus=if($null-ne$Coverage){$Coverage.Status}else{'NOT_ASSESSED'};InductiveArtifactStatus=if($null-ne$Coverage){$Coverage.InductiveArtifactStatus}else{'NOT_ASSESSED'};DuplicateFrequencyStatus=if($null-ne$Coverage){$Coverage.DuplicateFrequencyStatus}else{'NOT_ASSESSED'};FitQualityStatus=$FitQuality;Status=$Status;FailureReason=$Reason;SourceFile=$Import.SourceFile;EquivalentCircuit=if($null-ne$Ec){$Ec.equivalent_circuit}else{''};FittingSoftware=if($null-ne$Ec){$Ec.fitting_software}else{''};FitFile=if($null-ne$Ec){$Ec.fit_file}else{''};RsOhm=if($null-ne$Ec){$Ec.Rs_ohm}else{''};RsStandardErrorOhm=if($null-ne$Ec){$Ec.Rs_standard_error_ohm}else{''};FitQualityMetric=if($null-ne$Ec){$Ec.fit_quality_metric}else{''};FitQualityValue=if($null-ne$Ec){$Ec.fit_quality_value}else{''};SourceReference=if($null-ne$Ec){$Ec.source_reference}else{''};ContractStatus=if($null-ne$Ec){$Ec.status}else{''};Notes=if($null-ne$Ec){$Ec.notes}else{''};Detail=$Detail}
}

function Get-M03A2HfrEstimate {
    param([object]$ImportResult,[ValidateSet('FIRST_POINT_DIAGNOSTIC','HIGH_FREQUENCY_INTERCEPT','USER_DEFINED_EQUIVALENT_CIRCUIT')][string]$Method,[object]$EquivalentCircuitContract)
    if(([string]$ImportResult.Status)-like'BLOCKED_*'){return New-M03A2HfrResult $ImportResult $Method $(if($Method-eq'FIRST_POINT_DIAGNOSTIC'){'LAB_SCREENING_HEURISTIC'}else{'HFR_ESTIMATOR'}) ([double]::NaN) ([double]::NaN) @() 'NONE' ([double]::NaN) $null 'NOT_ASSESSED' $ImportResult.Status $ImportResult.Detail 'input blocked before estimation' $EquivalentCircuitContract}
    $Rows=@($ImportResult.Rows)
    if($Method-eq'FIRST_POINT_DIAGNOSTIC'){return New-M03A2HfrResult $ImportResult $Method 'LAB_SCREENING_HEURISTIC' ([double]$Rows[0].ZRealOhm) ([double]::NaN) @($Rows[0]) 'FIRST_NORMALIZED_POINT' ([double]::NaN) $null 'NOT_APPLICABLE' 'DIAGNOSTIC_ONLY_NOT_HFR' '' 'screening only; never an HFR estimator' $null}
    $Coverage=Test-M03A2HighFrequencyCoverage $Rows;if($Coverage.Status-ne'PASS'){return New-M03A2HfrResult $ImportResult $Method 'HFR_ESTIMATOR' ([double]::NaN) ([double]::NaN) @($Coverage.CandidateRows) 'NONE' $Coverage.ResidualMetric $Coverage $Coverage.FitQualityStatus $Coverage.Status $Coverage.Detail 'candidate rows are only the declared highest-frequency decade' $EquivalentCircuitContract}
    if($Method-eq'USER_DEFINED_EQUIVALENT_CIRCUIT'){
        $Missing=New-Object Collections.Generic.List[string];foreach($Name in @('equivalent_circuit','fitting_software','fit_file','Rs_ohm','Rs_standard_error_ohm','fit_quality_metric','fit_quality_value','source_reference','status','notes')){if($null-eq$EquivalentCircuitContract-or[string]::IsNullOrWhiteSpace([string]$EquivalentCircuitContract.$Name)){$Missing.Add($Name)}}
        $Rs=[double]::NaN;$Se=[double]::NaN;$Quality=[double]::NaN;if($Missing.Count-eq0){try{$Rs=ConvertTo-M03A2Double $EquivalentCircuitContract.Rs_ohm Rs_ohm;$Se=ConvertTo-M03A2Double $EquivalentCircuitContract.Rs_standard_error_ohm Rs_standard_error_ohm;$Quality=ConvertTo-M03A2Double $EquivalentCircuitContract.fit_quality_value fit_quality_value}catch{$Missing.Add('finite_numeric_contract')}}
        $Circuit=if($null-ne$EquivalentCircuitContract){([string]$EquivalentCircuitContract.equivalent_circuit)-replace'\s',''}else{''};if($Circuit-ne'Rs+(Rct||Cdl)'){$Missing.Add('supported_circuit')};if($Rs-le0-or$Se-lt0){$Missing.Add('finite_positive_Rs_and_nonnegative_SE')};if($null-ne$EquivalentCircuitContract-and$EquivalentCircuitContract.status-ne'SUPPORTED_SYNTHETIC_RANDLES_ONLY'){$Missing.Add('status')}
        if($Missing.Count){return New-M03A2HfrResult $ImportResult $Method 'USER_DEFINED_EQUIVALENT_CIRCUIT' ([double]::NaN) ([double]::NaN) @() 'NONE' ([double]::NaN) $Coverage 'FAILED_FIT_QUALITY' 'BLOCKED_EQUIVALENT_CIRCUIT_CONTRACT' ("missing or invalid: "+(($Missing|Select-Object -Unique)-join';')) 'no callback or first-point substitution is permitted' $EquivalentCircuitContract}
        return New-M03A2HfrResult $ImportResult $Method 'USER_DEFINED_EQUIVALENT_CIRCUIT' $Rs $Se $Rows 'DECLARED_SYNTHETIC_RANDLES_RS' $Quality $Coverage 'PASS' 'SUPPORTED_SYNTHETIC_RANDLES_ONLY' '' 'Rs read from complete declared fit contract; not inferred from first point' $EquivalentCircuitContract
    }
    try{$Fit=Get-M03A2CircleIntercept $Rows;if($Fit.FitQualityStatus-ne'PASS'){throw'fit residual exceeds threshold'};$Status=if($ImportResult.CaseType-eq'RS_RCT_CPE'){'METHOD_DEPENDENT_PROVISIONAL'}elseif($ImportResult.InductiveHighFrequencyCount-gt0){'WARNING_HIGH_FREQUENCY_INDUCTIVE_ARTIFACT'}elseif($ImportResult.DuplicateCount-gt0){'WARNING_DUPLICATE_FREQUENCY'}else{'PASS'};return New-M03A2HfrResult $ImportResult $Method 'HFR_ESTIMATOR' ([double]$Fit.EstimateOhm) ([double]$Fit.StandardUncertaintyOhm) @($Fit.FitRows) $Fit.FitType ([double]$Fit.ResidualMetric) $Coverage $Fit.FitQualityStatus $Status '' $(if($Fit.RequiresCompleteSemicircle){'full-arc circle model; complete-semicircle assumption is explicit'}else{'resistive real-axis level'}) $null}catch{return New-M03A2HfrResult $ImportResult $Method 'HFR_ESTIMATOR' ([double]::NaN) ([double]::NaN) @() 'NONE' ([double]::NaN) $Coverage 'FAILED_FIT_QUALITY' 'BLOCKED_HFR_ESTIMATION_FAILED' $_.Exception.Message 'fit failed without fallback to first point' $null}
}

function Get-M03A2ReplicateConsensus {
    param([object[]]$ReplicateRows,[double]$DisagreementLimit=.10)
    $Output=New-Object Collections.Generic.List[object];foreach($Group in @($ReplicateRows|Group-Object sample_id,method)){$Rows=@($Group.Group);$Valid=@($Rows|Where-Object{Test-M03A2Finite([double]$_.hfr_ohm)});$V=@($Valid|ForEach-Object{[double]$_.hfr_ohm}|Sort-Object);$Mean=if($V.Count){[double](($V|Measure-Object -Average).Average)}else{[double]::NaN};$Median=if($V.Count%2){$V[[int](($V.Count-1)/2)]}elseif($V.Count){.5*($V[$V.Count/2-1]+$V[$V.Count/2])}else{[double]::NaN};$SS=0.0;foreach($X in $V){$SS+=($X-$Mean)*($X-$Mean)};$Sd=if($V.Count-gt1){[Math]::Sqrt($SS/($V.Count-1))}else{0.0};$Min=if($V.Count){$V[0]}else{[double]::NaN};$Max=if($V.Count){$V[-1]}else{[double]::NaN};$Range=if($V.Count){($Max-$Min)/[Math]::Max([Math]::Abs($Mean),1e-30)}else{[double]::NaN};$Status=if($V.Count-ne$Rows.Count){'BLOCKED_INVALID_REPLICATE'}elseif($Range-gt$DisagreementLimit){'REPLICATE_DISAGREEMENT'}else{'PASS'};$Output.Add([PSCustomObject][ordered]@{sample_id=$Rows[0].sample_id;method=$Rows[0].method;replicate_count=$Rows.Count;valid_replicate_count=$V.Count;mean_HFR_ohm=Format-M03A2Number $Mean;median_HFR_ohm=Format-M03A2Number $Median;standard_deviation_ohm=Format-M03A2Number $Sd;coefficient_of_variation=Format-M03A2Number $(if($Mean-ne0){$Sd/[Math]::Abs($Mean)}else{[double]::NaN});minimum_HFR_ohm=Format-M03A2Number $Min;maximum_HFR_ohm=Format-M03A2Number $Max;relative_range=Format-M03A2Number $Range;consensus_status=$Status;failure_reason=if($Status-eq'PASS'){''}elseif($Status-eq'REPLICATE_DISAGREEMENT'){'relative range exceeds declared 0.10 threshold; no replicate removed'}else{'one or more invalid replicate estimates'}})};return @($Output|ForEach-Object{$_})
}

function Get-M03A2ResistanceLedger {
    param([string]$SampleId,[string]$CaseId,[object]$HfrTotalOhm,[object]$HfrUncertaintyOhm,[hashtable]$SeriesComponents)
    $Names=@('fixture','contact','membrane','other_series');$Values=@{};$Us=@{};$Failure='';foreach($Name in $Names){if(-not$SeriesComponents.ContainsKey($Name)-or$null-eq$SeriesComponents[$Name]){$Failure="missing required series component: $Name";break};$C=$SeriesComponents[$Name];$Source=[string]$C.source;$Status=[string]$C.status;if([string]::IsNullOrWhiteSpace($Source)){$Failure="missing source for $Name";break};if($Status-eq'NOT_APPLICABLE'){$Values[$Name]=0.0;$Us[$Name]=0.0;continue};try{$V=ConvertTo-M03A2Double $C.value_ohm "$Name resistance";$U=ConvertTo-M03A2Double $C.uncertainty_ohm "$Name uncertainty"}catch{$Failure=$_.Exception.Message;break};if($V-lt0-or$U-lt0){$Failure="negative value or uncertainty for $Name";break};if($V-eq0-and$Status-ne'MEASURED_ZERO'){$Failure="zero $Name requires MEASURED_ZERO evidence status";break};$Values[$Name]=$V;$Us[$Name]=$U}
    $Total=[double]::NaN;$TotalU=[double]::NaN;try{$Total=ConvertTo-M03A2Double $HfrTotalOhm HFR_total;$TotalU=ConvertTo-M03A2Double $HfrUncertaintyOhm HFR_uncertainty}catch{$Failure=$_.Exception.Message};if($TotalU-lt0){$Failure='negative HFR uncertainty'}
    $Get={param($Name,$Field) if($SeriesComponents.ContainsKey($Name)-and$null-ne$SeriesComponents[$Name]){return $SeriesComponents[$Name].$Field};return ''};$Sum=if($Failure){[double]::NaN}else{[double]($Values.Values|Measure-Object -Sum).Sum};$R=$Total-$Sum;$Var=$TotalU*$TotalU;if(-not$Failure){foreach($U in $Us.Values){$Var+=$U*$U}};$RU=[Math]::Sqrt($Var);$Status=if($Failure){'BLOCKED_MISSING_SERIES_COMPONENT'}elseif($R-lt-1e-12){'BLOCKED_NEGATIVE_DEEMBEDDED_RESISTANCE'}elseif($R-le1e-12){'BLOCKED_NONPOSITIVE_DEEMBEDDED_RESISTANCE'}else{'PASS'};if(-not$Failure-and$Status-ne'PASS'){$Failure='nonpositive R_electrolyte cannot enter conductivity inversion'}
    $FixtureSource=$Get.Invoke('fixture','source');$FixtureStatus=$Get.Invoke('fixture','status');$ContactSource=$Get.Invoke('contact','source');$ContactStatus=$Get.Invoke('contact','status');$MembraneSource=$Get.Invoke('membrane','source');$MembraneStatus=$Get.Invoke('membrane','status');$OtherSource=$Get.Invoke('other_series','source');$OtherStatus=$Get.Invoke('other_series','status')
    return [PSCustomObject][ordered]@{sample_id=$SampleId;case_id=$CaseId;EIS_HFR_total_ohm=if(Test-M03A2Finite $Total){Format-M03A2Number $Total}else{''};EIS_HFR_uncertainty_ohm=if(Test-M03A2Finite $TotalU){Format-M03A2Number $TotalU}else{''};fixture_resistance_ohm=if($Values.ContainsKey('fixture')){Format-M03A2Number $Values.fixture}else{''};fixture_uncertainty_ohm=if($Us.ContainsKey('fixture')){Format-M03A2Number $Us.fixture}else{''};fixture_source=$FixtureSource;fixture_status=$FixtureStatus;contact_resistance_ohm=if($Values.ContainsKey('contact')){Format-M03A2Number $Values.contact}else{''};contact_uncertainty_ohm=if($Us.ContainsKey('contact')){Format-M03A2Number $Us.contact}else{''};contact_source=$ContactSource;contact_status=$ContactStatus;membrane_resistance_ohm=if($Values.ContainsKey('membrane')){Format-M03A2Number $Values.membrane}else{''};membrane_uncertainty_ohm=if($Us.ContainsKey('membrane')){Format-M03A2Number $Us.membrane}else{''};membrane_source=$MembraneSource;membrane_status=$MembraneStatus;other_series_resistance_ohm=if($Values.ContainsKey('other_series')){Format-M03A2Number $Values.other_series}else{''};other_series_uncertainty_ohm=if($Us.ContainsKey('other_series')){Format-M03A2Number $Us.other_series}else{''};other_series_source=$OtherSource;other_series_status=$OtherStatus;R_non_electrolyte_ohm=if(Test-M03A2Finite $Sum){Format-M03A2Number $Sum}else{''};R_electrolyte_ohm=if(Test-M03A2Finite $R){Format-M03A2Number $R}else{''};R_electrolyte_uncertainty_ohm=if(Test-M03A2Finite $RU){Format-M03A2Number $RU}else{''};status=$Status;failure_reason=$Failure}
}

function Get-M03A2Conductivity { param([double]$ThicknessM,[double]$AreaM2,[double]$ElectrolyteResistanceOhm) foreach($V in @($ThicknessM,$AreaM2,$ElectrolyteResistanceOhm)){if(-not(Test-M03A2Finite $V)-or$V-le0){throw'conductivity inputs must be finite and positive'}};return $ThicknessM/($AreaM2*$ElectrolyteResistanceOhm) }
function Compare-M03A2Conductivity { param([double]$DirectSPerM,[double]$HfrSPerM,[double]$Limit=.10) $D=[Math]::Abs($DirectSPerM-$HfrSPerM)/[Math]::Max(.5*([Math]::Abs($DirectSPerM)+[Math]::Abs($HfrSPerM)),1e-30);return [PSCustomObject]@{relative_difference=$D;status=if($D-gt$Limit){'CALIBRATION_CONFLICT'}else{'PASS'}} }
function Get-M03A2AnalyticalUncertainty { param([double]$HfrTotalOhm,[double]$HfrUOhm,[double[]]$SeriesOhm,[double[]]$SeriesUOhm,[double]$ThicknessM,[double]$ThicknessUM,[double]$AreaM2,[double]$AreaUM2) $R=$HfrTotalOhm;foreach($V in $SeriesOhm){$R-=$V};if($R-le0){throw'nonpositive de-embedded resistance'};$Var=$HfrUOhm*$HfrUOhm;foreach($U in $SeriesUOhm){if($U-lt0){throw'negative uncertainty'};$Var+=$U*$U};$RU=[Math]::Sqrt($Var);$K=Get-M03A2Conductivity $ThicknessM $AreaM2 $R;$KU=$K*[Math]::Sqrt(($ThicknessUM/$ThicknessM)*($ThicknessUM/$ThicknessM)+($AreaUM2/$AreaM2)*($AreaUM2/$AreaM2)+($RU/$R)*($RU/$R));return [PSCustomObject]@{R_electrolyte_ohm=$R;R_electrolyte_uncertainty_ohm=$RU;kappa_S_m=$K;kappa_uncertainty_S_m=$KU;status='PASS'} }

function Invoke-M03A2MonteCarlo {
    param([hashtable]$Means,[hashtable]$StandardUncertainties,[int]$Seed=314159,[int]$SampleCount=20000,[double]$RejectedFractionLimit=.01)
    $Names=@('hfr','fixture','contact','membrane','other','thickness','area');$Random=New-Object Random($Seed);$Accepted=New-Object Collections.Generic.List[double];$Rejected=0
    for($I=0;$I-lt$SampleCount;$I++){$S=@{};foreach($N in $Names){$S[$N]=[double]$Means[$N]+[double]$StandardUncertainties[$N]*(Get-M03A2NextNormal $Random)};$R=$S.hfr-$S.fixture-$S.contact-$S.membrane-$S.other;if($S.hfr-le0-or$S.fixture-lt0-or$S.contact-lt0-or$S.membrane-lt0-or$S.other-lt0-or$S.thickness-le0-or$S.area-le0-or$R-le0){$Rejected++;continue};$Accepted.Add($S.thickness/($S.area*$R))}
    if($Accepted.Count-eq0){return [PSCustomObject]@{MC_seed=$Seed;MC_sample_count=$SampleCount;MC_accepted_count=0;MC_rejected_count=$Rejected;MC_rejected_fraction=1.0;mean=[double]::NaN;std=[double]::NaN;status='FAILED_INPUT_DISTRIBUTION'}};$Mean=[double](($Accepted|Measure-Object -Average).Average);$SS=0.0;foreach($V in $Accepted){$SS+=($V-$Mean)*($V-$Mean)};$Sd=[Math]::Sqrt($SS/[Math]::Max(1,$Accepted.Count-1));$Frac=$Rejected/[double]$SampleCount;return [PSCustomObject]@{MC_seed=$Seed;MC_sample_count=$SampleCount;MC_accepted_count=$Accepted.Count;MC_rejected_count=$Rejected;MC_rejected_fraction=$Frac;mean=$Mean;std=$Sd;status=if($Frac-gt$RejectedFractionLimit){'FAILED_INPUT_DISTRIBUTION'}else{'PASS'}}
}

function Invoke-M03A2AreaAudit {
    param([object[]]$Rows)
    $Required=@('geometric_electrode_area','actual_wetted_area','catalytic_layer_projected_area','GDE_exposed_area','EIS_area','current_density_reporting_area');$Output=New-Object Collections.Generic.List[object]
    foreach($Group in @($Rows|Group-Object sample_id,case_id)){$G=@($Group.Group);$Names=@($G.area_name);$Overall='PASS';$Reason='';foreach($Name in $Required){if(@($G|Where-Object area_name -ceq $Name).Count-ne1){$Overall='EXPERIMENTAL_INPUT_INCOMPLETE';$Reason="missing or duplicate area definition: $Name"}};foreach($R in $G){if([string]::IsNullOrWhiteSpace([string]$R.value_m2)-or[string]::IsNullOrWhiteSpace([string]$R.uncertainty_m2)-or[string]::IsNullOrWhiteSpace([string]$R.source_reference)){$Overall='EXPERIMENTAL_INPUT_INCOMPLETE';$Reason='area value uncertainty or provenance missing'}}
        if($Overall-eq'PASS'){$E=@($G|Where-Object area_name -ceq 'EIS_area')[0];$J=@($G|Where-Object area_name -ceq 'current_density_reporting_area')[0];$Complete=(-not[string]::IsNullOrWhiteSpace([string]$E.mapping_reference))-and($E.mapping_reference-eq$J.mapping_reference)-and(-not[string]::IsNullOrWhiteSpace([string]$E.mapping_formula))-and(-not[string]::IsNullOrWhiteSpace([string]$J.mapping_formula))-and(-not[string]::IsNullOrWhiteSpace([string]$E.mapping_uncertainty_m2))-and(-not[string]::IsNullOrWhiteSpace([string]$J.mapping_uncertainty_m2));if(-not$Complete){$Overall='AREA_BASIS_MISMATCH';$Reason='EIS and current-density areas lack complete source-backed formula and mapping uncertainty'}}
        foreach($R in $G){$Output.Add([PSCustomObject][ordered]@{sample_id=$R.sample_id;case_id=$R.case_id;area_name=$R.area_name;value_m2=$R.value_m2;uncertainty_m2=$R.uncertainty_m2;status=$Overall;source_type=$R.source_type;source_reference=$R.source_reference;definition=$R.definition;measurement_method=$R.measurement_method;mapping_reference=$R.mapping_reference;notes=$R.notes;mapping_formula=$R.mapping_formula;mapping_uncertainty_m2=$R.mapping_uncertainty_m2;failure_reason=$Reason})}
    };return @($Output|ForEach-Object{$_})
}

function New-M03A2Plot {
    param([ValidateSet('NYQUIST','HFR_RECOVERY','REPLICATES','LEDGER')][string]$Kind,[object[]]$Rows,[string]$Path)
    Add-Type -AssemblyName System.Drawing;$B=New-Object Drawing.Bitmap(1200,720);$G=[Drawing.Graphics]::FromImage($B);$Title=New-Object Drawing.Font('Arial',[single]18,[Drawing.FontStyle]::Bold);$Font=New-Object Drawing.Font('Arial',[single]10)
    try{$G.Clear([Drawing.Color]::White);$G.DrawString("M03A.2 $Kind formal synthetic contract",$Title,[Drawing.Brushes]::Black,300,25);$G.DrawRectangle([Drawing.Pens]::Black,100,90,1000,520);if($Kind-eq'NYQUIST'){$X=@($Rows|ForEach-Object{[double]$_.ZRealOhm});$Y=@($Rows|ForEach-Object{-[double]$_.ZImagOhm});$xmin=[double](($X|Measure-Object -Minimum).Minimum);$xmax=[double](($X|Measure-Object -Maximum).Maximum);$ymin=[double](($Y|Measure-Object -Minimum).Minimum);$ymax=[double](($Y|Measure-Object -Maximum).Maximum);if($xmax-eq$xmin){$xmax++};if($ymax-eq$ymin){$ymax++};foreach($R in $Rows){$px=100+1000*([double]$R.ZRealOhm-$xmin)/($xmax-$xmin);$py=610-520*(-[double]$R.ZImagOhm-$ymin)/($ymax-$ymin);$G.FillEllipse([Drawing.Brushes]::SteelBlue,[single]($px-3),[single]($py-3),6,6)}}else{$Max=[Math]::Max([double](($Rows|Measure-Object value -Maximum).Maximum),1e-12);for($I=0;$I-lt$Rows.Count;$I++){$Y=110+$I*[Math]::Min(70,450/[Math]::Max(1,$Rows.Count));$G.DrawString([string]$Rows[$I].label,$Font,[Drawing.Brushes]::Black,110,$Y);$G.FillRectangle([Drawing.Brushes]::DarkCyan,300,$Y,[int](700*[double]$Rows[$I].value/$Max),25)}};$G.DrawString('SYNTHETIC_SMOKE_TEST | PROVISIONAL | M03B_READY=FALSE',$Font,[Drawing.Brushes]::Firebrick,390,680);$B.Save($Path,[Drawing.Imaging.ImageFormat]::Png)}finally{$G.Dispose();$B.Dispose();$Title.Dispose();$Font.Dispose()}
}

Export-ModuleMember -Function @('Test-M03A2Finite','ConvertTo-M03A2Double','Format-M03A2Number','Write-M03A2Utf8NoBomText','Write-M03A2Utf8NoBomCsv','Convert-M03A2FrequencyToHz','Convert-M03A2ImpedanceToOhm','New-M03A2SyntheticFixtures','Import-M03A2Eis','Test-M03A2HighFrequencyCoverage','Get-M03A2HfrEstimate','Get-M03A2ReplicateConsensus','Get-M03A2ResistanceLedger','Get-M03A2Conductivity','Compare-M03A2Conductivity','Get-M03A2AnalyticalUncertainty','Invoke-M03A2MonteCarlo','Invoke-M03A2AreaAudit','New-M03A2Plot')
