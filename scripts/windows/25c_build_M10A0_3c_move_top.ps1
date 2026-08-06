[CmdletBinding()]
param()
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$ProjectRoot=(Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
. (Join-Path $PSScriptRoot 'lib\Invoke-M10A0CadStage.ps1')
Invoke-M10A0CadStage `
    -ProjectRoot $ProjectRoot `
    -StageToken 'M10A0_3c' `
    -RunSuffix '3c_move_top' `
    -JavaRelative 'src\java\LiNRR_M10A0_3c_MoveTop.java' `
    -JavaClassName 'LiNRR_M10A0_3c_MoveTop' `
    -RuntimeClassName 'LiNRR_M10A0_3c_RuntimeInputs' `
    -InputMphRelative 'models\generated\LiNRR_M10A0_3b_rotate_top.mph' `
    -OutputMphRelative 'models\generated\LiNRR_M10A0_3c_move_top.mph' `
    -PassMarker 'M10A0_3C_MOVE_TOP=PASS' `
    -RequiredMarkers @(
        'M10A0_3C_MOVE_FEATURE_CREATE_PASS',
        'M10A0_3C_MOVE_BUILD_PASS',
        'M10A0_3C_MODEL_SAVE_PASS',
        'M10A0_3C_MOVED_ENTITIES=domains:1,boundaries:135,edges:372,vertices:245',
        'M10A0_3C_BOLT_MAX_RESIDUAL_MM='
    )
