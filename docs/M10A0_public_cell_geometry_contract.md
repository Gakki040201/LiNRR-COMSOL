# M10A0 public-reference complete-cell geometry contract

## Purpose

Build a real, editable 3D COMSOL model of the publicly documented Shaofeng Li / DTU continuous-flow Li-NRR cell **without pretending that unpublished CAD dimensions are known**.

M10A0 is deliberately a geometry-only milestone. It produces a COMSOL `.mph` that can be opened, rotated, hidden/shown by component, and later extended with flow, species transport, porous-electrode and current-distribution physics.

## Public hard anchors

The public sources support the following directly:

- three-compartment continuous-flow Li-NRR architecture;
- assembled cell dimensions: **10.7 cm × 10.7 cm × 5.1 cm**;
- effective electrode/flow-field area: **25 cm²**;
- gas-flow-channel characteristic size: **1 mm**;
- central electrolyte chamber: **4 mm** in the long-term public operating configuration;
- N₂ and H₂ gas flows: **75 sccm** each in the public operating point;
- electrolyte flow: **1.0 mL min⁻¹** in the public operating point;
- gas inlet-to-outlet pressure gradient: **15 mbar** in the public operating point;
- patent baseline chamber topology includes **4 elongated bar-shaped spacers**; the patent explicitly tests 0, 1, 3 and 4 spacers.

Primary sources:

1. S. Li et al., *Long-term continuous ammonia electrosynthesis*, Nature 629, 92–97 (2024), DOI: 10.1038/s41586-024-07276-5, Supplementary Fig. 1 and Methods.
2. WO2024052575A2, *Flow cell for electrochemical ammonia synthesis*, DTU, especially Figures 3–8, Example 1 and Example 5.
3. X. Fu et al., *Phenol as proton shuttle and buffer for lithium-mediated ammonia electrosynthesis*, Nature Communications (2024), same 25 cm² DTU platform; used only for the 30 µm SSC thickness family value.

## Two-component COMSOL architecture

### `comp_assembly`

Purpose: **human-readable full-cell visualization**.

Contains:

- cathode end plate;
- cathode gas-manifold/current-collector envelope;
- cathode SSC/GDE;
- four-piece central chamber frame;
- explicit 4 mm electrolyte cavity;
- PtAu/SSC anode;
- anode gas-manifold/current-collector envelope;
- anode end plate;
- eight bolt envelopes.

The outer dimensions are fixed to the public 107 × 107 × 51 mm envelope. Internal metal thicknesses and bolt coordinates are marked `PROVISIONAL_VISUAL`; they are not to be used as calibrated transport/electrochemical parameters.

### `comp_active`

Purpose: **physics-ready active-zone geometry**.

Contains:

- lower H₂ gas header and 25 parallel 1 mm channels;
- H₂-side ribs;
- 30 µm PtAu/SSC anode sheet;
- 4 mm central electrolyte chamber;
- four elongated full-height spacer bars, generating five liquid lanes;
- liquid inlet/outlet headers;
- 30 µm SSC cathode sheet;
- upper N₂ header and 25 parallel 1 mm channels;
- N₂-side ribs.

The parallel-channel/rib layout is explicitly a **geometry interpretation**, not a claim that the unpublished DTU flow-field CAD is known. It gives us a real 3D manifold/channel/GDE/chamber topology now, while keeping the exact channel topology replaceable later.

## Scientific boundary

M10A0 does **not** claim:

- exact reconstruction of every unpublished plate thickness, bolt location, manifold shape, channel turn or gasket compression;
- our laboratory cell is identical to the DTU public cell;
- any model validation;
- any experimentally calibrated resistance, conductivity, permeability or kinetic parameter;
- any COMSOL solution result.

M10A0 only claims: **the public geometry anchors are encoded into a parameterized, editable 3D COMSOL geometry and all non-public dimensions are visibly separated as inferred/provisional parameters.**

## Acceptance criteria when run locally

1. Java compiles with COMSOL 6.4 `comsolcompile.exe`.
2. `comsolbatch.exe` exits 0.
3. `models/generated/LiNRR_M10A0_public_cell_geometry.mph` is newly created.
4. Batch log contains no fatal geometry error.
5. The MPH opens in COMSOL GUI.
6. Model Builder contains both components:
   - `comp_assembly`
   - `comp_active`
7. `comp_assembly` visibly forms the 107 × 107 × 51 mm complete stack.
8. `comp_active` visibly contains two gas-flow-field layers, two thin GDE sheets, and the central 4 mm electrolyte chamber with four spacer bars.
9. No physics/study/solver is added in this milestone.

## Next milestone after R0 passes

`M10A1 / RealCell-R1`: add **single-phase laminar flow only** to the liquid chamber first, validate mass conservation and pressure drop, then add the two gas networks as separate single-physics problems. Do not jump directly to N₂ transport, current distribution, SEI or reaction kinetics before the three flow networks each pass independently.
