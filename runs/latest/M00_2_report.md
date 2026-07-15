# M00.2 Geometry Parameterization and Named-Selection Audit

- Run: `20260715_185308`
- Status: **PASS - numerical verification only; not experimental validation**
- Geometry/material/calibration state: **PROVISIONAL**
- Cases: baseline; Lcell x 0.5/x 2; Hcell x 0.5/x 2; Wcell x 0.5/x 2.
- Named-selection checks: 35/35 PASS; no `FAILED_SELECTION_MAPPING`.
- Positive geometry measures: 7/7 domain areas and 28/28 boundary lengths PASS.
- Geometry build, mesh build, MPH save: 7/7 PASS.
- Independent MPH reload: PASS; geometry, all five named selections, and mesh rebuilt.
- Frozen baseline SHA-256 before/after: PASS; 12 paths unchanged (including absent generated baselines).

## Outputs

- `models/generated/LiNRR_M00_2_geometry_audit.mph`
- `results/tables/M00_2_geometry_scaling.csv`
- `results/tables/M00_2_selection_audit.csv`
- `runs/latest/M00_2_build.log`
- `runs/latest/M00_2_report.md`
- Timestamped evidence: `runs/20260715_185308_M00_2/`

## Commands

- `& 'F:\COMSOL64\Multiphysics\bin\win64\comsolcompile.exe' 'F:\LiNRR_COMSOL\worktrees\LiNRR_PreGeometry\src\java\LiNRR_M00_2_GeometryAudit.java'`
- `& 'F:\COMSOL64\Multiphysics\bin\win64\comsolcompile.exe' 'F:\LiNRR_COMSOL\worktrees\LiNRR_PreGeometry\tests\java\LiNRR_M00_2_LoadCheck.java'`
- `& 'F:\COMSOL64\Multiphysics\bin\win64\comsolbatch.exe' -inputfile 'F:\LiNRR_COMSOL\worktrees\LiNRR_PreGeometry\src\java\LiNRR_M00_2_GeometryAudit.class' -batchlog 'F:\LiNRR_COMSOL\worktrees\LiNRR_PreGeometry\runs\20260715_185308_M00_2\M00_2_build.log'`
- `& 'F:\COMSOL64\Multiphysics\bin\win64\comsolbatch.exe' -inputfile 'F:\LiNRR_COMSOL\worktrees\LiNRR_PreGeometry\tests\java\LiNRR_M00_2_LoadCheck.class' -batchlog 'F:\LiNRR_COMSOL\worktrees\LiNRR_PreGeometry\runs\20260715_185308_M00_2\M00_2_reload.log'`

## Log classification

- Fatal matches: 0.
- Warning matches: 0.

- None.

## Scope and uncertainty

The audit verifies coordinate-based named-selection mapping and editable-MPH serialization across parameter changes. It does not validate the provisional dimensions against experiment and adds no physics.

RUN_STATE = SYNTHETIC_SMOKE_TEST

CALIBRATION_MODE = PROVISIONAL

M03B_READY = FALSE
