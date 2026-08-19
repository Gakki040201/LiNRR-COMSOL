[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
. (Join-Path $PSScriptRoot 'lib\Invoke-ComsolCaptured.ps1')

$ComsolBin = 'F:\COMSOL64\Multiphysics\bin\win64'
$Compiler = Join-Path $ComsolBin 'comsolcompile.exe'
$Batch = Join-Path $ComsolBin 'comsolbatch.exe'
$Java = Join-Path $ProjectRoot 'src\java\LiNRR_M10A2_SSC_Pipe_Wetting.java'
$InputMph = Join-Path $ProjectRoot 'models\generated\LiNRR_M10A1_1_real_cad_flow_repaired.mph'
$BaselineMph = Join-Path $ProjectRoot 'models\generated\LiNRR_M10A1_real_cad_flow_solved.mph'
$OutputMph = Join-Path $ProjectRoot 'models\generated\LiNRR_M10A2_ssc_pipe_wetting.mph'
$Collector = Join-Path $ProjectRoot 'cad\raw\M10A0_2\block_currentCollector_flowField_v03.2.1_20210525_STEP-AP203.STEP'
$Chamber = Join-Path $ProjectRoot 'cad\raw\M10A0_2\electrolyteChamber_rectangularMask_v02.2.2_20210521_STEP-AP203.STEP'
$ExpectedBaseline = '535F90E93B42B6D69A31A635C429B01B0B36D3EA8FE39B5645352FCBDF3D43C5'
$ExpectedCollector = '0091777D10ECA620E08780381438F3522B3BE31FEE307F9DDCF074A1FACDDFE9'
$ExpectedChamber = 'AE350E006228A10EEC4476F79CAFCE6D63F18F10F58F0A8B3559EED0C37FF12C'

foreach ($PathValue in @($Compiler,$Batch,$Java,$InputMph,$BaselineMph,$Collector,$Chamber)) {
    if (-not (Test-Path -LiteralPath $PathValue -PathType Leaf)) { throw "M10A2_REQUIRED_INPUT_MISSING: $PathValue" }
}
if ((git -C $ProjectRoot branch --show-current).Trim() -cne 'm10a2-ssc-pipe-wetting') { throw 'M10A2_WRONG_BRANCH' }

$BaselineBefore = Get-Item -LiteralPath $BaselineMph
$BaselineShaBefore = (Get-FileHash -LiteralPath $BaselineMph -Algorithm SHA256).Hash.ToUpperInvariant()
$CollectorBefore = Get-Item -LiteralPath $Collector
$CollectorShaBefore = (Get-FileHash -LiteralPath $Collector -Algorithm SHA256).Hash.ToUpperInvariant()
$ChamberBefore = Get-Item -LiteralPath $Chamber
$ChamberShaBefore = (Get-FileHash -LiteralPath $Chamber -Algorithm SHA256).Hash.ToUpperInvariant()
if ($BaselineShaBefore -cne $ExpectedBaseline) { throw "M10A2_BASELINE_HASH_MISMATCH: $BaselineShaBefore" }
if ($CollectorShaBefore -cne $ExpectedCollector) { throw "M10A2_COLLECTOR_HASH_MISMATCH: $CollectorShaBefore" }
if ($ChamberShaBefore -cne $ExpectedChamber) { throw "M10A2_CHAMBER_HASH_MISMATCH: $ChamberShaBefore" }

$Stamp = Get-Date -Format 'yyyyMMdd_HHmmss'
$RunDir = Join-Path $ProjectRoot "runs\M10A2\$($Stamp)_ssc_pipe_wetting"
[System.IO.Directory]::CreateDirectory($RunDir) | Out-Null
[System.IO.Directory]::CreateDirectory((Split-Path $OutputMph -Parent)) | Out-Null
if (Test-Path -LiteralPath $OutputMph -PathType Leaf) {
    Move-Item -LiteralPath $OutputMph -Destination (Join-Path $RunDir ('preexisting_' + (Split-Path $OutputMph -Leaf)))
}

function ConvertTo-JavaLiteral([string]$Value) { $Value.Replace('\','\\').Replace('"','\"') }
$RuntimeJava = Join-Path $RunDir 'LiNRR_M10A2_RuntimeInputs.java'
$RuntimeClass = Join-Path $RunDir 'LiNRR_M10A2_RuntimeInputs.class'
$RuntimeText = @"
public final class LiNRR_M10A2_RuntimeInputs {
 public static final String INPUT_MPH="$(ConvertTo-JavaLiteral $InputMph)";
 public static final String OUTPUT_MPH="$(ConvertTo-JavaLiteral $OutputMph)";
 public static final String RUN_DIR="$(ConvertTo-JavaLiteral $RunDir)";
 public static final String CC_STEP="$(ConvertTo-JavaLiteral $Collector)";
 private LiNRR_M10A2_RuntimeInputs() {}
}
"@
[System.IO.File]::WriteAllText($RuntimeJava,$RuntimeText,(New-Object System.Text.UTF8Encoding($false)))

function Invoke-Compile([string]$PathValue,[string]$Stem) {
    $Result = Invoke-ComsolCaptured -Executable $Compiler -ArgumentList @($PathValue) `
        -StdoutPath (Join-Path $RunDir "$($Stem)_compile_stdout.txt") `
        -StderrPath (Join-Path $RunDir "$($Stem)_compile_stderr.txt") `
        -MergedConsolePath (Join-Path $RunDir "$($Stem)_compile_merged.txt")
    $CompileText = Get-Content -LiteralPath (Join-Path $RunDir "$($Stem)_compile_merged.txt") -Raw -ErrorAction SilentlyContinue
    if ($Result.ExitCode -ne 0 -or $CompileText -match 'Compilation failed|ERROR:') { throw "M10A2_COMPILE_FAILED: $Stem" }
}
Invoke-Compile $RuntimeJava 'runtime'
Invoke-Compile $Java 'main'
if (-not (Test-Path -LiteralPath $RuntimeClass -PathType Leaf)) { throw 'M10A2_RUNTIME_CLASS_MISSING' }

$BatchLog = Join-Path $RunDir 'M10A2.log'
$Stdout = Join-Path $RunDir 'M10A2_stdout.txt'
$Stderr = Join-Path $RunDir 'M10A2_stderr.txt'
$Capture = Invoke-ComsolCaptured -Executable $Batch `
    -ArgumentList @('-classpathadd',$RunDir,'-inputfile',([System.IO.Path]::ChangeExtension($Java,'.class')),'-batchlog',$BatchLog) `
    -StdoutPath $Stdout -StderrPath $Stderr -MergedConsolePath (Join-Path $RunDir 'M10A2_merged.txt')
if ($Capture.ExitCode -ne 0) { throw "M10A2_BATCH_FAILED: exit=$($Capture.ExitCode)" }
foreach ($Marker in @('M10A2A_HYDRAULIC_NETWORK=PASS','M10A2B_POROUS_SSC=PASS','M10A2C_WETTING=PASS','M10A2D_N2_TRANSFER=PASS','M10A2_INDEPENDENT_MPH_RELOAD=PASS')) {
    if (-not (Select-String -LiteralPath $Stdout -SimpleMatch $Marker -Quiet)) { throw "M10A2_PASS_MARKER_MISSING: $Marker" }
}
if (-not (Test-Path -LiteralPath $OutputMph -PathType Leaf) -or (Get-Item -LiteralPath $OutputMph).Length -le 0) { throw 'M10A2_OUTPUT_MPH_MISSING' }

$ScanFiles = @($BatchLog,$Stdout,$Stderr,(Join-Path $RunDir 'M10A2_merged.txt'))
$Fatal = Select-String -Path $ScanFiles -Pattern @('\berrors?\b','\bfailed\b','\bundefined\b','\bsingular\b','\bout of memory\b','\bexception\b','\bNaN\b','\bInf\b') -CaseSensitive:$false -ErrorAction SilentlyContinue
if ($Fatal) {
    $Fatal | ForEach-Object { Write-Host "FATAL|$($_.Path)|$($_.LineNumber)|$($_.Line)" }
    throw 'M10A2_FATAL_LOG_MATCH'
}
$Warnings = Select-String -Path $ScanFiles -Pattern @('\bwarning\b','\bwarn:') -CaseSensitive:$false -ErrorAction SilentlyContinue

$Audit = @('section,case,metric,value')
Select-String -LiteralPath $Stdout -Pattern '^M10A2_AUDIT_CSV\|' | ForEach-Object {
    $Record = $_.Line -replace '^M10A2_AUDIT_CSV\|',''
    if ($Record -match '^porous_ssc,K_m2=(?<case>[^,]+),dp_Pa=(?<dp>[^,]+),u_superficial_m_s=(?<u>[^,]+)$') {
        $Audit += "porous_ssc,K_m2_$($Matches.case),dp_Pa,$($Matches.dp)"
        $Audit += "porous_ssc,K_m2_$($Matches.case),u_superficial_m_s,$($Matches.u)"
    } elseif ($Record -match '^wetting,dp_Pa=(?<case>[^,]+),Sl_min=(?<min>[^,]+),Sl_max=(?<max>[^,]+),gas_side_Sl=(?<gas>[^,]+)$') {
        $Audit += "wetting,dp_Pa_$($Matches.case),Sl_min,$($Matches.min)"
        $Audit += "wetting,dp_Pa_$($Matches.case),Sl_max,$($Matches.max)"
        $Audit += "wetting,dp_Pa_$($Matches.case),gas_side_Sl,$($Matches.gas)"
    } elseif ($Record -match '^(?<section>[^,]+),(?<metric>[^,]+),(?<value>[^,]+)$') {
        $Audit += "$($Matches.section),saved_case,$($Matches.metric),$($Matches.value)"
    } else {
        throw "M10A2_AUDIT_RECORD_UNPARSEABLE: $Record"
    }
}
[System.IO.File]::WriteAllLines((Join-Path $RunDir 'M10A2_numerical_audit.csv'),$Audit,(New-Object System.Text.UTF8Encoding($false)))

function Match-Value([string]$Pattern,[string]$Group='value') {
    $Hit = Select-String -LiteralPath $Stdout -Pattern $Pattern | Select-Object -Last 1
    if (-not $Hit) { throw "M10A2_REPORT_VALUE_MISSING: $Pattern" }
    return $Hit.Matches[0].Groups[$Group].Value
}
$Area = Match-Value 'flowfield_footprint_mm2=(?<value>[0-9.eE+-]+)'
$OpenArea = Match-Value 'open_channel_area_mm2=(?<value>[0-9.eE+-]+)'
$N2Cell = Match-Value 'M10A2_REAL_CAD_FLOW\|.*n2_dp_Pa=(?<value>[0-9.eE+-]+)'
$LiqCell = Match-Value 'M10A2_REAL_CAD_FLOW\|liquid_dp_Pa=(?<value>[0-9.eE+-]+)'
$H2Cell = Match-Value 'M10A2_H2_REAL_CAD_MIRROR\|dp_Pa=(?<value>[0-9.eE+-]+)'
$N2Pipe = Match-Value 'M10A2_PIPE_NETWORK\|n2_dp_Pa=(?<value>[0-9.eE+-]+)'
$H2Pipe = Match-Value 'M10A2_PIPE_NETWORK\|.*h2_dp_Pa=(?<value>[0-9.eE+-]+)'
$LiqPipe = Match-Value 'M10A2_PIPE_NETWORK\|.*liquid_known_dp_Pa=(?<value>[0-9.eE+-]+)'
$SlMin = Match-Value 'M10A2_WETTING_SWEEP\|dp_mbar=15.*Sl_min=(?<value>[0-9.eE+-]+)'
$SlMax = Match-Value 'M10A2_WETTING_SWEEP\|dp_mbar=30.*Sl_max=(?<value>[0-9.eE+-]+)'
$BreakSl = Match-Value 'M10A2_WETTING_SWEEP\|dp_mbar=30.*gas_side_Sl=(?<value>[0-9.eE+-]+)'
$Transfer = Match-Value 'M10A2_N2_TRANSFER\|in_mol_s=(?<value>[0-9.eE+-]+)'
$InterfaceC = Match-Value 'M10A2_N2_TRANSFER\|.*interface_c_mol_m3=(?<value>[0-9.eE+-]+)'
$ResidualMol = Match-Value 'M10A2_N2_TRANSFER\|.*residual_mol_s=(?<value>[0-9.eE+-]+)'
$ResidualRelative = Match-Value 'M10A2_N2_TRANSFER\|.*residual_relative=(?<value>[0-9.eE+-]+)'
$N2Total = ([double]$N2Cell + [double]$N2Pipe).ToString('G15',[System.Globalization.CultureInfo]::InvariantCulture)
$H2Total = ([double]$H2Cell + [double]$H2Pipe).ToString('G15',[System.Globalization.CultureInfo]::InvariantCulture)
$LiqTotal = ([double]$LiqCell + [double]$LiqPipe).ToString('G15',[System.Globalization.CultureInfo]::InvariantCulture)

$Comparison = @(
    'model,assumption,n2_flow_cm3_min,n2_cell_dp_Pa,liquid_cell_dp_Pa,external_n2_pipe_dp_Pa,ssc_penetration,n2_availability',
    'M10A1,impermeable/no-crossflow single-phase limit,10,15.9303788618,3.42685382263,0,none,not solved',
    "M10A2,SSC+tubing+pressure+wetting,50,$N2Cell,$LiqCell,$N2Pipe,gas_side_Sl_30mbar_$BreakSl,interface_c_$InterfaceC`_mol_m3"
)
[System.IO.File]::WriteAllLines((Join-Path $RunDir 'M10A1_vs_M10A2_comparison.csv'),$Comparison,(New-Object System.Text.UTF8Encoding($false)))
$Report = @"
# M10A2 numerical report

- M10A1.1 repaired baseline was loaded read-only; original M10A1 stayed immutable.
- M10A1 used the former 10 mL/min numerical N2 test while M10A2 uses the 50 mL/min Manual baseline; their N2 pressure-drop difference is not attributable to SSC/tubing alone.
- Real-CAD SSC flow-field footprint: $Area mm^2; actual open-channel interface: $OpenArea mm^2; Manual cut area: 3600 mm^2.
- Cell pressure drops: N2 $N2Cell Pa; H2 $H2Cell Pa; electrolyte $LiqCell Pa at the saved calibration-required 1 cm^3/min liquid sensitivity case.
- Documented external PFA drops: N2 $N2Pipe Pa; H2 $H2Pipe Pa; liquid $LiqPipe Pa. Unknown gas-inlet and latex lengths are excluded and remain calibration-required.
- Known partial total pressure budgets (cell plus documented PFA only): N2 $N2Total Pa; H2 $H2Total Pa; liquid $LiqTotal Pa.
- Wetting sensitivity: Sl(15 mbar) minimum $SlMin; maximum at 30 mbar $SlMax; gas-side Sl at 30 mbar and 5 ms $BreakSl. No breakthrough threshold was reached in the audited 5 ms window.
- Phenomenological N2 transfer: $Transfer mol/s; future cathodic-interface concentration $InterfaceC mol/m^3; stationary N-atom residual $ResidualMol mol/s (relative $ResidualRelative).
- K, porosity, contact angle, entry pressure, tortuosity, Henry coefficient, and electrolyte-specific transport data are provisional/calibration-required, not experimental.
- No Li plating, Li-NRR kinetics, SEI, or electrochemistry is included.
"@
[System.IO.File]::WriteAllText((Join-Path $RunDir 'M10A2_report.md'),$Report,(New-Object System.Text.UTF8Encoding($false)))

$BaselineAfter = Get-Item -LiteralPath $BaselineMph
$CollectorAfter = Get-Item -LiteralPath $Collector
$ChamberAfter = Get-Item -LiteralPath $Chamber
$BaselineShaAfter = (Get-FileHash -LiteralPath $BaselineMph -Algorithm SHA256).Hash.ToUpperInvariant()
$CollectorShaAfter = (Get-FileHash -LiteralPath $Collector -Algorithm SHA256).Hash.ToUpperInvariant()
$ChamberShaAfter = (Get-FileHash -LiteralPath $Chamber -Algorithm SHA256).Hash.ToUpperInvariant()
if ($BaselineAfter.Length -ne $BaselineBefore.Length -or $BaselineShaAfter -cne $BaselineShaBefore) { throw 'M10A2_BASELINE_CHANGED' }
if ($CollectorAfter.Length -ne $CollectorBefore.Length -or $CollectorShaAfter -cne $CollectorShaBefore) { throw 'M10A2_COLLECTOR_CHANGED' }
if ($ChamberAfter.Length -ne $ChamberBefore.Length -or $ChamberShaAfter -cne $ChamberShaBefore) { throw 'M10A2_CHAMBER_CHANGED' }

$Output = Get-Item -LiteralPath $OutputMph
Write-Host 'M10A2A_HYDRAULIC_NETWORK=PASS'
Write-Host 'M10A2B_POROUS_SSC=PASS'
Write-Host 'M10A2C_WETTING=PASS'
Write-Host 'M10A2D_N2_TRANSFER=PASS'
Write-Host "FINAL_MPH=$($Output.FullName)"
Write-Host "FINAL_MPH_BYTES=$($Output.Length)"
Write-Host "FINAL_MPH_SHA256=$((Get-FileHash -LiteralPath $OutputMph -Algorithm SHA256).Hash.ToUpperInvariant())"
Write-Host "M10A1_BASELINE_SHA_UNCHANGED=$($BaselineShaAfter -ceq $ExpectedBaseline)"
Write-Host "STEP_SHA_UNCHANGED=$([bool](($CollectorShaAfter -ceq $ExpectedCollector) -and ($ChamberShaAfter -ceq $ExpectedChamber)) )"
Write-Host "WARNING_HITS=$(@($Warnings).Count)"
Write-Host "RUN_DIR=$RunDir"
