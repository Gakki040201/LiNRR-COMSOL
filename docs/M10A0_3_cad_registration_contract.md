# M10A0.3 CAD Registration Contract

## Objective

Create a reversible, auditable registration of the two exact M10A0.2 STEP
solids without modifying either source file.

## Registration evidence

The current collector contains eight 6.35 mm bolt-hole centers in its source
`x-z` plane. After subtracting `(54, -54)` from the collector in-plane center,
the eight points match the chamber's eight 6.35 mm bolt-hole centers exactly.

The top collector mapping is:

```text
X = x_cc - 54
Y = z_cc + 54
Z = 28 - y_cc
```

Equivalent operation sequence:

```text
Rotate -90 degrees about global X
Move by (-54, +54, +28) mm
```

The bottom collector is the mirror image of the registered top collector
through the global plane `Z = 0`.

## Evidence classification

- source geometry and hole coordinates: `CAD_SOURCE`
- in-plane transform: `LANDMARK_DERIVED_EXACT`
- axial mating plane: `GEOMETRY_DERIVED_HYPOTHESIS`
- second collector mirror: `ASSEMBLY_INFERENCE`

## Acceptance criteria

1. Both source hashes match M10A0.2.
2. Java and runtime classes compile.
3. Three components build.
4. `comp_registered` is a Form Assembly geometry.
5. Eight-hole in-plane residual is no greater than `1e-9 mm`.
6. Registered bounding box is within `0.02 mm` of:
   - X: `-54.0025 to 69.2025 mm`
   - Y: `-54.0025 to 54.2025 mm`
   - Z: `-28.0025 to 28.0025 mm`
7. No mesh, physics, material, study, or solver exists.
8. GUI confirms the flow-field faces point toward the chamber.
9. GUI confirms there is no unintended solid penetration.

## Not yet approved

This milestone does not approve:

- physical GDE/gasket spacing;
- compressed stack thickness;
- identity/contact pairs;
- active fluid-domain extraction;
- port boundary identification;
- flow simulation.

Those begin only after GUI registration review.
