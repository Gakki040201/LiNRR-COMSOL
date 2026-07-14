$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$ComsolBin = "F:\COMSOL64\Multiphysics\bin\win64"
$Compiler = Join-Path $ComsolBin "comsolcompile.exe"
$Batch = Join-Path $ComsolBin "comsolbatch.exe"
$Java = Join-Path $ProjectRoot "src\java\LiNRR_M01_Audit.java"
$Baseline = Join-Path $ProjectRoot "models\generated\LiNRR_M01_flow.mph"
$AuditModel = Join-Path $ProjectRoot "models\generated\LiNRR_M01_flow_audit.mph"
$Csv = Join-Path $ProjectRoot "results\tables\M01_mesh_audit.csv"
$LatestDir = Join-Path $ProjectRoot "runs\latest"
$LatestReport = Join-Path $LatestDir "M01_mesh_audit.md"
$RunStamp = Get-Date -Format "yyyyMMdd_HHmmss"
$RunDir = Join-Path $ProjectRoot ("runs\{0}_M01_audit" -f $RunStamp)
$CompileLog = Join-Path $RunDir "M01_audit_compile.log"
$RunLog = Join-Path $RunDir "M01_audit.log"
$Stdout = Join-Path $RunDir "M01_audit_stdout.log"
$RunReport = Join-Path $RunDir "M01_mesh_audit.md"
$BuildStarted = Get-Date
$DerivedModel = Join-Path $ProjectRoot "src\java\LiNRR_M01_Audit_M01Audit.mph"
$ClassStatus = Join-Path $ProjectRoot "src\java\LiNRR_M01_Audit.class.status"

New-Item -ItemType Directory -Force -Path $RunDir,$LatestDir,(Split-Path $Csv) | Out-Null
foreach ($Required in @($Compiler,$Batch,$Java,$Baseline)) {
    if (-not (Test-Path -LiteralPath $Required -PathType Leaf)) { throw "Missing required file: $Required" }
}

# Remove only audit-derived artifacts. M00 and M01 baselines are never touched.
Get-ChildItem -LiteralPath $ProjectRoot -Recurse -Filter "LiNRR_M01_Audit.class" -File -ErrorAction SilentlyContinue |
    Remove-Item -Force
Remove-Item -LiteralPath $AuditModel,$Csv,$LatestReport,$DerivedModel,$ClassStatus -Force -ErrorAction SilentlyContinue

Push-Location $ProjectRoot
try {
    & $Compiler $Java 2>&1 | Tee-Object -FilePath $CompileLog
    if ($LASTEXITCODE -ne 0) { throw "M01 audit compilation failed. See $CompileLog" }
    $Class = Get-ChildItem -LiteralPath $ProjectRoot -Recurse -Filter "LiNRR_M01_Audit.class" -File |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if ($null -eq $Class -or $Class.LastWriteTime -lt $BuildStarted.AddSeconds(-2)) {
        throw "Fresh LiNRR_M01_Audit.class was not found."
    }
    & $Batch -inputfile $Class.FullName -batchlog $RunLog 2>&1 | Tee-Object -FilePath $Stdout
    if ($LASTEXITCODE -ne 0) { throw "M01 audit batch failed. See $RunLog" }
    $FatalPattern = "Exception|ERROR|Failed|Undefined|Singular matrix|Out of memory|错误|出错"
    $FatalHits = @(Select-String -Encoding UTF8 -LiteralPath $RunLog -Pattern $FatalPattern -CaseSensitive:$false)
    if ($FatalHits.Count -gt 0) { throw "Fatal pattern found in M01 audit log: $($FatalHits[0].Line)" }

    $Lines = @(Get-Content -Encoding UTF8 -LiteralPath $Stdout | Where-Object { $_ -like 'M01_AUDIT|*' })
    if ($Lines.Count -ne 4) { throw "Expected audit header plus 3 rows; found $($Lines.Count)." }
    $Header = ($Lines[0] -split '\|') | Select-Object -Skip 1
    $Rows = foreach ($Line in $Lines | Select-Object -Skip 1) {
        $Values = ($Line -split '\|') | Select-Object -Skip 1
        $Object = [ordered]@{}
        for ($i=0; $i -lt $Header.Count; $i++) { $Object[$Header[$i]] = $Values[$i] }
        [PSCustomObject]$Object
    }
    $Rows | Export-Csv -LiteralPath $Csv -NoTypeInformation -Encoding UTF8
    $CsvText=[IO.File]::ReadAllText($Csv,[Text.Encoding]::UTF8) -replace "`r`n","`n"
    [IO.File]::WriteAllText($Csv,$CsvText,[Text.UTF8Encoding]::new($false))

    foreach ($Expected in @($AuditModel,$Csv)) {
        if (-not (Test-Path -LiteralPath $Expected -PathType Leaf) -or (Get-Item $Expected).Length -le 0) {
            throw "Audit output missing or empty: $Expected"
        }
    }
    $Fine = $Rows | Where-Object mesh -eq 'fine'
    $UinMeta = (Get-Content -Encoding UTF8 $Stdout | Where-Object { $_ -like 'M01_AUDIT_META|uin_expression|*' }) -split '\|'
    $MassCause = if ([double]$Fine.mass_error -lt [double]$Fine.mass_error_default * 0.1) {
        "The high-order integration reduced the reported imbalance by more than 10x; postprocessing quadrature is the dominant cause."
    } else {
        "High-order integration did not reduce the imbalance by 10x; the remaining error is dominated by the discrete velocity solution."
    }
    $MaxConclusion = "The fine-grid global maximum is classified as **$($Fine.max_region)** at x=$($Fine.max_x_m) m, y=$($Fine.max_y_m) m; max/uin=$($Fine.max_over_uin)."
    $Report = @"
# M01.1 Numerical Audit

- Run: ``$RunStamp``
- Status: independent numerical audit; M01 baseline source and MPH were not modified.
- Audit model copy: ``models/generated/LiNRR_M01_flow_audit.mph``
- ``uin`` definition: ``$($UinMeta[2])`` and COMSOL inlet ``LaminarInflow/Uav=uin``. This is the cross-sectional volumetric mean velocity, not a maximum velocity.

## Meshes and sampling

- Coarse: 40 x 20 mapped elements (length x height).
- Medium: 80 x 80.
- Fine: 160 x 200.
- Cross-sections: x/L = 0.01 (inlet vicinity), 0.50 (middle), and 0.99 (outlet vicinity).
- Centerline: y=Hcell/2. Cross-sectional mean: eighth-order line average of axial velocity.
- Reference for fully developed 2D parallel plates: ``u_center/u_mean = 1.5``.

## Fine-grid result

- Pressure drop: $($Fine.pressure_drop_Pa) Pa.
- Middle centerline velocity: $($Fine.mid_u_center_m_s) m/s.
- Middle mean velocity: $($Fine.mid_u_mean_m_s) m/s.
- Middle ratio: $($Fine.mid_ratio).
- Inlet-vicinity ratio: $($Fine.inlet_ratio); outlet-vicinity ratio: $($Fine.outlet_ratio).
- Inlet mass flow: $($Fine.mdot_in_kg_s) kg/s; outlet mass flow: $($Fine.mdot_out_kg_s) kg/s.
- High-order mass-conservation error: $($Fine.mass_error); default-postprocessing error: $($Fine.mass_error_default).

## Maximum-velocity location

$MaxConclusion

## Mass-flow expression audit

- Expression: ``rho_el*(u*nx+v*ny)*Wcell`` integrated on the named inlet/outlet selections.
- The outward normal makes inlet flow negative and outlet flow positive. Magnitudes are compared only after preserving/checking that sign convention.
- The out-of-plane width ``Wcell`` converts the 2D line integral to kg/s.
- Both the M01 default numerical integration and an explicitly activated eighth-order integration were evaluated.
- $MassCause
- Requiring 200 height elements is therefore assessed from the quantitative default-versus-high-order columns in the CSV, not assumed from thickness refinement alone.

## Scope

This audit establishes numerical behavior only. It is not mesh-independent experimental validation and does not change the accepted M01 baseline.
"@
    Set-Content -Encoding UTF8 -LiteralPath $LatestReport -Value $Report
    Copy-Item -LiteralPath $LatestReport -Destination $RunReport -Force
    Copy-Item -LiteralPath $RunLog -Destination (Join-Path $LatestDir 'M01_mesh_audit.log') -Force
    Write-Host "[SUCCESS] M01.1 audit completed: $Csv"
}
finally {
    Remove-Item -LiteralPath $DerivedModel,$ClassStatus -Force -ErrorAction SilentlyContinue
    Pop-Location
}
