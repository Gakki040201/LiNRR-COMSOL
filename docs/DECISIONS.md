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
