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
