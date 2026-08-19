[CmdletBinding()]param()
$ErrorActionPreference='Stop';Set-StrictMode -Version Latest
$ProjectRoot=(Resolve-Path(Join-Path $PSScriptRoot '..\..')).Path
.(Join-Path $PSScriptRoot 'lib\Invoke-M10A0CadStage.ps1')
Invoke-M10A0CadStage -ProjectRoot $ProjectRoot -StageToken 'M10A0_3d' `
 -RunSuffix '3d_mirror_bottom' -JavaRelative 'src\java\LiNRR_M10A0_3d_MirrorBottom.java' `
 -JavaClassName 'LiNRR_M10A0_3d_MirrorBottom' -RuntimeClassName 'LiNRR_M10A0_3d_RuntimeInputs' `
 -InputMphRelative 'models\generated\LiNRR_M10A0_3c_move_top.mph' `
 -OutputMphRelative 'models\generated\LiNRR_M10A0_3d_mirror_bottom.mph' `
 -PassMarker 'M10A0_3D_MIRROR_BOTTOM=PASS' -RequiredMarkers @(
 'M10A0_3D_MIRROR_FEATURE_CREATE_PASS','M10A0_3D_MIRROR_BUILD_PASS',
 'M10A0_3D_MODEL_SAVE_PASS','M10A0_3D_COLLECTOR_ENTITIES=domains:2,boundaries:270,edges:744,vertices:490',
 'M10A0_3D_NOMINAL_SOLID_PENETRATION_MM=0.00000000000')
