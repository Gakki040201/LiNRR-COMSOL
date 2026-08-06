# Static STEP audit completed before COMSOL runtime

## Current collector / gas-flow-field block

- filename: `block_currentCollector_flowField_v03.2.1_20210525_STEP-AP203.STEP`
- bytes: 328123
- SHA-256: `0091777D10ECA620E08780381438F3522B3BE31FEE307F9DDCF074A1FACDDFE9`
- schema: STEP AP203 / CONFIG_CONTROL_DESIGN
- export: SwSTEP 2.0, SolidWorks 2022
- unit: millimetre
- solid BREP count: 1
- advanced faces: 135
- edge curves: 372
- vertex points: 245
- vertex-coordinate envelope:
  - x: 0 to 108 mm
  - y: approximately 0 to 23 mm
  - z: -108 to 0 mm
  - spans: 108 × 23 × 108 mm

## Electrolyte chamber

- filename: `electrolyteChamber_rectangularMask_v02.2.2_20210521_STEP-AP203.STEP`
- bytes: 306476
- SHA-256: `AE350E006228A10EEC4476F79CAFCE6D63F18F10F58F0A8B3559EED0C37FF12C`
- schema: STEP AP203 / CONFIG_CONTROL_DESIGN
- export: SwSTEP 2.0, SolidWorks 2020
- unit: millimetre
- solid BREP count: 1
- advanced faces: 93
- edge curves: 260
- vertex points: 172
- vertex-coordinate envelope:
  - x: -53.2 to 69.2 mm
  - y: -53.2 to 54.2 mm
  - z: -5 to 5 mm
  - spans: 122.4 × 107.4 × 10 mm

## Immediate implication

The source coordinate systems are not directly aligned:

- the collector thickness direction is encoded along Y;
- the chamber thickness direction is encoded along Z;
- their origins and in-plane extents differ.

Therefore, no assembly transform is applied in M10A0.2. Registration must be determined from mating planes, bolt patterns and active-window geometry after strict COMSOL import.
