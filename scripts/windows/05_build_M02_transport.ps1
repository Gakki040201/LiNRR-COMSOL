$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$ComsolBin = "F:\COMSOL64\Multiphysics\bin\win64"
$Compiler = Join-Path $ComsolBin "comsolcompile.exe"
$Batch = Join-Path $ComsolBin "comsolbatch.exe"
$Java = Join-Path $ProjectRoot "src\java\LiNRR_M02_Transport.java"
$LoadJava = Join-Path $ProjectRoot "tests\java\LiNRR_M02_LoadCheck.java"
$Output = Join-Path $ProjectRoot "models\generated\LiNRR_M02_transport.mph"
$Csv = Join-Path $ProjectRoot "results\tables\M02_species_summary.csv"
$N2Figure = Join-Path $ProjectRoot "results\figures\M02_N2_concentration.png"
$NH3Figure = Join-Path $ProjectRoot "results\figures\M02_NH3_concentration.png"
$ProfileFigure = Join-Path $ProjectRoot "results\figures\M02_cathode_profiles.png"
$LatestDir = Join-Path $ProjectRoot "runs\latest"
$LatestLog = Join-Path $LatestDir "M02_build.log"
$LatestReport = Join-Path $LatestDir "M02_report.md"
$DerivedModel = Join-Path $ProjectRoot "src\java\LiNRR_M02_Transport_Model.mph"
$ClassStatus = Join-Path $ProjectRoot "src\java\LiNRR_M02_Transport.class.status"
$LoadDerivedModel = Join-Path $ProjectRoot "tests\java\LiNRR_M02_LoadCheck_M02LoadCheck.mph"
$LoadClassStatus = Join-Path $ProjectRoot "tests\java\LiNRR_M02_LoadCheck.class.status"

$RunStamp = Get-Date -Format "yyyyMMdd_HHmmss"
$RunDir = Join-Path $ProjectRoot ("runs\{0}_M02" -f $RunStamp)
$CompileLog = Join-Path $RunDir "M02_compile.log"
$RunLog = Join-Path $RunDir "M02_build.log"
$Stdout = Join-Path $RunDir "M02_stdout.log"
$LoadCompileLog = Join-Path $RunDir "M02_load_compile.log"
$LoadLog = Join-Path $RunDir "M02_load_check.log"
$LoadStdout = Join-Path $RunDir "M02_load_stdout.log"
$RunReport = Join-Path $RunDir "M02_report.md"
$BuildStarted = Get-Date
$Provisional = 'PROVISIONAL ' + [char]0x2014 + ' numerical smoke test only'

@((Split-Path $Output),(Split-Path $Csv),(Split-Path $N2Figure),$LatestDir,$RunDir) |
    ForEach-Object { New-Item -ItemType Directory -Force -Path $_ | Out-Null }
foreach ($Required in @($Compiler,$Batch,$Java,$LoadJava)) {
    if (-not (Test-Path -LiteralPath $Required -PathType Leaf)) { throw "Missing required file: $Required" }
}

# Clean only M02-derived artifacts. No M00/M01 source, class, MPH, CSV, PNG, or log is touched.
Get-ChildItem -LiteralPath $ProjectRoot -Recurse -File -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -in @('LiNRR_M02_Transport.class','LiNRR_M02_LoadCheck.class') } |
    Remove-Item -Force
foreach ($Stale in @($Output,$Csv,$N2Figure,$NH3Figure,$ProfileFigure,$LatestLog,
    $LatestReport,$DerivedModel,$ClassStatus,$LoadDerivedModel,$LoadClassStatus)) {
    Remove-Item -LiteralPath $Stale -Force -ErrorAction SilentlyContinue
}

Push-Location $ProjectRoot
try {
    & $Compiler $Java 2>&1 | Tee-Object -FilePath $CompileLog
    if ($LASTEXITCODE -ne 0) { throw "M02 Java compilation failed. See $CompileLog" }
    $Class = Get-ChildItem -LiteralPath $ProjectRoot -Recurse -Filter 'LiNRR_M02_Transport.class' -File |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if ($null -eq $Class -or $Class.LastWriteTime -lt $BuildStarted.AddSeconds(-2)) {
        throw "Fresh LiNRR_M02_Transport.class was not found."
    }

    & $Batch -inputfile $Class.FullName -batchlog $RunLog 2>&1 | Tee-Object -FilePath $Stdout
    if ($LASTEXITCODE -ne 0) { throw "M02 COMSOL batch failed. See $RunLog" }
    if (-not (Test-Path -LiteralPath $RunLog -PathType Leaf)) { throw "M02 batch log was not created." }

    $FatalPattern = 'Exception|ERROR|Failed|Undefined|Singular matrix|Out of memory'
    $FatalHits = @(Select-String -Encoding UTF8 -LiteralPath $RunLog -Pattern $FatalPattern -CaseSensitive:$false)
    if ($FatalHits.Count -gt 0) {
        $FatalHits | ForEach-Object { Write-Host ('FATAL: ' + $_.Line) }
        throw "Fatal pattern found in M02 batch log."
    }

    foreach ($Expected in @($Output,$N2Figure,$NH3Figure,$ProfileFigure)) {
        if (-not (Test-Path -LiteralPath $Expected -PathType Leaf)) { throw "Missing M02 output: $Expected" }
        $Item=Get-Item -LiteralPath $Expected
        if ($Item.Length -le 0 -or $Item.LastWriteTime -lt $BuildStarted.AddSeconds(-2)) {
            throw "M02 output is empty or stale: $Expected"
        }
    }

    $OutputLines = @(Get-Content -Encoding UTF8 -LiteralPath $Stdout)
    $BaseLines = @($OutputLines | Where-Object { $_ -like 'M02_BASE|*' })
    $ScanLines = @($OutputLines | Where-Object { $_ -like 'M02_SCAN|*' })
    if ($BaseLines.Count -ne 3) { throw "Expected M02 base header plus 2 rows; found $($BaseLines.Count)." }
    if ($ScanLines.Count -ne 26) { throw "Expected M02 scan header plus 25 rows; found $($ScanLines.Count)." }

    $BaseHeader = ($BaseLines[0] -split '\|') | Select-Object -Skip 1
    $ScanHeader = ($ScanLines[0] -split '\|') | Select-Object -Skip 1
    $AllHeaders = @('row_type') + $BaseHeader + @($ScanHeader | Where-Object { $_ -notin $BaseHeader })
    function Convert-StructuredLines($RowType,$Headers,$Lines) {
        foreach ($Line in $Lines) {
            $Fields=($Line -split '\|') | Select-Object -Skip 1
            if ($Fields.Count -ne $Headers.Count) { throw "Malformed M02 structured line: $Line" }
            $Lookup=@{}
            for($i=0;$i -lt $Headers.Count;$i++){$Lookup[$Headers[$i]]=$Fields[$i]}
            $Object=[ordered]@{row_type=$RowType}
            foreach($Header in $AllHeaders | Select-Object -Skip 1){$Object[$Header]=if($Lookup.ContainsKey($Header)){$Lookup[$Header]}else{''}}
            [PSCustomObject]$Object
        }
    }
    $CsvRows = @()
    $CsvRows += @(Convert-StructuredLines 'base' $BaseHeader $BaseLines[1..2])
    $CsvRows += @(Convert-StructuredLines 'scan' $ScanHeader $ScanLines[1..25])
    $CsvRows | Export-Csv -LiteralPath $Csv -NoTypeInformation -Encoding UTF8
    $CsvText=[IO.File]::ReadAllText($Csv,[Text.Encoding]::UTF8) -replace "`r`n","`n"
    [IO.File]::WriteAllText($Csv,$CsvText,[Text.UTF8Encoding]::new($false))

    $Culture=[Globalization.CultureInfo]::InvariantCulture
    function Number($Value){[double]::Parse([string]$Value,$Culture)}
    $A=$CsvRows | Where-Object {$_.row_type -eq 'base' -and $_.study -eq 'Study_A_no_reaction'}
    $B=$CsvRows | Where-Object {$_.row_type -eq 'base' -and $_.study -eq 'Study_B_reactive'}
    if ((Number $A.N2_species_error) -gt 1e-4) { throw "Study A N2 conservation exceeds 1e-4." }
    if ((Number $B.N_error_boundary) -gt 1e-4 -or (Number $B.N_error_overall) -gt 1e-4) {
        throw "Study B nitrogen conservation exceeds 1e-4."
    }
    if ((Number $B.min_cN2_mol_m3) -lt -5e-5 -or (Number $B.min_cNH3_mol_m3) -lt -5e-5) {
        throw "Study B has a significant negative concentration."
    }
    $Stoich= [math]::Abs(2*(Number $B.N2_boundary_consumed_mol_s)-(Number $B.NH3_boundary_generated_mol_s)) /
        [math]::Max([math]::Abs((Number $B.NH3_boundary_generated_mol_s)),1e-30)
    if ($Stoich -gt 1e-4) { throw "rNH3=2*rN2 numerical verification failed." }
    if ((Number $B.N2_wall_outward_mol_s) -le 0 -or (Number $B.NH3_wall_outward_mol_s) -ge 0) {
        throw "Cathode flux sign verification failed."
    }

    & $Compiler $LoadJava 2>&1 | Tee-Object -FilePath $LoadCompileLog
    if ($LASTEXITCODE -ne 0) { throw "M02 load-check compilation failed." }
    $LoadClass=Get-ChildItem -LiteralPath $ProjectRoot -Recurse -Filter 'LiNRR_M02_LoadCheck.class' -File |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1
    & $Batch -inputfile $LoadClass.FullName -batchlog $LoadLog 2>&1 | Tee-Object -FilePath $LoadStdout
    if ($LASTEXITCODE -ne 0 -or -not ((Get-Content -Encoding UTF8 $LoadStdout) -like 'M02_LOAD_CHECK|PASS*')) {
        throw "Generated M02 MPH did not pass the load-only structural check."
    }
    $LoadFatal=@(Select-String -Encoding UTF8 -LiteralPath $LoadLog -Pattern $FatalPattern -CaseSensitive:$false)
    if($LoadFatal.Count -gt 0){throw "Fatal pattern found in M02 load-check log."}

    $WarningHits=@(Select-String -Encoding UTF8 -LiteralPath $RunLog -Pattern 'warning' -CaseSensitive:$false)
    $Scan=$CsvRows | Where-Object row_type -eq 'scan'
    $Failed=@($Scan | Where-Object status -like 'FAILED*')
    $FailedText=if($Failed.Count -eq 0){'- None.'}else{($Failed | ForEach-Object {
        "- Qliq=$($_.Qliq_cm3_min) cm^3/min, kN2=$($_.kN2_m_s) m/s: $($_.status)"
    }) -join [Environment]::NewLine}
    $ScanTable=($Scan | ForEach-Object {
        "| $($_.Qliq_cm3_min) | $($_.kN2_m_s) | $($_.N2_conversion) | $($_.NH3_generation_mol_s) | $($_.outlet_avg_cN2_mol_m3) | $($_.outlet_avg_cNH3_mol_m3) | $($_.min_cathode_cN2_mol_m3) | $($_.N_error_boundary) | $($_.N_error_overall) | $($_.status) |"
    }) -join [Environment]::NewLine

    $Report=@"
# M02 Species Transport Report

- Run: ``$RunStamp``
- Status: **PASS for the requested base numerical smoke test; not experimentally validated**
- COMSOL 6.4; editable MPH load check: PASS.
- All transport/kinetic/material placeholders are **$Provisional**.

## Model structure

- M01 two-dimensional 55 mm x 4 mm liquid channel with 55 mm out-of-plane width, coordinate-based named selections, Laminar Flow, and fully developed mean-velocity inlet.
- Added Transport of Diluted Species with fields ``cN2`` and ``cNH3``. Its user-defined convection velocity is the solved Laminar Flow field ``(u,v)``.
- Excluded at this stage: current-distribution physics, Butler-Volmer, Li+ migration, porous GDE, SEI, two-phase flow, HOR, and heat transfer.

## Equations and boundary conditions

- Stationary species equation: ``div(-D_i grad(c_i) + u c_i)=0`` in the electrolyte.
- Inlet: ``cN2=cN2_in`` and ``cNH3=0``. Outlet: COMSOL Outflow. Upper wall: no flux.
- Study A sets ``reaction_on=0``. Study B sets ``reaction_on=1``.
- Cathode phenomenological rate: ``rN2=kN2*cN2`` and ``rNH3=2*rN2``.
- COMSOL ``GeneralInwardFlux/J0`` is positive into the liquid. Therefore ``J0_N2=-rN2`` consumes N2 and ``J0_NH3=+rNH3`` generates NH3.
- Numerical sign check: N2 cathode outward flux $($B.N2_wall_outward_mol_s) mol/s (>0), NH3 outward flux $($B.NH3_wall_outward_mol_s) mol/s (<0).

## Parameters and provenance

- ``DN2=2e-9 m^2/s``, ``DNH3=2e-9 m^2/s``, ``cN2_in=5 mol/m^3``, and ``kN2=1e-5 m/s``: **$Provisional**.
- ``rho_el=900 kg/m^3`` and ``mu_el=3 mPa*s``: inherited M01 provisional smoke-test placeholders.
- These are not claimed as real Diglyme/LiBF4 properties. ``kN2`` is a phenomenological wall coefficient with no experimental calibration range yet.

## Mesh and solver

- Mapped 100 x 200 quadrilateral mesh inherited from accepted M01 (20,000 elements).
- Stationary coupled Laminar Flow + species studies. ``kN2`` is increased monotonically during continuation and each scan row is solved explicitly.
- Concentrations are not clipped. A value below ``-1e-5*cN2_in=-5e-5 mol/m^3`` is classified as significant and FAILED.

## Conservation and base results

Study A:

- N2 inlet/outlet: $($A.N2_in_mol_s) / $($A.N2_out_mol_s) mol/s.
- N2 relative conservation error: $($A.N2_species_error) (target <=1e-4, PASS).
- cN2 range: $($A.min_cN2_mol_m3) to $($A.max_cN2_mol_m3) mol/m^3; cNH3 range: $($A.min_cNH3_mol_m3) to $($A.max_cNH3_mol_m3) mol/m^3.

Study B, Qliq=1 cm^3/min and kN2=1e-5 m/s:

- N2 inlet/outlet/boundary consumption: $($B.N2_in_mol_s) / $($B.N2_out_mol_s) / $($B.N2_boundary_consumed_mol_s) mol/s.
- NH3 boundary generation/outlet: $($B.NH3_boundary_generated_mol_s) / $($B.NH3_out_mol_s) mol/s.
- N2 conversion: $($B.N2_conversion).
- Boundary stoichiometric nitrogen error: $($B.N_error_boundary); overall inlet/outlet nitrogen error: $($B.N_error_overall) (both <=1e-4, PASS).
- Diagnostic recovered-flux errors for the individual equations are N2=$($B.N2_species_error) and NH3=$($B.NH3_species_error). These exceed 1e-4 and remain an explicit postprocessing/mesh limitation even though both required nitrogen-atom balances pass.
- ``rNH3=2*rN2`` numerical check error: $Stoich (PASS).
- cN2 min/max: $($B.min_cN2_mol_m3) / $($B.max_cN2_mol_m3) mol/m^3.
- cNH3 min/max: $($B.min_cNH3_mol_m3) / $($B.max_cNH3_mol_m3) mol/m^3. The small negative undershoot is below the documented significant-negative threshold and is not clipped.

## Dimensionless numbers

- Characteristic mean velocity: ``uin=Qliq/(Hcell*Wcell)``; hydraulic length for Re: ``Hcell=4 mm``; axial length for Pe: ``Lcell=55 mm``.
- ``Re=rho_el*uin*Hcell/mu_el`` = $($B.Re).
- ``Pe_N2=uin*Lcell/DN2`` = $($B.PeN2); ``Pe_NH3=uin*Lcell/DNH3`` = $($B.PeNH3).
- One-reactive-wall Damkohler definition: ``Da_N2=kN2*Lcell/(uin*Hcell)`` = $($B.DaN2).

## Parameter scan

| Qliq (cm^3/min) | kN2 (m/s) | N2 conversion | NH3 generation (mol/s) | avg outlet cN2 | avg outlet cNH3 | min cathode cN2 | boundary N error | overall N error | status |
|---:|---:|---:|---:|---:|---:|---:|---:|---:|:---|
$ScanTable

### Failed combinations

$FailedText

All $($Failed.Count) failed combinations remain in the CSV and table; none were silently removed. Failures are caused by significant negative concentration at aggressive reaction/flow combinations, so those predictions are rejected.

## Log review

- Fatal matches: 0.
- Warning matches: $($WarningHits.Count).
- Full log preserved at ``runs/$($RunStamp)_M02/M02_build.log`` and copied to ``runs/latest/M02_build.log``.

## Numerical limitations and missing calibration data

- Individual N2/NH3 boundary accounting is also exported for diagnosis; the two required independent nitrogen-atom checks are the acceptance metrics.
- High-Peclet numerical undershoot remains visible and motivates stabilization/mesh studies before scientific use.
- Required experimental inputs: measured density/viscosity, N2 diffusivity and solubility/Henry data in the actual electrolyte, NH3 diffusivity, independently measured uptake/production rates, reactor geometry tolerances, and concentration/flow validation data with uncertainty.
- No experimental comparison has been performed; M02 is not experimentally validated.

## Before M03

- Calibrate or replace provisional transport properties and ``kN2`` with traceable data and uncertainty.
- Resolve failed scan regions using verified stabilization and transport mesh independence, not concentration clipping.
- Confirm outlet averaging/sampling definition against the experimental collection method.
- Preserve M02 transport validation before adding current distribution; do not infer microscopic mechanism from this phenomenological continuum fit.
"@
    Set-Content -LiteralPath $LatestReport -Value $Report -Encoding UTF8
    Copy-Item -LiteralPath $LatestReport -Destination $RunReport -Force
    Copy-Item -LiteralPath $RunLog -Destination $LatestLog -Force
    Write-Host "[SUCCESS] M02 base numerical smoke test passed. Failed scan rows retained: $($Failed.Count)."
}
finally {
    Remove-Item -LiteralPath $DerivedModel,$ClassStatus,$LoadDerivedModel,$LoadClassStatus -Force -ErrorAction SilentlyContinue
    Pop-Location
}
