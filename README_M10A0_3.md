# M10A0.3 — CAD Registration and Interface Mapping

## Install

Extract this bundle into:

```text
F:\LiNRR_COMSOL\worktrees\LiNRR_M10A0
```

The bundle does not replace or modify the two raw STEP files.

## Run

```powershell
Set-Location 'F:\LiNRR_COMSOL\worktrees\LiNRR_M10A0'

powershell.exe `
  -NoProfile `
  -ExecutionPolicy Bypass `
  -File '.\scripts\windows\25_build_M10A0_3_cad_registration.ps1'
```

Expected final marker:

```text
M10A0_3_CAD_REGISTRATION=PASS
```

Generated model:

```text
models\generated\LiNRR_M10A0_3_real_cad_registered.mph
```

## Geometry architecture

- `comp_cc_raw`: exact current-collector STEP in source coordinates
- `comp_chamber_raw`: exact chamber STEP in source coordinates
- `comp_registered`: chamber-centered registered assembly

The registered component contains:

- one chamber;
- one top collector;
- one mirrored bottom collector;
- assembly finalization;
- coordinate-based domain and interface-band selections.

## GUI inspection

Open `comp_registered`, Build All, and inspect:

1. eight large bolt holes overlap in plan view;
2. top collector occupies approximately `Z = +5 to +28 mm`;
3. bottom collector occupies approximately `Z = -28 to -5 mm`;
4. chamber occupies approximately `Z = -5 to +5 mm`;
5. side ports on the chamber remain unobstructed;
6. flow-field faces point toward the chamber;
7. no collector penetrates the chamber solid.

Use transparency or Click and Hide.

Recommended screenshots:

```text
M10A0_3_01_registered_isometric.png
M10A0_3_02_registered_front.png
M10A0_3_03_registered_side.png
M10A0_3_04_top_interface_transparent.png
M10A0_3_05_bottom_interface_transparent.png
M10A0_3_06_bolt_pattern_alignment.png
```

## Important scientific boundary

The eight-hole in-plane registration is exact.

The axial placement assumes that collector source face `y = 23 mm` is the
mating flow-field face and aligns it to chamber outer planes `z = ±5 mm`.
That must be confirmed in GUI and, ideally, against the native SolidWorks
assembly or drawing.

No GDE thickness, gasket compression, or physical interfacial gap is included.
