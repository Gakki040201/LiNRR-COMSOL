$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$ComsolBin = "F:\COMSOL64\Multiphysics\bin\win64"
$Compiler = Join-Path $ComsolBin "comsolcompile.exe"
$Batch = Join-Path $ComsolBin "comsolbatch.exe"
$Java = Join-Path $ProjectRoot "src\java\LiNRR_M00_2_GeometryAudit.java"
$LoadJava = Join-Path $ProjectRoot "tests\java\LiNRR_M00_2_LoadCheck.java"
$Output = Join-Path $ProjectRoot "models\generated\LiNRR_M00_2_geometry_audit.mph"
$ScalingCsv = Join-Path $ProjectRoot "results\tables\M00_2_geometry_scaling.csv"
$SelectionCsv = Join-Path $ProjectRoot "results\tables\M00_2_selection_audit.csv"
$LatestDir = Join-Path $ProjectRoot "runs\latest"
$LatestLog = Join-Path $LatestDir "M00_2_build.log"
$LatestReport = Join-Path $LatestDir "M00_2_report.md"
$RunStamp = Get-Date -Format "yyyyMMdd_HHmmss"
$RunDir = Join-Path $ProjectRoot ("runs\{0}_M00_2" -f $RunStamp)
$CompileLog = Join-Path $RunDir "M00_2_compile.log"
$RunLog = Join-Path $RunDir "M00_2_build.log"
$Stdout = Join-Path $RunDir "M00_2_stdout.log"
$ReloadLog = Join-Path $RunDir "M00_2_reload.log"
$ReloadStdout = Join-Path $RunDir "M00_2_reload_stdout.log"
$RunReport = Join-Path $RunDir "M00_2_report.md"
$BuildStarted = Get-Date

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
        if (Test-Path -LiteralPath $Path -PathType Leaf) {
            $Map[$Relative] = (Get-FileHash -Algorithm SHA256 -LiteralPath $Path).Hash
        } else {
            $Map[$Relative] = "<ABSENT>"
        }
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
        if ($Values.Count -ne $Header.Count) {
            throw "Malformed $Prefix row: $Line"
        }
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

New-Item -ItemType Directory -Force -Path $RunDir,$LatestDir,
    (Split-Path $Output),(Split-Path $ScalingCsv) | Out-Null
foreach ($Required in @($Compiler,$Batch,$Java,$LoadJava)) {
    if (-not (Test-Path -LiteralPath $Required -PathType Leaf)) {
        throw "Missing required file: $Required"
    }
}

$FrozenBefore = Get-FrozenHashes
Get-ChildItem -LiteralPath $ProjectRoot -Recurse -File -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -in @("LiNRR_M00_2_GeometryAudit.class","LiNRR_M00_2_LoadCheck.class") } |
    Remove-Item -Force
Remove-Item -LiteralPath $Output,$ScalingCsv,$SelectionCsv,$LatestLog,$LatestReport -Force `
    -ErrorAction SilentlyContinue

Push-Location $ProjectRoot
try {
    & $Compiler $Java 2>&1 | Tee-Object -FilePath $CompileLog
    if ($LASTEXITCODE -ne 0) { throw "M00.2 Java compilation failed." }
    & $Compiler $LoadJava 2>&1 | Tee-Object -FilePath $CompileLog -Append
    if ($LASTEXITCODE -ne 0) { throw "M00.2 reload-check compilation failed." }

    $Class = Get-ChildItem -LiteralPath (Split-Path $Java) -Filter "LiNRR_M00_2_GeometryAudit.class" |
        Select-Object -First 1
    $LoadClass = Get-ChildItem -LiteralPath (Split-Path $LoadJava) -Filter "LiNRR_M00_2_LoadCheck.class" |
        Select-Object -First 1
    if ($null -eq $Class -or $null -eq $LoadClass) { throw "Fresh M00.2 class file missing." }

    & $Batch -inputfile $Class.FullName -batchlog $RunLog 2>&1 |
        Tee-Object -FilePath $Stdout
    if ($LASTEXITCODE -ne 0) { throw "M00.2 COMSOL batch returned nonzero." }

    $ScalingLines = @(Get-Content -Encoding UTF8 -LiteralPath $Stdout |
        Where-Object { $_ -like "M00_2_SCALING|*" })
    $SelectionLines = @(Get-Content -Encoding UTF8 -LiteralPath $Stdout |
        Where-Object { $_ -like "M00_2_SELECTION|*" })
    if ($ScalingLines.Count -ne 8) { throw "Expected scaling header plus 7 rows." }
    if ($SelectionLines.Count -ne 36) { throw "Expected selection header plus 35 rows." }
    $ScalingRows = @(Convert-PipeRows $ScalingLines "M00_2_SCALING")
    $SelectionRows = @(Convert-PipeRows $SelectionLines "M00_2_SELECTION")
    Export-Utf8Csv $ScalingRows $ScalingCsv
    Export-Utf8Csv $SelectionRows $SelectionCsv

    if (@($ScalingRows | Where-Object status -ne "PASS").Count -ne 0) {
        throw "M00.2 scaling failure."
    }
    if (@($SelectionRows | Where-Object status -ne "PASS").Count -ne 0) {
        throw "FAILED_SELECTION_MAPPING"
    }
    foreach ($Row in $ScalingRows) {
        foreach ($Field in @("domain_area_mm2","inlet_length_mm","outlet_length_mm",
                             "upper_length_mm","lower_length_mm")) {
            if ([double]$Row.$Field -le 0) { throw "Nonpositive M00.2 measure: $Field" }
        }
    }
    foreach ($Expected in @($Output,$ScalingCsv,$SelectionCsv)) {
        if (-not (Test-Path -LiteralPath $Expected -PathType Leaf) -or
            (Get-Item -LiteralPath $Expected).Length -le 0) {
            throw "Missing or empty M00.2 output: $Expected"
        }
    }

    & $Batch -inputfile $LoadClass.FullName -batchlog $ReloadLog 2>&1 |
        Tee-Object -FilePath $ReloadStdout
    if ($LASTEXITCODE -ne 0 -or
        -not (Select-String -LiteralPath $ReloadStdout -SimpleMatch "M00_2_RELOAD|PASS")) {
        throw "Independent M00.2 MPH reload failed."
    }

    $FatalPattern = "Exception|ERROR|Failed to find a solution|Undefined value|Singular matrix|Out of memory|\u9519\u8bef|\u51fa\u9519"
    $WarningPattern = "warning|\u8b66\u544a"
    $FatalHits = @(Select-String -Encoding UTF8 -LiteralPath $RunLog,$ReloadLog `
        -Pattern $FatalPattern -CaseSensitive:$false -ErrorAction SilentlyContinue)
    $WarningHits = @(Select-String -Encoding UTF8 -LiteralPath $RunLog,$ReloadLog `
        -Pattern $WarningPattern -CaseSensitive:$false -ErrorAction SilentlyContinue)
    if ($FatalHits.Count -gt 0) { throw "Fatal pattern found in M00.2 log." }

    $FrozenAfter = Get-FrozenHashes
    Assert-FrozenHashes $FrozenBefore $FrozenAfter
    Copy-Item -LiteralPath $RunLog -Destination $LatestLog -Force
    $WarningText = if ($WarningHits.Count) {
        ($WarningHits | ForEach-Object { "- " + $_.Line }) -join [Environment]::NewLine
    } else { "- None." }
    $Report = @"
# M00.2 Geometry Parameterization and Named-Selection Audit

- Run: ``$RunStamp``
- Status: **PASS - numerical verification only; not experimental validation**
- Geometry/material/calibration state: **PROVISIONAL**
- Cases: baseline; Lcell x 0.5/x 2; Hcell x 0.5/x 2; Wcell x 0.5/x 2.
- Named-selection checks: 35/35 PASS; no ``FAILED_SELECTION_MAPPING``.
- Positive geometry measures: 7/7 domain areas and 28/28 boundary lengths PASS.
- Geometry build, mesh build, MPH save: 7/7 PASS.
- Independent MPH reload: PASS; geometry, all five named selections, and mesh rebuilt.
- Frozen baseline SHA-256 before/after: PASS; $($FrozenBefore.Count) paths unchanged (including absent generated baselines).

## Outputs

- ``models/generated/LiNRR_M00_2_geometry_audit.mph``
- ``results/tables/M00_2_geometry_scaling.csv``
- ``results/tables/M00_2_selection_audit.csv``
- ``runs/latest/M00_2_build.log``
- ``runs/latest/M00_2_report.md``
- Timestamped evidence: ``runs/$($RunStamp)_M00_2/``

## Commands

- ``& '$Compiler' '$Java'``
- ``& '$Compiler' '$LoadJava'``
- ``& '$Batch' -inputfile '$($Class.FullName)' -batchlog '$RunLog'``
- ``& '$Batch' -inputfile '$($LoadClass.FullName)' -batchlog '$ReloadLog'``

## Log classification

- Fatal matches: 0.
- Warning matches: $($WarningHits.Count).

$WarningText

## Scope and uncertainty

The audit verifies coordinate-based named-selection mapping and editable-MPH serialization across parameter changes. It does not validate the provisional dimensions against experiment and adds no physics.

RUN_STATE = SYNTHETIC_SMOKE_TEST

CALIBRATION_MODE = PROVISIONAL

M03B_READY = FALSE
"@
    Set-Content -LiteralPath $LatestReport -Value $Report -Encoding UTF8
    Copy-Item -LiteralPath $LatestReport -Destination $RunReport -Force
    Write-Host "[SUCCESS] M00.2 geometry and selection audit passed."
}
finally {
    Pop-Location
}
