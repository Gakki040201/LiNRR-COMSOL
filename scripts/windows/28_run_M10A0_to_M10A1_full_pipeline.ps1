[CmdletBinding()]
param(
    [switch]$ForceRun
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$CcStep = Join-Path $ProjectRoot 'cad\raw\M10A0_2\block_currentCollector_flowField_v03.2.1_20210525_STEP-AP203.STEP'
$ChamberStep = Join-Path $ProjectRoot 'cad\raw\M10A0_2\electrolyteChamber_rectangularMask_v02.2.2_20210521_STEP-AP203.STEP'
$ExpectedCcSha = '0091777D10ECA620E08780381438F3522B3BE31FEE307F9DDCF074A1FACDDFE9'
$ExpectedChamberSha = 'AE350E006228A10EEC4476F79CAFCE6D63F18F10F58F0A8B3559EED0C37FF12C'

function Get-CheckedSourceHashes {
    foreach ($PathValue in @($CcStep,$ChamberStep)) {
        if (-not (Test-Path -LiteralPath $PathValue -PathType Leaf)) { throw "PIPELINE_SOURCE_STEP_MISSING: $PathValue" }
    }
    $Cc = (Get-FileHash -LiteralPath $CcStep -Algorithm SHA256).Hash.ToUpperInvariant()
    $Chamber = (Get-FileHash -LiteralPath $ChamberStep -Algorithm SHA256).Hash.ToUpperInvariant()
    if ($Cc -cne $ExpectedCcSha -or $Chamber -cne $ExpectedChamberSha) {
        throw "PIPELINE_SOURCE_STEP_HASH_MISMATCH: cc=$Cc chamber=$Chamber"
    }
    [pscustomobject]@{ Cc = $Cc; Chamber = $Chamber }
}

$InitialHashes = Get-CheckedSourceHashes
$Stamp = Get-Date -Format 'yyyyMMdd_HHmmss'
$PipelineRun = Join-Path $ProjectRoot "runs\M10A1\$Stamp`_full_pipeline"
[System.IO.Directory]::CreateDirectory($PipelineRun) | Out-Null
$Manifest = Join-Path $PipelineRun 'M10A1_full_pipeline_manifest.csv'

$Stages = @(
    [pscustomobject]@{ Stage='M10A0.3b'; Script='25b_build_M10A0_3b_rotate_top.ps1'; Output='models\generated\LiNRR_M10A0_3b_rotate_top.mph'; RunRoot='runs\M10A0_3'; RunPattern='*_3b_rotate_top'; Stdout='M10A0_3b_stdout.txt'; BatchLog='M10A0_3b_rotate_top.log'; Marker='M10A0_3B_ROTATE_TOP=PASS' },
    [pscustomobject]@{ Stage='M10A0.3c'; Script='25c_build_M10A0_3c_move_top.ps1'; Output='models\generated\LiNRR_M10A0_3c_move_top.mph'; RunRoot='runs\M10A0_3'; RunPattern='*_3c_move_top'; Stdout='M10A0_3c_stdout.txt'; BatchLog='M10A0_3c_batch.log'; Marker='M10A0_3C_MOVE_TOP=PASS' },
    [pscustomobject]@{ Stage='M10A0.3d'; Script='25d_build_M10A0_3d_mirror_bottom.ps1'; Output='models\generated\LiNRR_M10A0_3d_mirror_bottom.mph'; RunRoot='runs\M10A0_3'; RunPattern='*_3d_mirror_bottom'; Stdout='M10A0_3d_stdout.txt'; BatchLog='M10A0_3d_batch.log'; Marker='M10A0_3D_MIRROR_BOTTOM=PASS' },
    [pscustomobject]@{ Stage='M10A0.3e'; Script='25e_build_M10A0_3e_form_assembly.ps1'; Output='models\generated\LiNRR_M10A0_3e_form_assembly.mph'; RunRoot='runs\M10A0_3'; RunPattern='*_3e_form_assembly'; Stdout='M10A0_3e_stdout.txt'; BatchLog='M10A0_3e_batch.log'; Marker='M10A0_3E_FORM_ASSEMBLY=PASS' },
    [pscustomobject]@{ Stage='M10A0.3f'; Script='25f_build_M10A0_3f_registered_assembly.ps1'; Output='models\generated\LiNRR_M10A0_3_real_cad_registered.mph'; RunRoot='runs\M10A0_3'; RunPattern='*_3f_registered_assembly'; Stdout='M10A0_3f_stdout.txt'; BatchLog='M10A0_3f_batch.log'; Marker='M10A0_3F_REGISTERED_ASSEMBLY=PASS' },
    [pscustomobject]@{ Stage='M10A0.4'; Script='26_build_M10A0_4_fluid_domains.ps1'; Output='models\generated\LiNRR_M10A0_4_real_cad_fluid_domains.mph'; RunRoot='runs\M10A0_4'; RunPattern='*_fluid_domains'; Stdout='M10A0_4_stdout.txt'; BatchLog='M10A0_4_fluid_domains.log'; Marker='M10A0_4_REAL_CAD_FLUID_DOMAINS=PASS' },
    [pscustomobject]@{ Stage='M10A1'; Script='27_build_M10A1_real_cad_flow.ps1'; Output='models\generated\LiNRR_M10A1_real_cad_flow_solved.mph'; RunRoot='runs\M10A1'; RunPattern='*_real_cad_flow'; Stdout='M10A1_stdout.txt'; BatchLog='M10A1_real_cad_flow.log'; Marker='M10A1_REAL_CAD_FLOW_SOLVED=PASS' }
)

function Find-ValidEvidence([object]$Stage) {
    $OutputPath = Join-Path $ProjectRoot $Stage.Output
    if (-not (Test-Path -LiteralPath $OutputPath -PathType Leaf) -or (Get-Item -LiteralPath $OutputPath).Length -le 0) { return $null }
    $RunRoot = Join-Path $ProjectRoot $Stage.RunRoot
    if (-not (Test-Path -LiteralPath $RunRoot -PathType Container)) { return $null }
    foreach ($Run in @(Get-ChildItem -LiteralPath $RunRoot -Directory -Filter $Stage.RunPattern | Sort-Object LastWriteTime -Descending)) {
        $Stdout = Join-Path $Run.FullName $Stage.Stdout
        $BatchLog = Join-Path $Run.FullName $Stage.BatchLog
        if (-not (Test-Path -LiteralPath $Stdout -PathType Leaf) -or -not (Test-Path -LiteralPath $BatchLog -PathType Leaf)) { continue }
        if ((Get-Item -LiteralPath $Stdout).Length -le 0 -or (Get-Item -LiteralPath $BatchLog).Length -le 0) { continue }
        if (-not (Select-String -LiteralPath $Stdout -SimpleMatch $Stage.Marker -Quiet)) { continue }
        if ((Get-Item -LiteralPath $OutputPath).LastWriteTimeUtc -lt $Run.CreationTimeUtc) { continue }
        return [pscustomobject]@{ Run=$Run; Output=(Get-Item -LiteralPath $OutputPath) }
    }
    return $null
}

$Rows = [System.Collections.Generic.List[object]]::new()
foreach ($Stage in $Stages) {
    $Evidence = if ($ForceRun) { $null } else { Find-ValidEvidence $Stage }
    $Action = 'SKIP_VALIDATED'
    if ($null -eq $Evidence) {
        $Action = 'EXECUTED'
        $ScriptPath = Join-Path $PSScriptRoot $Stage.Script
        if (-not (Test-Path -LiteralPath $ScriptPath -PathType Leaf)) { throw "PIPELINE_STAGE_SCRIPT_MISSING: $ScriptPath" }
        & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $ScriptPath
        if ($LASTEXITCODE -ne 0) { throw "PIPELINE_STAGE_FAILED: stage=$($Stage.Stage) exit=$LASTEXITCODE" }
        $Evidence = Find-ValidEvidence $Stage
        if ($null -eq $Evidence) { throw "PIPELINE_STAGE_EVIDENCE_INVALID_AFTER_RUN: stage=$($Stage.Stage)" }
    }
    $Hashes = Get-CheckedSourceHashes
    $MphHash = (Get-FileHash -LiteralPath $Evidence.Output.FullName -Algorithm SHA256).Hash.ToUpperInvariant()
    $Rows.Add([pscustomobject]@{
        stage=$Stage.Stage; status='PASS'; action=$Action; run_dir=$Evidence.Run.FullName
        mph_path=$Evidence.Output.FullName; mph_bytes=$Evidence.Output.Length; mph_sha256=$MphHash
        current_collector_step_sha256=$Hashes.Cc; chamber_step_sha256=$Hashes.Chamber
    })
    Write-Host "PIPELINE_STAGE|stage=$($Stage.Stage)|status=PASS|action=$Action|mph_sha256=$MphHash"
}

$Rows | Export-Csv -LiteralPath $Manifest -NoTypeInformation -Encoding UTF8
$FinalHashes = Get-CheckedSourceHashes
$FinalMph = Join-Path $ProjectRoot 'models\generated\LiNRR_M10A1_real_cad_flow_solved.mph'
$FinalItem = Get-Item -LiteralPath $FinalMph
$FinalSha = (Get-FileHash -LiteralPath $FinalMph -Algorithm SHA256).Hash.ToUpperInvariant()
Write-Host "PIPELINE_MANIFEST=$Manifest"
Write-Host "FINAL_MPH=$($FinalItem.FullName)"
Write-Host "FINAL_MPH_BYTES=$($FinalItem.Length)"
Write-Host "FINAL_MPH_SHA256=$FinalSha"
Write-Host "CURRENT_COLLECTOR_STEP_SHA256=$($FinalHashes.Cc)"
Write-Host "CHAMBER_STEP_SHA256=$($FinalHashes.Chamber)"
Write-Host 'M10A1_REAL_CAD_FLOW_SOLVED=PASS'
