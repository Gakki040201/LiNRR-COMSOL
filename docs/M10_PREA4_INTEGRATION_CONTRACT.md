# M10 PRE-A4 Integration Contract

## Status

PRE-A4 integration contract. This is a verification/integration stage only; it adds no A4 physics and does not validate any real-cell prediction against experiment.

## Three-layer architecture

### LAYER 1 — NUMERICAL VERIFICATION

- M00.2
- M01.2
- M02.2
- M03A.2
- M03A.3
- M03A.4
- M03A.5
- M03A.6

These stages verify numerical operators, synthetic/tool behavior, conservation, provenance, and evidence-intake gating. Their parameter status is PROVISIONAL, SYNTHETIC, or NUMERICAL_VERIFICATION_ONLY unless otherwise documented.

### LAYER 2 — REAL-CELL APPLICATION

- M10A0
- M10A1
- M10A2
- M10A2R
- M10A3

These stages apply verified operators to real CAD and produce a real-cell neutral-species transport baseline. They do not yet include ionic current, Li plating, kinetics, or selectivity.

### LAYER 3 — EXPERIMENTAL CALIBRATION

Manual, flow calibration, EIS, conductivity, EC-Lab, NH3, FE, water, pressure, postmortem, and future isotope/operando evidence.

Layer 3 inputs are gated by M03A.5/M03A.6 tooling and must not be manufactured. Layer 2 predictions remain REAL_CELL_APPLICATION and REQUIRES_EXPERIMENTAL_VALIDATION until real evidence is accepted.

## Verification bridge

The bridge mapping is defined in:

- config/M10_preA4_verification_bridge.csv
- esults/tables/M10_preA4_stage_inventory.csv
- esults/tables/M10_preA4_parameter_provenance_audit.csv
- esults/tables/M10_preA4_geometry_authority.csv

## Acceptance

PRE-A4 integration passes only when all required Layer 1 regressions, M10A3 independent reload, artifact hashes, parameter contamination audit, and geometry authority audit pass, and when no new A4 physics has been created.

The architecture order is fixed:

NUMERICAL VERIFICATION -> REAL-CELL APPLICATION -> EXPERIMENTAL VALIDATION -> MECHANISTIC INTERPRETATION

Never reverse this order.
