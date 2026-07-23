Set-StrictMode -Version Latest

$script:Invariant=[Globalization.CultureInfo]::InvariantCulture
$script:ToolName='LiNRR M03A.6 acquisition tools'
$script:ToolVersion='M03A6_ACQUISITION_2.0.0'
$script:RequiredSeries=@('fixture','contact','membrane','other_series')
$script:RequiredAreas=@('electrode_spacing','out_of_plane_depth','geometric_electrode_area','EIS_area','current_density_reporting_area')
$script:EvidenceRoles=@('EIS_RAW','EIS_NORMALIZED','SERIES_RESISTANCE','GEOMETRY_AREA','DIRECT_CONDUCTIVITY')
$script:RoleDirectories=[ordered]@{EIS_RAW='raw/eis/';EIS_NORMALIZED='normalized/eis/';SERIES_RESISTANCE='raw/series_resistance/';GEOMETRY_AREA='raw/geometry_area/';DIRECT_CONDUCTIVITY='raw/direct_conductivity/'}
$script:InventoryColumns=@('relative_path','evidence_role','sample_id','cell_id','replicate_id','data_origin','status','notes')
$script:EisColumns=@('sample_id','cell_id','replicate_id','operator','measurement_datetime','instrument','instrument_software','raw_file_path','raw_file_size_bytes','raw_file_sha256','normalized_table_path','normalized_table_size_bytes','normalized_table_sha256','frequency_unit','impedance_unit','frequency_min_Hz','frequency_max_Hz','points_per_decade','perturbation_amplitude_V','dc_condition','dc_bias_V','temperature_K','electrolyte_composition','electrolyte_batch','salt_concentration_mol_L','water_content','water_content_unit','water_content_method','gas_atmosphere','pressure_Pa','flow_rate_m3_s','stabilization_time_s','eis_area_basis','eis_area_m2','source_notebook_reference','hfr_method','hfr_ohm','hfr_uncertainty_ohm','hfr_status','hfr_source_reference','conductivity_transfer_selection','conductivity_transfer_authorization_reference','data_origin','status','notes')
$script:SeriesColumns=@('sample_id','component_name','value_ohm','uncertainty_ohm','unit','method','replicate_count','temperature_K','source_reference','raw_evidence_path','raw_evidence_size_bytes','raw_evidence_sha256','data_origin','status','notes')
$script:AreaColumns=@('sample_id','quantity_name','value_SI','uncertainty_SI','unit','measurement_method','source_reference','raw_evidence_path','raw_evidence_size_bytes','raw_evidence_sha256','data_origin','status','definition','notes')
$script:DirectColumns=@('sample_id','conductivity_S_m','uncertainty_S_m','unit','temperature_K','method','cell_constant','cell_constant_unit','calibration_standard','instrument','raw_evidence_path','raw_evidence_size_bytes','raw_evidence_sha256','source_reference','data_origin','status','notes')
$script:LedgerColumns=@('ledger_version','relative_path','size_bytes','sha256','frozen_at','timezone_offset','tool_version')
$script:M03A6CaseEnumerationTestHook=$null

function Get-M03A6Schema {
    param([Parameter(Mandatory=$true)][ValidateSet('Inventory','Eis','Series','Area','Direct','Ledger')][string]$Name)
    switch($Name){'Inventory'{return @($script:InventoryColumns)}'Eis'{return @($script:EisColumns)}'Series'{return @($script:SeriesColumns)}'Area'{return @($script:AreaColumns)}'Direct'{return @($script:DirectColumns)}'Ledger'{return @($script:LedgerColumns)}}
}

function Write-M03A6Utf8NoBomText {
    param([Parameter(Mandatory=$true)][string]$Path,[Parameter(Mandatory=$true)][AllowEmptyString()][string]$Text)
    $Parent=[IO.Path]::GetDirectoryName([IO.Path]::GetFullPath($Path))
    if(-not[IO.Directory]::Exists($Parent)){[void][IO.Directory]::CreateDirectory($Parent)}
    [IO.File]::WriteAllText([IO.Path]::GetFullPath($Path),$Text,[Text.UTF8Encoding]::new($false))
}

function Write-M03A6Utf8NoBomCsv {
    param([Parameter(Mandatory=$true)][string[]]$Columns,[object[]]$Rows=@(),[Parameter(Mandatory=$true)][string]$Path)
    $Lines=New-Object Collections.Generic.List[string]
    [void]$Lines.Add(($Columns|ForEach-Object{'"'+$_.Replace('"','""')+'"'}) -join ',')
    foreach($Row in @($Rows)){$Fields=foreach($Column in $Columns){$Value=[string]$Row.$Column;'"'+$Value.Replace('"','""')+'"'};[void]$Lines.Add($Fields -join ',')}
    Write-M03A6Utf8NoBomText $Path (($Lines -join "`r`n")+"`r`n")
}

function Assert-M03A6Rfc4180Utf8NoBom {
    param([Parameter(Mandatory=$true)][string]$Path,[switch]$RequireDataRow)
    if(-not[IO.File]::Exists([IO.Path]::GetFullPath($Path))){throw "CSV_MISSING: $Path"}
    $Bytes=[IO.File]::ReadAllBytes([IO.Path]::GetFullPath($Path))
    if($Bytes.Length-ge 3-and$Bytes[0]-eq 0xef-and$Bytes[1]-eq 0xbb-and$Bytes[2]-eq 0xbf){throw "UTF8_BOM_FORBIDDEN: $Path"}
    try{$Text=[Text.UTF8Encoding]::new($false,$true).GetString($Bytes)}catch{throw "INVALID_UTF8: $Path"}
    if($Text.Length-eq 0){throw "RFC4180_EMPTY_CSV: $Path"}
    if([regex]::IsMatch($Text,"(?<!`r)`n")-or[regex]::IsMatch($Text,"`r(?!`n)")){throw "RFC4180_REQUIRES_CRLF: $Path"}
    $Counts=New-Object Collections.Generic.List[int];$Fields=1;$InQuote=$false;$AtStart=$true;$AfterQuote=$false
    for($Index=0;$Index-lt$Text.Length;$Index++){$C=$Text[$Index];if($InQuote){if($C-eq'"'){if($Index+1-lt$Text.Length-and$Text[$Index+1]-eq'"'){$Index++}else{$InQuote=$false;$AfterQuote=$true}};continue};if($AfterQuote){if($C-eq','){$Fields++;$AtStart=$true;$AfterQuote=$false;continue};if($C-eq"`r"){if($Index+1-ge$Text.Length-or$Text[$Index+1]-ne"`n"){throw "RFC4180_BAD_RECORD_END: $Path"};[void]$Counts.Add($Fields);$Fields=1;$AtStart=$true;$AfterQuote=$false;$Index++;continue};throw "RFC4180_CHAR_AFTER_QUOTE: $Path"};if($C-eq'"'){if(-not$AtStart){throw "RFC4180_QUOTE_IN_UNQUOTED_FIELD: $Path"};$InQuote=$true;$AtStart=$false;continue};if($C-eq','){$Fields++;$AtStart=$true;continue};if($C-eq"`r"){if($Index+1-ge$Text.Length-or$Text[$Index+1]-ne"`n"){throw "RFC4180_BAD_RECORD_END: $Path"};[void]$Counts.Add($Fields);$Fields=1;$AtStart=$true;$Index++;continue};$AtStart=$false}
    if($InQuote){throw "RFC4180_UNCLOSED_QUOTE: $Path"};if(-not$AtStart-or$AfterQuote-or$Fields-ne 1){[void]$Counts.Add($Fields)}
    if($Counts.Count-lt 1){throw "RFC4180_HEADER_MISSING: $Path"};foreach($Count in $Counts){if($Count-ne$Counts[0]){throw "RFC4180_COLUMN_COUNT_MISMATCH: $Path"}}
    if($RequireDataRow-and$Counts.Count-lt 2){throw "RFC4180_DATA_ROW_REQUIRED: $Path"};return $true
}

function Import-M03A6Csv {
    param([Parameter(Mandatory=$true)][string]$Path,[Parameter(Mandatory=$true)][string[]]$RequiredColumns,[switch]$AllowHeaderOnly,[switch]$ExactHeader)
    Assert-M03A6Rfc4180Utf8NoBom $Path -RequireDataRow:(-not$AllowHeaderOnly)|Out-Null
    $Rows=@(Import-Csv -LiteralPath $Path -Encoding UTF8)
    $Text=[IO.File]::ReadAllText([IO.Path]::GetFullPath($Path),[Text.UTF8Encoding]::new($false,$true));$HeaderLine=($Text-split"`r`n",2)[0];$Dummy=(@(1..$RequiredColumns.Count|ForEach-Object{'""'})-join',');$Probe=@("$HeaderLine`r`n$Dummy`r`n")|ConvertFrom-Csv;$Header=@($Probe[0].PSObject.Properties.Name)
    if(@($Header|Group-Object|Where-Object Count -gt 1).Count){throw "CSV_SCHEMA_DUPLICATE_HEADER: $Path"}
    foreach($Column in $RequiredColumns){if($Header-cnotcontains$Column){throw "CSV_SCHEMA_MISSING_COLUMN: $Column in $Path"}}
    if($ExactHeader){if($Header.Count-ne$RequiredColumns.Count){throw "CSV_SCHEMA_EXTRA_COLUMN: $Path"};for($I=0;$I-lt$Header.Count;$I++){if($Header[$I]-cne$RequiredColumns[$I]){throw "CSV_SCHEMA_HEADER_ORDER: $Path"}}}
    return $Rows
}

function ConvertTo-M03A6Int64Exact {param([AllowEmptyString()][string]$Value,[string]$Name='value',[switch]$Nonnegative);if([string]::IsNullOrWhiteSpace($Value)){throw "BLOCKED_REQUIRED_INT64: $Name"};$Number=[long]0;if(-not[long]::TryParse($Value,[Globalization.NumberStyles]::Integer,$script:Invariant,[ref]$Number)){throw "BLOCKED_INVALID_INT64: $Name"};if($Nonnegative-and$Number-lt 0){throw "BLOCKED_NEGATIVE_INT64: $Name"};return $Number}
function ConvertTo-M03A6FiniteDouble {param([AllowEmptyString()][string]$Value,[string]$Name='value');$Number=0.0;if([string]::IsNullOrWhiteSpace($Value)-or-not[double]::TryParse($Value,[Globalization.NumberStyles]::Float,$script:Invariant,[ref]$Number)-or[double]::IsNaN($Number)-or[double]::IsInfinity($Number)){throw "BLOCKED_INVALID_NUMERIC: $Name"};return $Number}
function Get-M03A6Sha256 {param([Parameter(Mandatory=$true)][string]$Path);return (Get-FileHash -LiteralPath ([IO.Path]::GetFullPath($Path)) -Algorithm SHA256).Hash.ToLowerInvariant()}
function Get-M03A6NormalizedFullPath {param([Parameter(Mandatory=$true)][string]$Path);return [IO.Path]::GetFullPath($Path).TrimEnd('\','/')}
function Test-M03A6PathInsideRoot {param([Parameter(Mandatory=$true)][string]$Root,[Parameter(Mandatory=$true)][string]$Candidate,[switch]$AllowRoot);$R=Get-M03A6NormalizedFullPath $Root;$C=Get-M03A6NormalizedFullPath $Candidate;if($AllowRoot-and$C.Equals($R,[StringComparison]::OrdinalIgnoreCase)){return $true};return $C.StartsWith($R+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)}

function Assert-M03A6PathSegmentsSafe {
    param([Parameter(Mandatory=$true)][string]$Path,[switch]$RequireExists)
    $Full=Get-M03A6NormalizedFullPath $Path;$Volume=[IO.Path]::GetPathRoot($Full)
    if([string]::IsNullOrWhiteSpace($Volume)){throw "BLOCKED_PATH_BOUNDARY_ESCAPE: $Path"}
    $Cursor=$Volume.TrimEnd('\','/');if($Cursor.Length-eq 2-and$Cursor[1]-eq':'){$Cursor+='\'}
    $Remainder=$Full.Substring($Volume.Length);$Segments=$Remainder.Split(@('\','/'),[StringSplitOptions]::RemoveEmptyEntries)
    if([IO.Directory]::Exists($Cursor)-or[IO.File]::Exists($Cursor)){$Item=Get-Item -LiteralPath $Cursor -Force;if(($Item.Attributes-band[IO.FileAttributes]::ReparsePoint)-ne 0){throw "BLOCKED_PATH_REPARSE_POINT: $($Item.FullName)"}}
    foreach($Segment in $Segments){$Cursor=Join-Path $Cursor $Segment;if(-not([IO.Directory]::Exists($Cursor)-or[IO.File]::Exists($Cursor))){break};$Item=Get-Item -LiteralPath $Cursor -Force;if(($Item.Attributes-band[IO.FileAttributes]::ReparsePoint)-ne 0){throw "BLOCKED_PATH_REPARSE_POINT: $($Item.FullName)"}}
    if($RequireExists-and-not([IO.Directory]::Exists($Full)-or[IO.File]::Exists($Full))){throw "BLOCKED_PATH_MISSING: $Full"};return $Full
}

function Assert-M03A6OutsideRepository {
    param([Parameter(Mandatory=$true)][string]$Candidate,[Parameter(Mandatory=$true)][string]$RepositoryRoot)
    $Repo=Assert-M03A6PathSegmentsSafe $RepositoryRoot -RequireExists;$Full=Assert-M03A6PathSegmentsSafe $Candidate
    if(Test-M03A6PathInsideRoot $Repo $Full -AllowRoot){throw "BLOCKED_DESTINATION_INSIDE_REPOSITORY: $Full"};return $Full
}

function Resolve-M03A6PackageRelativePath {
    param([Parameter(Mandatory=$true)][string]$PackageRoot,[Parameter(Mandatory=$true)][string]$RelativePath,[switch]$RequireFile,[ValidateSet('RequireExistingPath','AllowMissingLeaf','AllowMissingTail')][string]$PathMode='AllowMissingTail',[object]$CanonicalRootContext)
    return (Resolve-M03A6CanonicalPackagePath -PackageRoot $PackageRoot -RelativePath $RelativePath -RequireFile:$RequireFile -PathMode $PathMode -CanonicalRootContext $CanonicalRootContext).FullPath
}

function Get-M03A6DiskCaseMatches {
    param([Parameter(Mandatory=$true)][string]$Parent,[Parameter(Mandatory=$true)][string]$Segment,[Parameter(Mandatory=$true)][ValidateSet('ROOT','RELATIVE')][string]$Phase)
    $Matches=@(Get-ChildItem -LiteralPath $Parent -Force|Where-Object{$_.Name.Equals($Segment,[StringComparison]::OrdinalIgnoreCase)})
    if($null-ne$script:M03A6CaseEnumerationTestHook){$Directive=&$script:M03A6CaseEnumerationTestHook ([pscustomobject]@{Parent=$Parent;Segment=$Segment;Phase=$Phase;ActualMatches=@($Matches)});if($Directive-ceq'ZERO'){return}}
    return $Matches
}

function Resolve-M03A6DiskCaseFullPath {
    param([Parameter(Mandatory=$true)][string]$Path)
    $Full=Get-M03A6NormalizedFullPath $Path
    $Volume=[IO.Path]::GetPathRoot($Full)
    if([string]::IsNullOrWhiteSpace($Volume)){throw "BLOCKED_PATH_BOUNDARY_ESCAPE: $Path"}
    $Cursor=$Volume.TrimEnd('\','/');if($Cursor.Length-eq 2-and$Cursor[1]-eq':'){$Cursor+='\'}
    $Remainder=$Full.Substring($Volume.Length)
    $Segments=$Remainder.Split(@('\','/'),[StringSplitOptions]::RemoveEmptyEntries)
    foreach($Segment in $Segments){
        if(-not[IO.Directory]::Exists($Cursor)){throw "BLOCKED_PATH_CASE_NOT_FOUND: parent $Cursor"}
        $Matches=@(Get-M03A6DiskCaseMatches $Cursor $Segment ROOT)
        if($Matches.Count-eq 0){throw "BLOCKED_PATH_CASE_NOT_FOUND: $Cursor/$Segment"}
        if($Matches.Count-gt 1){throw "BLOCKED_PATH_CASE_AMBIGUOUS: $Cursor/$Segment"}
        $Cursor=Join-Path $Cursor $Matches[0].Name
    }
    return Get-M03A6NormalizedFullPath $Cursor
}

function Resolve-M03A6DiskCaseFromRoot {
    param([Parameter(Mandatory=$true)][string]$CanonicalRoot,[Parameter(Mandatory=$true)][string]$FullPath,[Parameter(Mandatory=$true)][ValidateSet('RequireExistingPath','AllowMissingLeaf','AllowMissingTail')][string]$PathMode)
    $Root=Get-M03A6NormalizedFullPath $CanonicalRoot;$Full=Get-M03A6NormalizedFullPath $FullPath
    if(-not(Test-M03A6PathInsideRoot $Root $Full)){throw "BLOCKED_PATH_BOUNDARY_ESCAPE: $FullPath"}
    $Relative=$Full.Substring($Root.Length).TrimStart('\','/');$Segments=$Relative.Split(@('\','/'),[StringSplitOptions]::RemoveEmptyEntries)
    $Cursor=$Root;$MissingTailStarted=$false
    for($Index=0;$Index-lt$Segments.Count;$Index++){$Segment=$Segments[$Index]
        if($MissingTailStarted){$Cursor=Join-Path $Cursor $Segment;continue}
        if(-not[IO.Directory]::Exists($Cursor)){throw "BLOCKED_PATH_CASE_NOT_FOUND: parent $Cursor"}
        $Matches=@(Get-M03A6DiskCaseMatches $Cursor $Segment RELATIVE)
        if($Matches.Count-gt 1){throw "BLOCKED_PATH_CASE_AMBIGUOUS: $Cursor/$Segment"}
        if($Matches.Count-eq 1){$Cursor=Join-Path $Cursor $Matches[0].Name;continue}
        $Candidate=Join-Path $Cursor $Segment
        if([IO.File]::Exists($Candidate)-or[IO.Directory]::Exists($Candidate)){throw "BLOCKED_PATH_CASE_NOT_FOUND: existing $Candidate"}
        if($PathMode-eq'RequireExistingPath'){throw "BLOCKED_PATH_CASE_NOT_FOUND: required $Candidate"}
        if($PathMode-eq'AllowMissingLeaf'-and$Index-ne($Segments.Count-1)){throw "BLOCKED_PATH_CASE_NOT_FOUND: non-leaf $Candidate"}
        $MissingTailStarted=$true;$Cursor=$Candidate
    }
    return [pscustomobject]@{CanonicalFullPath=(Get-M03A6NormalizedFullPath $Cursor);MissingTailStarted=$MissingTailStarted}
}

function Resolve-M03A6CanonicalRoot {
    param([Parameter(Mandatory=$true)][string]$PackageRoot)
    $RootInput=Assert-M03A6PathSegmentsSafe $PackageRoot -RequireExists
    if(-not[IO.Directory]::Exists($RootInput)){throw "BLOCKED_PATH_CASE_NOT_FOUND: package root $RootInput"}
    $Canonical=Resolve-M03A6DiskCaseFullPath $RootInput
    return [pscustomobject]@{CanonicalFullPath=$Canonical;CanonicalIdentityKey=$Canonical;VolumeRoot=[IO.Path]::GetPathRoot($Canonical)}
}

function Resolve-M03A6CanonicalPackagePath {
    param([Parameter(Mandatory=$true)][string]$PackageRoot,[Parameter(Mandatory=$true)][string]$RelativePath,[switch]$RequireFile,[ValidateSet('RequireExistingPath','AllowMissingLeaf','AllowMissingTail')][string]$PathMode='AllowMissingTail',[object]$CanonicalRootContext)
    if([string]::IsNullOrWhiteSpace($RelativePath)-or[IO.Path]::IsPathRooted($RelativePath)){throw "BLOCKED_PATH_BOUNDARY_ESCAPE: $RelativePath"}
    if($RequireFile){$PathMode='RequireExistingPath'}
    if($null-eq$CanonicalRootContext){$CanonicalRootContext=Resolve-M03A6CanonicalRoot $PackageRoot}else{$RootInput=Assert-M03A6PathSegmentsSafe $PackageRoot -RequireExists;if(-not$RootInput.Equals([string]$CanonicalRootContext.CanonicalIdentityKey,[StringComparison]::OrdinalIgnoreCase)){throw "BLOCKED_PATH_BOUNDARY_ESCAPE: canonical root context mismatch"}}
    $Root=[string]$CanonicalRootContext.CanonicalFullPath
    $FullInput=[IO.Path]::GetFullPath((Join-Path $Root $RelativePath.Replace('/','\')))
    if(-not(Test-M03A6PathInsideRoot $Root $FullInput)){throw "BLOCKED_PATH_BOUNDARY_ESCAPE: $RelativePath"}
    [void](Assert-M03A6PathSegmentsSafe $FullInput -RequireExists:($PathMode-eq'RequireExistingPath'))
    if($RequireFile-and-not[IO.File]::Exists($FullInput)){throw "BLOCKED_FILE_MISSING: $RelativePath"}
    $DiskResult=Resolve-M03A6DiskCaseFromRoot $Root $FullInput $PathMode;$Full=$DiskResult.CanonicalFullPath
    if(-not(Test-M03A6PathInsideRoot $Root $Full)){throw "BLOCKED_PATH_BOUNDARY_ESCAPE: $RelativePath"}
    $CanonicalRelative=$Full.Substring($Root.Length).TrimStart('\','/').Replace('\','/')
    if([string]::IsNullOrWhiteSpace($CanonicalRelative)-or$CanonicalRelative.StartsWith('/')-or$CanonicalRelative.EndsWith('/')){throw "BLOCKED_PATH_BOUNDARY_ESCAPE: $RelativePath"}
    $Segments=$CanonicalRelative.Split('/');if(@($Segments|Where-Object{[string]::IsNullOrWhiteSpace($_)-or$_-eq'.'-or$_-eq'..'}).Count){throw "BLOCKED_PATH_BOUNDARY_ESCAPE: $RelativePath"}
    $InputNormalized=$RelativePath.Replace('\','/')
    $WasCanonical=$InputNormalized.Equals($CanonicalRelative,[StringComparison]::Ordinal)
    return [pscustomobject]@{FullPath=$Full;CanonicalFullPath=$Full;CanonicalRelativePath=$CanonicalRelative;CanonicalIdentityKey=$Full;WasCanonical=$WasCanonical;OriginalRelativePath=$RelativePath;PathMode=$PathMode;MissingTailStarted=$DiskResult.MissingTailStarted}
}

function Assert-M03A6NoReparsePoints {
    param([Parameter(Mandatory=$true)][string]$Root)
    $Full=Assert-M03A6PathSegmentsSafe $Root -RequireExists;if(-not[IO.Directory]::Exists($Full)){throw "BLOCKED_PATH_MISSING: $Full"};$Queue=New-Object Collections.Generic.Queue[string];$Queue.Enqueue($Full)
    while($Queue.Count-gt 0){$Directory=$Queue.Dequeue();foreach($Item in @(Get-ChildItem -LiteralPath $Directory -Force)){if(($Item.Attributes-band[IO.FileAttributes]::ReparsePoint)-ne 0){throw "BLOCKED_PATH_REPARSE_POINT: $($Item.FullName)"};if($Item.PSIsContainer){$Queue.Enqueue($Item.FullName)}}};return $true
}

function Get-M03A6PackageFiles {
    param([Parameter(Mandatory=$true)][string]$PackageRoot)
    Assert-M03A6NoReparsePoints $PackageRoot|Out-Null;$Root=Get-M03A6NormalizedFullPath $PackageRoot;$Map=New-Object 'Collections.Generic.SortedDictionary[string,object]' ([StringComparer]::Ordinal);$Queue=New-Object Collections.Generic.Queue[string];$Queue.Enqueue($Root)
    while($Queue.Count-gt 0){$Directory=$Queue.Dequeue();foreach($Item in @(Get-ChildItem -LiteralPath $Directory -Force)){if($Item.PSIsContainer){$Queue.Enqueue($Item.FullName)}else{$Relative=$Item.FullName.Substring($Root.Length).TrimStart('\','/').Replace('\','/');$Map.Add($Relative,[pscustomobject]@{Item=$Item;RelativePath=$Relative})}}};return @($Map.Values)
}

function Test-M03A6FileStateUnchanged {param([object[]]$Before);foreach($State in @($Before)){if(-not[IO.File]::Exists($State.path)){return $false};$Item=Get-Item -LiteralPath $State.path;if([long]$Item.Length-ne$State.size-or(Get-M03A6Sha256 $State.path)-cne$State.sha256-or$Item.LastWriteTimeUtc-ne$State.last_write_utc){return $false}};return $true}
function Get-M03A6FileStates {param([string[]]$Paths);return @($Paths|ForEach-Object{$Item=Get-Item -LiteralPath $_;[pscustomobject]@{path=$Item.FullName;size=[long]$Item.Length;sha256=Get-M03A6Sha256 $Item.FullName;last_write_utc=$Item.LastWriteTimeUtc;creation_utc=$Item.CreationTimeUtc}})}

function Initialize-M03A6AcquisitionPackage {
    param([Parameter(Mandatory=$true)][string]$DestinationRoot,[Parameter(Mandatory=$true)][string]$RepositoryRoot,[ValidateSet('NONE','AFTER_DIRECTORIES','AFTER_FILES')][string]$InjectFailureAt='NONE')
    $Destination=Assert-M03A6OutsideRepository $DestinationRoot $RepositoryRoot;if([IO.Directory]::Exists($Destination)-or[IO.File]::Exists($Destination)){throw "DESTINATION_ALREADY_EXISTS: $Destination"};$Parent=[IO.Path]::GetDirectoryName($Destination);[void](Assert-M03A6PathSegmentsSafe $Parent -RequireExists);if(-not[IO.Directory]::Exists($Parent)){throw "DESTINATION_PARENT_MISSING: $Parent"}
    $Stage=Join-Path $Parent ('.M03A6_STAGING_'+[guid]::NewGuid().ToString('N'));$StageCreated=$false;$DestinationCreated=$false
    try{
        [void][IO.Directory]::CreateDirectory($Stage);$StageCreated=$true;[void](Assert-M03A6PathSegmentsSafe $Stage -RequireExists)
        foreach($Relative in @('raw/eis','normalized/eis','raw/series_resistance','raw/geometry_area','raw/direct_conductivity','metadata','hashes','reports')){[void][IO.Directory]::CreateDirectory((Join-Path $Stage $Relative))}
        if($InjectFailureAt-eq'AFTER_DIRECTORIES'){throw 'INJECTED_INITIALIZER_FAILURE'}
        $Readme="# M03A.6 external experimental acquisition package`r`n`r`nThis package is outside the Git repository and initially contains no experimental observations. Preserve raw files, use package-relative paths, freeze only after completion, and use the published M03A.5 validator for formal completeness.`r`n"
        Write-M03A6Utf8NoBomText (Join-Path $Stage 'ACQUISITION_PACKAGE_README.md') $Readme
        Write-M03A6Utf8NoBomCsv $script:InventoryColumns @() (Join-Path $Stage 'FILE_INVENTORY.csv');Write-M03A6Utf8NoBomCsv $script:LedgerColumns @() (Join-Path $Stage 'HASH_LEDGER.csv')
        $Actions=@('Define sample_id and cell_id','Record operator, date, timezone, and notebook page','Acquire independent EIS replicate 1','Acquire independent EIS replicate 2','Acquire independent EIS replicate 3','Preserve raw instrument files','Export normalized EIS without row deletion','Measure fixture resistance','Measure contact resistance','Measure membrane resistance','Measure other-series resistance','Measure electrode spacing','Measure out-of-plane depth','Measure geometric electrode area','Measure EIS area','Measure current-density reporting area','Complete independent direct conductivity measurement','Freeze all files','Record exact byte sizes and SHA-256','Complete M03A.5 formal configuration and change complete rows to MEASURED','Run M03A.5 intake validator and submit strict review');$Checklist=for($I=0;$I-lt$Actions.Count;$I++){[pscustomobject][ordered]@{step_id=('S{0:D2}'-f($I+1));action=$Actions[$I];status='PLANNED';operator_initials='';completed_datetime='';notebook_reference='';notes=''}};Write-M03A6Utf8NoBomCsv @('step_id','action','status','operator_initials','completed_datetime','notebook_reference','notes') $Checklist (Join-Path $Stage 'OPERATOR_CHECKLIST.csv')
        $EisTemplate=[pscustomobject][ordered]@{};foreach($C in $script:EisColumns){$EisTemplate|Add-Member NoteProperty $C $(if($C-eq'data_origin'){'EXPERIMENTAL'}elseif($C-eq'status'){'MISSING'}else{''})};Write-M03A6Utf8NoBomCsv $script:EisColumns @($EisTemplate) (Join-Path $Stage 'metadata/EIS_METADATA.csv')
        $SeriesRows=foreach($Name in $script:RequiredSeries){$R=[pscustomobject][ordered]@{};foreach($C in $script:SeriesColumns){$R|Add-Member NoteProperty $C $(if($C-eq'component_name'){$Name}elseif($C-eq'data_origin'){'EXPERIMENTAL'}elseif($C-eq'status'){'MISSING'}else{''})};$R};Write-M03A6Utf8NoBomCsv $script:SeriesColumns $SeriesRows (Join-Path $Stage 'metadata/SERIES_RESISTANCE_EVIDENCE.csv')
        $AreaRows=foreach($Name in $script:RequiredAreas){$R=[pscustomobject][ordered]@{};foreach($C in $script:AreaColumns){$R|Add-Member NoteProperty $C $(if($C-eq'quantity_name'){$Name}elseif($C-eq'data_origin'){'EXPERIMENTAL'}elseif($C-eq'status'){'MISSING'}else{''})};$R};Write-M03A6Utf8NoBomCsv $script:AreaColumns $AreaRows (Join-Path $Stage 'metadata/GEOMETRY_AREA_EVIDENCE.csv')
        $DirectTemplate=[pscustomobject][ordered]@{};foreach($C in $script:DirectColumns){$DirectTemplate|Add-Member NoteProperty $C $(if($C-eq'data_origin'){'EXPERIMENTAL'}elseif($C-eq'status'){'MISSING'}else{''})};Write-M03A6Utf8NoBomCsv $script:DirectColumns @($DirectTemplate) (Join-Path $Stage 'metadata/DIRECT_CONDUCTIVITY_EVIDENCE.csv')
        if($InjectFailureAt-eq'AFTER_FILES'){throw 'INJECTED_INITIALIZER_FAILURE'}
        $ExpectedFiles=@('ACQUISITION_PACKAGE_README.md','FILE_INVENTORY.csv','HASH_LEDGER.csv','OPERATOR_CHECKLIST.csv','metadata/EIS_METADATA.csv','metadata/SERIES_RESISTANCE_EVIDENCE.csv','metadata/GEOMETRY_AREA_EVIDENCE.csv','metadata/DIRECT_CONDUCTIVITY_EVIDENCE.csv');$Actual=@(Get-M03A6PackageFiles $Stage|ForEach-Object{$_.RelativePath});if(@($Actual|Where-Object{$_-notin$ExpectedFiles}).Count-or@($ExpectedFiles|Where-Object{$_-notin$Actual}).Count){throw 'INITIALIZER_FILE_MANIFEST_MISMATCH'}
        $ExpectedDirectories=@('raw','raw/eis','raw/series_resistance','raw/geometry_area','raw/direct_conductivity','normalized','normalized/eis','metadata','hashes','reports');$ActualDirectories=@(Get-ChildItem -LiteralPath $Stage -Directory -Recurse -Force|ForEach-Object{$_.FullName.Substring($Stage.Length).TrimStart('\','/').Replace('\','/')});if(@($ActualDirectories|Where-Object{$_-notin$ExpectedDirectories}).Count-or@($ExpectedDirectories|Where-Object{$_-notin$ActualDirectories}).Count){throw 'INITIALIZER_DIRECTORY_MANIFEST_MISMATCH'}
        foreach($Spec in @(@('FILE_INVENTORY.csv',$script:InventoryColumns),@('HASH_LEDGER.csv',$script:LedgerColumns),@('OPERATOR_CHECKLIST.csv',@('step_id','action','status','operator_initials','completed_datetime','notebook_reference','notes')),@('metadata/EIS_METADATA.csv',$script:EisColumns),@('metadata/SERIES_RESISTANCE_EVIDENCE.csv',$script:SeriesColumns),@('metadata/GEOMETRY_AREA_EVIDENCE.csv',$script:AreaColumns),@('metadata/DIRECT_CONDUCTIVITY_EVIDENCE.csv',$script:DirectColumns))){[void](Import-M03A6Csv (Join-Path $Stage $Spec[0]) $Spec[1] -AllowHeaderOnly -ExactHeader)}
        [IO.Directory]::Move($Stage,$Destination);$StageCreated=$false;$DestinationCreated=$true;[void](Assert-M03A6OutsideRepository $Destination $RepositoryRoot);[void](Assert-M03A6NoReparsePoints $Destination);return $Destination
    }catch{if($StageCreated-and[IO.Directory]::Exists($Stage)){[IO.Directory]::Delete($Stage,$true)};if($DestinationCreated-and[IO.Directory]::Exists($Destination)){[IO.Directory]::Delete($Destination,$true)};throw}
}

function Freeze-M03A6ExperimentalEvidence {
    param([Parameter(Mandatory=$true)][string]$PackageRoot,[Parameter(Mandatory=$true)][ValidatePattern('^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$')][string]$LedgerVersion,[string]$RepositoryRoot=(Resolve-Path(Join-Path $PSScriptRoot '..\..\..')).Path)
    $RootInput=Assert-M03A6OutsideRepository $PackageRoot $RepositoryRoot;$RootContext=Resolve-M03A6CanonicalRoot $RootInput;$Root=$RootContext.CanonicalFullPath;[void](Assert-M03A6PathSegmentsSafe $Root -RequireExists);Assert-M03A6NoReparsePoints $Root|Out-Null;$Hashes=Join-Path $Root 'hashes';$Reports=Join-Path $Root 'reports';foreach($D in @($Hashes,$Reports)){[void](Assert-M03A6PathSegmentsSafe $D -RequireExists);if(-not[IO.Directory]::Exists($D)){throw "PACKAGE_DIRECTORY_MISSING: $D"}}
    foreach($Existing in @(Get-ChildItem -LiteralPath $Hashes -File -Filter 'HASH_LEDGER_*.csv')){$V=$Existing.BaseName.Substring('HASH_LEDGER_'.Length);if($V.Equals($LedgerVersion,[StringComparison]::OrdinalIgnoreCase)){throw "LEDGER_VERSION_ALREADY_EXISTS: $LedgerVersion"}}
    foreach($Existing in @(Get-ChildItem -LiteralPath $Reports -File -Filter 'FREEZE_VERIFICATION_*.md')){$V=$Existing.BaseName.Substring('FREEZE_VERIFICATION_'.Length);if($V.Equals($LedgerVersion,[StringComparison]::OrdinalIgnoreCase)){throw "LEDGER_VERSION_ALREADY_EXISTS: $LedgerVersion"}}
    $LedgerPath=Join-Path $Hashes ("HASH_LEDGER_$LedgerVersion.csv");$ReportPath=Join-Path $Reports ("FREEZE_VERIFICATION_$LedgerVersion.md");$Guid=[guid]::NewGuid().ToString('N');$LedgerTemp=Join-Path $Hashes (".M03A6_LEDGER_$Guid.tmp");$ReportTemp=Join-Path $Reports (".M03A6_REPORT_$Guid.tmp");$LedgerMoved=$false;$ReportMoved=$false
    $Files=@(Get-M03A6PackageFiles $Root);$RawStates=Get-M03A6FileStates @($Files|Where-Object{$_.RelativePath-like'raw/*'}|ForEach-Object{$_.Item.FullName});$Now=[DateTimeOffset]::Now;$Frozen=$Now.ToString("yyyy-MM-dd'T'HH:mm:ss.fffffffzzz",$script:Invariant);$Offset=$Now.ToString('zzz',$script:Invariant)
    $Rows=foreach($File in $Files){[pscustomobject][ordered]@{ledger_version=$LedgerVersion;relative_path=$File.RelativePath;size_bytes=([long]$File.Item.Length).ToString($script:Invariant);sha256=Get-M03A6Sha256 $File.Item.FullName;frozen_at=$Frozen;timezone_offset=$Offset;tool_version=$script:ToolVersion}}
    try{Write-M03A6Utf8NoBomCsv $script:LedgerColumns $Rows $LedgerTemp;[void](Import-M03A6Csv $LedgerTemp $script:LedgerColumns -ExactHeader);$Verified=0;foreach($Row in @(Import-Csv -LiteralPath $LedgerTemp -Encoding UTF8)){$Full=Resolve-M03A6PackageRelativePath $Root $Row.relative_path -RequireFile -CanonicalRootContext $RootContext;$Size=ConvertTo-M03A6Int64Exact $Row.size_bytes "$($Row.relative_path) size" -Nonnegative;if([long](Get-Item -LiteralPath $Full).Length-ne$Size-or(Get-M03A6Sha256 $Full)-cne$Row.sha256){throw "FREEZE_RECHECK_FAILED: $($Row.relative_path)"};$Verified++};$Report=@('# M03A.6 freeze verification report','',"- ledger_version: $LedgerVersion","- frozen_at: $Frozen","- timezone_offset: $Offset","- tool_version: $script:ToolVersion","- files_verified: $Verified",'- exact_size_type: Int64','- hash_algorithm: SHA-256','- source_files_modified: FALSE','- raw_timestamp_modified: FALSE','- verification_status: PASS','')-join"`r`n";Write-M03A6Utf8NoBomText $ReportTemp $Report;if(-not(Test-M03A6FileStateUnchanged $RawStates)){throw 'RAW_FILE_CHANGED_DURING_FREEZE'};[IO.File]::Move($LedgerTemp,$LedgerPath);$LedgerMoved=$true;[IO.File]::Move($ReportTemp,$ReportPath);$ReportMoved=$true;(Get-Item -LiteralPath $ReportPath).IsReadOnly=$true;if(-not(Test-M03A6FileStateUnchanged $RawStates)){throw 'RAW_FILE_CHANGED_DURING_FREEZE'};return [pscustomobject]@{LedgerPath=$LedgerPath;ReportPath=$ReportPath;FileCount=$Verified;FrozenAt=$Frozen;ToolVersion=$script:ToolVersion;RawBytesUnchanged=$true;RawTimestampUnchanged=$true}}catch{foreach($P in @($LedgerTemp,$ReportTemp)){if([IO.File]::Exists($P)){[IO.File]::Delete($P)}};if($ReportMoved-and[IO.File]::Exists($ReportPath)){[IO.File]::SetAttributes($ReportPath,[IO.FileAttributes]::Normal);[IO.File]::Delete($ReportPath)};if($LedgerMoved-and[IO.File]::Exists($LedgerPath)){[IO.File]::Delete($LedgerPath)};throw}
}

function Prepare-M03A6NormalizedEis {
    param([Parameter(Mandatory=$true)][string]$InputPath,[Parameter(Mandatory=$true)][string]$OutputPath,[Parameter(Mandatory=$true)][string]$ParserProfile,[string]$FrequencyColumn,[string]$ZRealColumn,[string]$ZImagColumn,[string]$RowMappingPath,[string]$ProvenancePath)
    if($ParserProfile-cne'EXPLICIT_RFC4180_COLUMN_MAP'){throw "UNSUPPORTED_PROFILE: $ParserProfile"};foreach($P in @(@('frequency',$FrequencyColumn),@('z_real',$ZRealColumn),@('z_imag',$ZImagColumn))){if([string]::IsNullOrWhiteSpace($P[1])){throw "EXPLICIT_COLUMN_REQUIRED: $($P[0])"}}
    $Input=Assert-M03A6PathSegmentsSafe $InputPath -RequireExists;if(-not[IO.File]::Exists($Input)){throw 'INPUT_FILE_MISSING'};$Output=Get-M03A6NormalizedFullPath $OutputPath;if([string]::IsNullOrWhiteSpace($RowMappingPath)){$RowMappingPath=$Output+'.row_mapping.csv'};if([string]::IsNullOrWhiteSpace($ProvenancePath)){$ProvenancePath=$Output+'.conversion_provenance.csv'};$Mapping=Get-M03A6NormalizedFullPath $RowMappingPath;$Provenance=Get-M03A6NormalizedFullPath $ProvenancePath
    $All=@($Input,$Output,$Mapping,$Provenance);for($I=0;$I-lt$All.Count;$I++){for($J=$I+1;$J-lt$All.Count;$J++){if($All[$I].Equals($All[$J],[StringComparison]::OrdinalIgnoreCase)){throw 'BLOCKED_NORMALIZED_PATH_COLLISION'}}}
    foreach($Final in @($Output,$Mapping,$Provenance)){if([IO.File]::Exists($Final)-or[IO.Directory]::Exists($Final)){throw "OUTPUT_ALREADY_EXISTS: $Final"};$Parent=[IO.Path]::GetDirectoryName($Final);[void](Assert-M03A6PathSegmentsSafe $Parent -RequireExists);if(-not[IO.Directory]::Exists($Parent)){throw "OUTPUT_PARENT_MISSING: $Parent"}}
    $InputState=(Get-M03A6FileStates @($Input))[0];$Rows=@(Import-M03A6Csv $Input @($FrequencyColumn,$ZRealColumn,$ZImagColumn));$NormalizedRows=New-Object Collections.Generic.List[object];$MapRows=New-Object Collections.Generic.List[object]
    for($I=0;$I-lt$Rows.Count;$I++){$F=[string]$Rows[$I].$FrequencyColumn;$ZR=[string]$Rows[$I].$ZRealColumn;$ZI=[string]$Rows[$I].$ZImagColumn;$FN=ConvertTo-M03A6FiniteDouble $F "row $($I+2) frequency";if($FN-le 0){throw "BLOCKED_NONPOSITIVE_FREQUENCY: row $($I+2)"};[void](ConvertTo-M03A6FiniteDouble $ZR "row $($I+2) z_real");[void](ConvertTo-M03A6FiniteDouble $ZI "row $($I+2) z_imag");[void]$NormalizedRows.Add([pscustomobject][ordered]@{frequency=$F;z_real=$ZR;z_imag=$ZI});[void]$MapRows.Add([pscustomobject][ordered]@{source_row=($I+2).ToString($script:Invariant);output_row=($I+2).ToString($script:Invariant);row_action='RETAINED'})};if($NormalizedRows.Count-eq 0){throw 'INPUT_DATA_ROW_REQUIRED'}
    $Guid=[guid]::NewGuid().ToString('N');$OT=Join-Path ([IO.Path]::GetDirectoryName($Output)) ('.M03A6_NORM_'+$Guid+'.tmp');$MT=Join-Path ([IO.Path]::GetDirectoryName($Mapping)) ('.M03A6_MAP_'+$Guid+'.tmp');$PT=Join-Path ([IO.Path]::GetDirectoryName($Provenance)) ('.M03A6_PROV_'+$Guid+'.tmp');$Moved=New-Object Collections.Generic.List[string]
    try{Write-M03A6Utf8NoBomCsv @('frequency','z_real','z_imag') $NormalizedRows $OT;Write-M03A6Utf8NoBomCsv @('source_row','output_row','row_action') $MapRows $MT;[void](Import-M03A6Csv $OT @('frequency','z_real','z_imag') -ExactHeader);[void](Import-M03A6Csv $MT @('source_row','output_row','row_action') -ExactHeader);$Now=[DateTimeOffset]::Now;$Date=$Now.ToString("yyyy-MM-dd'T'HH:mm:ss.fffffffzzz",$script:Invariant);$Zone=$Now.ToString('zzz',$script:Invariant);$Prov=[pscustomobject][ordered]@{tool_name=$script:ToolName;tool_version=$script:ToolVersion;parser_profile=$ParserProfile;frequency_column=$FrequencyColumn;z_real_column=$ZRealColumn;z_imag_column=$ZImagColumn;input_path=$Input;input_size_bytes=$InputState.size.ToString($script:Invariant);input_sha256=$InputState.sha256;output_path=$Output;output_size_bytes=([long](Get-Item -LiteralPath $OT).Length).ToString($script:Invariant);output_sha256=Get-M03A6Sha256 $OT;row_mapping_path=$Mapping;row_mapping_size_bytes=([long](Get-Item -LiteralPath $MT).Length).ToString($script:Invariant);row_mapping_sha256=Get-M03A6Sha256 $MT;source_row_count=$Rows.Count.ToString($script:Invariant);output_row_count=$NormalizedRows.Count.ToString($script:Invariant);conversion_datetime=$Date;conversion_timezone=$Zone;smoothing_applied='FALSE';interpolation_applied='FALSE';outlier_removal_applied='FALSE';sorting_applied='FALSE';hfr_calculated='FALSE'};$ProvColumns=@($Prov.PSObject.Properties.Name);Write-M03A6Utf8NoBomCsv $ProvColumns @($Prov) $PT;[void](Import-M03A6Csv $PT $ProvColumns -ExactHeader);if(-not(Test-M03A6FileStateUnchanged @($InputState))){throw 'RAW_INPUT_CHANGED_DURING_CONVERSION'};foreach($Pair in @(@($OT,$Output),@($MT,$Mapping),@($PT,$Provenance))){[IO.File]::Move($Pair[0],$Pair[1]);[void]$Moved.Add($Pair[1])};if(-not(Test-M03A6FileStateUnchanged @($InputState))){throw 'RAW_INPUT_CHANGED_DURING_CONVERSION'};return [pscustomobject]@{OutputPath=$Output;RowMappingPath=$Mapping;ProvenancePath=$Provenance;RowCount=$NormalizedRows.Count;InputUnchanged=$true;RawBytesUnchanged=$true;RawTimestampUnchanged=$true}}catch{foreach($P in @($OT,$MT,$PT)){if([IO.File]::Exists($P)){[IO.File]::Delete($P)}};foreach($P in $Moved){if([IO.File]::Exists($P)){[IO.File]::Delete($P)}};throw}
}

function Test-M03A6DateTimeWithTimezone {param([string]$Value);if([string]::IsNullOrWhiteSpace($Value)-or$Value-notmatch'(Z|[+-][0-9]{2}:[0-9]{2})$'){return $false};$Parsed=[DateTimeOffset]::MinValue;return [DateTimeOffset]::TryParse($Value,$script:Invariant,[Globalization.DateTimeStyles]::None,[ref]$Parsed)}
function New-M03A6PreflightResult {param([string]$Status,[object[]]$Findings);return [pscustomobject]@{Status=$Status;Findings=@($Findings);ExperimentalEvidenceReceived=($Status-eq'EXPERIMENTAL_EVIDENCE_RECEIVED');ExperimentalInputComplete=$false;ParameterTransferMode='NOT_RUN';M03BCandidate=$false;M03BReady=$false}}

function Invoke-M03A6Preflight {
    param([Parameter(Mandatory=$true)][string]$PackageRoot,[string]$HashLedgerPath,[string]$RepositoryRoot=(Resolve-Path(Join-Path $PSScriptRoot '..\..\..')).Path)
    $Findings=New-Object Collections.Generic.List[object]
    function Add([string]$Code,[string]$Detail){[void]$Findings.Add([pscustomobject][ordered]@{code=$Code;detail=$Detail})}
    function AddEx([string]$Message){$Code=($Message-split':',2)[0];if($Code-notmatch'^BLOCKED_'){$Code='BLOCKED_PACKAGE_STRUCTURE'};Add $Code $Message}
    function AddNoncanonical([object]$Resolved,[string]$Context){if(-not$Resolved.WasCanonical){Add 'BLOCKED_NONCANONICAL_EVIDENCE_PATH' "$Context/$($Resolved.OriginalRelativePath) -> $($Resolved.CanonicalRelativePath)"}}
    function TestRoleDirectory([object]$Resolved,[string]$Role){$RoleRoot=Get-M03A6NormalizedFullPath (Join-Path $Root $script:RoleDirectories[$Role].TrimEnd('/'));return Test-M03A6PathInsideRoot $RoleRoot $Resolved.FullPath}
    function ResolveLedgerRowPath([string]$RelativePath){
        try{$Resolved=Resolve-M03A6CanonicalPackagePath $Root $RelativePath -RequireFile -PathMode RequireExistingPath -CanonicalRootContext $RootContext}
        catch{if($_.Exception.Message-match'^(BLOCKED_PATH_MISSING|BLOCKED_FILE_MISSING|BLOCKED_PATH_CASE_NOT_FOUND):'){throw "BLOCKED_LEDGER_FILE_MISSING: $RelativePath"};throw}
        return $Resolved
    }
    function Resolve-M03A6SelectedLedgerPath {
        param(
            [Parameter(Mandatory=$true)][string]$SelectedPackageRoot,
            [Parameter(Mandatory=$true)][object]$SelectedRootContext,
            [Parameter(Mandatory=$true)][string]$OriginalSelectedPath,
            [Parameter(Mandatory=$true)][ValidateSet('EXPLICIT','AUTO_SELECTED')][string]$PathSource
        )
        if([string]::IsNullOrWhiteSpace($OriginalSelectedPath)-or-not[IO.Path]::IsPathRooted($OriginalSelectedPath)){throw "BLOCKED_NONCANONICAL_EVIDENCE_PATH: selected ledger must be an absolute rooted path ($PathSource)"}
        $OriginalCallerFullPath=$OriginalSelectedPath
        $SeparatorNormalized=$OriginalCallerFullPath.Replace('/','\')
        if($SeparatorNormalized.EndsWith('\')){throw "BLOCKED_NONCANONICAL_EVIDENCE_PATH: selected ledger trailing separator ($PathSource)"}
        try{$Volume=[IO.Path]::GetPathRoot($SeparatorNormalized)}catch{throw "BLOCKED_NONCANONICAL_EVIDENCE_PATH: selected ledger invalid root ($PathSource)"}
        if([string]::IsNullOrWhiteSpace($Volume)-or$SeparatorNormalized.Length-le$Volume.Length){throw "BLOCKED_NONCANONICAL_EVIDENCE_PATH: selected ledger invalid absolute path ($PathSource)"}
        $Remainder=$SeparatorNormalized.Substring($Volume.Length)
        if($Remainder.Contains('\\')){throw "BLOCKED_NONCANONICAL_EVIDENCE_PATH: selected ledger repeated separator ($PathSource)"}
        $Segments=$Remainder.Split('\')
        $InvalidFileNameChars=[IO.Path]::GetInvalidFileNameChars()
        foreach($Segment in $Segments){
            if([string]::IsNullOrEmpty($Segment)-or$Segment-ceq'.'-or$Segment-ceq'..'){throw "BLOCKED_NONCANONICAL_EVIDENCE_PATH: selected ledger dot or empty segment ($PathSource)"}
            if($Segment.IndexOfAny($InvalidFileNameChars)-ge0-or$Segment.EndsWith('.')-or$Segment.EndsWith(' ')){throw "BLOCKED_NONCANONICAL_EVIDENCE_PATH: selected ledger invalid segment ($PathSource)"}
        }
        try{$NormalizedForBoundary=[IO.Path]::GetFullPath($SeparatorNormalized)}catch{throw "BLOCKED_NONCANONICAL_EVIDENCE_PATH: selected ledger invalid path ($PathSource)"}
        if(-not$SeparatorNormalized.Equals($NormalizedForBoundary,[StringComparison]::Ordinal)){throw "BLOCKED_NONCANONICAL_EVIDENCE_PATH: selected ledger lexical alias ($PathSource)"}
        $CanonicalRoot=[string]$SelectedRootContext.CanonicalFullPath
        if(-not(Test-M03A6PathInsideRoot $CanonicalRoot $NormalizedForBoundary)){throw 'BLOCKED_HASH_LEDGER: ledger must be inside package root'}
        $Relative=$NormalizedForBoundary.Substring($CanonicalRoot.Length).TrimStart('\','/').Replace('\','/')
        $Resolved=Resolve-M03A6CanonicalPackagePath $SelectedPackageRoot $Relative -RequireFile -PathMode RequireExistingPath -CanonicalRootContext $SelectedRootContext
        if(-not(Test-M03A6PathInsideRoot (Join-Path $CanonicalRoot 'hashes') $Resolved.CanonicalFullPath)){throw 'BLOCKED_HASH_LEDGER: ledger must be under hashes'}
        if(-not$OriginalCallerFullPath.Equals($Resolved.CanonicalFullPath,[StringComparison]::Ordinal)){throw "BLOCKED_NONCANONICAL_EVIDENCE_PATH: selected ledger/$OriginalCallerFullPath -> $($Resolved.CanonicalFullPath) ($PathSource)"}
        return [pscustomobject]@{FullPath=$Resolved.CanonicalFullPath;CanonicalFullPath=$Resolved.CanonicalFullPath;CanonicalRelativePath=$Resolved.CanonicalRelativePath;CanonicalIdentityKey=$Resolved.CanonicalIdentityKey;OriginalPath=$OriginalCallerFullPath;PathSource=$PathSource}
    }
    function ResolveValidatedSelectedLedgerRow([object]$Row,[string]$SelectedLedgerFull,[object]$ExistingLedgerMap){
        try{
            $Resolved=ResolveLedgerRowPath ([string]$Row.relative_path)
            $Key=$Resolved.CanonicalIdentityKey

            # Recompute both actual integrity values immediately after required-existing
            # resolution. Semantic map checks must not short-circuit these reads.
            $ActualItem=Get-Item -LiteralPath $Resolved.FullPath -Force
            $ActualSize=[long]$ActualItem.Length
            $ActualHash=Get-M03A6Sha256 $Resolved.FullPath
            $DeclaredSize=ConvertTo-M03A6Int64Exact ([string]$Row.size_bytes) "$($Resolved.CanonicalRelativePath) ledger size" -Nonnegative
            $DeclaredHash=[string]$Row.sha256
            $Valid=$true
            if($DeclaredSize-ne$ActualSize){Add 'BLOCKED_LEDGER_SIZE_MISMATCH' $Resolved.CanonicalRelativePath;$Valid=$false}
            if($DeclaredHash-notmatch'^[0-9a-fA-F]{64}$'-or$DeclaredHash.ToLowerInvariant()-cne$ActualHash){Add 'BLOCKED_LEDGER_HASH_MISMATCH' $Resolved.CanonicalRelativePath;$Valid=$false}

            # Perform semantic checks only after actual size and hash have been read and
            # compared. Return a temporary validated row; the caller inserts it once.
            if(-not$Resolved.WasCanonical){AddNoncanonical $Resolved 'ledger';$Valid=$false}
            if($null-ne$SelectedLedgerFull-and$Resolved.FullPath.Equals($SelectedLedgerFull,[StringComparison]::OrdinalIgnoreCase)){Add 'BLOCKED_LEDGER_SELF_REFERENCE' $Resolved.CanonicalRelativePath;$Valid=$false}
            if($ExistingLedgerMap.ContainsKey($Key)){Add 'BLOCKED_DUPLICATE_LEDGER_PHYSICAL_PATH' $Resolved.CanonicalRelativePath;Add 'BLOCKED_DUPLICATE_LEDGER_PATH' $Resolved.CanonicalRelativePath;$Valid=$false}
            if($Valid){return [pscustomobject]@{Key=$Key;Row=$Row;Resolved=$Resolved;ActualSize=$ActualSize;ActualHash=$ActualHash}}
        }catch{AddEx $_.Exception.Message}
        return $null
    }
    try{
        $RootInput=Assert-M03A6OutsideRepository $PackageRoot $RepositoryRoot;$RootContext=Resolve-M03A6CanonicalRoot $RootInput;$Root=$RootContext.CanonicalFullPath
        [void](Assert-M03A6NoReparsePoints $Root)
        foreach($D in @('raw/eis','normalized/eis','raw/series_resistance','raw/geometry_area','raw/direct_conductivity','metadata','hashes','reports')){$Full=Join-Path $Root $D;if(-not[IO.Directory]::Exists($Full)){throw "BLOCKED_PACKAGE_STRUCTURE: missing $D"}}
        $InventoryAll=@(Import-M03A6Csv (Join-Path $Root 'FILE_INVENTORY.csv') $script:InventoryColumns -AllowHeaderOnly -ExactHeader)
        $EisAll=@(Import-M03A6Csv (Join-Path $Root 'metadata/EIS_METADATA.csv') $script:EisColumns -AllowHeaderOnly -ExactHeader)
        $SeriesAll=@(Import-M03A6Csv (Join-Path $Root 'metadata/SERIES_RESISTANCE_EVIDENCE.csv') $script:SeriesColumns -AllowHeaderOnly -ExactHeader)
        $AreaAll=@(Import-M03A6Csv (Join-Path $Root 'metadata/GEOMETRY_AREA_EVIDENCE.csv') $script:AreaColumns -AllowHeaderOnly -ExactHeader)
        $DirectAll=@(Import-M03A6Csv (Join-Path $Root 'metadata/DIRECT_CONDUCTIVITY_EVIDENCE.csv') $script:DirectColumns -AllowHeaderOnly -ExactHeader)
        $Inventory=@($InventoryAll|Where-Object{$_.status-in@('MEASURED','PRESENT')})
        $Eis=@($EisAll|Where-Object status -ne 'MISSING');$Series=@($SeriesAll|Where-Object status -ne 'MISSING');$Areas=@($AreaAll|Where-Object status -ne 'MISSING');$Direct=@($DirectAll|Where-Object status -ne 'MISSING')
        $EvidenceFiles=@(Get-M03A6PackageFiles $Root|Where-Object{$R=$_.RelativePath;@($script:RoleDirectories.Values|Where-Object{$R.StartsWith($_,[StringComparison]::OrdinalIgnoreCase)}).Count-gt 0})
        $LedgerEvidenceRows=New-Object Collections.Generic.List[string]
        $LedgerCandidates=@((Join-Path $Root 'HASH_LEDGER.csv'))+@(Get-ChildItem -LiteralPath (Join-Path $Root 'hashes') -File -Filter 'HASH_LEDGER_*.csv'|ForEach-Object{$_.FullName})
        foreach($CandidateLedger in $LedgerCandidates){if(-not[IO.File]::Exists($CandidateLedger)){continue};try{foreach($LedgerRow in @(Import-M03A6Csv $CandidateLedger $script:LedgerColumns -AllowHeaderOnly -ExactHeader)){$Resolved=ResolveLedgerRowPath ([string]$LedgerRow.relative_path);AddNoncanonical $Resolved 'ledger';if(@($script:RoleDirectories.Values|Where-Object{$Resolved.CanonicalRelativePath.StartsWith($_,[StringComparison]::OrdinalIgnoreCase)}).Count){[void]$LedgerEvidenceRows.Add($Resolved.CanonicalRelativePath)}}}catch{AddEx $_.Exception.Message}}
        if($Inventory.Count+$Eis.Count+$Series.Count+$Areas.Count+$Direct.Count-eq 0){if($EvidenceFiles.Count-or$LedgerEvidenceRows.Count){$EvidenceRelative=@($EvidenceFiles|ForEach-Object{$_.RelativePath});Add 'BLOCKED_UNREGISTERED_EVIDENCE_FILE' (($EvidenceRelative+[string[]]$LedgerEvidenceRows.ToArray())-join'; ')};if($Findings.Count){return New-M03A6PreflightResult 'PRE_INTAKE_BLOCKED' $Findings.ToArray()};return New-M03A6PreflightResult 'ACQUISITION_PACKAGE_READY' @()}
        foreach($Row in @($Inventory+$Eis+$Series+$Areas+$Direct)){if([string]$Row.data_origin-cne'EXPERIMENTAL'){Add 'BLOCKED_NONEXPERIMENTAL_ORIGIN' 'data_origin must be EXPERIMENTAL'};if([string]$Row.status-notin@('MEASURED','PRESENT')){Add 'BLOCKED_STATUS_NOT_MEASURED' 'active evidence status must be MEASURED or PRESENT'}}

        $InvPathMap=New-Object 'Collections.Generic.Dictionary[string,object]' ([StringComparer]::OrdinalIgnoreCase)
        foreach($Row in $Inventory){
            $Role=[string]$Row.evidence_role;$Rel=[string]$Row.relative_path
            try{$Resolved=Resolve-M03A6CanonicalPackagePath $Root $Rel -RequireFile -CanonicalRootContext $RootContext;AddNoncanonical $Resolved 'inventory';$Key=$Resolved.CanonicalIdentityKey
                if(-not$InvPathMap.ContainsKey($Key)){$InvPathMap[$Key]=New-Object Collections.Generic.List[object]}
                $Existing=[object[]]$InvPathMap[$Key].ToArray();if($Existing.Count){if(@($Existing|Where-Object{$_.evidence_role-cne$Role}).Count){Add 'BLOCKED_CONFLICTING_PHYSICAL_EVIDENCE_ROLE' $Resolved.CanonicalRelativePath;Add 'BLOCKED_CONFLICTING_EVIDENCE_ROLE' $Resolved.CanonicalRelativePath}else{Add 'BLOCKED_DUPLICATE_PHYSICAL_EVIDENCE' $Resolved.CanonicalRelativePath;Add 'BLOCKED_DUPLICATE_ROLE_PATH' $Resolved.CanonicalRelativePath}}
                [void]$InvPathMap[$Key].Add($Row)
                if($Role-notin$script:EvidenceRoles){Add 'BLOCKED_UNKNOWN_EVIDENCE_ROLE' $Role}else{if(-not(TestRoleDirectory $Resolved $Role)){Add 'BLOCKED_EVIDENCE_ROLE_DIRECTORY_MISMATCH' "$Role/$Rel"}}
            }catch{AddEx $_.Exception.Message}
        }
        foreach($File in $EvidenceFiles){$Key=$File.Item.FullName;if(-not$InvPathMap.ContainsKey($Key)){Add 'BLOCKED_UNREGISTERED_EVIDENCE_FILE' $File.RelativePath}}
        $LedgerPathSource='EXPLICIT';if([string]::IsNullOrWhiteSpace($HashLedgerPath)){$LedgerPathSource='AUTO_SELECTED';$Candidates=@(Get-ChildItem -LiteralPath(Join-Path $Root 'hashes') -File -Filter'HASH_LEDGER_*.csv');if($Candidates.Count-eq 1){$HashLedgerPath=$Candidates[0].FullName}else{Add 'BLOCKED_HASH_LEDGER_SELECTION' "found $($Candidates.Count) ledgers"}}
        $Ledger=@();$LedgerFull=$null;if(-not[string]::IsNullOrWhiteSpace($HashLedgerPath)){try{$LedgerResolved=Resolve-M03A6SelectedLedgerPath -SelectedPackageRoot $Root -SelectedRootContext $RootContext -OriginalSelectedPath $HashLedgerPath -PathSource $LedgerPathSource;$LedgerFull=$LedgerResolved.FullPath;$Ledger=@(Import-M03A6Csv $LedgerFull $script:LedgerColumns -ExactHeader)}catch{AddEx $_.Exception.Message}}
        $LedgerMap=New-Object 'Collections.Generic.Dictionary[string,object]' ([StringComparer]::OrdinalIgnoreCase)
        foreach($Row in $Ledger){$ValidatedRow=ResolveValidatedSelectedLedgerRow $Row $LedgerFull $LedgerMap;if($null-ne$ValidatedRow){$LedgerMap[$ValidatedRow.Key]=$ValidatedRow.Row};if([string]::IsNullOrWhiteSpace($Row.frozen_at)-or-not(Test-M03A6DateTimeWithTimezone $Row.frozen_at)-or[string]::IsNullOrWhiteSpace($Row.timezone_offset)-or[string]::IsNullOrWhiteSpace($Row.tool_version)){Add 'BLOCKED_HASH_LEDGER_METADATA' $Row.relative_path}}
        $Current=@(Get-M03A6PackageFiles $Root|Where-Object{$_.RelativePath-notlike'hashes/*'-and$_.RelativePath-notlike'reports/*'})
        foreach($File in $Current){$K=$File.Item.FullName;if(-not$LedgerMap.ContainsKey($K)){Add 'BLOCKED_UNHASHED_ACTUAL_FILE' $File.RelativePath;continue};$L=$LedgerMap[$K];try{$S=ConvertTo-M03A6Int64Exact $L.size_bytes "$($File.RelativePath) size" -Nonnegative;if($S-ne[long]$File.Item.Length){Add 'BLOCKED_SIZE_MISMATCH' $File.RelativePath};if($L.sha256-notmatch'^[0-9a-fA-F]{64}$'-or(Get-M03A6Sha256 $File.Item.FullName)-cne$L.sha256.ToLowerInvariant()){Add 'BLOCKED_HASH_MISMATCH' $File.RelativePath}}catch{AddEx $_.Exception.Message}}
        function CheckEvidence([object]$Row,[string]$PathField,[string]$SizeField,[string]$HashField,[string]$Role,[string]$Sample,[string]$Cell,[string]$Rep){$Rel=[string]$Row.$PathField;try{$Resolved=Resolve-M03A6CanonicalPackagePath $Root $Rel -RequireFile -CanonicalRootContext $RootContext;AddNoncanonical $Resolved 'metadata';$K=$Resolved.CanonicalIdentityKey;if(-not(TestRoleDirectory $Resolved $Role)){Add 'BLOCKED_EVIDENCE_ROLE_DIRECTORY_MISMATCH' "$Role/$Rel"};$Inv=@();if($InvPathMap.ContainsKey($K)){$Inv=@([object[]]$InvPathMap[$K].ToArray()|Where-Object{$_.evidence_role-ceq$Role})};if($Inv.Count-ne 1){Add 'BLOCKED_EIS_IDENTIFIER_MISMATCH' "$Role/$Rel inventory count=$($Inv.Count)"}else{if($Inv[0].sample_id-cne$Sample){Add 'BLOCKED_CROSS_SAMPLE_EVIDENCE' $Rel};if($Role-in@('EIS_RAW','EIS_NORMALIZED')-and$Inv[0].cell_id-cne$Cell){Add 'BLOCKED_CROSS_CELL_EVIDENCE' $Rel};if($Role-in@('EIS_RAW','EIS_NORMALIZED')-and$Inv[0].replicate_id-cne$Rep){Add 'BLOCKED_EIS_IDENTIFIER_MISMATCH' $Rel}};if(-not$LedgerMap.ContainsKey($K)){Add 'BLOCKED_UNHASHED_EVIDENCE' $Rel;return};$L=$LedgerMap[$K];$DS=ConvertTo-M03A6Int64Exact ([string]$Row.$SizeField) "$Rel declared size" -Nonnegative;$LS=ConvertTo-M03A6Int64Exact $L.size_bytes "$Rel ledger size" -Nonnegative;$Actual=[long](Get-Item -LiteralPath $Resolved.FullPath).Length;$Hash=Get-M03A6Sha256 $Resolved.FullPath;if($DS-ne$Actual-or$LS-ne$Actual){Add 'BLOCKED_SIZE_MISMATCH' $Rel};if([string]$Row.$HashField-notmatch'^[0-9a-fA-F]{64}$'-or([string]$Row.$HashField).ToLowerInvariant()-cne$Hash-or$L.sha256.ToLowerInvariant()-cne$Hash){Add 'BLOCKED_HASH_MISMATCH' $Rel}}catch{AddEx $_.Exception.Message}}
        $EisRequired=@('sample_id','cell_id','replicate_id','operator','measurement_datetime','instrument','instrument_software','raw_file_path','raw_file_size_bytes','raw_file_sha256','normalized_table_path','normalized_table_size_bytes','normalized_table_sha256','frequency_unit','impedance_unit','frequency_min_Hz','frequency_max_Hz','points_per_decade','perturbation_amplitude_V','dc_condition','temperature_K','electrolyte_composition','electrolyte_batch','salt_concentration_mol_L','water_content','water_content_unit','water_content_method','gas_atmosphere','pressure_Pa','flow_rate_m3_s','stabilization_time_s','eis_area_basis','eis_area_m2','source_notebook_reference');foreach($Row in $Eis){foreach($F in $EisRequired){if([string]::IsNullOrWhiteSpace([string]$Row.$F)){Add 'BLOCKED_REQUIRED_METADATA' "$($Row.replicate_id)/$F"}};if(-not(Test-M03A6DateTimeWithTimezone $Row.measurement_datetime)){Add 'BLOCKED_MISSING_TIMEZONE' $Row.replicate_id};if($Row.dc_condition-cne'OCP'-and[string]::IsNullOrWhiteSpace($Row.dc_bias_V)){Add 'BLOCKED_REQUIRED_METADATA' "$($Row.replicate_id)/dc_bias_V"}}
        $Keys=@{};foreach($Row in $Eis){$K="$($Row.sample_id)`0$($Row.cell_id)`0$($Row.replicate_id)";if($Keys.ContainsKey($K)){Add 'BLOCKED_DUPLICATE_REPLICATE_ID' $Row.replicate_id}else{$Keys[$K]=$Row}};foreach($G in @($Eis|Group-Object replicate_id|Where-Object Count -gt 1)){Add 'BLOCKED_DUPLICATE_REPLICATE_ID' $G.Name};if($Eis.Count-lt 3){Add 'BLOCKED_MISSING_REPLICATE' "count=$($Eis.Count)"}
        $RawFull=New-Object Collections.Generic.List[string];$NormFull=New-Object Collections.Generic.List[string];foreach($Row in $Eis){CheckEvidence $Row raw_file_path raw_file_size_bytes raw_file_sha256 EIS_RAW $Row.sample_id $Row.cell_id $Row.replicate_id;CheckEvidence $Row normalized_table_path normalized_table_size_bytes normalized_table_sha256 EIS_NORMALIZED $Row.sample_id $Row.cell_id $Row.replicate_id;try{$RF=Resolve-M03A6PackageRelativePath $Root $Row.raw_file_path -RequireFile -CanonicalRootContext $RootContext;$NF=Resolve-M03A6PackageRelativePath $Root $Row.normalized_table_path -RequireFile -CanonicalRootContext $RootContext;if($RF.Equals($NF,[StringComparison]::OrdinalIgnoreCase)){Add 'BLOCKED_RAW_NORMALIZED_PATH_COLLISION' $Row.replicate_id};[void]$RawFull.Add($RF);[void]$NormFull.Add($NF);try{$NR=@(Import-M03A6Csv $NF @('frequency','z_real','z_imag') -ExactHeader)}catch{$M=$_.Exception.Message;$Code=if($M-like'*DATA_ROW_REQUIRED*'){'BLOCKED_NORMALIZED_EIS_EMPTY'}else{'BLOCKED_NORMALIZED_EIS_SCHEMA'};Add $Code $M;continue};for($I=0;$I-lt$NR.Count;$I++){try{$F=ConvertTo-M03A6FiniteDouble $NR[$I].frequency 'frequency';if($F-le 0){throw 'frequency nonpositive'};[void](ConvertTo-M03A6FiniteDouble $NR[$I].z_real 'z_real');[void](ConvertTo-M03A6FiniteDouble $NR[$I].z_imag 'z_imag')}catch{Add 'BLOCKED_NORMALIZED_EIS_NUMERIC' "$($Row.replicate_id)/row $($I+2)"}}}catch{AddEx $_.Exception.Message}}
        $Samples=@($Eis.sample_id|Select-Object -Unique);$SeriesReq=@('sample_id','component_name','value_ohm','uncertainty_ohm','unit','method','replicate_count','temperature_K','source_reference','raw_evidence_path','raw_evidence_size_bytes','raw_evidence_sha256');foreach($Row in $Series){if($Row.component_name-notin$script:RequiredSeries){Add 'BLOCKED_UNKNOWN_SERIES_COMPONENT' $Row.component_name};if($Row.sample_id-notin$Samples){Add 'BLOCKED_ORPHAN_SERIES_SAMPLE' $Row.sample_id};foreach($F in $SeriesReq){if([string]::IsNullOrWhiteSpace([string]$Row.$F)){Add 'BLOCKED_REQUIRED_METADATA' "series/$F"}};CheckEvidence $Row raw_evidence_path raw_evidence_size_bytes raw_evidence_sha256 SERIES_RESISTANCE $Row.sample_id '' ''};foreach($S in $Samples){$R=@($Series|Where-Object{$_.sample_id-ceq$S});if($R.Count-ne 4-or@($script:RequiredSeries|Where-Object{$N=$_;@($R|Where-Object{$_.component_name-ceq$N}).Count-ne 1}).Count){Add 'BLOCKED_SERIES_CARDINALITY' $S}}
        $AreaReq=@('sample_id','quantity_name','value_SI','uncertainty_SI','unit','measurement_method','source_reference','raw_evidence_path','raw_evidence_size_bytes','raw_evidence_sha256','definition');foreach($Row in $Areas){if($Row.quantity_name-notin$script:RequiredAreas){Add 'BLOCKED_UNKNOWN_AREA_QUANTITY' $Row.quantity_name};if($Row.sample_id-notin$Samples){Add 'BLOCKED_ORPHAN_AREA_SAMPLE' $Row.sample_id};foreach($F in $AreaReq){if([string]::IsNullOrWhiteSpace([string]$Row.$F)){Add 'BLOCKED_REQUIRED_METADATA' "area/$F"}};CheckEvidence $Row raw_evidence_path raw_evidence_size_bytes raw_evidence_sha256 GEOMETRY_AREA $Row.sample_id '' ''};foreach($S in $Samples){$R=@($Areas|Where-Object{$_.sample_id-ceq$S});if($R.Count-ne 5-or@($script:RequiredAreas|Where-Object{$N=$_;@($R|Where-Object{$_.quantity_name-ceq$N}).Count-ne 1}).Count){Add 'BLOCKED_AREA_CARDINALITY' $S}}
        $DirectReq=@('sample_id','conductivity_S_m','uncertainty_S_m','unit','temperature_K','method','cell_constant','cell_constant_unit','calibration_standard','instrument','raw_evidence_path','raw_evidence_size_bytes','raw_evidence_sha256','source_reference');foreach($Row in $Direct){if($Row.sample_id-notin$Samples){Add 'BLOCKED_ORPHAN_DIRECT_SAMPLE' $Row.sample_id};foreach($F in $DirectReq){if([string]::IsNullOrWhiteSpace([string]$Row.$F)){Add 'BLOCKED_REQUIRED_METADATA' "direct/$F"}};CheckEvidence $Row raw_evidence_path raw_evidence_size_bytes raw_evidence_sha256 DIRECT_CONDUCTIVITY $Row.sample_id '' ''};foreach($S in $Samples){if(@($Direct|Where-Object{$_.sample_id-ceq$S}).Count-ne 1){Add 'BLOCKED_DIRECT_CARDINALITY' $S}}
    }catch{AddEx $_.Exception.Message}
    $Status=if($Findings.Count-eq 0){'EXPERIMENTAL_EVIDENCE_RECEIVED'}else{'PRE_INTAKE_BLOCKED'};return New-M03A6PreflightResult $Status $Findings.ToArray()
}

Export-ModuleMember -Function @('Get-M03A6Schema','Write-M03A6Utf8NoBomText','Write-M03A6Utf8NoBomCsv','Assert-M03A6Rfc4180Utf8NoBom','Import-M03A6Csv','ConvertTo-M03A6Int64Exact','ConvertTo-M03A6FiniteDouble','Get-M03A6Sha256','Get-M03A6NormalizedFullPath','Test-M03A6PathInsideRoot','Assert-M03A6PathSegmentsSafe','Assert-M03A6OutsideRepository','Resolve-M03A6PackageRelativePath','Resolve-M03A6CanonicalPackagePath','Assert-M03A6NoReparsePoints','Get-M03A6PackageFiles','Initialize-M03A6AcquisitionPackage','Freeze-M03A6ExperimentalEvidence','Prepare-M03A6NormalizedEis','Test-M03A6DateTimeWithTimezone','Invoke-M03A6Preflight')
