Set-StrictMode -Version Latest

$script:Invariant = [Globalization.CultureInfo]::InvariantCulture
$script:RequiredSeries = @('fixture','contact','membrane','other_series')
$script:RequiredAreas = @('electrode_spacing','out_of_plane_depth','geometric_electrode_area','EIS_area','current_density_reporting_area')
$script:ExperimentalHfrStatuses = @('ACCEPTED','ACCEPTED_WITH_RETAINED_DIAGNOSTIC')
$script:SyntheticHfrStatuses = @('ACCEPTED_SYNTHETIC','ACCEPTED_SYNTHETIC_WITH_RETAINED_DIAGNOSTIC')
$script:HfrMethods = @('HIGH_FREQUENCY_INTERCEPT_REVIEWED','EQUIVALENT_CIRCUIT_FIT_REVIEWED')
$script:CellConstantUnits = @('1/m','m^-1')

function Test-M03A5Finite {
    param([double]$Value)
    return (-not [double]::IsNaN($Value)) -and (-not [double]::IsInfinity($Value))
}

function ConvertTo-M03A5Double {
    param([AllowEmptyString()][string]$Value,[string]$Name)
    if ([string]::IsNullOrWhiteSpace($Value)) { throw "BLOCKED_REQUIRED_NUMERIC: $Name is blank" }
    $Number=0.0
    if (-not [double]::TryParse($Value,[Globalization.NumberStyles]::Float,$script:Invariant,[ref]$Number)){throw "BLOCKED_INVALID_NUMERIC: $Name is not an InvariantCulture number"}
    if (-not (Test-M03A5Finite $Number)){throw "BLOCKED_INVALID_NUMERIC: $Name is NaN or Infinity"}
    return $Number
}

function ConvertTo-M03A5Int64Exact {
    param([AllowEmptyString()][string]$Value,[string]$Name,[switch]$Nonnegative)
    if ([string]::IsNullOrWhiteSpace($Value)){throw "BLOCKED_REQUIRED_INTEGER: $Name is blank"}
    $Number=[long]0
    if (-not [long]::TryParse($Value,[Globalization.NumberStyles]::Integer,$script:Invariant,[ref]$Number)){throw "BLOCKED_INVALID_INT64: $Name must be an exact Int64 integer"}
    if ($Nonnegative -and $Number -lt 0){throw "BLOCKED_INVALID_INT64: $Name must be nonnegative"}
    return $Number
}

function ConvertTo-M03A5PositiveInt32Exact {
    param([AllowEmptyString()][string]$Value,[string]$Name)
    if ([string]::IsNullOrWhiteSpace($Value)){throw "BLOCKED_REQUIRED_INTEGER: $Name is blank"}
    $Number=0
    if (-not [int]::TryParse($Value,[Globalization.NumberStyles]::Integer,$script:Invariant,[ref]$Number) -or $Number -le 0){throw "BLOCKED_INVALID_POSITIVE_INTEGER: $Name must be a strictly positive Int32 integer"}
    return $Number
}

function ConvertTo-M03A5DateTimeOffsetStrict {
    param([string]$Value,[string]$Name)
    if ([string]::IsNullOrWhiteSpace($Value) -or $Value -notmatch '(Z|[+-][0-9]{2}:[0-9]{2})$'){throw "BLOCKED_INVALID_DATETIME: $Name must include an explicit timezone"}
    $Parsed=[DateTimeOffset]::MinValue
    if ($Value.EndsWith('Z',[StringComparison]::Ordinal)){
        [string[]]$Formats=@("yyyy-MM-dd'T'HH:mm:ss'Z'","yyyy-MM-dd'T'HH:mm:ss.FFFFFFF'Z'")
        $Styles=[Globalization.DateTimeStyles]::AssumeUniversal -bor [Globalization.DateTimeStyles]::AdjustToUniversal
    } else {
        [string[]]$Formats=@("yyyy-MM-dd'T'HH:mm:sszzz","yyyy-MM-dd'T'HH:mm:ss.FFFFFFFzzz")
        $Styles=[Globalization.DateTimeStyles]::None
    }
    if (-not [DateTimeOffset]::TryParseExact($Value,$Formats,$script:Invariant,$Styles,[ref]$Parsed)){throw "BLOCKED_INVALID_DATETIME: $Name is not strict ISO-8601"}
    return $Parsed
}

function Format-M03A5Number {
    param([double]$Value)
    if ([double]::IsNaN($Value)){return 'NaN'}
    if ([double]::IsPositiveInfinity($Value)){return 'Infinity'}
    if ([double]::IsNegativeInfinity($Value)){return ' -Infinity'}
    return $Value.ToString('G17',$script:Invariant)
}

function Write-M03A5Utf8NoBomText {
    param([string]$Path,[string]$Text)
    $Parent=Split-Path -Parent $Path
    if ($Parent -and -not (Test-Path -LiteralPath $Parent)){New-Item -ItemType Directory -Path $Parent -Force|Out-Null}
    [IO.File]::WriteAllText($Path,$Text,[Text.UTF8Encoding]::new($false))
}

function Write-M03A5Utf8NoBomCsv {
    param([object[]]$Rows,[string]$Path)
    if (@($Rows).Count -eq 0){throw "Cannot write empty CSV: $Path"}
    Write-M03A5Utf8NoBomText $Path (((@($Rows)|ConvertTo-Csv -NoTypeInformation)-join"`r`n")+"`r`n")
}

function Get-M03A5Sha256 {param([string]$Path);return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()}

function Assert-M03A5Rfc4180Utf8NoBom {
    param([string]$Path)
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)){throw "CSV missing: $Path"}
    $Bytes=[IO.File]::ReadAllBytes($Path)
    if ($Bytes.Length -ge 3 -and $Bytes[0] -eq 0xef -and $Bytes[1] -eq 0xbb -and $Bytes[2] -eq 0xbf){throw "UTF8_BOM_FORBIDDEN: $Path"}
    try{$Text=[Text.UTF8Encoding]::new($false,$true).GetString($Bytes)}catch{throw "INVALID_UTF8: $Path"}
    if ($Text.Length -eq 0){throw "RFC4180_EMPTY_CSV: $Path"}
    if ([regex]::IsMatch($Text,"(?<!`r)`n") -or [regex]::IsMatch($Text,"`r(?!`n)")){throw "RFC4180_REQUIRES_CRLF: $Path"}
    $Counts=New-Object Collections.Generic.List[int];$Fields=1;$InQuote=$false;$AtStart=$true;$AfterQuote=$false
    for ($i=0;$i -lt $Text.Length;$i++){
        $C=$Text[$i]
        if ($InQuote){if ($C -eq '"'){if ($i+1 -lt $Text.Length -and $Text[$i+1] -eq '"'){$i++}else{$InQuote=$false;$AfterQuote=$true}};continue}
        if ($AfterQuote){if ($C -eq ','){$Fields++;$AtStart=$true;$AfterQuote=$false;continue};if ($C -eq "`r"){if ($i+1 -ge $Text.Length -or $Text[$i+1] -ne "`n"){throw "RFC4180_BAD_RECORD_END: $Path"};$Counts.Add($Fields);$Fields=1;$AtStart=$true;$AfterQuote=$false;$i++;continue};throw "RFC4180_CHAR_AFTER_QUOTE: $Path"}
        if ($C -eq '"'){if (-not $AtStart){throw "RFC4180_QUOTE_IN_UNQUOTED_FIELD: $Path"};$InQuote=$true;$AtStart=$false;continue}
        if ($C -eq ','){$Fields++;$AtStart=$true;continue}
        if ($C -eq "`r"){if ($i+1 -ge $Text.Length -or $Text[$i+1] -ne "`n"){throw "RFC4180_BAD_RECORD_END: $Path"};$Counts.Add($Fields);$Fields=1;$AtStart=$true;$i++;continue}
        $AtStart=$false
    }
    if ($InQuote){throw "RFC4180_UNCLOSED_QUOTE: $Path"}
    if (-not $AtStart -or $AfterQuote -or $Fields -ne 1){$Counts.Add($Fields)}
    if ($Counts.Count -lt 2){throw "RFC4180_HEADER_OR_DATA_MISSING: $Path"}
    foreach ($Count in $Counts){if ($Count -ne $Counts[0]){throw "RFC4180_COLUMN_COUNT_MISMATCH: $Path"}}
    return $true
}

function Import-M03A5CsvStrict {
    param([string]$Path,[string[]]$RequiredColumns)
    Assert-M03A5Rfc4180Utf8NoBom $Path|Out-Null
    $Rows=@(Import-Csv -LiteralPath $Path -Encoding UTF8)
    if ($Rows.Count -eq 0){throw "CSV_DATA_ROW_REQUIRED: $Path"}
    $Header=@($Rows[0].PSObject.Properties.Name)
    foreach ($Name in $RequiredColumns){if ($Header -notcontains $Name){throw "CSV_SCHEMA_MISSING_COLUMN: $Name in $Path"}}
    if (@($Header|Group-Object|Where-Object Count -gt 1).Count){throw "CSV_SCHEMA_DUPLICATE_HEADER: $Path"}
    return $Rows
}

function New-M03A5Finding {param([string]$Section,[string]$Item,[string]$Code,[string]$State,[string]$Detail);return [pscustomobject][ordered]@{section=$Section;item=$Item;code=$Code;state=$State;detail=$Detail}}
function Add-M03A5Finding {param([Collections.Generic.List[object]]$List,[string]$Section,[string]$Item,[string]$Code,[string]$State,[string]$Detail);$List.Add((New-M03A5Finding $Section $Item $Code $State $Detail))}

function Add-M03A5Completeness {
    param([Collections.Generic.List[object]]$Completeness,[Collections.Generic.List[object]]$Missing,[string]$Source,[string]$Record,[object]$Row,[string[]]$Fields)
    foreach ($Field in $Fields){$Value=[string]$Row.$Field;$State=if ([string]::IsNullOrWhiteSpace($Value)){'MISSING'}else{'PRESENT'};$Completeness.Add([pscustomobject][ordered]@{source_file=$Source;record_id=$Record;field_name=$Field;required='TRUE';state=$State;detail=if ($State -eq 'MISSING'){'user must supply this field'}else{''}});if ($State -eq 'MISSING'){$Missing.Add([pscustomobject][ordered]@{source_file=$Source;record_id=$Record;field_name=$Field;required_action="Collect or declare $Field";state='MISSING'})}}
}

function Get-M03A5ThresholdMap {
    param([object[]]$Rows)
    $Required=@('minimum_accepted_eis_replicates','direct_vs_hfr_relative_difference_limit','monte_carlo_minimum_samples','monte_carlo_nonphysical_rejection_limit','electrolyte_resistance_minimum_exclusive','series_resistance_minimum_inclusive','hfr_resistance_minimum_exclusive','electrode_spacing_minimum_exclusive','out_of_plane_depth_minimum_exclusive','required_area_minimum_exclusive','conductivity_minimum_exclusive','source_sha256_required','experimental_metadata_completeness_required','replicate_temperature_tolerance_K','replicate_numeric_relative_tolerance','hfr_replicate_relative_range_limit','minimum_normalized_eis_rows','minimum_high_frequency_span_ratio','high_frequency_window_ratio','frequency_metadata_relative_tolerance','points_per_decade_minimum_fraction','area_consistency_relative_tolerance','direct_temperature_tolerance_K')
    $Map=@{}
    foreach ($Row in $Rows){$Name=[string]$Row.threshold_name;if ($Map.ContainsKey($Name)){throw "DUPLICATE_THRESHOLD: $Name"};$Map[$Name]=ConvertTo-M03A5Double ([string]$Row.value) "threshold:$Name"}
    foreach ($Name in $Required){if (-not $Map.ContainsKey($Name)){throw "MISSING_THRESHOLD: $Name"}}
    foreach ($Name in @('minimum_accepted_eis_replicates','monte_carlo_minimum_samples','minimum_normalized_eis_rows')){[void](ConvertTo-M03A5PositiveInt32Exact ([string](@($Rows|Where-Object threshold_name -eq $Name)[0].value)) "threshold:$Name")}
    foreach ($Name in @('direct_vs_hfr_relative_difference_limit','monte_carlo_nonphysical_rejection_limit','replicate_numeric_relative_tolerance','hfr_replicate_relative_range_limit','frequency_metadata_relative_tolerance','area_consistency_relative_tolerance')){if ($Map[$Name] -lt 0 -or $Map[$Name] -gt 1){throw "INVALID_THRESHOLD_FRACTION: $Name"}}
    foreach ($Name in @('electrolyte_resistance_minimum_exclusive','series_resistance_minimum_inclusive','hfr_resistance_minimum_exclusive','electrode_spacing_minimum_exclusive','out_of_plane_depth_minimum_exclusive','required_area_minimum_exclusive','conductivity_minimum_exclusive','replicate_temperature_tolerance_K','direct_temperature_tolerance_K')){if ($Map[$Name] -lt 0){throw "INVALID_NONNEGATIVE_THRESHOLD: $Name"}}
    foreach ($Name in @('minimum_high_frequency_span_ratio','high_frequency_window_ratio','points_per_decade_minimum_fraction')){if ($Map[$Name] -le 0){throw "INVALID_POSITIVE_THRESHOLD: $Name"}}
    foreach ($Name in @('source_sha256_required','experimental_metadata_completeness_required')){if ($Map[$Name] -notin @(0.0,1.0)){throw "INVALID_BOOLEAN_THRESHOLD: $Name"}}
    return $Map
}

function Resolve-M03A5EvidencePath {
    param([string]$ProjectRoot,[string]$RelativePath)
    if ([string]::IsNullOrWhiteSpace($RelativePath) -or [IO.Path]::IsPathRooted($RelativePath)){return [pscustomobject]@{passed=$false;code='BLOCKED_EVIDENCE_PATH';full_path='';detail='path must be repository-relative'}}
    $Root=[IO.Path]::GetFullPath($ProjectRoot).TrimEnd('\','/');$Full=[IO.Path]::GetFullPath((Join-Path $Root $RelativePath.Replace('/','\')))
    if (-not $Full.StartsWith($Root+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){return [pscustomobject]@{passed=$false;code='BLOCKED_EVIDENCE_PATH';full_path=$Full;detail='path escapes repository root'}}
    if (-not (Test-Path -LiteralPath $Full -PathType Leaf)){return [pscustomobject]@{passed=$false;code='BLOCKED_RAW_FILE_MISSING';full_path=$Full;detail='evidence file is missing'}}
    $CursorPath=$Root
    $RelativeResolved=$Full.Substring($Root.Length).TrimStart('\','/')
    foreach ($Segment in $RelativeResolved.Split(@('\','/'),[StringSplitOptions]::RemoveEmptyEntries)){
        $CursorPath=Join-Path $CursorPath $Segment
        $Cursor=Get-Item -LiteralPath $CursorPath -Force
        if (($Cursor.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0){return [pscustomobject]@{passed=$false;code='BLOCKED_EVIDENCE_REPARSE_POINT';full_path=$Full;detail="reparse point found: $($Cursor.FullName)"}}
    }
    return [pscustomobject]@{passed=$true;code='PASS';full_path=$Full;detail='repository-relative lexical and reparse audit passed'}
}

function Test-M03A5Evidence {
    param([Collections.Generic.List[object]]$Findings,[Collections.Generic.List[object]]$Provenance,[string]$ProjectRoot,[string]$Record,[string]$Role,[string]$PathToken,[string]$SizeToken,[string]$HashToken,[bool]$MissingRow,[double]$HashRequired)
    if ($MissingRow){$Provenance.Add([pscustomobject][ordered]@{record_id=$Record;evidence_role=$Role;path=$PathToken;declared_size_bytes=$SizeToken;actual_size_bytes='';declared_sha256=$HashToken;actual_sha256='';state='MISSING';detail='experimental evidence not supplied'});return [pscustomobject]@{passed=$false;full_path='';code='MISSING'}}
    $Resolved=Resolve-M03A5EvidencePath $ProjectRoot $PathToken
    if (-not $Resolved.passed){Add-M03A5Finding $Findings 'PROVENANCE' "$Record/$Role" $Resolved.code 'BLOCKED' $Resolved.detail;$Provenance.Add([pscustomobject][ordered]@{record_id=$Record;evidence_role=$Role;path=$PathToken;declared_size_bytes=$SizeToken;actual_size_bytes='';declared_sha256=$HashToken;actual_sha256='';state='BLOCKED';detail=$Resolved.detail});return [pscustomobject]@{passed=$false;full_path=$Resolved.full_path;code=$Resolved.code}}
    $ActualSize=[long](Get-Item -LiteralPath $Resolved.full_path).Length;$ActualHash=Get-M03A5Sha256 $Resolved.full_path;$Passed=$true;$Details=New-Object Collections.Generic.List[string]
    try{$DeclaredSize=ConvertTo-M03A5Int64Exact $SizeToken "$Record/$Role size" -Nonnegative}catch{Add-M03A5Finding $Findings 'PROVENANCE' "$Record/$Role" $(if ($_.Exception.Message -like 'BLOCKED_REQUIRED*'){'BLOCKED_REQUIRED_INTEGER'}else{'BLOCKED_INVALID_INT64'}) 'BLOCKED' $_.Exception.Message;$DeclaredSize=[long]-1;$Passed=$false;$Details.Add($_.Exception.Message)}
    if ($DeclaredSize -ne $ActualSize){Add-M03A5Finding $Findings 'PROVENANCE' "$Record/$Role" 'BLOCKED_RAW_SIZE_MISMATCH' 'BLOCKED' "declared=$DeclaredSize actual=$ActualSize";$Passed=$false;$Details.Add('size mismatch')}
    if ($HashRequired -ge 1){if ($HashToken -notmatch '^[0-9A-Fa-f]{64}$'){Add-M03A5Finding $Findings 'PROVENANCE' "$Record/$Role" 'BLOCKED_SOURCE_SHA256' 'BLOCKED' 'SHA-256 missing or malformed';$Passed=$false;$Details.Add('hash missing or malformed')}elseif ($HashToken.ToLowerInvariant() -ne $ActualHash){Add-M03A5Finding $Findings 'PROVENANCE' "$Record/$Role" 'BLOCKED_RAW_HASH_MISMATCH' 'BLOCKED' 'declared SHA-256 does not match evidence';$Passed=$false;$Details.Add('hash mismatch')}}
    $Provenance.Add([pscustomobject][ordered]@{record_id=$Record;evidence_role=$Role;path=$PathToken;declared_size_bytes=$SizeToken;actual_size_bytes=$ActualSize;declared_sha256=$HashToken;actual_sha256=$ActualHash;state=if ($Passed){'PASS'}else{'BLOCKED'};detail=($Details-join'; ')})
    return [pscustomobject]@{passed=$Passed;full_path=$Resolved.full_path;code=if ($Passed){'PASS'}else{'BLOCKED_PROVENANCE'}}
}

function Get-M03A5RelativeDifference {param([double]$A,[double]$B);$Scale=[Math]::Max([Math]::Abs($A),[Math]::Abs($B));if ($Scale -eq 0){return 0.0};return [math]::Abs($A-$B)/$Scale}

function Test-M03A5NormalizedEis {
    param([Collections.Generic.List[object]]$Findings,[string]$Path,[string]$Record,[object]$ManifestRow,[hashtable]$Thresholds)
    $Codes=New-Object Collections.Generic.List[string];$Parsed=New-Object Collections.Generic.List[object]
    try{$Rows=@(Import-M03A5CsvStrict $Path @('frequency','z_real','z_imag'))}catch{Add-M03A5Finding $Findings 'EIS' $Record 'BLOCKED_NORMALIZED_EIS_FORMAT' 'BLOCKED' $_.Exception.Message;$Codes.Add('BLOCKED_NORMALIZED_EIS_FORMAT');return [pscustomobject]@{actual_row_count=0;actual_frequency_min_Hz='';actual_frequency_max_Hz='';actual_average_points_per_decade='';duplicate_frequency_count=0;inductive_HF_count=0;high_frequency_span_ratio='';metadata_match=$false;hfr_eligible=$false;blocking_codes='BLOCKED_NORMALIZED_EIS_FORMAT'}}
    for ($i=0;$i -lt $Rows.Count;$i++){try{$F=ConvertTo-M03A5Double ([string]$Rows[$i].frequency) "$Record frequency row $($i+2)";$Zr=ConvertTo-M03A5Double ([string]$Rows[$i].z_real) "$Record z_real row $($i+2)";$Zi=ConvertTo-M03A5Double ([string]$Rows[$i].z_imag) "$Record z_imag row $($i+2)";if ($F -le 0){throw 'frequency must be positive'};$Parsed.Add([pscustomobject]@{frequency=$F;z_real=$Zr;z_imag=$Zi;original_row=$i+2})}catch{Add-M03A5Finding $Findings 'EIS' $Record 'BLOCKED_INVALID_NUMERIC' 'BLOCKED' $_.Exception.Message;$Codes.Add('BLOCKED_INVALID_NUMERIC')}}
    $RowCount=$Parsed.Count;$Min=[double]::NaN;$Max=[double]::NaN;$Average=[double]::NaN;$Span=[double]::NaN;$DuplicateCount=0;$Inductive=0;$Metadata=$true
    if ($RowCount -lt [int]$Thresholds.minimum_normalized_eis_rows){$Codes.Add('BLOCKED_INSUFFICIENT_HIGH_FREQUENCY_INFORMATION');Add-M03A5Finding $Findings 'EIS' $Record 'BLOCKED_INSUFFICIENT_HIGH_FREQUENCY_INFORMATION' 'BLOCKED' "retained rows=$RowCount"}
    if ($RowCount){$Min=[double](($Parsed|Measure-Object frequency -Minimum).Minimum);$Max=[double](($Parsed|Measure-Object frequency -Maximum).Maximum);$DuplicateCount=[int](($Parsed|Group-Object frequency|ForEach-Object{[Math]::Max(0,$_.Count-1)}|Measure-Object -Sum).Sum);if ($DuplicateCount -gt 0){Add-M03A5Finding $Findings 'EIS' $Record 'RETAINED_DUPLICATE_FREQUENCY' 'INFO' "$DuplicateCount duplicate rows retained"}}
    if ($RowCount -gt 1 -and $Max -gt $Min -and $Min -gt 0){$Average=($RowCount-1)/[Math]::Log10($Max/$Min);$High=@($Parsed|Where-Object{$_.frequency -ge $Max/$Thresholds.high_frequency_window_ratio});$MinHigh=[double](($High|Measure-Object frequency -Minimum).Minimum);$Span=$Max/$MinHigh;$Inductive=@($High|Where-Object z_imag -gt 0).Count;if ($Inductive){Add-M03A5Finding $Findings 'EIS' $Record 'RETAINED_INDUCTIVE_HIGH_FREQUENCY' 'INFO' "$Inductive inductive high-frequency rows retained"};if ($Span -lt $Thresholds.minimum_high_frequency_span_ratio){$Codes.Add('BLOCKED_INSUFFICIENT_HIGH_FREQUENCY_INFORMATION');Add-M03A5Finding $Findings 'EIS' $Record 'BLOCKED_INSUFFICIENT_HIGH_FREQUENCY_INFORMATION' 'BLOCKED' "high-frequency span ratio=$(Format-M03A5Number $Span)"}}
    else{$Metadata=$false;$Codes.Add('BLOCKED_FREQUENCY_RANGE');Add-M03A5Finding $Findings 'EIS' $Record 'BLOCKED_FREQUENCY_RANGE' 'BLOCKED' 'actual frequency range must have min < max'}
    try{$DeclaredMin=ConvertTo-M03A5Double ([string]$ManifestRow.frequency_min_Hz) "$Record declared frequency min";$DeclaredMax=ConvertTo-M03A5Double ([string]$ManifestRow.frequency_max_Hz) "$Record declared frequency max";$DeclaredPpd=ConvertTo-M03A5PositiveInt32Exact ([string]$ManifestRow.points_per_decade) "$Record points_per_decade";if ($DeclaredMin -ge $DeclaredMax){throw 'frequency_min_Hz must be less than frequency_max_Hz'};if ((Get-M03A5RelativeDifference $Min $DeclaredMin) -gt $Thresholds.frequency_metadata_relative_tolerance){$Metadata=$false;$Codes.Add('BLOCKED_FREQUENCY_MIN_METADATA_MISMATCH');Add-M03A5Finding $Findings 'EIS' $Record 'BLOCKED_FREQUENCY_MIN_METADATA_MISMATCH' 'BLOCKED' "actual=$(Format-M03A5Number $Min) declared=$(Format-M03A5Number $DeclaredMin)"};if ((Get-M03A5RelativeDifference $Max $DeclaredMax) -gt $Thresholds.frequency_metadata_relative_tolerance){$Metadata=$false;$Codes.Add('BLOCKED_FREQUENCY_MAX_METADATA_MISMATCH');Add-M03A5Finding $Findings 'EIS' $Record 'BLOCKED_FREQUENCY_MAX_METADATA_MISMATCH' 'BLOCKED' "actual=$(Format-M03A5Number $Max) declared=$(Format-M03A5Number $DeclaredMax)"};if ($Average -lt $DeclaredPpd*$Thresholds.points_per_decade_minimum_fraction){$Metadata=$false;$Codes.Add('BLOCKED_POINTS_PER_DECADE_DENSITY');Add-M03A5Finding $Findings 'EIS' $Record 'BLOCKED_POINTS_PER_DECADE_DENSITY' 'BLOCKED' "actual=$(Format-M03A5Number $Average) declared=$DeclaredPpd"}}catch{$Metadata=$false;$Code=if ($_.Exception.Message -like 'BLOCKED_INVALID_POSITIVE_INTEGER*'){'BLOCKED_POINTS_PER_DECADE_INTEGER'}else{'BLOCKED_FREQUENCY_METADATA'};$Codes.Add($Code);Add-M03A5Finding $Findings 'EIS' $Record $Code 'BLOCKED' $_.Exception.Message}
    Add-M03A5Finding $Findings 'EIS' $Record 'EIS_ROWS_RETAINED' 'PASS' "$RowCount rows audited without deletion or clipping"
    return [pscustomobject][ordered]@{actual_row_count=$RowCount;actual_frequency_min_Hz=Format-M03A5Number $Min;actual_frequency_max_Hz=Format-M03A5Number $Max;actual_average_points_per_decade=Format-M03A5Number $Average;duplicate_frequency_count=$DuplicateCount;inductive_HF_count=$Inductive;high_frequency_span_ratio=Format-M03A5Number $Span;metadata_match=$Metadata;hfr_eligible=($Codes.Count -eq 0);blocking_codes=(@($Codes|Select-Object -Unique)-join';')}
}

function Invoke-M03A5MonteCarlo {
    param([double]$HfrOhm,[double]$HfrUOhm,[double[]]$SeriesOhm,[double[]]$SeriesUOhm,[double]$SpacingM,[double]$SpacingUM,[double]$AreaM2,[double]$AreaUM2,[int]$SampleCount,[int]$Seed,[double]$RejectionLimit)
    if ($SampleCount -lt 1){throw 'Monte Carlo sample count must be positive'};$Random=[Random]::new($Seed);$Accepted=New-Object Collections.Generic.List[double];$Rejected=0
    for ($i=0;$i -lt $SampleCount;$i++){$Normal={$U1=[Math]::Max($Random.NextDouble(),[double]::Epsilon);$U2=$Random.NextDouble();[Math]::Sqrt(-2*[Math]::Log($U1))*[Math]::Cos(2*[Math]::PI*$U2)};$R=$HfrOhm+$HfrUOhm*(&$Normal);for ($j=0;$j -lt $SeriesOhm.Count;$j++){$R-=$SeriesOhm[$j]+$SeriesUOhm[$j]*(&$Normal)};$H=$SpacingM+$SpacingUM*(&$Normal);$A=$AreaM2+$AreaUM2*(&$Normal);if ($R -le 0 -or $H -le 0 -or $A -le 0){$Rejected++;continue};$K=$H/($A*$R);if (-not (Test-M03A5Finite $K) -or $K -le 0){$Rejected++;continue};$Accepted.Add($K)}
    $Fraction=$Rejected/[double]$SampleCount;$Mean=if ($Accepted.Count){[double](($Accepted|Measure-Object -Average).Average)}else{[double]::NaN};$Sum=0.0;foreach ($V in $Accepted){$Sum+=($V-$Mean)*($V-$Mean)};$Sd=if ($Accepted.Count -gt 1){[Math]::Sqrt($Sum/($Accepted.Count-1))}else{[double]::NaN}
    return [pscustomobject][ordered]@{sample_count=$SampleCount;accepted_count=$Accepted.Count;rejected_count=$Rejected;rejected_fraction=Format-M03A5Number $Fraction;mean_S_m=Format-M03A5Number $Mean;standard_deviation_S_m=Format-M03A5Number $Sd;repair_count=0;resample_count=0;status=if ($Fraction -gt $RejectionLimit){'FAILED_INPUT_DISTRIBUTION'}else{'PASS'}}
}

function Test-M03A5MonteCarloRejection {param([int]$RejectedCount,[int]$SampleCount,[double]$Limit);if ($SampleCount -le 0 -or $RejectedCount -lt 0 -or $RejectedCount -gt $SampleCount){throw 'invalid Monte Carlo accounting'};$Fraction=$RejectedCount/[double]$SampleCount;return [pscustomobject]@{rejected_fraction=$Fraction;repair_count=0;resample_count=0;status=if ($Fraction -gt $Limit){'FAILED_INPUT_DISTRIBUTION'}else{'PASS'}}}

function Test-M03A5NumericCompatibility {
    param([object[]]$Rows,[string]$Field,[double]$Tolerance)
    $Values=@();foreach ($Row in $Rows){try{$Values+=ConvertTo-M03A5Double ([string]$Row.$Field) $Field}catch{return $false}}
    if ($Values.Count -lt 2){return $true};$Min=[double](($Values|Measure-Object -Minimum).Minimum);$Max=[double](($Values|Measure-Object -Maximum).Maximum);return (Get-M03A5RelativeDifference $Min $Max) -le $Tolerance
}

function Invoke-M03A5IntakeAudit {
    param([string]$ProjectRoot,[string]$ManifestPath,[string]$ResistancePath,[string]$AreaPath,[string]$DirectPath,[string]$ThresholdPath,[string]$ExpectedDataOrigin='EXPERIMENTAL')
    $Findings=New-Object Collections.Generic.List[object];$Completeness=New-Object Collections.Generic.List[object];$Missing=New-Object Collections.Generic.List[object];$Provenance=New-Object Collections.Generic.List[object]
    $ManifestColumns=@('sample_id','cell_id','replicate_id','operator','measurement_datetime','instrument','instrument_software','raw_file_path','raw_file_size_bytes','raw_file_sha256','normalized_table_path','normalized_table_size_bytes','normalized_table_sha256','frequency_unit','impedance_unit','frequency_min_Hz','frequency_max_Hz','points_per_decade','perturbation_amplitude_V','dc_condition','dc_bias_V','temperature_K','electrolyte_composition','electrolyte_batch','salt_concentration_mol_L','water_content','water_content_unit','water_content_method','gas_atmosphere','pressure_Pa','flow_rate_m3_s','stabilization_time_s','eis_area_basis','eis_area_m2','source_notebook_reference','hfr_method','hfr_ohm','hfr_uncertainty_ohm','hfr_status','hfr_source_reference','conductivity_transfer_selection','conductivity_transfer_authorization_reference','data_origin','status','notes')
    $ResistanceColumns=@('sample_id','component_name','value_ohm','uncertainty_ohm','unit','method','replicate_count','temperature_K','source_reference','raw_evidence_path','raw_evidence_size_bytes','raw_evidence_sha256','data_origin','status','notes')
    $AreaColumns=@('sample_id','quantity_name','value_SI','uncertainty_SI','unit','measurement_method','source_reference','raw_evidence_path','raw_evidence_size_bytes','raw_evidence_sha256','data_origin','status','definition','notes')
    $DirectColumns=@('sample_id','conductivity_S_m','uncertainty_S_m','unit','temperature_K','method','cell_constant','cell_constant_unit','calibration_standard','instrument','raw_evidence_path','raw_evidence_size_bytes','raw_evidence_sha256','source_reference','data_origin','status','notes')
    $ThresholdColumns=@('threshold_name','value','unit','comparison','description')
    $Manifest=@(Import-M03A5CsvStrict $ManifestPath $ManifestColumns);$Resistance=@(Import-M03A5CsvStrict $ResistancePath $ResistanceColumns);$Areas=@(Import-M03A5CsvStrict $AreaPath $AreaColumns);$Direct=@(Import-M03A5CsvStrict $DirectPath $DirectColumns);$ThresholdRows=@(Import-M03A5CsvStrict $ThresholdPath $ThresholdColumns);$Thresholds=Get-M03A5ThresholdMap $ThresholdRows
    Add-M03A5Finding $Findings 'FRAMEWORK' 'CSV' 'RFC4180_UTF8_NO_BOM' 'PASS' 'all canonical CSV files passed syntax encoding schema and threshold audit'
    foreach ($Set in @(@('manifest',$Manifest),@('resistance',$Resistance),@('area',$Areas),@('direct',$Direct))){foreach ($Row in @($Set[1])){$Record="$($Set[0]):$([string]$Row.sample_id)";if ([string]$Row.data_origin -ne $ExpectedDataOrigin){Add-M03A5Finding $Findings 'ORIGIN' $Record 'BLOCKED_DATA_ORIGIN' 'BLOCKED' "expected $ExpectedDataOrigin, found $([string]$Row.data_origin)"};if (@('MISSING','MEASURED') -notcontains [string]$Row.status){Add-M03A5Finding $Findings 'STATUS' $Record 'BLOCKED_INVALID_STATUS' 'BLOCKED' 'status must be MISSING or MEASURED'}}}
    $MetadataRequired=$Thresholds.experimental_metadata_completeness_required -ge 1;$HashRequired=$Thresholds.source_sha256_required
    $ManifestRequired=@('cell_id','replicate_id','operator','measurement_datetime','instrument','instrument_software','raw_file_path','raw_file_size_bytes','raw_file_sha256','normalized_table_path','normalized_table_size_bytes','normalized_table_sha256','frequency_unit','impedance_unit','frequency_min_Hz','frequency_max_Hz','points_per_decade','perturbation_amplitude_V','dc_condition','temperature_K','electrolyte_composition','electrolyte_batch','salt_concentration_mol_L','water_content','water_content_unit','water_content_method','gas_atmosphere','pressure_Pa','flow_rate_m3_s','stabilization_time_s','eis_area_basis','eis_area_m2','source_notebook_reference','hfr_method','hfr_ohm','hfr_uncertainty_ohm','hfr_status','hfr_source_reference')
    $ManifestNumeric=@('frequency_min_Hz','frequency_max_Hz','perturbation_amplitude_V','temperature_K','salt_concentration_mol_L','water_content','pressure_Pa','flow_rate_m3_s','stabilization_time_s','eis_area_m2','hfr_ohm','hfr_uncertainty_ohm')
    $HfrEligible=@{};$NormalizedAudits=@{};$MeasuredManifest=@($Manifest|Where-Object status -eq 'MEASURED');$AcceptedStatuses=if ($ExpectedDataOrigin -eq 'EXPERIMENTAL'){$script:ExperimentalHfrStatuses}else{$script:SyntheticHfrStatuses}
    foreach ($Row in $Manifest){$Id="$($Row.sample_id)/$($Row.replicate_id)";$MissingRow=[string]$Row.status -eq 'MISSING';Add-M03A5Completeness $Completeness $Missing 'config/M03A_5_experimental_manifest.csv' $Id $Row $ManifestRequired;if ($MissingRow){[void](Test-M03A5Evidence $Findings $Provenance $ProjectRoot $Id 'RAW_INSTRUMENT_FILE' ([string]$Row.raw_file_path) ([string]$Row.raw_file_size_bytes) ([string]$Row.raw_file_sha256) $true $HashRequired);[void](Test-M03A5Evidence $Findings $Provenance $ProjectRoot $Id 'NORMALIZED_EIS_TABLE' ([string]$Row.normalized_table_path) ([string]$Row.normalized_table_size_bytes) ([string]$Row.normalized_table_sha256) $true $HashRequired);$HfrEligible[$Id]=$false;continue}
        $Before=@($Findings|Where-Object state -eq 'BLOCKED').Count
        if ($MetadataRequired){foreach ($Field in $ManifestRequired){if ([string]::IsNullOrWhiteSpace([string]$Row.$Field)){Add-M03A5Finding $Findings 'EIS' $Id $(if ($Field -like '*source*reference'){'BLOCKED_SOURCE_REFERENCE'}else{'BLOCKED_REQUIRED_METADATA'}) 'BLOCKED' "$Field is required";if ($Field -in $ManifestNumeric){Add-M03A5Finding $Findings 'EIS' $Id 'BLOCKED_REQUIRED_NUMERIC' 'BLOCKED' "$Field is a required numeric field"}}}}
        foreach ($Field in $ManifestNumeric){if (-not [string]::IsNullOrWhiteSpace([string]$Row.$Field)){try{$V=ConvertTo-M03A5Double ([string]$Row.$Field) "$Id/$Field";if ($Field -in @('frequency_min_Hz','frequency_max_Hz','perturbation_amplitude_V','temperature_K','pressure_Pa','eis_area_m2') -and $V -le 0){throw "$Field must be positive"};if ($Field -eq 'hfr_ohm' -and $V -le $Thresholds.hfr_resistance_minimum_exclusive){throw 'hfr_ohm does not exceed configured minimum'};if ($Field -in @('salt_concentration_mol_L','water_content','flow_rate_m3_s','stabilization_time_s','hfr_uncertainty_ohm') -and $V -lt 0){throw "$Field must be nonnegative"}}catch{Add-M03A5Finding $Findings 'EIS' $Id 'BLOCKED_INVALID_NUMERIC' 'BLOCKED' $_.Exception.Message}}}
        try{[void](ConvertTo-M03A5PositiveInt32Exact ([string]$Row.points_per_decade) "$Id/points_per_decade")}catch{Add-M03A5Finding $Findings 'EIS' $Id 'BLOCKED_POINTS_PER_DECADE_INTEGER' 'BLOCKED' $_.Exception.Message}
        try{[void](ConvertTo-M03A5DateTimeOffsetStrict ([string]$Row.measurement_datetime) "$Id/measurement_datetime")}catch{Add-M03A5Finding $Findings 'EIS' $Id 'BLOCKED_INVALID_DATETIME' 'BLOCKED' $_.Exception.Message}
        if ([string]$Row.frequency_unit -ne 'Hz'){Add-M03A5Finding $Findings 'EIS' $Id 'BLOCKED_FREQUENCY_UNIT' 'BLOCKED' 'unit must be Hz'};if ([string]$Row.impedance_unit -ne 'ohm'){Add-M03A5Finding $Findings 'EIS' $Id 'BLOCKED_IMPEDANCE_UNIT' 'BLOCKED' 'unit must be ohm'}
        $Raw=Test-M03A5Evidence $Findings $Provenance $ProjectRoot $Id 'RAW_INSTRUMENT_FILE' ([string]$Row.raw_file_path) ([string]$Row.raw_file_size_bytes) ([string]$Row.raw_file_sha256) $false $HashRequired;$Norm=Test-M03A5Evidence $Findings $Provenance $ProjectRoot $Id 'NORMALIZED_EIS_TABLE' ([string]$Row.normalized_table_path) ([string]$Row.normalized_table_size_bytes) ([string]$Row.normalized_table_sha256) $false $HashRequired
        if ($Raw.full_path -and $Norm.full_path -and $Raw.full_path.Equals($Norm.full_path,[StringComparison]::OrdinalIgnoreCase)){Add-M03A5Finding $Findings 'PROVENANCE' $Id 'BLOCKED_RAW_NORMALIZED_PATH_COLLISION' 'BLOCKED' 'raw and normalized evidence paths resolve to the same file';$Norm=[pscustomobject]@{passed=$false;full_path=$Norm.full_path;code='BLOCKED_RAW_NORMALIZED_PATH_COLLISION'}}
        $NormAudit=if ($Norm.passed){Test-M03A5NormalizedEis $Findings $Norm.full_path $Id $Row $Thresholds}else{[pscustomobject]@{hfr_eligible=$false;blocking_codes=$Norm.code}};$NormalizedAudits[$Id]=$NormAudit
        if ([string]$Row.hfr_status -notin $AcceptedStatuses){Add-M03A5Finding $Findings 'HFR' $Id 'BLOCKED_HFR_STATUS_NOT_ACCEPTED' 'BLOCKED' "status=$($Row.hfr_status)"};if ([string]$Row.hfr_method -notin $script:HfrMethods){Add-M03A5Finding $Findings 'HFR' $Id 'BLOCKED_HFR_METHOD_NOT_ACCEPTED' 'BLOCKED' "method=$($Row.hfr_method)"};if ([string]::IsNullOrWhiteSpace([string]$Row.hfr_source_reference)){Add-M03A5Finding $Findings 'HFR' $Id 'BLOCKED_SOURCE_REFERENCE' 'BLOCKED' 'HFR source reference is required'}
        $HfrEligible[$Id]=$Raw.passed -and $Norm.passed -and $NormAudit.hfr_eligible -and (@($Findings|Where-Object state -eq 'BLOCKED').Count -eq $Before)
    }
    $Replicate=@{};$MeasuredSamples=@($MeasuredManifest|ForEach-Object {[string]$_.sample_id}|Sort-Object -Unique)
    foreach ($Group in @($MeasuredManifest|Group-Object sample_id)){$Rows=@($Group.Group);$Sample=$Group.Name;$Before=@($Findings|Where-Object state -eq 'BLOCKED').Count;if (@($Rows|Group-Object replicate_id|Where-Object Count -gt 1).Count){Add-M03A5Finding $Findings 'REPLICATES' $Sample 'BLOCKED_DUPLICATE_REPLICATE_ID' 'BLOCKED' 'replicate_id must be unique'};$Eligible=@($Rows|Where-Object{$HfrEligible["$($_.sample_id)/$($_.replicate_id)"]});if ($Eligible.Count -lt [int]$Thresholds.minimum_accepted_eis_replicates){Add-M03A5Finding $Findings 'REPLICATES' $Sample 'BLOCKED_INSUFFICIENT_ELIGIBLE_HFR_REPLICATES' 'BLOCKED' "eligible=$($Eligible.Count) required=$([int]$Thresholds.minimum_accepted_eis_replicates)"}
        foreach ($Field in @('cell_id','instrument','instrument_software','electrolyte_composition','electrolyte_batch','water_content_unit','water_content_method','gas_atmosphere','dc_condition','eis_area_basis')){if (@($Rows|Select-Object -ExpandProperty $Field -Unique).Count -ne 1){Add-M03A5Finding $Findings 'REPLICATES' $Sample 'BLOCKED_REPLICATE_METADATA_INCOMPATIBLE' 'BLOCKED' "$Field differs"}}
        $Temps=@($Rows|ForEach-Object{try{ConvertTo-M03A5Double ([string]$_.temperature_K) 'temperature'}catch{[double]::NaN}}|Where-Object{Test-M03A5Finite $_});if ($Temps.Count -ne $Rows.Count -or ([double](($Temps|Measure-Object -Maximum).Maximum)-[double](($Temps|Measure-Object -Minimum).Minimum)) -gt $Thresholds.replicate_temperature_tolerance_K){Add-M03A5Finding $Findings 'REPLICATES' $Sample 'BLOCKED_REPLICATE_METADATA_INCOMPATIBLE' 'BLOCKED' 'temperature differs'}
        foreach ($Field in @('salt_concentration_mol_L','water_content','pressure_Pa','flow_rate_m3_s','stabilization_time_s','perturbation_amplitude_V','eis_area_m2')){if (-not (Test-M03A5NumericCompatibility $Rows $Field $Thresholds.replicate_numeric_relative_tolerance)){Add-M03A5Finding $Findings 'REPLICATES' $Sample 'BLOCKED_REPLICATE_METADATA_INCOMPATIBLE' 'BLOCKED' "$Field differs"}}
        $Dc=@($Rows|Select-Object -ExpandProperty dc_condition -Unique);if ($Dc.Count -eq 1 -and $Dc[0].ToUpperInvariant() -ne 'OCP'){if (@($Rows|Where-Object{[string]::IsNullOrWhiteSpace([string]$_.dc_bias_V)}).Count){Add-M03A5Finding $Findings 'REPLICATES' $Sample 'BLOCKED_NONOCP_BIAS_REQUIRED' 'BLOCKED' 'non-OCP bias is required'}elseif (-not (Test-M03A5NumericCompatibility $Rows 'dc_bias_V' $Thresholds.replicate_numeric_relative_tolerance)){Add-M03A5Finding $Findings 'REPLICATES' $Sample 'BLOCKED_REPLICATE_METADATA_INCOMPATIBLE' 'BLOCKED' 'dc_bias_V differs'}}
        $Mean=[double]::NaN;$Within=[double]::NaN;$Between=[double]::NaN;$HfrU=[double]::NaN;$Range=[double]::NaN
        if ($Eligible.Count){$HV=@($Eligible|ForEach-Object{ConvertTo-M03A5Double ([string]$_.hfr_ohm) 'hfr'});$HU=@($Eligible|ForEach-Object{ConvertTo-M03A5Double ([string]$_.hfr_uncertainty_ohm) 'hfr uncertainty'});$Mean=[double](($HV|Measure-Object -Average).Average);$Min=[double](($HV|Measure-Object -Minimum).Minimum);$Max=[double](($HV|Measure-Object -Maximum).Maximum);$Range=($Max-$Min)/[Math]::Abs($Mean);if ($Range -gt $Thresholds.hfr_replicate_relative_range_limit){Add-M03A5Finding $Findings 'HFR' $Sample 'BLOCKED_HFR_REPLICATE_DISAGREEMENT' 'BLOCKED' "relative_range=$(Format-M03A5Number $Range)"};$Within=[double](($HU|ForEach-Object{$_*$_}|Measure-Object -Sum).Sum)/($Eligible.Count*$Eligible.Count);$SS=0.0;foreach ($V in $HV){$SS+=($V-$Mean)*($V-$Mean)};$S=if ($Eligible.Count -gt 1){[Math]::Sqrt($SS/($Eligible.Count-1))}else{0.0};$Between=$S*$S/$Eligible.Count;$HfrU=[Math]::Sqrt($Within+$Between)}
        $Ready=@($Findings|Where-Object state -eq 'BLOCKED').Count -eq $Before;$Replicate[$Sample]=[pscustomobject]@{ready=$Ready;eligible_count=$Eligible.Count;hfr_mean=$Mean;u_within_mean=[Math]::Sqrt($Within);u_between_mean=[Math]::Sqrt($Between);hfr_uncertainty=$HfrU;relative_range=$Range;temperature_mean=if ($Temps.Count){[double](($Temps|Measure-Object -Average).Average)}else{[double]::NaN};rows=$Rows};if ($Ready){Add-M03A5Finding $Findings 'HFR' $Sample 'HFR_CONSENSUS_ELIGIBLE' 'PASS' "n=$($Eligible.Count); u_within=$(Format-M03A5Number ([Math]::Sqrt($Within))); u_between=$(Format-M03A5Number ([Math]::Sqrt($Between))); u_total=$(Format-M03A5Number $HfrU)"}
    }
    $ResistanceRequired=@('value_ohm','uncertainty_ohm','unit','method','replicate_count','temperature_K','source_reference','raw_evidence_path','raw_evidence_size_bytes','raw_evidence_sha256');$ResistanceEvidence=@{};$ResistanceValidation=@{}
    foreach ($Row in $Resistance){
        $Id="$($Row.sample_id)/$($Row.component_name)";$MissingRow=[string]$Row.status -eq 'MISSING';Add-M03A5Completeness $Completeness $Missing 'config/M03A_5_resistance_ledger.csv' $Id $Row $ResistanceRequired
        if ($MissingRow){[void](Test-M03A5Evidence $Findings $Provenance $ProjectRoot $Id 'SERIES_RESISTANCE_EVIDENCE' ([string]$Row.raw_evidence_path) ([string]$Row.raw_evidence_size_bytes) ([string]$Row.raw_evidence_sha256) $true $HashRequired);$ResistanceValidation[$Id]=$false;continue}
        $BeforeRow=@($Findings|Where-Object state -eq 'BLOCKED').Count
        if ($MetadataRequired){foreach ($Field in $ResistanceRequired){if ([string]::IsNullOrWhiteSpace([string]$Row.$Field)){Add-M03A5Finding $Findings 'RESISTANCE' $Id 'BLOCKED_REQUIRED_METADATA' 'BLOCKED' "$Field is required"}}}
        try{$V=ConvertTo-M03A5Double ([string]$Row.value_ohm) "$Id/value";if ($V -lt $Thresholds.series_resistance_minimum_inclusive){throw 'value below configured minimum'};$U=ConvertTo-M03A5Double ([string]$Row.uncertainty_ohm) "$Id/uncertainty";if ($U -lt 0){throw 'uncertainty negative'};$T=ConvertTo-M03A5Double ([string]$Row.temperature_K) "$Id/temperature";if ($T -le 0){throw 'temperature must be positive'};[void](ConvertTo-M03A5PositiveInt32Exact ([string]$Row.replicate_count) "$Id/replicate_count")}catch{$Code=if ($_.Exception.Message -like 'BLOCKED_REQUIRED_NUMERIC*'){'BLOCKED_REQUIRED_NUMERIC'}elseif ($_.Exception.Message -like 'BLOCKED_INVALID_NUMERIC*'){'BLOCKED_INVALID_NUMERIC'}else{'BLOCKED_INVALID_RESISTANCE'};Add-M03A5Finding $Findings 'RESISTANCE' $Id $Code 'BLOCKED' $_.Exception.Message}
        if ([string]$Row.unit -ne 'ohm'){Add-M03A5Finding $Findings 'RESISTANCE' $Id 'BLOCKED_RESISTANCE_UNIT' 'BLOCKED' 'unit must be ohm'}
        $ResistanceEvidence[$Id]=Test-M03A5Evidence $Findings $Provenance $ProjectRoot $Id 'SERIES_RESISTANCE_EVIDENCE' ([string]$Row.raw_evidence_path) ([string]$Row.raw_evidence_size_bytes) ([string]$Row.raw_evidence_sha256) $false $HashRequired
        $ResistanceValidation[$Id]=$ResistanceEvidence[$Id].passed -and (@($Findings|Where-Object state -eq 'BLOCKED').Count -eq $BeforeRow)
    }
    $ResistanceReady=@{};foreach ($Sample in $MeasuredSamples){$Rows=@($Resistance|Where-Object sample_id -eq $Sample);$Before=@($Findings|Where-Object state -eq 'BLOCKED').Count;foreach ($Name in $script:RequiredSeries){$Match=@($Rows|Where-Object component_name -eq $Name);if ($Match.Count -ne 1 -or $Match[0].status -ne 'MEASURED'){Add-M03A5Finding $Findings 'RESISTANCE' $Sample 'BLOCKED_SERIES_LEDGER' 'BLOCKED' "$Name must occur exactly once as MEASURED"}elseif (-not $ResistanceValidation["$Sample/$Name"]){Add-M03A5Finding $Findings 'RESISTANCE' $Sample 'BLOCKED_SERIES_PROVENANCE' 'BLOCKED' "$Name validation or provenance failed"}};if (@($Rows|Where-Object{$_.status -eq 'MEASURED' -and $_.component_name -notin $script:RequiredSeries}).Count -or @($Rows|Where-Object status -eq 'MEASURED').Count -ne $script:RequiredSeries.Count){Add-M03A5Finding $Findings 'RESISTANCE' $Sample 'BLOCKED_SERIES_LEDGER' 'BLOCKED' 'series set cardinality mismatch'};$ResistanceReady[$Sample]=[pscustomobject]@{ready=(@($Findings|Where-Object state -eq 'BLOCKED').Count -eq $Before);rows=@($Rows|Where-Object status -eq 'MEASURED')}}
    foreach ($Row in @($Resistance|Where-Object status -eq 'MEASURED')){if ($MeasuredSamples -notcontains $Row.sample_id){Add-M03A5Finding $Findings 'SAMPLE_MAPPING' $Row.sample_id 'BLOCKED_ORPHAN_RESISTANCE_SAMPLE' 'BLOCKED' 'no measured EIS sample'}}
    $AreaRequired=@('value_SI','uncertainty_SI','unit','measurement_method','source_reference','raw_evidence_path','raw_evidence_size_bytes','raw_evidence_sha256','definition');$AreaEvidence=@{};$AreaValidation=@{}
    foreach ($Row in $Areas){
        $Id="$($Row.sample_id)/$($Row.quantity_name)";$MissingRow=[string]$Row.status -eq 'MISSING';Add-M03A5Completeness $Completeness $Missing 'config/M03A_5_area_measurements.csv' $Id $Row $AreaRequired
        if ($MissingRow){[void](Test-M03A5Evidence $Findings $Provenance $ProjectRoot $Id 'GEOMETRY_AREA_EVIDENCE' ([string]$Row.raw_evidence_path) ([string]$Row.raw_evidence_size_bytes) ([string]$Row.raw_evidence_sha256) $true $HashRequired);$AreaValidation[$Id]=$false;continue}
        $BeforeRow=@($Findings|Where-Object state -eq 'BLOCKED').Count
        if ($MetadataRequired){foreach ($Field in $AreaRequired){if ([string]::IsNullOrWhiteSpace([string]$Row.$Field)){Add-M03A5Finding $Findings 'AREA' $Id 'BLOCKED_REQUIRED_METADATA' 'BLOCKED' "$Field is required"}}}
        try{$V=ConvertTo-M03A5Double ([string]$Row.value_SI) "$Id/value";$Minimum=if ($Row.quantity_name -eq 'electrode_spacing'){$Thresholds.electrode_spacing_minimum_exclusive}elseif ($Row.quantity_name -eq 'out_of_plane_depth'){$Thresholds.out_of_plane_depth_minimum_exclusive}else{$Thresholds.required_area_minimum_exclusive};if ($V -le $Minimum){throw 'value does not exceed configured minimum'};$U=ConvertTo-M03A5Double ([string]$Row.uncertainty_SI) "$Id/uncertainty";if ($U -lt 0){throw 'uncertainty negative'}}catch{$Code=if ($_.Exception.Message -like 'BLOCKED_REQUIRED_NUMERIC*'){'BLOCKED_REQUIRED_NUMERIC'}elseif ($_.Exception.Message -like 'BLOCKED_INVALID_NUMERIC*'){'BLOCKED_INVALID_NUMERIC'}elseif ($Row.quantity_name -in @('electrode_spacing','out_of_plane_depth')){'BLOCKED_NONPOSITIVE_GEOMETRY'}else{'BLOCKED_NONPOSITIVE_AREA'};Add-M03A5Finding $Findings 'AREA' $Id $Code 'BLOCKED' $_.Exception.Message}
        $ExpectedUnit=if ($Row.quantity_name -in @('electrode_spacing','out_of_plane_depth')){'m'}else{'m^2'};if ([string]$Row.unit -ne $ExpectedUnit){Add-M03A5Finding $Findings 'AREA' $Id 'BLOCKED_AREA_UNIT' 'BLOCKED' "unit must be $ExpectedUnit"}
        $AreaEvidence[$Id]=Test-M03A5Evidence $Findings $Provenance $ProjectRoot $Id 'GEOMETRY_AREA_EVIDENCE' ([string]$Row.raw_evidence_path) ([string]$Row.raw_evidence_size_bytes) ([string]$Row.raw_evidence_sha256) $false $HashRequired
        $AreaValidation[$Id]=$AreaEvidence[$Id].passed -and (@($Findings|Where-Object state -eq 'BLOCKED').Count -eq $BeforeRow)
    }
    $AreaReady=@{};foreach ($Sample in $MeasuredSamples){$Rows=@($Areas|Where-Object sample_id -eq $Sample);$Before=@($Findings|Where-Object state -eq 'BLOCKED').Count;foreach ($Name in $script:RequiredAreas){$Match=@($Rows|Where-Object quantity_name -eq $Name);if ($Match.Count -ne 1 -or $Match[0].status -ne 'MEASURED'){Add-M03A5Finding $Findings 'AREA' $Sample 'BLOCKED_AREA_DEFINITIONS' 'BLOCKED' "$Name must occur exactly once as MEASURED"}elseif (-not $AreaValidation["$Sample/$Name"]){Add-M03A5Finding $Findings 'AREA' $Sample 'BLOCKED_AREA_PROVENANCE' 'BLOCKED' "$Name validation or provenance failed"}};if (@($Rows|Where-Object{$_.status -eq 'MEASURED' -and $_.quantity_name -notin $script:RequiredAreas}).Count -or @($Rows|Where-Object status -eq 'MEASURED').Count -ne $script:RequiredAreas.Count){Add-M03A5Finding $Findings 'AREA' $Sample 'BLOCKED_AREA_DEFINITIONS' 'BLOCKED' 'area set cardinality mismatch'};$AreaReady[$Sample]=[pscustomobject]@{ready=(@($Findings|Where-Object state -eq 'BLOCKED').Count -eq $Before);rows=@($Rows|Where-Object status -eq 'MEASURED')}}
    foreach ($Row in @($Areas|Where-Object status -eq 'MEASURED')){if ($MeasuredSamples -notcontains $Row.sample_id){Add-M03A5Finding $Findings 'SAMPLE_MAPPING' $Row.sample_id 'BLOCKED_ORPHAN_AREA_SAMPLE' 'BLOCKED' 'no measured EIS sample'}}
    $DirectRequired=@('conductivity_S_m','uncertainty_S_m','unit','temperature_K','method','cell_constant','cell_constant_unit','calibration_standard','instrument','raw_evidence_path','raw_evidence_size_bytes','raw_evidence_sha256','source_reference');$DirectEvidence=@{};$DirectValidation=@{}
    foreach ($Row in $Direct){
        $Id="$($Row.sample_id)/direct_conductivity";$MissingRow=[string]$Row.status -eq 'MISSING';Add-M03A5Completeness $Completeness $Missing 'config/M03A_5_direct_conductivity.csv' $Id $Row $DirectRequired
        if ($MissingRow){[void](Test-M03A5Evidence $Findings $Provenance $ProjectRoot $Id 'DIRECT_CONDUCTIVITY_EVIDENCE' ([string]$Row.raw_evidence_path) ([string]$Row.raw_evidence_size_bytes) ([string]$Row.raw_evidence_sha256) $true $HashRequired);$DirectValidation[$Id]=$false;continue}
        $BeforeRow=@($Findings|Where-Object state -eq 'BLOCKED').Count
        if ($MetadataRequired){foreach ($Field in $DirectRequired){if ([string]::IsNullOrWhiteSpace([string]$Row.$Field)){Add-M03A5Finding $Findings 'DIRECT_CONDUCTIVITY' $Id 'BLOCKED_DIRECT_CONDUCTIVITY_PROVENANCE' 'BLOCKED' "$Field is required"}}}
        try{$K=ConvertTo-M03A5Double ([string]$Row.conductivity_S_m) "$Id/conductivity";if ($K -le $Thresholds.conductivity_minimum_exclusive){throw 'conductivity does not exceed configured minimum'};$U=ConvertTo-M03A5Double ([string]$Row.uncertainty_S_m) "$Id/uncertainty";if ($U -lt 0){throw 'uncertainty negative'};$T=ConvertTo-M03A5Double ([string]$Row.temperature_K) "$Id/temperature";if ($T -le 0){throw 'temperature must be positive'};$Cell=ConvertTo-M03A5Double ([string]$Row.cell_constant) "$Id/cell constant";if ($Cell -le 0){throw 'cell constant must be positive'}}catch{$Code=if ($_.Exception.Message -like 'BLOCKED_REQUIRED_NUMERIC*'){'BLOCKED_REQUIRED_NUMERIC'}elseif ($_.Exception.Message -like 'BLOCKED_INVALID_NUMERIC*'){'BLOCKED_INVALID_NUMERIC'}else{'BLOCKED_INVALID_DIRECT_CONDUCTIVITY'};Add-M03A5Finding $Findings 'DIRECT_CONDUCTIVITY' $Id $Code 'BLOCKED' $_.Exception.Message}
        if ([string]$Row.unit -ne 'S/m'){Add-M03A5Finding $Findings 'DIRECT_CONDUCTIVITY' $Id 'BLOCKED_CONDUCTIVITY_UNIT' 'BLOCKED' 'unit must be S/m'};if ([string]$Row.cell_constant_unit -notin $script:CellConstantUnits){Add-M03A5Finding $Findings 'DIRECT_CONDUCTIVITY' $Id 'BLOCKED_CELL_CONSTANT_UNIT' 'BLOCKED' "unit=$($Row.cell_constant_unit)"}
        $DirectEvidence[$Id]=Test-M03A5Evidence $Findings $Provenance $ProjectRoot $Id 'DIRECT_CONDUCTIVITY_EVIDENCE' ([string]$Row.raw_evidence_path) ([string]$Row.raw_evidence_size_bytes) ([string]$Row.raw_evidence_sha256) $false $HashRequired
        $DirectValidation[$Id]=$DirectEvidence[$Id].passed -and (@($Findings|Where-Object state -eq 'BLOCKED').Count -eq $BeforeRow)
    }
    foreach ($Row in @($Direct|Where-Object status -eq 'MEASURED')){if ($MeasuredSamples -notcontains $Row.sample_id){Add-M03A5Finding $Findings 'SAMPLE_MAPPING' $Row.sample_id 'BLOCKED_ORPHAN_DIRECT_SAMPLE' 'BLOCKED' 'no measured EIS sample'}}
    $DirectReady=@{};foreach ($Sample in $MeasuredSamples){$Rows=@($Direct|Where-Object{$_.sample_id -eq $Sample -and $_.status -eq 'MEASURED'});$Before=@($Findings|Where-Object state -eq 'BLOCKED').Count;if ($Rows.Count -ne 1){Add-M03A5Finding $Findings 'DIRECT_CONDUCTIVITY' $Sample 'BLOCKED_DIRECT_CONDUCTIVITY_CARDINALITY' 'BLOCKED' "found $($Rows.Count), require exactly one"}elseif (-not $DirectValidation["$Sample/direct_conductivity"]){Add-M03A5Finding $Findings 'DIRECT_CONDUCTIVITY' $Sample 'BLOCKED_DIRECT_CONDUCTIVITY_PROVENANCE' 'BLOCKED' 'record validation or evidence failed'};$DirectReady[$Sample]=[pscustomobject]@{ready=(@($Findings|Where-Object state -eq 'BLOCKED').Count -eq $Before);rows=$Rows}}
    $SampleReadiness=New-Object Collections.Generic.List[object];$AllAnalytical=$MeasuredSamples.Count -gt 0;$AllMonteCarlo=$MeasuredSamples.Count -gt 0;$AllComparison=$MeasuredSamples.Count -gt 0
    foreach ($Sample in $MeasuredSamples){$Before=@($Findings|Where-Object state -eq 'BLOCKED').Count;$Rep=$Replicate[$Sample];$Res=$ResistanceReady[$Sample];$Ar=$AreaReady[$Sample];$Dir=$DirectReady[$Sample];if ($null -eq $Rep -or -not $Rep.ready -or $null -eq $Res -or -not $Res.ready -or $null -eq $Ar -or -not $Ar.ready -or $null -eq $Dir -or -not $Dir.ready){$AllAnalytical=$false;$AllMonteCarlo=$false;$AllComparison=$false;$SampleReadiness.Add([pscustomobject]@{sample_id=$Sample;ready=$false});continue}
        $RR=@($Res.rows);$AR=@($Ar.rows);$DR=$Dir.rows[0]
        $Series=@($script:RequiredSeries|ForEach-Object{$Component=$_;$ComponentRow=@($RR|Where-Object component_name -eq $Component)[0];[double](ConvertTo-M03A5Double ([string]$ComponentRow.value_ohm) $Component)})
        $SeriesU=@($script:RequiredSeries|ForEach-Object{$Component=$_;$ComponentRow=@($RR|Where-Object component_name -eq $Component)[0];[double](ConvertTo-M03A5Double ([string]$ComponentRow.uncertainty_ohm) "${Component}_u")})
        $R=$Rep.hfr_mean-[double](($Series|Measure-Object -Sum).Sum)
        if ($R -le $Thresholds.electrolyte_resistance_minimum_exclusive){Add-M03A5Finding $Findings 'DEEMBEDDING' $Sample 'BLOCKED_NONPOSITIVE_DEEMBEDDED_RESISTANCE' 'BLOCKED' "R_electrolyte=$(Format-M03A5Number $R)"}
        $SpacingRow=@($AR|Where-Object quantity_name -eq 'electrode_spacing')[0];$EisAreaRow=@($AR|Where-Object quantity_name -eq 'EIS_area')[0];$Spacing=ConvertTo-M03A5Double ([string]$SpacingRow.value_SI) 'spacing';$SpacingU=ConvertTo-M03A5Double ([string]$SpacingRow.uncertainty_SI) 'spacing uncertainty';$EisArea=ConvertTo-M03A5Double ([string]$EisAreaRow.value_SI) 'EIS area';$EisAreaU=ConvertTo-M03A5Double ([string]$EisAreaRow.uncertainty_SI) 'EIS area uncertainty'
        foreach ($MR in $Rep.rows){if ([string]$MR.eis_area_basis -ne 'EIS_area' -or (Get-M03A5RelativeDifference (ConvertTo-M03A5Double ([string]$MR.eis_area_m2) 'manifest EIS area') $EisArea) -gt $Thresholds.area_consistency_relative_tolerance){Add-M03A5Finding $Findings 'AREA' $Sample 'BLOCKED_AREA_BASIS_MISMATCH' 'BLOCKED' 'manifest EIS area does not match EIS_area evidence';break}}
        if (@($Findings|Where-Object state -eq 'BLOCKED').Count -eq $Before){$RU=[Math]::Sqrt($Rep.hfr_uncertainty*$Rep.hfr_uncertainty+[double](($SeriesU|ForEach-Object{$_*$_}|Measure-Object -Sum).Sum));$K=$Spacing/($EisArea*$R);$KU=$K*[Math]::Sqrt(($SpacingU/$Spacing)*($SpacingU/$Spacing)+($EisAreaU/$EisArea)*($EisAreaU/$EisArea)+($RU/$R)*($RU/$R));Add-M03A5Finding $Findings 'UNCERTAINTY' $Sample 'ANALYTICAL_UNCERTAINTY_READY' 'PASS' "kappa=$(Format-M03A5Number $K); u_within=$(Format-M03A5Number $Rep.u_within_mean); u_between=$(Format-M03A5Number $Rep.u_between_mean); u_total_HFR=$(Format-M03A5Number $Rep.hfr_uncertainty); u_kappa=$(Format-M03A5Number $KU)";$Count=[int]$Thresholds.monte_carlo_minimum_samples;$MC=Invoke-M03A5MonteCarlo $Rep.hfr_mean $Rep.hfr_uncertainty $Series $SeriesU $Spacing $SpacingU $EisArea $EisAreaU $Count 50305 $Thresholds.monte_carlo_nonphysical_rejection_limit;if ($MC.status -ne 'PASS'){Add-M03A5Finding $Findings 'UNCERTAINTY' $Sample 'FAILED_INPUT_DISTRIBUTION' 'BLOCKED' "rejected_fraction=$($MC.rejected_fraction)";$AllMonteCarlo=$false}else{Add-M03A5Finding $Findings 'UNCERTAINTY' $Sample 'MONTE_CARLO_READY' 'PASS' "samples=$Count rejected_fraction=$($MC.rejected_fraction)"};$DirectTemp=ConvertTo-M03A5Double ([string]$DR.temperature_K) 'direct temperature';if ([Math]::Abs($DirectTemp-$Rep.temperature_mean) -gt $Thresholds.direct_temperature_tolerance_K){Add-M03A5Finding $Findings 'CONDUCTIVITY' $Sample 'BLOCKED_DIRECT_EIS_TEMPERATURE_MISMATCH' 'BLOCKED' "direct=$(Format-M03A5Number $DirectTemp) EIS=$(Format-M03A5Number $Rep.temperature_mean)";$AllComparison=$false}else{$DirectValue=ConvertTo-M03A5Double ([string]$DR.conductivity_S_m) 'direct conductivity';$Diff=[Math]::Abs($DirectValue-$K)/[Math]::Max(0.5*([Math]::Abs($DirectValue)+[Math]::Abs($K)),[double]::Epsilon);if ($Diff -gt $Thresholds.direct_vs_hfr_relative_difference_limit){Add-M03A5Finding $Findings 'CONDUCTIVITY' $Sample 'CALIBRATION_CONFLICT' 'BLOCKED' "relative_difference=$(Format-M03A5Number $Diff); selection=NONE_AUTOMATIC";$AllComparison=$false}else{Add-M03A5Finding $Findings 'CONDUCTIVITY' $Sample 'DIRECT_HFR_COMPARISON_PASS' 'PASS' "relative_difference=$(Format-M03A5Number $Diff); selection=NONE_AUTOMATIC"}}}else{$AllAnalytical=$false;$AllMonteCarlo=$false;$AllComparison=$false}
        $SampleReadiness.Add([pscustomobject]@{sample_id=$Sample;ready=(@($Findings|Where-Object state -eq 'BLOCKED').Count -eq $Before)})
    }
    Add-M03A5Finding $Findings 'POLICY' 'outlier handling' 'NO_OUTLIER_DELETION' 'PASS' 'all declared replicates retained';Add-M03A5Finding $Findings 'POLICY' 'negative handling' 'NO_NEGATIVE_VALUE_CLIPPING' 'PASS' 'nonphysical values block or reject';Add-M03A5Finding $Findings 'POLICY' 'Monte Carlo handling' 'NO_MONTE_CARLO_REPAIR_OR_RESAMPLING' 'PASS' 'requested denominator retained';Add-M03A5Finding $Findings 'POLICY' 'conductivity selection' 'NO_AUTOMATIC_CONDUCTIVITY_SELECTION' 'PASS' 'manual authorization required'
    $Blocked=@($Findings|Where-Object state -eq 'BLOCKED').Count;$HasMissing=@($Manifest+$Resistance+$Areas+$Direct|Where-Object status -eq 'MISSING').Count -gt 0;$AllSamplesReady=$SampleReadiness.Count -gt 0 -and @($SampleReadiness|Where-Object ready -eq $false).Count -eq 0;$ExperimentalComplete=$ExpectedDataOrigin -eq 'EXPERIMENTAL' -and -not $HasMissing -and $Blocked -eq 0 -and $MeasuredSamples.Count -gt 0 -and $AllSamplesReady
    $Selections=@($MeasuredManifest|Select-Object sample_id,conductivity_transfer_selection,conductivity_transfer_authorization_reference -Unique);$ManualSelection=$Selections.Count -eq $MeasuredSamples.Count -and @($Selections|Where-Object{$_.conductivity_transfer_selection -notin @('DIRECT','HFR_DERIVED') -or [string]::IsNullOrWhiteSpace([string]$_.conductivity_transfer_authorization_reference)}).Count -eq 0;$TransferEligible=$ExperimentalComplete -and $ManualSelection
    $Readiness=@([pscustomobject][ordered]@{item='RUN_STATE';value=if ($ExperimentalComplete){'EXPERIMENTAL_INTAKE_COMPLETE'}else{'EXPERIMENTAL_INPUT_WAIT'};status=if ($ExperimentalComplete){'PASS'}else{'WAIT'};detail='intake state only; no COMSOL run'},[pscustomobject][ordered]@{item='CALIBRATION_MODE';value='PROVISIONAL';status='WAIT';detail='not an experimental calibration'},[pscustomobject][ordered]@{item='EXPERIMENTAL_INPUT_COMPLETE';value=if ($ExperimentalComplete){'TRUE'}else{'FALSE'};status=if ($ExperimentalComplete){'PASS'}else{'WAIT'};detail="samples=$($MeasuredSamples.Count); missing=$HasMissing; blocked=$Blocked"},[pscustomobject][ordered]@{item='ANALYTICAL_UNCERTAINTY_READY';value=if ($AllAnalytical){'TRUE'}else{'FALSE'};status=if ($AllAnalytical){'PASS'}else{'WAIT'};detail='all parent uncertainties and between-replicate dispersion required'},[pscustomobject][ordered]@{item='MONTE_CARLO_UNCERTAINTY_READY';value=if ($AllMonteCarlo){'TRUE'}else{'FALSE'};status=if ($AllMonteCarlo){'PASS'}else{'WAIT'};detail='fixed configured sample count; no repair or resampling'},[pscustomobject][ordered]@{item='PARAMETER_TRANSFER_MODE';value=if ($TransferEligible){'ELIGIBLE_NOT_RUN'}else{'NOT_RUN'};status=if ($TransferEligible){'CANDIDATE'}else{'WAIT'};detail='manual selection and authorization required; validator never transfers'},[pscustomobject][ordered]@{item='M03B_CANDIDATE';value=if ($TransferEligible){'TRUE'}else{'FALSE'};status=if ($TransferEligible){'CANDIDATE'}else{'WAIT'};detail='candidate is not authorization'},[pscustomobject][ordered]@{item='M03B_READY';value='FALSE';status='BLOCKED_BY_STAGE_SCOPE';detail='M03A.5 never authorizes M03B'})
    return [pscustomobject]@{Findings=@($Findings|ForEach-Object{$_});Completeness=@($Completeness|ForEach-Object{$_});MissingInputs=@($Missing|ForEach-Object{$_});Provenance=@($Provenance|ForEach-Object{$_});Readiness=$Readiness;NormalizedAudits=$NormalizedAudits;SampleReadiness=@($SampleReadiness|ForEach-Object{$_});FrameworkAuditPassed=$true;BlockedFindingCount=$Blocked;ExperimentalInputComplete=$ExperimentalComplete;ParameterTransferEligible=$TransferEligible;M03BCandidate=$TransferEligible;M03BReady=$false}
}

Export-ModuleMember -Function @('Test-M03A5Finite','ConvertTo-M03A5Double','ConvertTo-M03A5Int64Exact','ConvertTo-M03A5PositiveInt32Exact','ConvertTo-M03A5DateTimeOffsetStrict','Format-M03A5Number','Write-M03A5Utf8NoBomText','Write-M03A5Utf8NoBomCsv','Get-M03A5Sha256','Assert-M03A5Rfc4180Utf8NoBom','Import-M03A5CsvStrict','Resolve-M03A5EvidencePath','Test-M03A5NormalizedEis','Invoke-M03A5MonteCarlo','Test-M03A5MonteCarloRejection','Invoke-M03A5IntakeAudit')
