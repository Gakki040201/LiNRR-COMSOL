# Modeling Decision Log

## D0001 — Automation interface

Decision: COMSOL Java API is the primary automation interface.

Reason:
- Full model control;
- no MATLAB dependency for the core path;
- output remains an editable MPH;
- suitable for Codex, Git, and batch execution.

Alternative:
- LiveLink for MATLAB, retained as an optional analysis layer after license confirmation.

## D0002 — Model construction strategy

Decision: Incremental M00–M10 construction.

Reason:
- isolates numerical failures;
- supports conservation and mesh checks;
- prevents an uninterpretable all-at-once multiphysics model.

## D0003 — Model truth hierarchy

1. Raw experimental data;
2. calibrated parameters with uncertainty;
3. literature parameters with exact conditions;
4. provisional assumptions;
5. qualitative hypotheses.

No lower level may be presented as a higher level.

## D0004 — M01 numerical smoke-test flow model

Decision: M01 is a stationary, single-phase, two-dimensional Laminar Flow model only.

Implementation:
- coordinate-based named selections are used for the electrolyte domain, inlet, outlet, and both walls;
- the inlet uses COMSOL's fully developed laminar condition with average velocity `uin`;
- the mapped mesh has 100 elements along `Lcell` and 200 elements through `Hcell`;
- acceptance requires a boundary-integrated mass-balance relative error no greater than `1e-4`.

Parameter provenance:
- `rho_el = 900[kg/m^3]` and `mu_el = 3[mPa*s]` are **PROVISIONAL — numerical smoke test only**;
- `Qliq = 1 mL/min` is stored as the exactly equivalent `1[cm^3/min]` because COMSOL 6.4 rejected the literal `mL` token as an unknown unit during a load/evaluation check.

Limitations:
- passing M01 is numerical smoke-test acceptance, not experimental validation or mesh independence;
- no species transport, electrochemistry, porous media, two-phase flow, SEI, HOR, or heat transfer is included.

## D0005 — M01.1 independent numerical audit

Decision: Keep the accepted `LiNRR_M01_Flow.java` and M01 MPH unchanged; perform the
mesh, cross-section, maximum-location, and mass-flow postprocessing audit in the
independent `LiNRR_M01_Audit.java` derivative.

Findings:
- `uin=Qliq/(Hcell*Wcell)` is the volumetric cross-sectional mean velocity used by
  the fully developed Laminar Inflow condition, not the inlet maximum velocity;
- coarse/medium/fine mapped meshes change both streamwise and height resolution
  (40x20, 80x80, and 160x200);
- the fine-grid middle-channel ratio is `u_center/u_mean=1.50003749`, consistent
  with the 2D parallel-plate reference value 1.5;
- the global speed maximum occurs in the outlet region, not in the true channel
  middle; the outlet vicinity retains a larger ratio because of the outlet end effect;
- default and explicitly eighth-order mass-flow integrations agree to numerical
  precision. The need for high height resolution in the accepted M01 balance is
  therefore a discrete flow/outlet-boundary error, not a low postprocessing
  integration order.

Limitation: this is numerical mesh/conservation evidence, not experimental validation.

## D0006 — M02 provisional N2/NH3 transport smoke test

Decision: M02 adds only Transport of Diluted Species to the M01 Laminar Flow
geometry and velocity field. The cathode uses a phenomenological first-order wall
law `rN2=kN2*cN2` with the enforced stoichiometry `rNH3=2*rN2`.

Flux convention:
- COMSOL General Inward Flux is positive into the liquid;
- N2 consumption uses `J0_N2=-rN2`;
- NH3 generation uses `J0_NH3=+rNH3`.

Parameter status:
- `DN2=2e-9[m^2/s]`, `DNH3=2e-9[m^2/s]`, `cN2_in=5[mol/m^3]`, and
  `kN2=1e-5[m/s]` are **PROVISIONAL — numerical smoke test only**;
- these values are not assigned to or claimed representative of a real
  Diglyme/LiBF4 electrolyte;
- the wall law is phenomenological and has no experimental calibration range.

Numerical policy:
- concentrations are never clipped;
- parameter combinations below `-1e-5*cN2_in` are retained and marked FAILED;
- the two independent nitrogen-atom balances must each be no greater than `1e-4`;
- M02 passing means numerical smoke-test acceptance only, not experimental validation.
