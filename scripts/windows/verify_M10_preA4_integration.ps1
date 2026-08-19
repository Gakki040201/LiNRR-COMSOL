param(
    [switch]$Fast,
    [switch]$Full
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$ExpectedBranch = 'integration/m10-prea4-verification-bridge'
$ExpectedHead = '09a5ca3e09064c197b1cf77f7c7591dc49430c44'
$A3Mph = Join-Path $ProjectRoot 'models\generated\LiNRR_M10A3_real_species_transport.mph'
$CollectorStep = Join-Path $ProjectRoot 'cad\raw\M10A0_2\block_currentCollector_flowField_v03.2.1_20210525_STEP-AP203.STEP'
$ChamberStep = Join-Path $ProjectRoot 'cad\raw\M10A0_2\electrolyteChamber_rectangularMask_v02.2.2_20210521_STEP-AP203.STEP'

function Invoke-External([string]$Worktree, [string]$Script) {
    Push-Location $Worktree
    try {
        & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $Script
        if ($LASTEXITCODE -ne 0) { throw "External script failed: $Script" }
    }
    finally {
        Pop-Location
    }
}

function Assert-File([string]$Path, [string]$Label) {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf) -or (Get-Item -LiteralPath $Path).Length -le 0) {
        throw "Missing or empty $Label`: $Path"
    }
}

function Assert-Hash([string]$Path, [string]$Expected, [string]$Label) {
    Assert-File $Path $Label
    $Actual = (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash
    if ($Actual -cne $Expected) { throw "$Label hash mismatch: $Actual" }
}

function Run-Fast {
    Write-Host '=== PRE-A4 FAST ==='
    if ((git branch --show-current) -cne $ExpectedBranch) { throw "Unexpected branch: $(git branch --show-current)" }
    if ((git rev-parse HEAD) -cne $ExpectedHead) { throw "Unexpected HEAD: $(git rev-parse HEAD)" }

    Assert-Hash $A3Mph '03612FDB08D993595ABDA41D5873CBBCAA97580DB432ADCD2FBCD2CA06260C00' 'M10A3 MPH'
    Assert-Hash $CollectorStep '0091777D10ECA620E08780381438F3522B3BE31FEE307F9DDCF074A1FACDDFE9' 'Collector STEP'
    Assert-Hash $ChamberStep 'AE350E006228A10EEC4476F79CAFCE6D63F18F10F58F0A8B3559EED0C37FF12C' 'Chamber STEP'

    foreach ($Path in @(
        'results/tables/M10_preA4_git_lineage.csv',
        'results/tables/M10_preA4_stage_inventory.csv',
        'docs/M10_PREA4_INTEGRATION_CONTRACT.md',
        'config/M10_preA4_verification_bridge.csv',
        'results/tables/M10_preA4_parameter_provenance_audit.csv',
        'results/tables/M10_preA4_geometry_authority.csv',
        'results/tables/M10A3_RTD.csv',
        'results/tables/M10A3_RTD_temporal_quadrature_convergence.csv'
    )) {
        Assert-File (Join-Path $ProjectRoot $Path) $Path
    }

    foreach ($Test in @(
        'tests/powershell/Test_M03A_2_EIS.ps1',
        'tests/powershell/Test_M03A_4_Calibration.ps1',
        'tests/powershell/Test_M03A_5_ExperimentalIntake.ps1',
        'tests/powershell/Test_M03A_6_Acquisition.ps1'
    )) {
        Write-Host "RUN $Test"
        & powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $ProjectRoot $Test)
        if ($LASTEXITCODE -ne 0) { throw "Unit test failed: $Test" }
    }

    $Lineage = @(Import-Csv (Join-Path $ProjectRoot 'results/tables/M10_preA4_git_lineage.csv'))
    if (@($Lineage | Where-Object ancestor_status -ne 'PASS').Count -ne 0) { throw 'Git lineage audit failed' }

    $ParamAudit = @(Import-Csv (Join-Path $ProjectRoot 'results/tables/M10_preA4_parameter_provenance_audit.csv'))
    if ($ParamAudit.Count -eq 0 -or @($ParamAudit | Where-Object transfer_allowed -eq 'TRUE').Count -ne 0) {
        throw 'Parameter contamination audit failed'
    }

    $GeoAudit = @(Import-Csv (Join-Path $ProjectRoot 'results/tables/M10_preA4_geometry_authority.csv'))
    if (@($GeoAudit | Where-Object { $_.is_authority_for_M10 -eq 'TRUE' -and $_.stage -in @('M00','M01','M02','M03A') }).Count -ne 0) {
        throw 'Geometry authority audit failed'
    }

    $ReloadJava = Join-Path $ProjectRoot 'tests/java/LiNRR_M10A3_ReloadCheck.java'
    Assert-File $ReloadJava 'M10A3 reload check source'
    & 'F:\COMSOL64\Multiphysics\bin\win64\comsolcompile.exe' $ReloadJava
    if ($LASTEXITCODE -ne 0) { throw 'M10A3 reload check compilation failed' }

    $Session = Join-Path $ProjectRoot 'runs/MASTER/20260819_165548'
    $Prefs = Join-Path $Session 'm10a3_reload_prefs'
    $ReloadOut = Join-Path $Session 'M10A3_reload_driver_stdout.log'
    $ReloadLog = Join-Path $Session 'M10A3_reload_driver_batch.log'
    $ReloadErr = Join-Path $Session 'M10A3_reload_driver_stderr.log'
    & 'F:\COMSOL64\Multiphysics\bin\win64\comsolbatch.exe' -prefsdir $Prefs `
        -inputfile (Join-Path $ProjectRoot 'tests/java/LiNRR_M10A3_ReloadCheck.class') `
        -batchlog $ReloadLog 1> $ReloadOut 2> $ReloadErr
    if (-not (Select-String -LiteralPath $ReloadOut -SimpleMatch 'M10A3_INDEPENDENT_RELOAD=PASS' -Quiet)) {
        throw 'M10A3 independent reload failed'
    }

    $RtdRows = @(Import-Csv (Join-Path $ProjectRoot 'results/tables/M10A3_RTD_temporal_quadrature_convergence.csv'))
    $Accepted = $RtdRows | Where-Object case -eq 'two_x_denser_tau_nom_over_600'
    if ($null -eq $Accepted) { throw 'Accepted M10A3 RTD row missing' }
    if ([int]$Accepted.output_points -ne 3703) { throw 'M10A3 RTD output point count mismatch' }
    Write-Host 'M10_PREA4_FAST=PASS'
}

function Run-Full {
    Write-Host '=== PRE-A4 FULL ==='
    Run-Fast
    $Pre = 'F:\LiNRR_COMSOL\worktrees\LiNRR_PreGeometry'
    $M02 = 'F:\LiNRR_COMSOL\worktrees\LiNRR_M02_2'
    $M03 = 'F:\LiNRR_COMSOL\worktrees\LiNRR_M03A_3'
    Invoke-External $Pre 'scripts/windows/12_verify_M00_2_geometry.ps1'
    Invoke-External $Pre 'scripts/windows/13_verify_M01_2_flow.ps1'
    Invoke-External $M02 'scripts/windows/14_verify_M02_2_transport.ps1'
    $env:M03A3_RESUME_RUN = 'F:\LiNRR_COMSOL\worktrees\LiNRR_M03A_3\runs\20260717_200926_M03A_3'
    Invoke-External $M03 'scripts/windows/16_verify_M03A_3_coupling.ps1'
    Write-Host 'M10_PREA4_FULL=PASS'
}

if ($Full) {
    Run-Full
}
elseif ($Fast) {
    Run-Fast
}
else {
    Write-Host 'Usage: verify_M10_preA4_integration.ps1 -Fast | -Full'
}
