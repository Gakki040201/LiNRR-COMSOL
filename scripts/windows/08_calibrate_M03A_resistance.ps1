$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$ComsolRoot = 'F:\COMSOL64\Multiphysics'
$ComsolBin = Join-Path $ComsolRoot 'bin\win64'
$Compiler = Join-Path $ComsolBin 'comsolcompile.exe'
$Batch = Join-Path $ComsolBin 'comsolbatch.exe'
$Java = Join-Path $ProjectRoot 'src\java\LiNRR_M03A_1_Calibration.java'
$LoadJava = Join-Path $ProjectRoot 'tests\java\LiNRR_M03A_1_LoadCheck.java'
$InputCsv = Join-Path $ProjectRoot 'config\M03A_calibration_inputs.csv'
$OutputMph = Join-Path $ProjectRoot 'models\generated\LiNRR_M03A_1_calibration.mph'
$ReadinessCsv = Join-Path $ProjectRoot 'results\tables\M03A_1_calibration_readiness.csv'
$LedgerCsv = Join-Path $ProjectRoot 'results\tables\M03A_1_resistance_ledger.csv'
$UncertaintyCsv = Join-Path $ProjectRoot 'results\tables\M03A_1_uncertainty.csv'
$ResistancePng = Join-Path $ProjectRoot 'results\figures\M03A_1_resistance_decomposition.png'
$VoltagePng = Join-Path $ProjectRoot 'results\figures\M03A_1_voltage_uncertainty.png'
$LatestDir = Join-Path $ProjectRoot 'runs\latest'
$LatestLog = Join-Path $LatestDir 'M03A_1_build.log'
$LatestReport = Join-Path $LatestDir 'M03A_1_report.md'
$SelectedJava = Join-Path $LatestDir 'LiNRR_M03A_1_SelectedInputs.java'
$SelectedClass = Join-Path $LatestDir 'LiNRR_M03A_1_SelectedInputs.class'

$MonteCarloSeed = 314159
$MonteCarloSamples = 20000
$InvalidFractionLimit = 0.01
$ConflictLimit = 0.10
$BuildStarted = Get-Date
$RunStamp = Get-Date -Format 'yyyyMMdd_HHmmss'
$RunDir = Join-Path $ProjectRoot ("runs\{0}_M03A_1" -f $RunStamp)
$CompileLog = Join-Path $RunDir 'M03A_1_comsolcompile.log'
$SelectedCompileLog = Join-Path $RunDir 'M03A_1_selected_inputs_compile.log'
$LoadCompileLog = Join-Path $RunDir 'M03A_1_loadcheck_compile.log'
$RunLog = Join-Path $RunDir 'M03A_1_build.log'
$Stdout = Join-Path $RunDir 'M03A_1_stdout.log'
$Stderr = Join-Path $RunDir 'M03A_1_stderr.log'
$ReloadLog = Join-Path $RunDir 'M03A_1_reload.log'
$ReloadStdout = Join-Path $RunDir 'M03A_1_reload_stdout.log'
$ReloadStderr = Join-Path $RunDir 'M03A_1_reload_stderr.log'
$RunReport = Join-Path $RunDir 'M03A_1_report.md'

$BaselineFiles = @(
    'src\java\LiNRR_M03A_PrimaryCurrent.java',
    'models\generated\LiNRR_M03A_primary_current.mph',
    'results\tables\M03A_current_summary.csv',
    'results\tables\M03A_parameter_scan.csv',
    'results\tables\M03A_mesh_audit.csv',
    'runs\latest\M03A_report.md'
)
$BaselineHashes = @{}
foreach ($Relative in $BaselineFiles) {
    $Path = Join-Path $ProjectRoot $Relative
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { throw "Frozen baseline missing: $Relative" }
    $BaselineHashes[$Relative] = (Get-FileHash -Algorithm SHA256 -LiteralPath $Path).Hash
}

@((Split-Path $OutputMph), (Split-Path $ReadinessCsv), (Split-Path $ResistancePng),
  $LatestDir, $RunDir) | ForEach-Object { New-Item -ItemType Directory -Force -Path $_ | Out-Null }
foreach ($Required in @($Compiler, $Batch, $Java, $LoadJava, $InputCsv)) {
    if (-not (Test-Path -LiteralPath $Required -PathType Leaf)) { throw "Missing M03A.1 input: $Required" }
}

# Remove only M03A.1-derived artifacts; every earlier milestone remains read-only.
Get-ChildItem -LiteralPath (Split-Path $Java) -Filter 'LiNRR_M03A_1_Calibration*.class' -File -ErrorAction SilentlyContinue |
    Remove-Item -Force
Get-ChildItem -LiteralPath (Split-Path $LoadJava) -Filter 'LiNRR_M03A_1_LoadCheck*.class' -File -ErrorAction SilentlyContinue |
    Remove-Item -Force
foreach ($Stale in @($OutputMph, $ReadinessCsv, $LedgerCsv, $UncertaintyCsv,
    $ResistancePng, $VoltagePng, $LatestLog, $LatestReport, $SelectedJava, $SelectedClass,
    (Join-Path (Split-Path $Java) 'LiNRR_M03A_1_Calibration.class.status'),
    (Join-Path (Split-Path $Java) 'LiNRR_M03A_1_Calibration_Model.mph'),
    (Join-Path (Split-Path $LoadJava) 'LiNRR_M03A_1_LoadCheck.class.status'),
    (Join-Path (Split-Path $LoadJava) 'LiNRR_M03A_1_LoadCheck_M03A1LoadCheck.mph'))) {
    Remove-Item -LiteralPath $Stale -Force -ErrorAction SilentlyContinue
}

function Number([object]$Value) {
    return [double]::Parse([string]$Value, [Globalization.CultureInfo]::InvariantCulture)
}

function Fmt([double]$Value) {
    return $Value.ToString('G12', [Globalization.CultureInfo]::InvariantCulture)
}

function Test-Finite([double]$Value) {
    return -not [double]::IsNaN($Value) -and -not [double]::IsInfinity($Value)
}

function Write-Utf8NoBomCsv([object[]]$Rows, [string]$Path) {
    @($Rows) | Export-Csv -LiteralPath $Path -NoTypeInformation -Encoding UTF8
    $Text = [IO.File]::ReadAllText($Path, [Text.Encoding]::UTF8) -replace "`r`n", "`n"
    [IO.File]::WriteAllText($Path, $Text, [Text.UTF8Encoding]::new($false))
}

function Convert-Unit([double]$Value, [string]$From, [string]$To) {
    $F = $From.Trim().ToLowerInvariant().Replace('ω','ohm')
    $T = $To.Trim().ToLowerInvariant().Replace('ω','ohm')
    if ($F -eq $T) { return $Value }
    $Scale = @{
        'm|m' = 1.0; 'mm|m' = 1e-3; 'um|m' = 1e-6; 'µm|m' = 1e-6
        'm^2|m^2' = 1.0; 'cm^2|m^2' = 1e-4; 'mm^2|m^2' = 1e-6
        'k|k' = 1.0
        's/m|s/m' = 1.0; 'ms/cm|s/m' = 0.1; 'ms/m|s/m' = 1e-3
        'ohm|ohm' = 1.0; 'mohm|ohm' = 1e-3; 'kohm|ohm' = 1e3
        'a|a' = 1.0; 'ma|a' = 1e-3; 'ua|a' = 1e-6; 'µa|a' = 1e-6
        'v|v' = 1.0; 'mv|v' = 1e-3
    }
    $Key = "$F|$T"
    if (-not $Scale.ContainsKey($Key)) { throw "Unsupported unit conversion: '$From' to '$To'" }
    return $Value * [double]$Scale[$Key]
}

$Rows = @(Import-Csv -LiteralPath $InputCsv -Encoding UTF8)
$RequiredColumns = @('field_name','value','unit','uncertainty','uncertainty_unit','status',
    'source_type','source_reference','measurement_date','temperature_K','notes')
foreach ($Column in $RequiredColumns) {
    if ($Rows.Count -eq 0 -or -not ($Rows[0].PSObject.Properties.Name -contains $Column)) {
        throw "M03A.1 input is missing required column: $Column"
    }
}
$ByName = @{}
foreach ($Row in $Rows) {
    $Name = ([string]$Row.field_name).Trim()
    if ([string]::IsNullOrWhiteSpace($Name)) { continue }
    if ($ByName.ContainsKey($Name)) { throw "Duplicate M03A.1 field_name: $Name" }
    $ByName[$Name] = $Row
}
$RequiredFields = @('Lcell','Hcell','Wcell','electrode_active_area','temperature','kappa_direct',
    'kappa_direct_uncertainty','EIS_HFR_total','EIS_HFR_uncertainty','fixture_resistance',
    'fixture_resistance_uncertainty','contact_resistance','contact_resistance_uncertainty',
    'membrane_resistance','membrane_resistance_uncertainty','other_series_resistance',
    'other_series_resistance_uncertainty','Icell','Icell_uncertainty','measured_cell_voltage',
    'measured_cell_voltage_uncertainty','area_basis','electrode_spacing_definition',
    'electrolyte_composition','water_content','measurement_method','replicate_count')
foreach ($Field in $RequiredFields) {
    if (-not $ByName.ContainsKey($Field)) { throw "M03A.1 input is missing required field: $Field" }
}

function Row-HasValue([string]$Name) {
    return $ByName.ContainsKey($Name) -and -not [string]::IsNullOrWhiteSpace([string]$ByName[$Name].value)
}

function Row-HasSource([string]$Name) {
    if (-not $ByName.ContainsKey($Name)) { return $false }
    $R = $ByName[$Name]
    return -not [string]::IsNullOrWhiteSpace([string]$R.source_type) -and
        -not [string]::IsNullOrWhiteSpace([string]$R.source_reference)
}

function Row-IsAvailable([string]$Name) {
    return (Row-HasValue $Name) -and ([string]$ByName[$Name].status).Trim().ToUpperInvariant() -eq 'AVAILABLE' -and
        (Row-HasSource $Name)
}

function Row-IsMeasured([string]$Name) {
    return (Row-IsAvailable $Name) -and
        ([string]$ByName[$Name].source_type).Trim().ToUpperInvariant() -eq 'MEASURED'
}

function Quantity([string]$Name, [string]$TargetUnit) {
    if (-not (Row-HasValue $Name)) { return $null }
    $R = $ByName[$Name]
    return Convert-Unit (Number $R.value) ([string]$R.unit) $TargetUnit
}

function Quantity-Uncertainty([string]$Name, [string]$TargetUnit) {
    $R = $ByName[$Name]
    $FromColumn = $null
    if (-not [string]::IsNullOrWhiteSpace([string]$R.uncertainty)) {
        $UUnit = if ([string]::IsNullOrWhiteSpace([string]$R.uncertainty_unit)) {[string]$R.unit} else {[string]$R.uncertainty_unit}
        $FromColumn = Convert-Unit (Number $R.uncertainty) $UUnit $TargetUnit
    }
    $PairName = $Name + '_uncertainty'
    $FromPair = $null
    if ($ByName.ContainsKey($PairName) -and (Row-HasValue $PairName)) {
        $Pair = $ByName[$PairName]
        $PairUnit = if ([string]::IsNullOrWhiteSpace([string]$Pair.unit)) {[string]$R.unit} else {[string]$Pair.unit}
        $FromPair = Convert-Unit (Number $Pair.value) $PairUnit $TargetUnit
    }
    if ($null -ne $FromColumn -and $null -ne $FromPair) {
        $Scale = [Math]::Max([Math]::Max([Math]::Abs($FromColumn), [Math]::Abs($FromPair)), 1e-30)
        if ([Math]::Abs($FromColumn - $FromPair) / $Scale -gt 1e-6) {
            throw "Inconsistent uncertainty encodings for $Name"
        }
    }
    if ($null -ne $FromColumn) { return [double]$FromColumn }
    if ($null -ne $FromPair) { return [double]$FromPair }
    return $null
}

function Source-Text([string]$Name) {
    if (-not $ByName.ContainsKey($Name)) { return '' }
    $R = $ByName[$Name]
    return (([string]$R.source_type).Trim() + ': ' + ([string]$R.source_reference).Trim()).Trim(': ')
}

$Readiness = [Collections.Generic.List[object]]::new()
function Add-Ready([string]$Item, [string]$Kind, [string]$Status, [bool]$Critical,
                   [string]$Fields, [string]$Detail, [string]$Source) {
    $Readiness.Add([PSCustomObject][ordered]@{
        item=$Item; kind=$Kind; status=$Status; critical=$Critical.ToString().ToUpperInvariant()
        fields=$Fields; detail=$Detail; source_reference=$Source
    })
}

foreach ($Row in $Rows) {
    if ([string]::IsNullOrWhiteSpace([string]$Row.field_name)) { continue }
    $Status = ([string]$Row.status).Trim().ToUpperInvariant()
    if ([string]::IsNullOrWhiteSpace([string]$Row.value)) { $Status = 'MISSING' }
    elseif ($Status -notin @('AVAILABLE','MISSING','AMBIGUOUS','INCONSISTENT','PROVISIONAL')) { $Status = 'AMBIGUOUS' }
    elseif ($Status -eq 'AVAILABLE' -and -not (Row-HasSource ([string]$Row.field_name))) { $Status = 'AMBIGUOUS' }
    Add-Ready ([string]$Row.field_name) 'INPUT_FIELD' $Status $false ([string]$Row.field_name) `
        ($(if ($Status -eq 'MISSING') {'blank value in calibration input'} else {[string]$Row.notes})) `
        ([string]$Row.source_reference)
}

$L = Quantity 'Lcell' 'm'
$H = Quantity 'Hcell' 'm'
$W = Quantity 'Wcell' 'm'
$Area = Quantity 'electrode_active_area' 'm^2'
$Temperature = Quantity 'temperature' 'K'
$DirectKappa = Quantity 'kappa_direct' 'S/m'
$HfrTotal = Quantity 'EIS_HFR_total' 'ohm'
$FixtureR = Quantity 'fixture_resistance' 'ohm'
$ContactR = Quantity 'contact_resistance' 'ohm'
$MembraneR = Quantity 'membrane_resistance' 'ohm'
$OtherR = Quantity 'other_series_resistance' 'ohm'
$Current = Quantity 'Icell' 'A'
$MeasuredV = Quantity 'measured_cell_voltage' 'V'

$DirectReady = (Row-IsMeasured 'kappa_direct') -and $null -ne $DirectKappa -and $DirectKappa -gt 0
$HfrFields = @('EIS_HFR_total','fixture_resistance','contact_resistance','membrane_resistance','other_series_resistance')
$HfrSourcesReady = (Row-IsMeasured 'EIS_HFR_total') -and
    (@($HfrFields | Select-Object -Skip 1 | Where-Object {-not (Row-IsAvailable $_)}).Count -eq 0)
$RNonElectrolyte = $null
$RElectrolyteHfr = $null
$KappaHfr = $null
$HfrIdentityError = $null
if ($null -ne $HfrTotal -and $null -ne $FixtureR -and $null -ne $ContactR -and $null -ne $MembraneR -and $null -ne $OtherR) {
    $RNonElectrolyte = $FixtureR + $ContactR + $MembraneR + $OtherR
    $RElectrolyteHfr = $HfrTotal - $RNonElectrolyte
    $HfrIdentityError = [Math]::Abs($HfrTotal - ($RNonElectrolyte + $RElectrolyteHfr))
    if ($RElectrolyteHfr -gt 0 -and $null -ne $H -and $null -ne $Area -and $H -gt 0 -and $Area -gt 0) {
        $KappaHfr = $H / ($Area * $RElectrolyteHfr)
    }
}
$HfrReady = $HfrSourcesReady -and $null -ne $KappaHfr -and $KappaHfr -gt 0

$CalibrationConflict = $false
$RelativeKappaDifference = $null
if ($DirectReady -and $HfrReady) {
    $RelativeKappaDifference = [Math]::Abs($DirectKappa - $KappaHfr) /
        [Math]::Max(0.5 * ([Math]::Abs($DirectKappa) + [Math]::Abs($KappaHfr)), 1e-30)
    $CalibrationConflict = $RelativeKappaDifference -gt $ConflictLimit
}

$AreaMismatch = $false
$AreaDetails = [Collections.Generic.List[string]]::new()
if ($null -ne $L -and $null -ne $W -and $null -ne $Area -and (Row-IsAvailable 'area_basis')) {
    $Basis = ([string]$ByName['area_basis'].value).ToLowerInvariant()
    if ($Basis -match 'geometric' -and [Math]::Abs($Area - $L*$W) / [Math]::Max($Area,1e-30) -gt 1e-6) {
        $AreaMismatch = $true; $AreaDetails.Add('electrode_active_area differs from Lcell*Wcell for geometric basis')
    }
}
foreach ($Name in @('EIS_area','current_density_reporting_area')) {
    if ((Row-IsAvailable $Name) -and $null -ne $Area) {
        $AuditArea = Quantity $Name 'm^2'
        if ([Math]::Abs($AuditArea - $Area) / [Math]::Max($Area,1e-30) -gt 1e-6) {
            $AreaMismatch = $true; $AreaDetails.Add("$Name differs from electrode_active_area")
        }
    }
}
$AreaStatus = if ($AreaMismatch) {'AREA_BASIS_MISMATCH'} elseif ((Row-IsAvailable 'electrode_active_area') -and (Row-IsAvailable 'area_basis')) {'AVAILABLE'} elseif ((Row-HasValue 'electrode_active_area') -or (Row-HasValue 'area_basis')) {'PROVISIONAL'} else {'MISSING'}

$SpacingDefinition = if (Row-HasValue 'electrode_spacing_definition') {[string]$ByName['electrode_spacing_definition'].value} else {''}
$SpacingExplicit = $SpacingDefinition -match '(actual_electrode_spacing|electrolyte_layer_thickness|separator_thickness|model_equivalent_distance)'
$SpacingStatus = if ((Row-IsAvailable 'Hcell') -and (Row-IsAvailable 'electrode_spacing_definition') -and $SpacingExplicit) {'AVAILABLE'} elseif ((Row-HasValue 'Hcell') -or (Row-HasValue 'electrode_spacing_definition')) {'PROVISIONAL'} else {'MISSING'}

function Gate-Field([string]$Item, [string[]]$Fields, [string]$Detail) {
    $Missing = @($Fields | Where-Object {-not (Row-HasValue $_)})
    $Available = @($Fields | Where-Object {Row-IsAvailable $_})
    $Status = if ($Missing.Count -gt 0) {'MISSING'} elseif ($Available.Count -eq $Fields.Count) {'AVAILABLE'} else {
        $Raw = @($Fields | ForEach-Object {([string]$ByName[$_].status).Trim().ToUpperInvariant()})
        if ($Raw -contains 'INCONSISTENT') {'INCONSISTENT'} elseif ($Raw -contains 'AMBIGUOUS') {'AMBIGUOUS'} else {'PROVISIONAL'}
    }
    $Sources = ($Fields | ForEach-Object {Source-Text $_} | Where-Object {$_}) -join '; '
    Add-Ready $Item 'M03B_GATE' $Status $true ($Fields -join '+') $Detail $Sources
    return $Status
}

function Gate-MeasuredField([string]$Item, [string]$Field, [string]$Detail) {
    $Status = if (-not (Row-HasValue $Field)) {'MISSING'} elseif (Row-IsMeasured $Field) {'AVAILABLE'} elseif (Row-IsAvailable $Field) {'AMBIGUOUS'} else {
        $Raw=([string]$ByName[$Field].status).Trim().ToUpperInvariant()
        if($Raw -in @('INCONSISTENT','AMBIGUOUS','PROVISIONAL')){$Raw}else{'AMBIGUOUS'}
    }
    Add-Ready $Item 'M03B_GATE' $Status $true $Field $Detail (Source-Text $Field)
    return $Status
}

$GateStatuses = [Collections.Generic.List[string]]::new()
$GateStatuses.Add((Gate-Field 'electrolyte_composition' @('electrolyte_composition') 'salt solvent additives and preparation basis'))
$GateStatuses.Add((Gate-Field 'temperature' @('temperature') 'measurement temperature'))
$ConductivityStatus = if ($CalibrationConflict) {'INCONSISTENT'} elseif ($DirectReady -or $HfrReady) {'AVAILABLE'} elseif ((Row-HasValue 'kappa_direct') -or (Row-HasValue 'EIS_HFR_total')) {'AMBIGUOUS'} else {'MISSING'}
Add-Ready 'conductivity_path' 'M03B_GATE' $ConductivityStatus $true 'kappa_direct or deembedded HFR' 'direct conductivity or positive fully sourced de-embedded electrolyte resistance' ((Source-Text 'kappa_direct') + '; ' + (Source-Text 'EIS_HFR_total'))
$GateStatuses.Add($ConductivityStatus)
Add-Ready 'electrode_active_area_and_basis' 'M03B_GATE' $AreaStatus $true 'electrode_active_area+area_basis+area audit' ($(if($AreaDetails.Count){$AreaDetails -join '; '}else{'area equivalence remains unproven unless all bases are traceably reconciled'})) ((Source-Text 'electrode_active_area') + '; ' + (Source-Text 'area_basis'))
$GateStatuses.Add($AreaStatus)
Add-Ready 'electrode_spacing' 'M03B_GATE' $SpacingStatus $true 'Hcell+electrode_spacing_definition' $SpacingDefinition ((Source-Text 'Hcell') + '; ' + (Source-Text 'electrode_spacing_definition'))
$GateStatuses.Add($SpacingStatus)
$GateStatuses.Add((Gate-Field 'EIS_measurement_method' @('measurement_method') 'EIS acquisition method and conductivity method'))
$GateStatuses.Add((Gate-Field 'EIS_intercept_definition' @('EIS_intercept_definition') 'equivalent circuit or high-frequency intercept definition'))
$GateStatuses.Add((Gate-MeasuredField 'total_cell_voltage' 'measured_cell_voltage' 'matched measured total cell voltage'))
$GateStatuses.Add((Gate-MeasuredField 'current' 'Icell' 'matched measured applied current'))
$GateStatuses.Add((Gate-MeasuredField 'WE_potential' 'WE_potential' 'measured potential; reference electrode and polarity required'))
$GateStatuses.Add((Gate-MeasuredField 'CE_potential' 'CE_potential' 'measured potential; reference electrode and polarity required'))

$NeededUncertainty = @('Hcell','electrode_active_area','Icell')
$UncertaintyAvailable = $true
foreach ($Name in $NeededUncertainty) { if ($null -eq (Quantity-Uncertainty $Name ($(if($Name -eq 'Hcell'){'m'}elseif($Name -eq 'electrode_active_area'){'m^2'}else{'A'})))) {$UncertaintyAvailable=$false} }
if ($DirectReady -and $null -eq (Quantity-Uncertainty 'kappa_direct' 'S/m')) {$UncertaintyAvailable=$false}
if ($HfrReady) { foreach($Name in $HfrFields){if($null -eq (Quantity-Uncertainty $Name 'ohm')){$UncertaintyAvailable=$false}} }
$ReplicatesAvailable = (Row-IsAvailable 'replicate_count') -and (Number $ByName['replicate_count'].value) -ge 2
$UncertaintyStatus = if ($UncertaintyAvailable -or $ReplicatesAvailable) {'AVAILABLE'} elseif ((Row-HasValue 'replicate_count')) {'AMBIGUOUS'} else {'MISSING'}
Add-Ready 'uncertainty_or_replicates' 'M03B_GATE' $UncertaintyStatus $true 'standard uncertainties or replicate_count' 'numerical propagation requires standard uncertainties; replicate data must be reduced before calibration' (Source-Text 'replicate_count')
$GateStatuses.Add($UncertaintyStatus)

$AnyMeasured = @($Rows | Where-Object {([string]$_.status).Trim().ToUpperInvariant() -eq 'AVAILABLE' -and ([string]$_.source_type).Trim().ToUpperInvariant() -eq 'MEASURED'}).Count -gt 0
$M03BReady = @($GateStatuses | Where-Object {$_ -ne 'AVAILABLE'}).Count -eq 0 -and -not $CalibrationConflict -and -not $AreaMismatch
$RunState = if ($M03BReady) {'EXPERIMENTALLY_CALIBRATED'} elseif ($AnyMeasured) {'EXPERIMENTAL_INPUT_INCOMPLETE'} else {'SYNTHETIC_SMOKE_TEST'}
$Mode = if ($DirectReady -and $HfrReady) {'CROSS_CHECK'} elseif ($DirectReady) {'DIRECT_CONDUCTIVITY'} elseif ($HfrReady) {'DEEMBEDDED_HFR'} else {'PROVISIONAL'}
Add-Ready 'CALIBRATION_CONFLICT' 'CALIBRATION_GATE' ($(if($CalibrationConflict){'INCONSISTENT'}elseif($null -eq $RelativeKappaDifference){'MISSING'}else{'AVAILABLE'})) $false 'kappa_direct+kappa_from_HFR' ($(if($null -eq $RelativeKappaDifference){'cross-check unavailable; one valid conductivity path is sufficient for the gate'}else{"relative difference=$(Fmt $RelativeKappaDifference); limit=$ConflictLimit"})) ''
Add-Ready 'M03B_READY' 'FINAL_GATE' ($(if($M03BReady){'AVAILABLE'}else{'MISSING'})) $true 'all critical gates' $M03BReady.ToString().ToUpperInvariant() ''
Add-Ready 'RUN_STATE' 'FINAL_GATE' ($(if($RunState -eq 'EXPERIMENTALLY_CALIBRATED'){'AVAILABLE'}elseif($RunState -eq 'EXPERIMENTAL_INPUT_INCOMPLETE'){'AMBIGUOUS'}else{'PROVISIONAL'})) $true 'three-state classification' $RunState ''
Add-Ready 'CALIBRATION_MODE' 'FINAL_GATE' ($(if($Mode -eq 'PROVISIONAL'){'PROVISIONAL'}else{'AVAILABLE'})) $false 'mode selection' $Mode ''
Write-Utf8NoBomCsv @($Readiness) $ReadinessCsv

# Select model inputs. Incomplete data never displace the frozen M03A values.
$SelectedL = 0.055; $SelectedH = 0.004; $SelectedW = 0.055; $SelectedArea = 0.003025
$SelectedT = 298.15; $SelectedKappa = 0.5; $SelectedKappaU = 0.05
$SelectedCurrent = 0.1; $SelectedCurrentU = 0.005
$GeometrySource = 'PROVISIONAL: frozen M03A numerical geometry'
$KappaSource = 'PROVISIONAL: frozen M03A kappa; assumed 10 percent smoke-test uncertainty'
$CurrentSource = 'PROVISIONAL: frozen M03A current; assumed 5 percent smoke-test uncertainty'
$SelectedHU = 0.00004; $SelectedAreaU = 0.00003025
if ($RunState -eq 'EXPERIMENTALLY_CALIBRATED') {
    $SelectedL=$L; $SelectedH=$H; $SelectedW=$W; $SelectedArea=$Area; $SelectedT=$Temperature; $SelectedCurrent=$Current
    $SelectedHU=Quantity-Uncertainty 'Hcell' 'm'; $SelectedAreaU=Quantity-Uncertainty 'electrode_active_area' 'm^2'
    $SelectedCurrentU=Quantity-Uncertainty 'Icell' 'A'
    $GeometrySource=(Source-Text 'Hcell')+'; '+(Source-Text 'electrode_active_area')
    $CurrentSource=Source-Text 'Icell'
    if ($Mode -eq 'DEEMBEDDED_HFR') {
        $SelectedKappa=$KappaHfr
        $RVar=0.0; foreach($Name in $HfrFields){$U=Quantity-Uncertainty $Name 'ohm'; $RVar += $U*$U}
        $RElectrolyteU=[Math]::Sqrt($RVar)
        $SelectedKappaU=$SelectedKappa*[Math]::Sqrt(
            [Math]::Pow($SelectedHU/$SelectedH,2)+
            [Math]::Pow($SelectedAreaU/$SelectedArea,2)+
            [Math]::Pow($RElectrolyteU/$RElectrolyteHfr,2))
        $KappaSource='DEEMBEDDED_HFR: '+(($HfrFields|ForEach-Object{Source-Text $_})-join '; ')
    } else {
        $SelectedKappa=$DirectKappa; $SelectedKappaU=Quantity-Uncertainty 'kappa_direct' 'S/m'
        $KappaSource='DIRECT_CONDUCTIVITY selected; HFR retained as independent cross-check: '+(Source-Text 'kappa_direct')
    }
}

$RElectrolyteModel = $SelectedH / ($SelectedKappa * $SelectedArea)
$RElectrolyteModelU = $RElectrolyteModel * [Math]::Sqrt(
    [Math]::Pow($SelectedHU/$SelectedH,2) +
    [Math]::Pow($SelectedKappaU/$SelectedKappa,2) +
    [Math]::Pow($SelectedAreaU/$SelectedArea,2))
$RAnalytic = $RElectrolyteModel
$RAnalyticU = $RElectrolyteModelU
$VOhmic = $SelectedCurrent * $RAnalytic
$VOhmicU = $VOhmic * [Math]::Sqrt(
    [Math]::Pow($SelectedCurrentU/$SelectedCurrent,2) +
    [Math]::Pow($RAnalyticU/$RAnalytic,2))
$JApp = $SelectedCurrent / $SelectedArea
$JAppU = $JApp * [Math]::Sqrt(
    [Math]::Pow($SelectedCurrentU/$SelectedCurrent,2) +
    [Math]::Pow($SelectedAreaU/$SelectedArea,2))

$Ledger = [Collections.Generic.List[object]]::new()
function Add-Ledger([string]$Kind,[string]$QuantityName,[object]$Value,[string]$Unit,[object]$Uncertainty,
                    [string]$Status,[string]$Source,[string]$Definition) {
    $Ledger.Add([PSCustomObject][ordered]@{
        ledger_kind=$Kind; quantity=$QuantityName
        value=$(if($null -eq $Value){''}else{Fmt ([double]$Value)}); unit=$Unit
        standard_uncertainty=$(if($null -eq $Uncertainty){''}else{Fmt ([double]$Uncertainty)})
        status=$Status; source=$Source; definition=$Definition
    })
}
Add-Ledger 'RESISTANCE' 'R_total_HFR' $HfrTotal 'ohm' (Quantity-Uncertainty 'EIS_HFR_total' 'ohm') ($(if($null -eq $HfrTotal){'MISSING'}else{[string]$ByName['EIS_HFR_total'].status})) (Source-Text 'EIS_HFR_total') 'EIS_HFR_total; not automatically electrolyte resistance'
Add-Ledger 'RESISTANCE' 'fixture_resistance' $FixtureR 'ohm' (Quantity-Uncertainty 'fixture_resistance' 'ohm') ($(if($null -eq $FixtureR){'MISSING'}else{[string]$ByName['fixture_resistance'].status})) (Source-Text 'fixture_resistance') 'fixture series contribution'
Add-Ledger 'RESISTANCE' 'contact_resistance' $ContactR 'ohm' (Quantity-Uncertainty 'contact_resistance' 'ohm') ($(if($null -eq $ContactR){'MISSING'}else{[string]$ByName['contact_resistance'].status})) (Source-Text 'contact_resistance') 'contact series contribution'
Add-Ledger 'RESISTANCE' 'membrane_resistance' $MembraneR 'ohm' (Quantity-Uncertainty 'membrane_resistance' 'ohm') ($(if($null -eq $MembraneR){'MISSING'}else{[string]$ByName['membrane_resistance'].status})) (Source-Text 'membrane_resistance') 'membrane series contribution'
Add-Ledger 'RESISTANCE' 'other_series_resistance' $OtherR 'ohm' (Quantity-Uncertainty 'other_series_resistance' 'ohm') ($(if($null -eq $OtherR){'MISSING'}else{[string]$ByName['other_series_resistance'].status})) (Source-Text 'other_series_resistance') 'other identified series contribution'
Add-Ledger 'RESISTANCE' 'R_non_electrolyte' $RNonElectrolyte 'ohm' $null ($(if($null -eq $RNonElectrolyte){'MISSING'}else{'DERIVED'})) 'sum of sourced component rows' 'fixture+contact+membrane+other series resistance'
Add-Ledger 'RESISTANCE' 'R_electrolyte_from_HFR' $RElectrolyteHfr 'ohm' $null ($(if($null -eq $RElectrolyteHfr){'MISSING'}elseif($RElectrolyteHfr-le 0){'INCONSISTENT'}else{'DERIVED'})) 'de-embedded HFR ledger' 'R_total_HFR-R_non_electrolyte'
Add-Ledger 'RESISTANCE' 'R_electrolyte_model' $RElectrolyteModel 'ohm' $RElectrolyteModelU ($(if($RunState-eq'EXPERIMENTALLY_CALIBRATED'){'CALIBRATED'}else{'PROVISIONAL'})) $KappaSource 'Hcell/(kappa_selected*electrode_active_area)'
Add-Ledger 'CONDUCTIVITY' 'kappa_direct' $DirectKappa 'S/m' (Quantity-Uncertainty 'kappa_direct' 'S/m') ($(if($null-eq$DirectKappa){'MISSING'}else{[string]$ByName['kappa_direct'].status})) (Source-Text 'kappa_direct') 'direct measurement only'
Add-Ledger 'CONDUCTIVITY' 'kappa_from_HFR' $KappaHfr 'S/m' $null ($(if($null-eq$KappaHfr){'MISSING'}else{'DERIVED'})) 'de-embedded HFR ledger' 'Hcell/(electrode_active_area*R_electrolyte_from_HFR)'
Add-Ledger 'CONDUCTIVITY' 'relative_kappa_difference' $RelativeKappaDifference '1' $null ($(if($CalibrationConflict){'CALIBRATION_CONFLICT'}elseif($null-eq$RelativeKappaDifference){'MISSING'}else{'PASS'})) 'direct versus de-embedded HFR' 'absolute difference divided by arithmetic mean; conflict above 0.10'
Add-Ledger 'VOLTAGE' 'V_measured' $MeasuredV 'V' (Quantity-Uncertainty 'measured_cell_voltage' 'V') ($(if($null-eq$MeasuredV){'MISSING'}else{[string]$ByName['measured_cell_voltage'].status})) (Source-Text 'measured_cell_voltage') 'measured total cell voltage'
Add-Ledger 'VOLTAGE' 'V_ohmic_electrolyte' $VOhmic 'V' $VOhmicU ($(if($RunState-eq'EXPERIMENTALLY_CALIBRATED'){'CALIBRATED'}else{'PROVISIONAL'})) $KappaSource 'Icell*R_electrolyte_model'
$VHfrTotal=if($null-ne$HfrTotal-and$null-ne$Current){$Current*$HfrTotal}else{$null}
$VNonElectrolyte=if($null-ne$RNonElectrolyte-and$null-ne$Current){$Current*$RNonElectrolyte}else{$null}
$VUnexplained=if($null-ne$MeasuredV-and$null-ne$VHfrTotal){$MeasuredV-$VHfrTotal}else{$null}
Add-Ledger 'VOLTAGE' 'V_HFR_total' $VHfrTotal 'V' $null ($(if($null-eq$VHfrTotal){'MISSING'}else{'DERIVED'})) 'matched Icell and EIS HFR' 'Icell*EIS_HFR_total'
Add-Ledger 'VOLTAGE' 'V_non_electrolyte_ohmic' $VNonElectrolyte 'V' $null ($(if($null-eq$VNonElectrolyte){'MISSING'}else{'DERIVED'})) 'resistance ledger' 'Icell*R_non_electrolyte'
Add-Ledger 'VOLTAGE' 'V_unexplained' $VUnexplained 'V' $null ($(if($null-eq$VUnexplained){'MISSING'}else{'DERIVED_NOT_ACTIVATION'})) 'measured voltage minus total HFR drop' 'V_measured-V_HFR_total; not automatically activation overpotential'
Write-Utf8NoBomCsv @($Ledger) $LedgerCsv

function Next-Normal([Random]$Random) {
    $U1=[Math]::Max($Random.NextDouble(),1e-16); $U2=$Random.NextDouble()
    return [Math]::Sqrt(-2.0*[Math]::Log($U1))*[Math]::Cos(2.0*[Math]::PI*$U2)
}

function Quantile([double[]]$Sorted,[double]$P) {
    if($Sorted.Length-eq0){return [double]::NaN}
    $X=$P*($Sorted.Length-1); $Lo=[int][Math]::Floor($X); $Hi=[int][Math]::Ceiling($X)
    if($Lo-eq$Hi){return $Sorted[$Lo]}
    return $Sorted[$Lo]+($X-$Lo)*($Sorted[$Hi]-$Sorted[$Lo])
}

function Summary-Stats([string]$QuantityName,[double[]]$Values,[string]$Unit,[int]$Invalid,[string]$Notes) {
    [Array]::Sort($Values)
    $Mean=($Values|Measure-Object -Average).Average
    $SumSq=0.0; foreach($X in $Values){$SumSq+=($X-$Mean)*($X-$Mean)}
    $Std=if($Values.Length-gt1){[Math]::Sqrt($SumSq/($Values.Length-1))}else{0.0}
    $InvalidFraction=$Invalid/[double]$MonteCarloSamples
    return [PSCustomObject][ordered]@{
        quantity=$QuantityName; method='MONTE_CARLO'; mean=Fmt $Mean; standard_deviation=Fmt $Std
        median=Fmt (Quantile $Values 0.5); percentile_2_5=Fmt (Quantile $Values 0.025)
        percentile_97_5=Fmt (Quantile $Values 0.975); coefficient_of_variation=Fmt ($Std/[Math]::Max([Math]::Abs($Mean),1e-30))
        unit=$Unit; sample_count=$Values.Length; invalid_sample_fraction=Fmt $InvalidFraction
        random_seed=$MonteCarloSeed; status=$(if($InvalidFraction-gt$InvalidFractionLimit){'FAILED_INPUT_DISTRIBUTION'}else{'PASS'}); notes=$Notes
    }
}

function Run-MonteCarlo([int]$Seed) {
    $Random=[Random]::new($Seed); $Invalid=0
    $RValues=[Collections.Generic.List[double]]::new(); $KValues=[Collections.Generic.List[double]]::new()
    $RAValues=[Collections.Generic.List[double]]::new(); $VValues=[Collections.Generic.List[double]]::new(); $JValues=[Collections.Generic.List[double]]::new()
    for($i=0;$i-lt$MonteCarloSamples;$i++){
        $Hs=$SelectedH+$SelectedHU*(Next-Normal $Random)
        $As=$SelectedArea+$SelectedAreaU*(Next-Normal $Random)
        $Is=$SelectedCurrent+$SelectedCurrentU*(Next-Normal $Random)
        if($Mode-eq'DEEMBEDDED_HFR' -and $RunState-eq'EXPERIMENTALLY_CALIBRATED'){
            $Rt=$HfrTotal+(Quantity-Uncertainty 'EIS_HFR_total' 'ohm')*(Next-Normal $Random)
            $Rf=$FixtureR+(Quantity-Uncertainty 'fixture_resistance' 'ohm')*(Next-Normal $Random)
            $Rc=$ContactR+(Quantity-Uncertainty 'contact_resistance' 'ohm')*(Next-Normal $Random)
            $Rm=$MembraneR+(Quantity-Uncertainty 'membrane_resistance' 'ohm')*(Next-Normal $Random)
            $Ro=$OtherR+(Quantity-Uncertainty 'other_series_resistance' 'ohm')*(Next-Normal $Random)
            $Rs=$Rt-$Rf-$Rc-$Rm-$Ro
            if($Hs-le0-or$As-le0-or$Is-le0-or$Rs-le0){$Invalid++;continue}
            $Ks=$Hs/($As*$Rs)
        } else {
            $Ks=$SelectedKappa+$SelectedKappaU*(Next-Normal $Random)
            if($Hs-le0-or$As-le0-or$Is-le0-or$Ks-le0){$Invalid++;continue}
            $Rs=$Hs/($Ks*$As)
        }
        $RAs=$Hs/($Ks*$As); $Vs=$Is*$RAs; $Js=$Is/$As
        if($Rs-le0-or$Ks-le0-or$RAs-le0-or-not(Test-Finite $Vs)-or-not(Test-Finite $Js)){$Invalid++;continue}
        $RValues.Add($Rs);$KValues.Add($Ks);$RAValues.Add($RAs);$VValues.Add($Vs);$JValues.Add($Js)
    }
    if($RValues.Count-eq0){throw 'All Monte Carlo samples were invalid.'}
    return @(
        Summary-Stats 'R_electrolyte' $RValues.ToArray() 'ohm' $Invalid 'positive-sample gate; provisional distribution when run is synthetic'
        Summary-Stats 'kappa' $KValues.ToArray() 'S/m' $Invalid 'selected calibration path'
        Summary-Stats 'R_analytic' $RAValues.ToArray() 'ohm' $Invalid 'Hcell/(kappa*area)'
        Summary-Stats 'V_ohmic' $VValues.ToArray() 'V' $Invalid 'Icell*R_analytic'
        Summary-Stats 'j_app' $JValues.ToArray() 'A/m^2' $Invalid 'Icell/electrode_active_area'
    )
}

$Analytic = @(
    [PSCustomObject][ordered]@{quantity='R_electrolyte';method='FIRST_ORDER_ANALYTIC';mean=Fmt $RElectrolyteModel;standard_deviation=Fmt $RElectrolyteModelU;median='';percentile_2_5='';percentile_97_5='';coefficient_of_variation=Fmt($RElectrolyteModelU/$RElectrolyteModel);unit='ohm';sample_count='';invalid_sample_fraction='';random_seed='';status='PASS';notes='first-order independent-input propagation'}
    [PSCustomObject][ordered]@{quantity='kappa';method='FIRST_ORDER_ANALYTIC';mean=Fmt $SelectedKappa;standard_deviation=Fmt $SelectedKappaU;median='';percentile_2_5='';percentile_97_5='';coefficient_of_variation=Fmt($SelectedKappaU/$SelectedKappa);unit='S/m';sample_count='';invalid_sample_fraction='';random_seed='';status='PASS';notes='selected-input standard uncertainty'}
    [PSCustomObject][ordered]@{quantity='R_analytic';method='FIRST_ORDER_ANALYTIC';mean=Fmt $RAnalytic;standard_deviation=Fmt $RAnalyticU;median='';percentile_2_5='';percentile_97_5='';coefficient_of_variation=Fmt($RAnalyticU/$RAnalytic);unit='ohm';sample_count='';invalid_sample_fraction='';random_seed='';status='PASS';notes='first-order independent-input propagation'}
    [PSCustomObject][ordered]@{quantity='V_ohmic';method='FIRST_ORDER_ANALYTIC';mean=Fmt $VOhmic;standard_deviation=Fmt $VOhmicU;median='';percentile_2_5='';percentile_97_5='';coefficient_of_variation=Fmt($VOhmicU/$VOhmic);unit='V';sample_count='';invalid_sample_fraction='';random_seed='';status='PASS';notes='first-order independent-input propagation'}
    [PSCustomObject][ordered]@{quantity='j_app';method='FIRST_ORDER_ANALYTIC';mean=Fmt $JApp;standard_deviation=Fmt $JAppU;median='';percentile_2_5='';percentile_97_5='';coefficient_of_variation=Fmt($JAppU/$JApp);unit='A/m^2';sample_count='';invalid_sample_fraction='';random_seed='';status='PASS';notes='first-order independent-input propagation'}
)
$MonteCarlo = @(Run-MonteCarlo $MonteCarloSeed)
$Repeat = @(Run-MonteCarlo $MonteCarloSeed)
for($i=0;$i-lt$MonteCarlo.Count;$i++){
    if($MonteCarlo[$i].mean-ne$Repeat[$i].mean-or$MonteCarlo[$i].standard_deviation-ne$Repeat[$i].standard_deviation-or$MonteCarlo[$i].median-ne$Repeat[$i].median){
        throw "Fixed-seed Monte Carlo repeatability failed for $($MonteCarlo[$i].quantity)"
    }
}
$UncertaintyRows=@($Analytic)+@($MonteCarlo)
foreach($Row in $UncertaintyRows){foreach($Field in @('mean','standard_deviation')){if(-not(Test-Finite (Number $Row.$Field))){throw "Nonfinite uncertainty result: $($Row.quantity) $Field"}}}
Write-Utf8NoBomCsv $UncertaintyRows $UncertaintyCsv

function Draw-ResistanceFigure([string]$Path) {
    Add-Type -AssemblyName System.Drawing
    $Bitmap=[Drawing.Bitmap]::new(1200,720);$G=[Drawing.Graphics]::FromImage($Bitmap)
    $Title=[Drawing.Font]::new('Arial',[single]19,[Drawing.FontStyle]::Bold);$Font=[Drawing.Font]::new('Arial',[single]11);$Small=[Drawing.Font]::new('Arial',[single]9)
    try{
        $G.SmoothingMode=[Drawing.Drawing2D.SmoothingMode]::AntiAlias;$G.Clear([Drawing.Color]::White)
        $G.DrawString('M03A.1 resistance decomposition',$Title,[Drawing.Brushes]::Black,350,25)
        $Items=@(
            @('Total HFR',$HfrTotal,[Drawing.Color]::SteelBlue),@('Fixture',$FixtureR,[Drawing.Color]::DarkOrange),
            @('Contact',$ContactR,[Drawing.Color]::Goldenrod),@('Membrane',$MembraneR,[Drawing.Color]::MediumPurple),
            @('Other series',$OtherR,[Drawing.Color]::IndianRed),@('De-embedded electrolyte',$RElectrolyteHfr,[Drawing.Color]::SeaGreen),
            @('Model electrolyte',$RElectrolyteModel,[Drawing.Color]::DarkCyan))
        $Values=@($Items|ForEach-Object{if($null-eq$_[1]){0.0}else{[double]$_[1]}});$Max=[Math]::Max(($Values|Measure-Object -Maximum).Maximum,1e-12)
        for($i=0;$i-lt$Items.Count;$i++){
            $Y=105+$i*70;$Name=$Items[$i][0];$Value=$Items[$i][1];$Color=$Items[$i][2]
            $G.DrawString($Name,$Font,[Drawing.Brushes]::Black,30,$Y+8)
            if($null-ne$Value){$WBar=[int](760*[double]$Value/$Max);$B=[Drawing.SolidBrush]::new($Color);try{$G.FillRectangle($B,250,$Y,$WBar,38)}finally{$B.Dispose()};$G.DrawString((Fmt([double]$Value))+' ohm',$Font,[Drawing.Brushes]::Black,1020,$Y+8)}
            else{$G.DrawRectangle([Drawing.Pens]::Gray,250,$Y,760,38);$G.DrawString('MISSING',$Font,[Drawing.Brushes]::DimGray,590,$Y+8)}
        }
        $G.DrawString("Run state: $RunState; mode: $Mode",$Font,[Drawing.Brushes]::Black,30,630)
        $G.DrawString('Total HFR is not automatically electrolyte resistance. Missing terms are not silently set to zero.',$Small,[Drawing.Brushes]::Firebrick,30,675)
        $Bitmap.Save($Path,[Drawing.Imaging.ImageFormat]::Png)
    }finally{$Title.Dispose();$Font.Dispose();$Small.Dispose();$G.Dispose();$Bitmap.Dispose()}
}

function Draw-VoltageFigure([string]$Path) {
    Add-Type -AssemblyName System.Drawing
    $McV=$MonteCarlo|Where-Object{$_.quantity-eq'V_ohmic'}
    $Low=Number $McV.percentile_2_5;$High=Number $McV.percentile_97_5;$Mean=Number $McV.mean
    $MeasuredScale=$(if($null-ne$MeasuredV){$MeasuredV*1.15}else{$High*1.25})
    $Max=[Math]::Max($High*1.25,$MeasuredScale)
    $Bitmap=[Drawing.Bitmap]::new(1200,650);$G=[Drawing.Graphics]::FromImage($Bitmap)
    $Title=[Drawing.Font]::new('Arial',[single]19,[Drawing.FontStyle]::Bold);$Font=[Drawing.Font]::new('Arial',[single]11);$Small=[Drawing.Font]::new('Arial',[single]9)
    try{
        $G.SmoothingMode=[Drawing.Drawing2D.SmoothingMode]::AntiAlias;$G.Clear([Drawing.Color]::White)
        $G.DrawString('M03A.1 voltage uncertainty (fixed-seed Monte Carlo)',$Title,[Drawing.Brushes]::Black,245,25)
        $Left=180;$PlotW=900;$Y=230
        for($i=0;$i-le5;$i++){$X=$Left+$PlotW*$i/5;$G.DrawLine([Drawing.Pens]::LightGray,$X,120,$X,440);$G.DrawString((Fmt($Max*$i/5))+' V',$Small,[Drawing.Brushes]::Black,$X-20,455)}
        $XLow=$Left+$PlotW*$Low/$Max;$XHigh=$Left+$PlotW*$High/$Max;$XMean=$Left+$PlotW*$Mean/$Max
        $Pen=[Drawing.Pen]::new([Drawing.Color]::DarkCyan,[single]8);try{$G.DrawLine($Pen,$XLow,$Y,$XHigh,$Y)}finally{$Pen.Dispose()}
        $G.FillEllipse([Drawing.Brushes]::DarkBlue,$XMean-7,$Y-7,14,14)
        $G.DrawString('V_ohmic electrolyte',$Font,[Drawing.Brushes]::Black,20,$Y-10)
        $G.DrawString("mean=$(Fmt $Mean) V; 95% interval [$(Fmt $Low), $(Fmt $High)] V",$Font,[Drawing.Brushes]::Black,310,285)
        if($null-ne$MeasuredV){$Xm=$Left+$PlotW*$MeasuredV/$Max;$G.DrawLine([Drawing.Pens]::Firebrick,$Xm,160,$Xm,360);$G.DrawString('V_measured',$Font,[Drawing.Brushes]::Firebrick,$Xm-45,135)}else{$G.DrawString('Measured cell voltage: MISSING',$Font,[Drawing.Brushes]::Firebrick,450,350)}
        $G.DrawString("seed=$MonteCarloSeed; requested samples=$MonteCarloSamples; invalid fraction=$($McV.invalid_sample_fraction)",$Small,[Drawing.Brushes]::DimGray,330,530)
        $G.DrawString('Unexplained voltage is not automatically activation overpotential.',$Small,[Drawing.Brushes]::Firebrick,390,585)
        $Bitmap.Save($Path,[Drawing.Imaging.ImageFormat]::Png)
    }finally{$Title.Dispose();$Font.Dispose();$Small.Dispose();$G.Dispose();$Bitmap.Dispose()}
}
Draw-ResistanceFigure $ResistancePng
Draw-VoltageFigure $VoltagePng

# COMSOL Runtime forbids getenv and arbitrary file reads. Compile the selected
# inputs into a transparent run-specific class and add it through the documented
# comsolbatch -classpathadd option. The durable builder falls back to frozen
# provisional values if this companion class is absent.
function Java-Escape([string]$Value) {
    return $Value.Replace('\','\\').Replace('"','\"').Replace("`r",' ').Replace("`n",' ')
}
$SelectedMap=[ordered]@{
    M03A1_RUN_STATE=$RunState;M03A1_MODE=$Mode;M03A1_L_M=Fmt $SelectedL;M03A1_H_M=Fmt $SelectedH
    M03A1_W_M=Fmt $SelectedW;M03A1_AREA_M2=Fmt $SelectedArea;M03A1_T_K=Fmt $SelectedT
    M03A1_KAPPA_S_M=Fmt $SelectedKappa;M03A1_KAPPA_U_S_M=Fmt $SelectedKappaU
    M03A1_ICELL_A=Fmt $SelectedCurrent;M03A1_ICELL_U_A=Fmt $SelectedCurrentU
    M03A1_HAS_VMEASURED=$(if($null-ne$MeasuredV){'1'}else{'0'})
    M03A1_VMEASURED_V=$(if($null-ne$MeasuredV){Fmt $MeasuredV}else{'0'})
    M03A1_GEOMETRY_SOURCE=$GeometrySource;M03A1_KAPPA_SOURCE=$KappaSource;M03A1_CURRENT_SOURCE=$CurrentSource
}
$Cases=($SelectedMap.GetEnumerator()|ForEach-Object{'            case "'+(Java-Escape $_.Key)+'": return "'+(Java-Escape ([string]$_.Value))+'";'})-join[Environment]::NewLine
$SelectedSource=@"
/** Generated M03A.1 run input bridge; values retain readiness-gate provenance. */
public final class LiNRR_M03A_1_SelectedInputs {
    private LiNRR_M03A_1_SelectedInputs() {}
    public static String get(String name) {
        switch (name) {
$Cases
            default: return null;
        }
    }
}
"@
[IO.File]::WriteAllText($SelectedJava,$SelectedSource,[Text.UTF8Encoding]::new($false))

Push-Location $ProjectRoot
try {
    & $Compiler $SelectedJava 2>&1 | Tee-Object -FilePath $SelectedCompileLog
    if($LASTEXITCODE-ne0){throw "M03A.1 selected-input class compilation failed: $SelectedCompileLog"}
    & $Compiler $Java 2>&1 | Tee-Object -FilePath $CompileLog
    if($LASTEXITCODE-ne0){throw "M03A.1 Java compilation failed: $CompileLog"}
    & $Compiler $LoadJava 2>&1 | Tee-Object -FilePath $LoadCompileLog
    if($LASTEXITCODE-ne0){throw "M03A.1 load-check compilation failed: $LoadCompileLog"}
    $Class=Join-Path (Split-Path $Java) 'LiNRR_M03A_1_Calibration.class';$LoadClass=Join-Path (Split-Path $LoadJava) 'LiNRR_M03A_1_LoadCheck.class'
    foreach($Bytecode in @($SelectedClass,$Class,$LoadClass)){if(-not(Test-Path -LiteralPath $Bytecode -PathType Leaf)){throw "Missing bytecode: $Bytecode"}}
    $Process=Start-Process -FilePath $Batch -ArgumentList @('-classpathadd',$LatestDir,'-inputfile',$Class,'-batchlog',$RunLog) -RedirectStandardOutput $Stdout -RedirectStandardError $Stderr -WindowStyle Hidden -Wait -PassThru
    if($Process.ExitCode-ne0){throw "M03A.1 batch exit code $($Process.ExitCode)"}
    $FatalPattern='Exception|ERROR|Failed|Undefined|Singular matrix|Out of memory|错误|出错'
    $Fatal=@(Select-String -Encoding UTF8 -LiteralPath $RunLog,$Stderr -Pattern $FatalPattern -CaseSensitive:$false)
    if($Fatal.Count-gt0){$Fatal|ForEach-Object{Write-Host('FATAL: '+$_.Line)};throw 'Fatal pattern in M03A.1 solve log.'}
    $ResultLines=@(Get-Content -LiteralPath $Stdout -Encoding UTF8|Where-Object{$_-like'M03A1_RESULT|*'})
    if($ResultLines.Count-ne2){throw "Expected M03A1 result header and row; got $($ResultLines.Count)"}
    $Headers=@($ResultLines[0]-split'\|'|Select-Object -Skip 1);$Values=@($ResultLines[1]-split'\|'|Select-Object -Skip 1)
    $Comsol=[ordered]@{};for($i=0;$i-lt$Headers.Count;$i++){$Comsol[$Headers[$i]]=$Values[$i]}
    if($Comsol.status-ne'PASS'){throw 'M03A.1 COMSOL result did not pass.'}

    $LoadProcess=Start-Process -FilePath $Batch -ArgumentList @('-inputfile',$LoadClass,'-batchlog',$ReloadLog) -RedirectStandardOutput $ReloadStdout -RedirectStandardError $ReloadStderr -WindowStyle Hidden -Wait -PassThru
    if($LoadProcess.ExitCode-ne0){throw "M03A.1 reload exit code $($LoadProcess.ExitCode)"}
    $ReloadFatal=@(Select-String -Encoding UTF8 -LiteralPath $ReloadLog,$ReloadStderr -Pattern $FatalPattern -CaseSensitive:$false)
    if($ReloadFatal.Count-gt0){throw 'Fatal pattern in M03A.1 reload log.'}
    if(@(Get-Content -LiteralPath $ReloadStdout -Encoding UTF8|Where-Object{$_-like'M03A1_LOAD|PASS|*'}).Count-ne1){throw 'M03A.1 reload did not emit PASS.'}

    foreach($Relative in $BaselineFiles){$Now=(Get-FileHash -Algorithm SHA256 -LiteralPath (Join-Path $ProjectRoot $Relative)).Hash;if($Now-ne$BaselineHashes[$Relative]){throw "Frozen baseline changed: $Relative"}}
    if(Select-String -LiteralPath $Java -Pattern 'SecondaryCurrentDistribution' -Quiet){throw 'Secondary Current Distribution token found in M03A.1 source.'}
    if(Select-String -LiteralPath $Java -Pattern 'ElectrodeReaction' -Quiet){throw 'Electrode reaction feature token found in M03A.1 source.'}
    $IdentityStatus=if($null-eq$HfrIdentityError){'NOT_EVALUATED_MISSING_INPUT'}elseif($HfrIdentityError-le1e-12){'PASS'}else{'FAILED'}
    if($IdentityStatus-eq'FAILED'){throw 'Resistance-ledger identity failed.'}
    $Warnings=@(Select-String -Encoding UTF8 -LiteralPath $RunLog,$ReloadLog -Pattern 'warning|警告' -CaseSensitive:$false)

    $MissingCritical=@($Readiness|Where-Object{$_.kind-eq'M03B_GATE'-and$_.status-ne'AVAILABLE'})
    $MissingList=if($MissingCritical.Count-eq0){'- None.'}else{($MissingCritical|ForEach-Object{"- ``$($_.item)``: $($_.status) — $($_.detail)"})-join[Environment]::NewLine}
    $SourceList=($Rows|Where-Object{-not[string]::IsNullOrWhiteSpace([string]$_.value)}|ForEach-Object{"- ``$($_.field_name)``: $($_.source_type) — $($_.source_reference)"})-join[Environment]::NewLine
    $WarningText=if($Warnings.Count-eq0){'- None.'}else{($Warnings|ForEach-Object{"- ``$($_.Line)``"})-join[Environment]::NewLine}
    $HfrText=if($null-eq$HfrTotal){'MISSING'}else{Fmt $HfrTotal};$REText=if($null-eq$RElectrolyteHfr){'MISSING'}else{Fmt $RElectrolyteHfr};$KHfrText=if($null-eq$KappaHfr){'MISSING'}else{Fmt $KappaHfr}
    $RelText=if($null-eq$RelativeKappaDifference){'MISSING'}else{Fmt $RelativeKappaDifference}
    $Report=@"
# M03A.1 Experimental Calibration, Resistance, and Uncertainty Report

- Run: ``$RunStamp``
- Current run state: **$RunState**
- Calibration mode: **$Mode**
- M03B readiness: **$($M03BReady.ToString().ToUpperInvariant())**
- COMSOL editable MPH reload: **PASS**
- Monte Carlo: ``$MonteCarloSamples`` requested samples, fixed seed ``$MonteCarloSeed``; fixed-seed repeatability **PASS**.
- Frozen M03A hashes after the run: **UNCHANGED**.

## Input inventory and data sources

The complete row-level inventory, including every blank input, is in ``results/tables/M03A_1_calibration_readiness.csv``. Nonblank template entries and provenance are:

$SourceList

No default parameter is treated as experimental data. The current template contains no ``AVAILABLE/MEASURED`` input, so the generated model uses the frozen M03A geometry, ``kappa=0.5 S/m``, and ``Icell=0.1 A`` only as a synthetic smoke test.

## Area and spacing definitions

- Geometric model area: ``Lcell*Wcell = $(Fmt($SelectedL*$SelectedW)) m^2``.
- Selected active area: ``$(Fmt $SelectedArea) m^2``; basis: ``$([string]$ByName['area_basis'].value)``.
- Actual wetted, catalytic-layer projected, GDE exposed, EIS, and current-density reporting areas remain separate fields; no silent conversion was made.
- Area audit: **$AreaStatus**. $(if($AreaDetails.Count){$AreaDetails -join '; '}else{'No numerical mismatch was asserted; experimental area equivalence is still unproven.'})
- ``Hcell=$(Fmt $SelectedH) m`` is currently ``$SpacingDefinition`` with **$SpacingStatus** status; it is not an experimentally established spacing.

## HFR decomposition and conductivity

- Total HFR: ``$HfrText ohm``.
- Fixture/contact/membrane/other series resistance: ``$(if($null-eq$FixtureR){'MISSING'}else{Fmt $FixtureR})`` / ``$(if($null-eq$ContactR){'MISSING'}else{Fmt $ContactR})`` / ``$(if($null-eq$MembraneR){'MISSING'}else{Fmt $MembraneR})`` / ``$(if($null-eq$OtherR){'MISSING'}else{Fmt $OtherR}) ohm``.
- De-embedded electrolyte resistance: ``$REText ohm``; ledger identity: **$IdentityStatus**.
- Direct conductivity: ``$(if($null-eq$DirectKappa){'MISSING'}else{Fmt $DirectKappa}) S/m``.
- HFR-derived conductivity: ``$KHfrText S/m``.
- Relative conductivity difference: ``$RelText``; conflict threshold ``0.10``; conflict: **$CalibrationConflict**.
- Selected model conductivity: ``$(Fmt $SelectedKappa) ± $(Fmt $SelectedKappaU) S/m`` (**$(if($RunState-eq'EXPERIMENTALLY_CALIBRATED'){'CALIBRATED'}else{'PROVISIONAL'})**).

**总HFR不自动等于电解液电阻。** The forbidden shortcut ``kappa=Hcell/(A*EIS_HFR_total)`` was not used.

## Resistance and voltage ledger

- Model electrolyte/analytical resistance: ``$(Fmt $RAnalytic) ± $(Fmt $RAnalyticU) ohm``.
- Electrolyte ohmic voltage: ``$(Fmt $VOhmic) ± $(Fmt $VOhmicU) V``.
- Total-HFR voltage: ``$(if($null-eq$VHfrTotal){'MISSING'}else{Fmt $VHfrTotal}) V``.
- Non-electrolyte ohmic voltage: ``$(if($null-eq$VNonElectrolyte){'MISSING'}else{Fmt $VNonElectrolyte}) V``.
- Measured voltage: ``$(if($null-eq$MeasuredV){'MISSING'}else{Fmt $MeasuredV}) V``.
- Unexplained voltage: ``$(if($null-eq$VUnexplained){'MISSING'}else{Fmt $VUnexplained}) V``.

**未解释电压不自动等于活化过电位。** It may include cathode activation, anode activation, concentration polarization, SEI, nonlinear contacts, measurement offset, and unidentified series contributions.

## Uncertainty propagation

Both first-order analytical propagation and fixed-seed Monte Carlo are recorded in ``results/tables/M03A_1_uncertainty.csv`` for ``R_electrolyte``, ``kappa``, ``R_analytic``, ``V_ohmic``, and ``j_app``. Monte Carlo rows include mean, standard deviation, median, 2.5th/97.5th percentiles, coefficient of variation, accepted sample count, invalid fraction, and seed. Nonpositive thickness, area, conductivity, current, or de-embedded electrolyte resistance samples are rejected. Invalid fraction above 1% is ``FAILED_INPUT_DISTRIBUTION``.

Current synthetic uncertainty assumptions are 1% for ``Hcell`` and area, 10% for conductivity, and 5% for current. They are numerical stress-test assumptions, not measurements.

## Data conflicts

- ``CALIBRATION_CONFLICT``: **$CalibrationConflict**.
- ``AREA_BASIS_MISMATCH``: **$AreaMismatch**.
- No conductivity estimate is automatically selected when a cross-check conflict exists.

## COMSOL result

- Physics: ``PrimaryCurrentDistribution`` only; stable tag ``cd_cal``.
- Simulated/analytical electrolyte voltage: ``$($Comsol.simulated_voltage_V) / $($Comsol.analytic_voltage_V) V``.
- Voltage relative error: ``$($Comsol.voltage_relative_error)``.
- Effective/analytical resistance: ``$($Comsol.effective_resistance_ohm) / $($Comsol.analytic_resistance_ohm) ohm``.
- Anode/cathode signed outward current: ``$($Comsol.anode_current_A) / $($Comsol.cathode_current_A) A``.
- Current-balance relative error: ``$($Comsol.current_balance_relative_error)``.
- Mean current density: ``$($Comsol.mean_current_density_A_m2) A/m^2``.
- Model label: ``SYNTHETIC_SMOKE_TEST — NOT EXPERIMENTALLY CALIBRATED`` unless all critical gates pass.

## M03B readiness and missing experiments

``M03B_READY = $($M03BReady.ToString().ToUpperInvariant())``.

$MissingList

## Log review and acceptance

- Java compilation: PASS.
- PowerShell parse and execution: PASS.
- Synthetic CSV parsing: PASS.
- Resistance ledger identity: **$IdentityStatus**.
- Finite analytical and Monte Carlo outputs: PASS.
- Fixed random seed repeatability: PASS.
- Editable MPH reload: PASS.
- Frozen M03A baseline hashes: UNCHANGED.
- Forbidden physics feature scan: PASS.
- Fatal solve/reload log matches (error, failed, undefined, singular, out of memory): 0.
- Warning matches: $($Warnings.Count). Full logs are preserved in ``runs/$($RunStamp)_M03A_1``.
$WarningText

## Scientific limitations

M03A.1 does not contain reaction kinetics, activation polarization, species transport, concentration polarization, Li deposition, HOR, SEI, N2 conversion, ammonia production, or FE. **M03A.1不包含反应动力学。** It is a pure-ohmic calibration framework, not a microscopic Li-NRR mechanism model.

The current run has no traceable experimental calibration inputs. **PROVISIONAL结果不得用于论文定量结论。** Passing this run establishes reproducible parsing, bookkeeping, uncertainty propagation, and COMSOL numerical behavior only; it is not experimental validation and does not authorize Secondary Current Distribution.
"@
    [IO.File]::WriteAllText($LatestReport,$Report,[Text.UTF8Encoding]::new($false));Copy-Item -LiteralPath $LatestReport -Destination $RunReport -Force;Copy-Item -LiteralPath $RunLog -Destination $LatestLog -Force
    foreach($Artifact in @($OutputMph,$ReadinessCsv,$LedgerCsv,$UncertaintyCsv,$ResistancePng,$VoltagePng,$SelectedJava,$SelectedClass)){if(-not(Test-Path -LiteralPath $Artifact -PathType Leaf)-or(Get-Item -LiteralPath $Artifact).Length-le0){throw "Missing or empty M03A.1 artifact: $Artifact"};Copy-Item -LiteralPath $Artifact -Destination $RunDir -Force}
    Write-Host "[SUCCESS] M03A.1 $RunState; mode=$Mode; M03B_READY=$M03BReady; seed=$MonteCarloSeed."
}
finally {
    Get-ChildItem -LiteralPath (Split-Path $Java) -Filter 'LiNRR_M03A_1_Calibration*.class' -File -ErrorAction SilentlyContinue|Remove-Item -Force -ErrorAction SilentlyContinue
    Get-ChildItem -LiteralPath (Split-Path $LoadJava) -Filter 'LiNRR_M03A_1_LoadCheck*.class' -File -ErrorAction SilentlyContinue|Remove-Item -Force -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath (Join-Path (Split-Path $Java) 'LiNRR_M03A_1_Calibration.class.status'),(Join-Path (Split-Path $Java) 'LiNRR_M03A_1_Calibration_Model.mph'),(Join-Path (Split-Path $LoadJava) 'LiNRR_M03A_1_LoadCheck.class.status'),(Join-Path (Split-Path $LoadJava) 'LiNRR_M03A_1_LoadCheck_M03A1LoadCheck.mph') -Force -ErrorAction SilentlyContinue
    Pop-Location
}
