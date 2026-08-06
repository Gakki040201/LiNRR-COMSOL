# M10A0.2 Real CAD Intake bundle

## Install

Extract this ZIP into the root of the formal worktree:

```text
F:\LiNRR_COMSOL\worktrees\LiNRR_M10A0
```

The two source CAD files will be placed under:

```text
cad\raw\M10A0_2
```

## Run the strict intake first

```powershell
Set-Location 'F:\LiNRR_COMSOL\worktrees\LiNRR_M10A0'

powershell.exe `
  -NoProfile `
  -ExecutionPolicy Bypass `
  -File '.\scripts\windows\24_build_M10A0_2_real_cad_intake.ps1' `
  -ImportMode Strict
```

Expected final marker:

```text
M10A0_2_REAL_CAD_IMPORT=PASS
```

The generated model is:

```text
models\generated\LiNRR_M10A0_2_real_cad_strict.mph
```

## GUI inspection

Open the strict MPH and inspect only:

- `comp_cc_raw`
- `comp_chamber_raw`

For each component:

1. Geometry → Build All
2. Zoom Extents
3. confirm one solid object
4. use Measure to record the bounding box
5. capture isometric, front, side and back/underside views

Do not align, rotate, mirror, simplify, delete holes, create fluid domains, add mesh or add physics yet.

## Controlled fallback

Do not use this unless strict import fails and the strict log has been reviewed:

```powershell
powershell.exe `
  -NoProfile `
  -ExecutionPolicy Bypass `
  -File '.\scripts\windows\24_build_M10A0_2_real_cad_intake.ps1' `
  -ImportMode AutoRepair
```

AutoRepair is a separate artifact and never replaces strict provenance.
