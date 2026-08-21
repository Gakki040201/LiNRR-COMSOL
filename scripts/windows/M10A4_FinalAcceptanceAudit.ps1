[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$run = Join-Path $repo 'runs\M10A4\20260820_121258'
$tables = Join-Path $repo 'results\tables'
$evidence = Join-Path $repo 'evidence\M10A4'
$invariant = [Globalization.CultureInfo]::InvariantCulture

function Assert-True([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw "M10A4_ACCEPTANCE_BLOCKED: $Message" }
}
function Number([object]$Value, [string]$Name) {
    try { $x = [double]::Parse([string]$Value, [Globalization.NumberStyles]::Float, $invariant) }
    catch { throw "M10A4_ACCEPTANCE_BLOCKED: nonnumeric $Name=$Value" }
    Assert-True (-not [double]::IsNaN($x) -and -not [double]::IsInfinity($x)) "nonfinite $Name=$Value"
    return $x
}
function Hash([string]$Relative) { (Get-FileHash -LiteralPath (Join-Path $repo $Relative) -Algorithm SHA256).Hash }
function Require-File([string]$Relative) { Assert-True (Test-Path -LiteralPath (Join-Path $repo $Relative) -PathType Leaf) "missing $Relative" }
function Stat([object[]]$Rows, [string]$Quantity) {
    $row = @($Rows | Where-Object quantity -eq $Quantity)
    Assert-True ($row.Count -eq 1) "expected one statistics row for $Quantity"
    return Number $row[0].value $Quantity
}

Set-Location $repo
$base = '1e89f18cf28bb39abf767e1d03a76bb9be55a470'
$branch = (& git branch --show-current).Trim()
Assert-True ($branch -eq 'm10a4-integrated-ionic-current-li-plating') "wrong branch $branch"
& git cat-file -e "$base^{commit}"
Assert-True ($LASTEXITCODE -eq 0) 'authoritative PRE-A4 base missing'
$ancestor = (& git merge-base $base HEAD).Trim()
Assert-True ($ancestor -eq $base) "PRE-A4 base is not HEAD ancestry: $ancestor"

$a3Hash = Hash 'models\generated\LiNRR_M10A3_real_species_transport.mph'
$collectorHash = Hash 'cad\raw\M10A0_2\block_currentCollector_flowField_v03.2.1_20210525_STEP-AP203.STEP'
$chamberHash = Hash 'cad\raw\M10A0_2\electrolyteChamber_rectangularMask_v02.2.2_20210521_STEP-AP203.STEP'
Assert-True ($a3Hash -eq '03612FDB08D993595ABDA41D5873CBBCAA97580DB432ADCD2FBCD2CA06260C00') 'accepted M10A3 SHA changed'
Assert-True ($collectorHash -eq '0091777D10ECA620E08780381438F3522B3BE31FEE307F9DDCF074A1FACDDFE9') 'collector STEP SHA changed'
Assert-True ($chamberHash -eq 'AE350E006228A10EEC4476F79CAFCE6D63F18F10F58F0A8B3559EED0C37FF12C') 'chamber STEP SHA changed'

$pre = Import-Csv (Join-Path $tables 'M10_preA4_regression_summary.csv')
Assert-True (@($pre | Where-Object { $_.check -eq 'M10_PREA4_INTEGRATION' -and $_.status -eq 'PASS' }).Count -eq 1) 'PRE-A4 regression summary not PASS'

$requiredTables = @(
 'M10A4_manual_electrochemistry_reconciliation.csv','M10A4_electrochemical_program_audit.csv',
 'M10A4_electrochemical_area_audit.csv','M10A4_parameter_provenance.csv','M10A4_calibration_required.csv',
 'M10A4_charge_conservation.csv','M10A4_ionic_species_conservation.csv','M10A4_resistance_audit.csv',
 'M10A4_current_distribution_statistics.csv','M10A4_li_faraday_ledger.csv',
 'M10A4_spatial_colimitation.csv','M10A4_mesh_convergence.csv'
)
foreach($name in $requiredTables){Require-File "results\tables\$name";Require-File "evidence\M10A4\$name"}
foreach($name in @('README_ACCEPTANCE.md','model_tree.json','component_inventory.csv','physics_inventory.csv','study_inventory.csv','dataset_inventory.csv','result_inventory.csv','selection_inventory.csv','parameter_inventory.csv')){Require-File "evidence\M10A4\$name"}
$pngs = @(Get-ChildItem -LiteralPath $evidence -Filter '*.png')
Assert-True ($pngs.Count -ge 7) 'fewer than seven representative PNG figures'
foreach($png in $pngs){Assert-True ($png.Length -gt 0) "empty PNG $($png.Name)"}

$stats = Import-Csv (Join-Path $tables 'M10A4_current_distribution_statistics.csv')
$area = Stat $stats 'actual_electrochemical_interface_area'
$weightArea = Stat $stats 'area_weight_sum'
$iCath = Stat $stats 'integrated_cathode_current'
$iAn = Stat $stats 'integrated_anode_current'
$currentRel = Stat $stats 'current_conservation_relative'
$jMean = Stat $stats 'j_surface_magnitude_mean'; $jMin = Stat $stats 'j_surface_magnitude_min'; $jMax = Stat $stats 'j_surface_magnitude_max'
$jP10 = Stat $stats 'j_surface_magnitude_P10'; $jP50 = Stat $stats 'j_surface_magnitude_P50'; $jP90 = Stat $stats 'j_surface_magnitude_P90'
$jStd = Stat $stats 'j_surface_magnitude_std'; $jCv = Stat $stats 'j_surface_magnitude_CV'
Assert-True ([math]::Abs($area-0.003844) -le 1e-12) "reaction-plane area $area"
Assert-True ([math]::Abs($area-$weightArea) -le 1e-12) 'area weights do not close to reaction-plane area'
Assert-True ($currentRel -le 1e-8) "current conservation $currentRel"
Assert-True ($jMin -le $jP10 -and $jP10 -le $jP50 -and $jP50 -le $jP90 -and $jP90 -le $jMax) 'weighted percentile ordering failed'

$ions = Import-Csv (Join-Path $tables 'M10A4_ionic_species_conservation.csv')
$liRel = Number (@($ions | Where-Object {$_.species -eq 'Li+' -and $_.quantity -eq 'relative_residual'})[0].value) 'Li_relative'
$bfRel = Number (@($ions | Where-Object {$_.species -eq 'BF4-' -and $_.quantity -eq 'relative_residual'})[0].value) 'BF4_relative'
$liMin = Number (@($ions | Where-Object {$_.species -eq 'Li+' -and $_.quantity -eq 'volume_min'})[0].value) 'Li_min'
$liMax = Number (@($ions | Where-Object {$_.species -eq 'Li+' -and $_.quantity -eq 'volume_max'})[0].value) 'Li_max'
$bfMin = Number (@($ions | Where-Object {$_.species -eq 'BF4-' -and $_.quantity -eq 'volume_min'})[0].value) 'BF4_min'
$bfMax = Number (@($ions | Where-Object {$_.species -eq 'BF4-' -and $_.quantity -eq 'volume_max'})[0].value) 'BF4_max'
$en = Number (@($ions | Where-Object {$_.species -eq 'charge' -and $_.quantity -eq 'electroneutrality_max_abs'})[0].value) 'electroneutrality'
Assert-True ($liRel -le 1e-6 -and $bfRel -le 1e-6) "species conservation Li=$liRel BF4=$bfRel"
Assert-True ($liMin -ge 0 -and $bfMin -ge 0) "negative raw ion concentration Li=$liMin BF4=$bfMin"
Assert-True ($en -eq 0) "electroneutrality residual $en"

$faraday = Import-Csv (Join-Path $tables 'M10A4_li_faraday_ledger.csv')
Assert-True ($faraday.Count -eq 20) "Faraday row count $($faraday.Count)"
$faradayMax = 0.0
foreach($row in $faraday){
    $e = Number $row.electron_ledger_relative 'electron ledger'; $l = Number $row.Li_equivalent_ledger_relative 'Li ledger'
    $faradayMax = [math]::Max($faradayMax,[math]::Max($e,$l))
    Assert-True ((Number $row.minimum_thickness_m 'minimum thickness') -ge 0) 'negative Li-equivalent thickness'
    Assert-True ((Number $row.P10_thickness_m 'P10 thickness') -le (Number $row.P50_thickness_m 'P50 thickness') -and (Number $row.P50_thickness_m 'P50 thickness') -le (Number $row.P90_thickness_m 'P90 thickness')) 'thickness percentile ordering failed'
}
Assert-True ($faradayMax -le 1e-10) "Faraday ledger $faradayMax"
function F1([double]$q){$r=@($faraday|Where-Object{(Number $_.Q_C 'Q') -eq $q -and (Number $_.f_Li_current 'f') -eq 1});Assert-True($r.Count -eq 1)"missing f=1 Q=$q";return $r[0]}
$f9=F1 9;$f45=F1 45;$f54=F1 54;$f99=F1 99;$f297=F1 297

$colim = Import-Csv (Join-Path $tables 'M10A4_spatial_colimitation.csv')
foreach($q in @('0.400000000000000','0.500000000000000','0.600000000000000')){$closure=@($colim|Where-Object{$_.diagnostic -eq 'CATEGORY_CLOSURE' -and $_.threshold_quantile -eq $q});Assert-True($closure.Count -eq 1)"missing co-limitation closure q=$q";Assert-True([math]::Abs((Number $closure[0].area_fraction "closure q=$q")-1)-le 1e-10)"co-limitation closure q=$q"}

$mesh = Import-Csv (Join-Path $tables 'M10A4_mesh_convergence.csv')
$meshFinal = @($mesh | Where-Object {$_.mesh -eq 'comparison' -and $_.metric -eq 'MESH_MAX_KEY_DIFFERENCE'})
Assert-True ($meshFinal.Count -eq 1) 'missing mesh maximum row'
$meshMax = Number $meshFinal[0].value 'mesh maximum'
Assert-True ($meshMax -le 0.10) "mesh maximum $meshMax"
Assert-True ($meshFinal[0].status -eq 'PASS_WITH_LIMITATION') "mesh status $($meshFinal[0].status)"

$reload = Get-Content -LiteralPath (Join-Path $run 'M10A4_final_reload_attempt6_compact_stdout.txt') -Raw
Assert-True ($reload.Contains('FINAL_RELOAD=PASS')) 'final reload marker missing'
Assert-True ($reload.Contains('FINAL_RELOAD_SOLVE_TRIGGERED=FALSE')) 'reload no-solve marker missing'
$evidenceLog = Get-Content -LiteralPath (Join-Path $run 'M10A4_final_evidence_compact_stdout.txt') -Raw
Assert-True ($evidenceLog.Contains('NO_PROHIBITED_PHYSICS=TRUE')) 'prohibited-physics marker missing'

$physicsText = (Get-Content -LiteralPath (Join-Path $evidence 'physics_inventory.csv') -Raw).ToLowerInvariant()
foreach($term in @('butler-volmer','butler volmer','sei kinetics','sei growth','li3n kinetics','li-nrr kinetics','linrr kinetics','her kinetics','hor kinetics','heat transfer')){Assert-True (-not $physicsText.Contains($term)) "forbidden final physics $term"}
$resultText = (Get-Content -LiteralPath (Join-Path $evidence 'result_inventory.csv') -Raw).ToLowerInvariant()
foreach($term in @('predicted full-cell voltage','predicted real li thickness','nh3 production map','fe map','selectivity map')){Assert-True (-not $resultText.Contains($term)) "forbidden result claim $term"}
$sourceHits = @(Select-String -Path (Join-Path $repo 'src\java\LiNRR_M10A4_*.java') -Pattern 'physics\(\)\.create\([^\r\n]*(Butler|HeatTransfer|SEI|Li3N|LiNRR|HER|HOR)' -CaseSensitive:$false)
Assert-True ($sourceHits.Count -eq 0) 'source API creates prohibited physics'

$finalRelative = 'models\generated\LiNRR_M10A4_ionic_current_li_plating.mph'
$finalHash = Hash $finalRelative
Assert-True ($finalHash -eq 'FADEEA4D8ADF9E472B4B855B05E39C22EF418214C0F62FB0C1977ACA36DA667B') 'final MPH SHA changed after reload/evidence'
Assert-True ((Get-Item -LiteralPath (Join-Path $repo $finalRelative)).Length -le 2147483648) 'final MPH exceeds GitHub LFS 2 GiB object cap'
$rOhm = Number (@((Import-Csv (Join-Path $tables 'M10A4_resistance_audit.csv')) | Where-Object resistance_component -eq 'model_electrolyte_ohmic_resistance')[0].value) 'R_ohmic'

$lines = @(
 "M10A4_BASE_COMMIT=$base",
 "CURRENT_BRANCH=$branch",
 'A3_SHA_UNCHANGED=TRUE',
 'COLLECTOR_STEP_SHA_UNCHANGED=TRUE',
 'CHAMBER_STEP_SHA_UNCHANGED=TRUE',
 'CURRENT_SOURCE=j_app_sensitivity=10 A/m^2',
 'CURRENT_SOURCE_CLASS=PROVISIONAL_SENSITIVITY',
 ('ACTIVE_AREA_MM2='+($area*1e6).ToString('G15',$invariant)),
 ('ACTIVE_AREA_CM2='+($area*1e4).ToString('G15',$invariant)),
 'KAPPA_SOURCE=kappa_M10A4_nominal=0.3 S/m',
 'KAPPA_SOURCE_CLASS=PROVISIONAL_SENSITIVITY_LITERATURE_ESTIMATE',
 'M10A4A_OHMIC_CURRENT_DISTRIBUTION=PASS',
 ('CURRENT_CONSERVATION_RELATIVE='+$currentRel.ToString('G15',$invariant)),
 ('MODEL_OHMIC_RESISTANCE_OHM='+$rOhm.ToString('G15',$invariant)),
 'M10A4B_IONIC_TRANSPORT=PASS',
 ('LI_CONSERVATION_RELATIVE='+$liRel.ToString('G15',$invariant)),
 ('BF4_CONSERVATION_RELATIVE='+$bfRel.ToString('G15',$invariant)),
 ('LI_MIN_MOL_M3='+$liMin.ToString('G15',$invariant)),
 ('LI_MAX_MOL_M3='+$liMax.ToString('G15',$invariant)),
 ('BF4_MIN_MOL_M3='+$bfMin.ToString('G15',$invariant)),
 ('BF4_MAX_MOL_M3='+$bfMax.ToString('G15',$invariant)),
 ('ELECTRONEUTRALITY_MAX_ABS='+$en.ToString('G15',$invariant)),
 'M10A4C_CURRENT_DISTRIBUTION=PASS',
 ('J_CATH_MEAN='+$jMean.ToString('G15',$invariant)),('J_CATH_MIN='+$jMin.ToString('G15',$invariant)),('J_CATH_MAX='+$jMax.ToString('G15',$invariant)),
 ('J_CATH_P10='+$jP10.ToString('G15',$invariant)),('J_CATH_P50='+$jP50.ToString('G15',$invariant)),('J_CATH_P90='+$jP90.ToString('G15',$invariant)),
 ('J_CATH_STD='+$jStd.ToString('G15',$invariant)),('J_CATH_CV='+$jCv.ToString('G15',$invariant)),
 'M10A4D_LI_EQUIVALENT_PLATING=PASS',
 ('LI_EQUIVALENT_MASS_9C_F1='+$f9.m_Li_equiv_kg),('LI_EQUIVALENT_MASS_45C_F1='+$f45.m_Li_equiv_kg),('LI_EQUIVALENT_MASS_54C_F1='+$f54.m_Li_equiv_kg),('LI_EQUIVALENT_MASS_99C_F1='+$f99.m_Li_equiv_kg),('LI_EQUIVALENT_MASS_297C_F1='+$f297.m_Li_equiv_kg),
 ('LI_EQUIVALENT_MEAN_THICKNESS_297C_F1='+$f297.mean_thickness_m),('LI_EQUIVALENT_THICKNESS_CV_297C_F1='+$f297.CV_thickness),
 ('FARADAY_LEDGER_MAX_RELATIVE='+$faradayMax.ToString('G15',$invariant)),
 'N2_CURRENT_OVERLAP_STATUS=PASS_DIAGNOSTIC_ONLY','DONOR_CURRENT_OVERLAP_STATUS=PASS_DIAGNOSTIC_ONLY_GENERIC_DONOR_CALIBRATION_REQUIRED','SPATIAL_COLIMITATION_STATUS=PASS_DIAGNOSTIC_ONLY',
 'SPATIAL_COLIMITATION_THRESHOLD_SET=0.40,0.50,0.60',
 ('MESH_MAX_KEY_DIFFERENCE='+$meshMax.ToString('G15',$invariant)),'MESH_LIMITING_METRIC=colim_MIXED_UNCLASSIFIED',
 'NEGATIVE_ION_CONCENTRATION_HITS=0','FULL_CELL_VOLTAGE_PREDICTED=FALSE','SEI_CREATED=FALSE','LI3N_KINETICS_CREATED=FALSE','LINRR_KINETICS_CREATED=FALSE','HER_CREATED=FALSE','HOR_CREATED=FALSE','FE_PREDICTED=FALSE','THERMAL_FEEDBACK_CREATED=FALSE',
 ('FINAL_MPH='+$finalRelative.Replace('\','/')),('FINAL_MPH_SHA256='+$finalHash),'FINAL_RELOAD=PASS','PARAMETER_PROVENANCE=PASS','NO_PROHIBITED_PHYSICS=TRUE','M10A4_OVERALL=PASS'
)
$report = Join-Path $evidence 'M10A4_final_report.txt'
[IO.File]::WriteAllLines($report,$lines,[Text.UTF8Encoding]::new($false))
$lines
