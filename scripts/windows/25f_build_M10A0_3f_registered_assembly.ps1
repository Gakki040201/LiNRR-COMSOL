[CmdletBinding()]param()
$ErrorActionPreference='Stop';Set-StrictMode -Version Latest
$ProjectRoot=(Resolve-Path(Join-Path $PSScriptRoot '..\..')).Path
.(Join-Path $PSScriptRoot 'lib\Invoke-M10A0CadStage.ps1')
Invoke-M10A0CadStage -ProjectRoot $ProjectRoot -StageToken 'M10A0_3f' `
 -RunSuffix '3f_registered_assembly' -JavaRelative 'src\java\LiNRR_M10A0_3f_RegisteredAssembly.java' `
 -JavaClassName 'LiNRR_M10A0_3f_RegisteredAssembly' -RuntimeClassName 'LiNRR_M10A0_3f_RuntimeInputs' `
 -InputMphRelative 'models\generated\LiNRR_M10A0_3e_form_assembly.mph' `
 -OutputMphRelative 'models\generated\LiNRR_M10A0_3_real_cad_registered.mph' `
 -PassMarker 'M10A0_3F_REGISTERED_ASSEMBLY=PASS' -RequiredMarkers @(
 'M10A0_3F_SELECTION_CREATE_PASS','M10A0_3F_SELECTION_AUDIT_PASS','M10A0_3F_MODEL_SAVE_PASS')
