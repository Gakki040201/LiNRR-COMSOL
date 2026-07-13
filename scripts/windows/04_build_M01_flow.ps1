$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$ComsolBin = "F:\COMSOL64\Multiphysics\bin\win64"
$Compiler = Join-Path $ComsolBin "comsolcompile.exe"
$Batch = Join-Path $ComsolBin "comsolbatch.exe"

$Java = Join-Path $ProjectRoot "src\java\LiNRR_M01_Flow.java"
$Output = Join-Path $ProjectRoot "models\generated\LiNRR_M01_flow.mph"
$Csv = Join-Path $ProjectRoot "results\tables\M01_flow_summary.csv"
$VelocityFigure = Join-Path $ProjectRoot "results\figures\M01_velocity.png"
$PressureFigure = Join-Path $ProjectRoot "results\figures\M01_pressure.png"
$MisplacedVelocityFigure = Join-Path $ProjectRoot "models\generated\results\figures\M01_velocity.png"
$MisplacedPressureFigure = Join-Path $ProjectRoot "models\generated\results\figures\M01_pressure.png"
$LatestDir = Join-Path $ProjectRoot "runs\latest"
$LatestLog = Join-Path $LatestDir "M01_build.log"
$LatestReport = Join-Path $LatestDir "M01_report.md"
$DerivedModel = Join-Path $ProjectRoot "src\java\LiNRR_M01_Flow_Model.mph"

$RunStamp = Get-Date -Format "yyyyMMdd_HHmmss"
$RunDir = Join-Path $ProjectRoot ("runs\{0}_M01" -f $RunStamp)
$CompileLog = Join-Path $RunDir "M01_compile.log"
$RunLog = Join-Path $RunDir "M01_build.log"
$BatchStdout = Join-Path $RunDir "M01_stdout.log"
$RunReport = Join-Path $RunDir "M01_report.md"
$BuildStarted = Get-Date

@(
    (Split-Path $Output),
    (Split-Path $Csv),
    (Split-Path $VelocityFigure),
    $LatestDir,
    $RunDir
) | ForEach-Object { New-Item -ItemType Directory -Force -Path $_ | Out-Null }

foreach ($RequiredPath in @($Compiler, $Batch, $Java)) {
    if (-not (Test-Path -LiteralPath $RequiredPath -PathType Leaf)) {
        throw "Required file does not exist: $RequiredPath"
    }
}

# Clean only M01-derived outputs so stale files cannot masquerade as success.
Get-ChildItem -LiteralPath $ProjectRoot -Recurse -Filter "LiNRR_M01_Flow.class" `
    -File -ErrorAction SilentlyContinue | Remove-Item -Force
foreach ($Stale in @(
    $Output, $Csv, $VelocityFigure, $PressureFigure,
    $MisplacedVelocityFigure, $MisplacedPressureFigure,
    $LatestLog, $LatestReport, $DerivedModel
)) {
    Remove-Item -LiteralPath $Stale -Force -ErrorAction SilentlyContinue
}

Push-Location $ProjectRoot
try {
    Write-Host "Project root: $ProjectRoot"
    Write-Host "Run directory: $RunDir"
    Write-Host "Compiling: $Java"
    New-Item -ItemType File -Force -Path $CompileLog | Out-Null
    & $Compiler $Java 2>&1 | Tee-Object -FilePath $CompileLog -Append
    $CompileExitCode = $LASTEXITCODE
    if ($CompileExitCode -ne 0) {
        Copy-Item -LiteralPath $CompileLog -Destination $LatestLog -Force
        throw "COMSOL Java compilation failed with exit code $CompileExitCode. See $CompileLog"
    }

    $ClassFile = Get-ChildItem -LiteralPath $ProjectRoot -Recurse `
        -Filter "LiNRR_M01_Flow.class" -File |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
    if ($null -eq $ClassFile) {
        throw "Compilation returned success, but LiNRR_M01_Flow.class was not found."
    }
    if ($ClassFile.LastWriteTime -lt $BuildStarted.AddSeconds(-2)) {
        throw "Located class file predates this build: $($ClassFile.FullName)"
    }

    Write-Host "Compiled class: $($ClassFile.FullName)"
    Write-Host "Running COMSOL batch..."
    & $Batch -inputfile $ClassFile.FullName -batchlog $RunLog 2>&1 |
        Tee-Object -FilePath $BatchStdout
    $BatchExitCode = $LASTEXITCODE

    if (Test-Path -LiteralPath $RunLog) {
        Copy-Item -LiteralPath $RunLog -Destination $LatestLog -Force
    }
    if ($BatchExitCode -ne 0) {
        throw "COMSOL batch failed with exit code $BatchExitCode. See $RunLog"
    }
    if (-not (Test-Path -LiteralPath $RunLog -PathType Leaf)) {
        throw "COMSOL batch returned success, but no batch log was created: $RunLog"
    }

    $FatalPattern = "Exception|ERROR|Failed|Undefined|Singular matrix|Out of memory|错误|出错"
    $FatalHits = @(Select-String -Encoding UTF8 -LiteralPath $RunLog -Pattern $FatalPattern `
        -CaseSensitive:$false -ErrorAction SilentlyContinue)
    if ($FatalHits.Count -gt 0) {
        $FatalHits | ForEach-Object { Write-Host ("FATAL: " + $_.Line) }
        throw "Fatal pattern(s) found in M01 batch log."
    }

    if (-not (Test-Path -LiteralPath $BatchStdout -PathType Leaf)) {
        throw "COMSOL batch stdout capture was not created: $BatchStdout"
    }
    $ResultLines = @(Get-Content -Encoding UTF8 -LiteralPath $BatchStdout |
        Where-Object { $_ -like "M01_RESULT|*" })
    if ($ResultLines.Count -lt 14) {
        throw "Expected 14 structured M01 result lines, found $($ResultLines.Count)."
    }
    $CsvRows = foreach ($Line in $ResultLines | Select-Object -Skip 1) {
        $Fields = $Line -split '\|', 6
        if ($Fields.Count -ne 6) {
            throw "Malformed structured M01 result line: $Line"
        }
        $Status = $Fields[4]
        if ($Fields[1] -in @("rho_el", "mu_el")) {
            $Status = "PROVISIONAL — numerical smoke test only"
        }
        [PSCustomObject][ordered]@{
            parameter_name = $Fields[1]
            value = $Fields[2]
            unit = $Fields[3]
            status = $Status
            provenance = $Fields[5]
        }
    }
    $CsvRows | Export-Csv -LiteralPath $Csv -NoTypeInformation -Encoding UTF8
    $CsvText = [System.IO.File]::ReadAllText($Csv, [System.Text.Encoding]::UTF8)
    $CsvText = $CsvText -replace "`r`n", "`n"
    [System.IO.File]::WriteAllText(
        $Csv, $CsvText, [System.Text.UTF8Encoding]::new($false))

    $ExpectedOutputs = @($Output, $Csv, $VelocityFigure, $PressureFigure)
    foreach ($Expected in $ExpectedOutputs) {
        if (-not (Test-Path -LiteralPath $Expected -PathType Leaf)) {
            throw "Expected M01 output was not created: $Expected"
        }
        $Item = Get-Item -LiteralPath $Expected
        if ($Item.Length -le 0) {
            throw "Expected M01 output is empty: $Expected"
        }
        if ($Item.LastWriteTime -lt $BuildStarted.AddSeconds(-2)) {
            throw "Expected M01 output predates this build: $Expected"
        }
    }

    $Rows = @(Import-Csv -LiteralPath $Csv)
    $BalanceRow = $Rows | Where-Object { $_.parameter_name -eq "mass_balance_relative_error" }
    if ($null -eq $BalanceRow) {
        throw "CSV does not contain mass_balance_relative_error."
    }
    $Culture = [System.Globalization.CultureInfo]::InvariantCulture
    $BalanceError = [double]::Parse($BalanceRow.value, $Culture)
    if ([double]::IsNaN($BalanceError) -or [double]::IsInfinity($BalanceError)) {
        throw "Mass-balance relative error is not finite."
    }
    if ($BalanceError -gt 1.0e-4) {
        throw "Mass-balance relative error $BalanceError exceeds 1e-4."
    }

    function Get-ResultValue([string]$Name) {
        $Row = $Rows | Where-Object { $_.parameter_name -eq $Name }
        if ($null -eq $Row) { throw "CSV does not contain $Name." }
        return $Row.value
    }

    $Uin = Get-ResultValue "uin"
    $MaxVelocity = Get-ResultValue "max_velocity"
    $MdotIn = Get-ResultValue "mdot_in"
    $MdotOut = Get-ResultValue "mdot_out"
    $PressureDrop = Get-ResultValue "pressure_drop"
    $ResidenceTime = Get-ResultValue "residence_time"

    $WarningHits = @(Select-String -Encoding UTF8 -LiteralPath $RunLog `
        -Pattern "warning|警告" -CaseSensitive:$false -ErrorAction SilentlyContinue)
    $WarningText = if ($WarningHits.Count -eq 0) {
        "- None found in the batch log."
    } else {
        ($WarningHits | ForEach-Object { "- " + $_.Line }) -join [Environment]::NewLine
    }

    $Report = @"
# M01 Flow Build Report

- Run timestamp: $RunStamp
- COMSOL: 6.4, executable directory ``$ComsolBin``
- Status: **PASS — numerical smoke-test acceptance only; not experimentally validated**
- Model label: ``LiNRR_M01_flow | PROVISIONAL — numerical smoke test only``

## Changed and generated files

- ``src/java/LiNRR_M01_Flow.java``
- ``scripts/windows/04_build_M01_flow.ps1``
- ``models/generated/LiNRR_M01_flow.mph``
- ``results/tables/M01_flow_summary.csv``
- ``results/figures/M01_velocity.png``
- ``results/figures/M01_pressure.png``
- ``runs/latest/M01_build.log``
- ``runs/latest/M01_report.md``
- Timestamped evidence: ``runs/$($RunStamp)_M01/``

## Commands

- Compile: ``& "$Compiler" "$Java"``
- Batch: ``& "$Batch" -inputfile "$($ClassFile.FullName)" -batchlog "$RunLog"``
- Entry script: ``powershell -ExecutionPolicy Bypass -File .\scripts\windows\04_build_M01_flow.ps1``

## Model structure

- 2D rectangle: 55 mm length x 4 mm liquid height; 55 mm out-of-plane width.
- Stable coordinate-based Box selections: ``sel_electrolyte``, ``sel_inlet``, ``sel_outlet``, ``sel_anode_wall``, and ``sel_cathode_wall``.
- Stationary single-phase incompressible Laminar Flow only.
- Left inlet: fully developed laminar profile with mean speed ``uin = Qliq/(Hcell*Wcell)`` in +x.
- Right outlet: 0 Pa gauge pressure; upper and lower walls: no slip.
- Auditable mapped mesh: 100 elements along length and 200 through height (20,000 quadrilateral cells).
- No species transport, electrochemistry, porous media, SEI, two-phase flow, HOR, or heat transfer.

## Provisional inputs

**PROVISIONAL — numerical smoke test only**

- ``rho_el = 900 kg/m^3``
- ``mu_el = 3 mPa*s``

These values are assumed placeholders with no assigned experimental or literature provenance. Geometry, temperature, and flow rate are user-specified inputs, not validation data.

COMSOL 6.4 rejected the literal unit token ``mL`` as unknown during a load/evaluation check. The model therefore stores ``Qliq`` as the exactly equivalent ``1[cm^3/min]`` while reports and tables retain the requested engineering unit ``1 mL/min``.

## Numerical results

- Inlet mean velocity: $Uin m/s
- Maximum velocity: $MaxVelocity m/s
- Inlet mass flow magnitude: $MdotIn kg/s
- Outlet mass flow magnitude: $MdotOut kg/s
- Pressure drop: $PressureDrop Pa
- Nominal residence time: $ResidenceTime s
- Mass-balance relative error: $BalanceError
- Acceptance limit: <= 1e-4 (PASS)

## Log review

Fatal search pattern: ``Exception|ERROR|Failed|Undefined|Singular matrix|Out of memory|错误|出错``

- Fatal matches: 0
- Warning matches: $($WarningHits.Count)

$WarningText

## Acceptance

Java compilation, COMSOL batch execution, editable MPH generation, CSV export, both PNG exports, fatal-log screening, and mass-balance acceptance all passed. This establishes a numerical M01 smoke-test model; it does **not** constitute experimental validation or mesh independence.

## Data needed for M02

- Measured electrolyte density and viscosity at the actual composition and 298.15 K.
- Measured flow-rate/pressure-drop or residence-time data for hydraulic validation.
- N2 diffusivity, solubility/Henry-law data, inlet concentration, and cathode consumption boundary data with provenance and uncertainty.
- NH3 transport properties and outlet/analytical sampling definition.

## Remaining scientific and numerical uncertainty

- Provisional Newtonian liquid properties dominate the absolute pressure-drop uncertainty.
- The 2D single-phase rectangular channel omits manifolds, GDE permeability, gas-liquid effects, and three-dimensional edge effects.
- The inlet profile is the COMSOL fully developed laminar solution constrained by mean velocity; the residence time is nominal volume/flow, not an experimentally validated RTD.
- A formal mesh-independence study and independent pressure-drop/velocity comparison remain outstanding.
"@

    Set-Content -LiteralPath $LatestReport -Value $Report -Encoding UTF8
    Copy-Item -LiteralPath $LatestReport -Destination $RunReport -Force

    Write-Host ""
    Write-Host "[SUCCESS] M01 numerical smoke-test acceptance passed."
    Write-Host "Editable MPH: $Output"
    Write-Host "CSV: $Csv"
    Write-Host "Velocity figure: $VelocityFigure"
    Write-Host "Pressure figure: $PressureFigure"
    Write-Host "Latest log: $LatestLog"
    Write-Host "Latest report: $LatestReport"
    Write-Host "Mass-balance relative error: $BalanceError"
}
finally {
    Remove-Item -LiteralPath $DerivedModel -Force -ErrorAction SilentlyContinue
    Pop-Location
}
