# M10A0.2 — Real CAD Intake and Geometry Reconstruction Contract

## Purpose

Replace the inferred internal geometry used in `PUBLIC_MACRO_REFERENCE_R0` with the two user-provided source CAD solids:

1. `block_currentCollector_flowField_v03.2.1_20210525_STEP-AP203.STEP`
2. `electrolyteChamber_rectangularMask_v02.2.2_20210521_STEP-AP203.STEP`

This milestone is an **intake and audit gate**, not yet an assembled-cell or flow-simulation milestone.

## Source integrity

| Role | Bytes | SHA-256 |
|---|---:|---|
| Current collector / gas flow field | 328123 | `0091777D10ECA620E08780381438F3522B3BE31FEE307F9DDCF074A1FACDDFE9` |
| Electrolyte chamber | 306476 | `AE350E006228A10EEC4476F79CAFCE6D63F18F10F58F0A8B3559EED0C37FF12C` |

The build script refuses to run if either hash differs.

## Static facts read directly from the STEP text

### Current collector / gas-flow-field block

- STEP AP203 / `CONFIG_CONTROL_DESIGN`
- SolidWorks 2022, SwSTEP 2.0
- millimetre length unit
- one `MANIFOLD_SOLID_BREP`
- one closed shell
- 135 advanced faces
- 372 edge curves
- 245 vertex points
- vertex envelope: approximately **108 × 23 × 108 mm**
- source normal/thickness axis appears to be **Y**

### Electrolyte chamber

- STEP AP203 / `CONFIG_CONTROL_DESIGN`
- SolidWorks 2020, SwSTEP 2.0
- millimetre length unit
- one `MANIFOLD_SOLID_BREP`
- one closed shell
- 93 advanced faces
- 260 edge curves
- 172 vertex points
- vertex envelope: approximately **122.4 × 107.4 × 10 mm**
- source normal/thickness axis appears to be **Z**

These are source-coordinate envelopes. They do not yet establish the assembly transform between the two parts.

## COMSOL model architecture

The generated MPH contains two independent CAD-kernel components:

- `comp_cc_raw`
  - exact current-collector / gas-flow-field source coordinates
- `comp_chamber_raw`
  - exact electrolyte-chamber source coordinates

The import settings preserve raw topology:

- CAD kernel (`cadps`)
- source units
- solids retained
- no small-detail deletion
- no simplification
- no redundant-edge removal
- no edge healing
- no tolerance minimization
- no hole filling
- strict mode: automatic topological repair disabled
- resulting entity selections enabled

## Strict versus AutoRepair

Run `Strict` first. It proves that the source solids import without COMSOL modifying their topology.

`AutoRepair` is a controlled fallback only. It enables `fixerrors=auto` but still disables defeaturing and simplification. A repaired import must never silently replace the strict record.

## Deliberately excluded

M10A0.2 does not:

- align the two CAD coordinate systems;
- infer which face is the GDE contact face;
- mirror the current collector to create the second gas side;
- create gaskets, SSC, PtAu/SSC, bolts, tubing or the reference electrode;
- extract the liquid or gas fluid volumes;
- remove manufacturing fillets or bolt holes;
- generate coordinate-based inlet/outlet selections;
- add materials, mesh, physics, studies or solvers;
- claim that the two source parts alone form the complete assembled cell.

## Acceptance criteria

1. Both input hashes match the manifest.
2. COMSOL Java source compiles.
3. Strict STEP import exits successfully.
4. The generated MPH opens in COMSOL.
5. `comp_cc_raw` contains one valid CAD solid.
6. `comp_chamber_raw` contains one valid CAD solid.
7. COMSOL audit CSV records finite bounding boxes and nonzero entities.
8. Source units resolve to millimetres.
9. No physics, mesh, study or solver exists.
10. GUI screenshots are captured for the two raw components.

## Next milestone

After strict import and GUI inspection:

**M10A0.3 — CAD Registration and Interface Mapping**

It will:

1. determine the rigid-body transform between the two source coordinate systems;
2. identify the collector–GDE and chamber–GDE mating planes;
3. verify bolt-hole and active-window correspondence;
4. create a reversible registered assembly preview;
5. create coordinate-based selections for ports and mating faces;
6. retain the two raw components unchanged as evidence.

Fluid-domain extraction starts only after registration is verified.
