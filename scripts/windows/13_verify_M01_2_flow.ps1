$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$ComsolBin = "F:\COMSOL64\Multiphysics\bin\win64"
$Compiler = Join-Path $ComsolBin "comsolcompile.exe"
$Batch = Join-Path $ComsolBin "comsolbatch.exe"
$FrozenJava = Join-Path $ProjectRoot "src\java\LiNRR_M01_Flow.java"
$MetricsJava = Join-Path $ProjectRoot "src\java\LiNRR_M01_2_Metrics.java"
$Java = Join-Path $ProjectRoot "src\java\LiNRR_M01_2_FlowVerification.java"
$LoadJava = Join-Path $ProjectRoot "tests\java\LiNRR_M01_2_LoadCheck.java"
$Output = Join-Path $ProjectRoot "models\generated\LiNRR_M01_2_flow_verification.mph"
$AnalyticCsv = Join-Path $ProjectRoot "results\tables\M01_2_flow_analytic.csv"
$MeshCsv = Join-Path $ProjectRoot "results\tables\M01_2_flow_mesh.csv"
$SweepCsv = Join-Path $ProjectRoot "results\tables\M01_2_flow_sweep.csv"
$VelocityFigure = Join-Path $ProjectRoot "results\figures\M01_2_velocity_profile.png"
$GradientFigure = Join-Path $ProjectRoot "results\figures\M01_2_pressure_gradient.png"
$ShearFigure = Join-Path $ProjectRoot "results\figures\M01_2_wall_shear.png"
$LatestDir = Join-Path $ProjectRoot "runs\latest"
$FrozenSource = Join-Path $LatestDir "M01_2_frozen_source.mph"
$LatestLog = Join-Path $LatestDir "M01_2_build.log"
$LatestReport = Join-Path $LatestDir "M01_2_report.md"
$RunStamp = Get-Date -Format "yyyyMMdd_HHmmss"
$RunDir = Join-Path $ProjectRoot ("runs\{0}_M01_2" -f $RunStamp)
$SourceStage = Join-Path $RunDir "frozen_M01_source_stage"
$CompileLog = Join-Path $RunDir "M01_2_compile.log"
$SourceLog = Join-Path $RunDir "M01_2_frozen_source.log"
$SourceStdout = Join-Path $RunDir "M01_2_frozen_source_stdout.log"
$RunLog = Join-Path $RunDir "M01_2_build.log"
$Stdout = Join-Path $RunDir "M01_2_stdout.log"
$ReloadLog = Join-Path $RunDir "M01_2_reload.log"
$ReloadStdout = Join-Path $RunDir "M01_2_reload_stdout.log"
$RunReport = Join-Path $RunDir "M01_2_report.md"

$FrozenRelative = @(
    "src\java\LiNRR_M00_Geometry.java",
    "src\java\LiNRR_M01_Flow.java",
    "src\java\LiNRR_M01_Audit.java",
    "scripts\windows\02_build_M00_geometry.ps1",
    "scripts\windows\04_build_M01_flow.ps1",
    "scripts\windows\04a_audit_M01_flow.ps1",
    "results\tables\M01_flow_summary.csv",
    "results\tables\M01_mesh_audit.csv",
    "results\figures\M01_velocity.png",
    "results\figures\M01_pressure.png",
    "models\generated\LiNRR_M00_geometry.mph",
    "models\generated\LiNRR_M01_flow.mph"
)

function Get-FrozenHashes {
    $Map = @{}
    foreach ($Relative in $FrozenRelative) {
        $Path = Join-Path $ProjectRoot $Relative
        $Map[$Relative] = if (Test-Path -LiteralPath $Path -PathType Leaf) {
            (Get-FileHash -Algorithm SHA256 -LiteralPath $Path).Hash
        } else { "<ABSENT>" }
    }
    return $Map
}

function Assert-FrozenHashes([hashtable]$Before, [hashtable]$After) {
    foreach ($Relative in $Before.Keys) {
        if ($Before[$Relative] -ne $After[$Relative]) {
            throw "Frozen baseline SHA-256 changed: $Relative"
        }
    }
}

function Convert-PipeRows([string[]]$Lines, [string]$Prefix) {
    if ($Lines.Count -lt 2) { throw "No structured rows for $Prefix" }
    $Header = ($Lines[0] -split '\|') | Select-Object -Skip 1
    foreach ($Line in $Lines | Select-Object -Skip 1) {
        $Values = ($Line -split '\|') | Select-Object -Skip 1
        if ($Values.Count -ne $Header.Count) { throw "Malformed $Prefix row: $Line" }
        $Data = [ordered]@{}
        for ($i = 0; $i -lt $Header.Count; $i++) { $Data[$Header[$i]] = $Values[$i] }
        [PSCustomObject]$Data
    }
}

function Export-Utf8Csv($Rows, [string]$Path) {
    $Rows | Export-Csv -LiteralPath $Path -NoTypeInformation -Encoding UTF8
    $Text = [IO.File]::ReadAllText($Path, [Text.Encoding]::UTF8) -replace "`r`n", "`n"
    [IO.File]::WriteAllText($Path, $Text, [Text.UTF8Encoding]::new($false))
}

function As-Double($Value) {
    return [double]::Parse([string]$Value, [Globalization.CultureInfo]::InvariantCulture)
}

New-Item -ItemType Directory -Force -Path $RunDir,$LatestDir,
    (Join-Path $SourceStage "models\generated"),(Join-Path $SourceStage "results\figures"),
    (Split-Path $Output),(Split-Path $AnalyticCsv),(Split-Path $VelocityFigure) | Out-Null
foreach ($Required in @($Compiler,$Batch,$FrozenJava,$MetricsJava,$Java,$LoadJava)) {
    if (-not (Test-Path -LiteralPath $Required -PathType Leaf)) {
        throw "Missing required file: $Required"
    }
}

$FrozenBefore = Get-FrozenHashes
Get-ChildItem -LiteralPath $ProjectRoot -Recurse -File -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -in @("LiNRR_M01_2_Metrics.class",
        "LiNRR_M01_2_FlowVerification.class","LiNRR_M01_2_LoadCheck.class") } |
    Remove-Item -Force
Remove-Item -LiteralPath $Output,$AnalyticCsv,$MeshCsv,$SweepCsv,$VelocityFigure,
    $GradientFigure,$ShearFigure,$FrozenSource,$LatestLog,$LatestReport -Force `
    -ErrorAction SilentlyContinue

Push-Location $ProjectRoot
try {
    # Rebuild the frozen M01 source in an isolated timestamped staging root.
    # Its hard-coded relative outputs therefore never touch frozen workspace files.
    & $Compiler $FrozenJava 2>&1 | Tee-Object -FilePath $CompileLog
    if ($LASTEXITCODE -ne 0) { throw "Frozen M01 source compilation failed." }
    $FrozenClass = Get-ChildItem -LiteralPath (Split-Path $FrozenJava) `
        -Filter "LiNRR_M01_Flow.class" | Select-Object -First 1
    if ($null -eq $FrozenClass) { throw "Frozen M01 class missing." }
    Push-Location $SourceStage
    try {
        & $Batch -inputfile $FrozenClass.FullName -batchlog $SourceLog 2>&1 |
            Tee-Object -FilePath $SourceStdout
        if ($LASTEXITCODE -ne 0) { throw "Isolated frozen M01 source batch failed." }
    } finally { Pop-Location }
    $StagedSource = Join-Path $SourceStage "models\generated\LiNRR_M01_flow.mph"
    if (-not (Test-Path -LiteralPath $StagedSource -PathType Leaf)) {
        throw "Isolated frozen M01 MPH was not generated."
    }
    Copy-Item -LiteralPath $StagedSource -Destination $FrozenSource -Force

    & $Compiler $MetricsJava 2>&1 | Tee-Object -FilePath $CompileLog -Append
    if ($LASTEXITCODE -ne 0) { throw "M01.2 metrics compilation failed." }
    & $Compiler -classpathadd (Split-Path $MetricsJava) $Java 2>&1 |
        Tee-Object -FilePath $CompileLog -Append
    if ($LASTEXITCODE -ne 0) { throw "M01.2 verification compilation failed." }
    & $Compiler $LoadJava 2>&1 | Tee-Object -FilePath $CompileLog -Append
    if ($LASTEXITCODE -ne 0) { throw "M01.2 reload-check compilation failed." }

    $Class = Get-ChildItem -LiteralPath (Split-Path $Java) `
        -Filter "LiNRR_M01_2_FlowVerification.class" | Select-Object -First 1
    $LoadClass = Get-ChildItem -LiteralPath (Split-Path $LoadJava) `
        -Filter "LiNRR_M01_2_LoadCheck.class" | Select-Object -First 1
    if ($null -eq $Class -or $null -eq $LoadClass) { throw "Fresh M01.2 class missing." }

    & $Batch -inputfile $Class.FullName -batchlog $RunLog 2>&1 |
        Tee-Object -FilePath $Stdout
    if ($LASTEXITCODE -ne 0) { throw "M01.2 COMSOL batch returned nonzero." }

    $MeshLines = @(Get-Content -Encoding UTF8 -LiteralPath $Stdout |
        Where-Object { $_ -like "M01_2_MESH|*" })
    $AnalyticLines = @(Get-Content -Encoding UTF8 -LiteralPath $Stdout |
        Where-Object { $_ -like "M01_2_ANALYTIC|*" })
    $SweepLines = @(Get-Content -Encoding UTF8 -LiteralPath $Stdout |
        Where-Object { $_ -like "M01_2_SWEEP|*" })
    if ($MeshLines.Count -ne 4) { throw "Expected mesh header plus 3 rows." }
    if ($AnalyticLines.Count -ne 3) { throw "Expected analytic header plus 2 rows." }
    if ($SweepLines.Count -ne 6) { throw "Expected sweep header plus 5 rows." }
    $MeshRows = @(Convert-PipeRows $MeshLines "M01_2_MESH")
    $AnalyticRows = @(Convert-PipeRows $AnalyticLines "M01_2_ANALYTIC")
    $SweepRows = @(Convert-PipeRows $SweepLines "M01_2_SWEEP")
    Export-Utf8Csv $MeshRows $MeshCsv
    Export-Utf8Csv $AnalyticRows $AnalyticCsv
    Export-Utf8Csv $SweepRows $SweepCsv

    if (($MeshRows.mesh -join ",") -ne "coarse,medium,fine") {
        throw "M01.2 mesh rows are incomplete or out of order."
    }
    if (($SweepRows.flow_scale -join ",") -ne
        "0.250000000000,0.500000000000,1.00000000000,2.00000000000,4.00000000000") {
        throw "M01.2 sweep rows are incomplete or out of order."
    }
    $Fine = $MeshRows | Where-Object mesh -eq "fine"
    $Accepted = $AnalyticRows | Where-Object case -eq "baseline_fine_fully_developed_outlet"
    $PressureReference = $AnalyticRows | Where-Object case -eq "frozen_pressure_outlet_reference"
    foreach ($Row in @($Accepted) + $SweepRows) {
        if ((As-Double $Row.mass_balance_error) -gt 1e-4) { throw "Mass balance tolerance failed." }
        if ((As-Double $Row.ratio_error) -gt 5e-3) { throw "Center/mean tolerance failed." }
        if ((As-Double $Row.dpdx_error) -gt 1e-2) { throw "Pressure-gradient tolerance failed." }
        if ((As-Double $Row.tau_upper_error) -gt 1e-2 -or
            (As-Double $Row.tau_lower_error) -gt 1e-2) { throw "Wall-shear tolerance failed." }
    }
    foreach ($Field in @("change_center","change_mean","change_ratio","change_dpdx",
        "change_pdrop","change_upper_shear","change_lower_shear","change_mdot_in",
        "change_mdot_out")) {
        if ((As-Double $Fine.$Field) -gt 5e-3) { throw "Mesh-change tolerance failed: $Field" }
    }

    foreach ($Expected in @($Output,$AnalyticCsv,$MeshCsv,$SweepCsv,$VelocityFigure,
                             $GradientFigure,$ShearFigure)) {
        if (-not (Test-Path -LiteralPath $Expected -PathType Leaf) -or
            (Get-Item -LiteralPath $Expected).Length -le 0) {
            throw "Missing or empty M01.2 output: $Expected"
        }
    }

    & $Batch -inputfile $LoadClass.FullName -batchlog $ReloadLog 2>&1 |
        Tee-Object -FilePath $ReloadStdout
    if ($LASTEXITCODE -ne 0 -or
        -not (Select-String -LiteralPath $ReloadStdout -SimpleMatch "M01_2_RELOAD|PASS")) {
        throw "Independent M01.2 MPH reload failed."
    }

    $FatalPattern = "Exception|ERROR|Failed to find a solution|Undefined value|Singular matrix|Out of memory|\u9519\u8bef|\u51fa\u9519"
    $WarningPattern = "warning|\u8b66\u544a"
    $FatalHits = @(Select-String -Encoding UTF8 -LiteralPath $SourceLog,$RunLog,$ReloadLog `
        -Pattern $FatalPattern -CaseSensitive:$false -ErrorAction SilentlyContinue)
    $WarningHits = @(Select-String -Encoding UTF8 -LiteralPath $SourceLog,$RunLog,$ReloadLog `
        -Pattern $WarningPattern -CaseSensitive:$false -ErrorAction SilentlyContinue)
    if ($FatalHits.Count -gt 0) { throw "Fatal pattern found in M01.2 logs." }

    $FrozenAfter = Get-FrozenHashes
    Assert-FrozenHashes $FrozenBefore $FrozenAfter
    Copy-Item -LiteralPath $RunLog -Destination $LatestLog -Force
    $WarningText = if ($WarningHits.Count) {
        ($WarningHits | ForEach-Object { "- " + $_.Line }) -join [Environment]::NewLine
    } else { "- None." }
    $SweepBalance = ($SweepRows | ForEach-Object {
        "- Q/Qbaseline=$($_.flow_scale): $($_.mass_balance_error)"
    }) -join [Environment]::NewLine
    $Report = @"
# M01.2 Two-Dimensional Parallel-Plate Flow Closure Verification

- Run: ``$RunStamp``
- Status: **PASS - numerical verification only; not experimental validation**
- Source: frozen M01 rebuilt in isolated timestamped staging and loaded as an independent derivative.
- Physics: stationary single-phase Laminar Flow only; no new physical field.
- Audit discretization: mapped 40x20, 80x40, 160x80 element families with ``order_fluid=2``.
- Accepted outlet closure: COMSOL ``LaminarOutflow`` with average exit pressure 0 Pa.
- Inputs (geometry, rho, mu, Q): **PROVISIONAL**.

## Baseline fine-grid analytical closure

- Umean analytical: $($Accepted.umean_analytic_m_s) m/s.
- Center velocity: $($Accepted.center_velocity_m_s) m/s; analytical $($Accepted.center_analytic_m_s); relative error $($Accepted.center_error).
- Cross-section mean: $($Accepted.cross_mean_m_s) m/s; relative error $($Accepted.mean_error).
- Center/mean ratio: $($Accepted.center_mean_ratio); relative error to 1.5: $($Accepted.ratio_error).
- Mid-channel dp/dx: $($Accepted.dpdx_mid_Pa_m) Pa/m; analytical $($Accepted.dpdx_analytic_Pa_m); relative error $($Accepted.dpdx_error).
- Global inlet-outlet pressure drop: $($Accepted.global_pdrop_Pa) Pa; analytical $($Accepted.pdrop_analytic_Pa); relative error $($Accepted.pdrop_error).
- Upper wall shear: $($Accepted.tau_upper_Pa) Pa; magnitude error $($Accepted.tau_upper_error).
- Lower wall shear: $($Accepted.tau_lower_Pa) Pa; magnitude error $($Accepted.tau_lower_error).
- Analytical wall-shear magnitude: $($Accepted.tau_abs_analytic_Pa) Pa.
- Inlet mass flow: $($Accepted.inlet_mass_flow_kg_s) kg/s.
- Outlet mass flow: $($Accepted.outlet_mass_flow_kg_s) kg/s.
- Mass-balance relative error: $($Accepted.mass_balance_error).

## Frozen pressure-outlet end-effect reference

The frozen pressure outlet is retained as a diagnostic row, not silently accepted: global pressure-drop error $($PressureReference.pdrop_error), outlet-boundary mass discrepancy $($PressureReference.mass_balance_error), while the middle-channel dp/dx error is $($PressureReference.dpdx_error). This distinguishes global truncation/outlet behavior from the fully developed middle solution. No global speed maximum is used; center velocity is evaluated only at x=Lcell/2, y=Hcell/2.

## Flow-sweep conservation errors

$SweepBalance

## Medium-to-fine changes

- Center velocity: $($Fine.change_center)
- Cross-section mean: $($Fine.change_mean)
- Center/mean ratio: $($Fine.change_ratio)
- Mid pressure gradient: $($Fine.change_dpdx)
- Global pressure drop: $($Fine.change_pdrop)
- Upper/lower wall shear: $($Fine.change_upper_shear) / $($Fine.change_lower_shear)
- Inlet/outlet mass flow: $($Fine.change_mdot_in) / $($Fine.change_mdot_out)
- All target changes <= 0.5%: PASS.

## MPH and log checks

- Independent MPH reload and retained center-velocity evaluation: PASS.
- Frozen baseline SHA-256 before/after: PASS; $($FrozenBefore.Count) paths unchanged.
- Fatal matches: 0.
- Warning matches: $($WarningHits.Count).

$WarningText

## Commands

- ``& '$Compiler' '$FrozenJava'``
- isolated frozen-source batch: ``& '$Batch' -inputfile '$($FrozenClass.FullName)' -batchlog '$SourceLog'``
- ``& '$Compiler' '$MetricsJava'``
- ``& '$Compiler' -classpathadd '$(Split-Path $MetricsJava)' '$Java'``
- ``& '$Compiler' '$LoadJava'``
- ``& '$Batch' -inputfile '$($Class.FullName)' -batchlog '$RunLog'``
- ``& '$Batch' -inputfile '$($LoadClass.FullName)' -batchlog '$ReloadLog'``

## Remaining uncertainty

Absolute pressure drop and shear scale directly with the provisional viscosity; Umean scales with provisional geometry and flow. The closure model is an ideal 2D parallel-plate benchmark and omits manifolds, 3D sidewalls, GDE permeability, gas-liquid effects, and experimental comparison. The fully developed outlet is an analytical-closure boundary, whereas the frozen pressure-outlet row quantifies computational truncation behavior.

RUN_STATE = SYNTHETIC_SMOKE_TEST

CALIBRATION_MODE = PROVISIONAL

M03B_READY = FALSE
"@
    Set-Content -LiteralPath $LatestReport -Value $Report -Encoding UTF8
    Copy-Item -LiteralPath $LatestReport -Destination $RunReport -Force
    Write-Host "[SUCCESS] M01.2 analytical closure, sweep, and mesh acceptance passed."
}
finally {
    Pop-Location
}
