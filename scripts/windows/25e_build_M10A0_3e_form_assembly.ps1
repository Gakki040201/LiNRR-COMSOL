[CmdletBinding()]param()
$ErrorActionPreference='Stop';Set-StrictMode -Version Latest
$ProjectRoot=(Resolve-Path(Join-Path $PSScriptRoot '..\..')).Path
.(Join-Path $PSScriptRoot 'lib\Invoke-M10A0CadStage.ps1')
Invoke-M10A0CadStage -ProjectRoot $ProjectRoot -StageToken 'M10A0_3e' `
 -RunSuffix '3e_form_assembly' -JavaRelative 'src\java\LiNRR_M10A0_3e_FormAssembly.java' `
 -JavaClassName 'LiNRR_M10A0_3e_FormAssembly' -RuntimeClassName 'LiNRR_M10A0_3e_RuntimeInputs' `
 -InputMphRelative 'models\generated\LiNRR_M10A0_3d_mirror_bottom.mph' `
 -OutputMphRelative 'models\generated\LiNRR_M10A0_3e_form_assembly.mph' `
 -PassMarker 'M10A0_3E_FORM_ASSEMBLY=PASS' -RequiredMarkers @(
 'M10A0_3E_FORM_ASSEMBLY_FEATURE_PASS','M10A0_3E_FORM_ASSEMBLY_BUILD_PASS',
 'M10A0_3E_MODEL_SAVE_PASS','M10A0_3E_ASSEMBLY_OBJECT_COUNT=3',
 'M10A0_3E_ASSEMBLY_ENTITIES=domains:3,boundaries:363,edges:1004,vertices:662',
 'M10A0_3E_AUTOMATIC_PAIRS_CREATED=FALSE')
