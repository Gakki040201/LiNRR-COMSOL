# M01.2 Two-Dimensional Parallel-Plate Flow Closure Verification

- Run: `20260715_190412`
- Status: **PASS - numerical verification only; not experimental validation**
- Source: frozen M01 rebuilt in isolated timestamped staging and loaded as an independent derivative.
- Physics: stationary single-phase Laminar Flow only; no new physical field.
- Audit discretization: mapped 40x20, 80x40, 160x80 element families with `order_fluid=2`.
- Accepted outlet closure: COMSOL `LaminarOutflow` with average exit pressure 0 Pa.
- Inputs (geometry, rho, mu, Q): **PROVISIONAL**.

## Baseline fine-grid analytical closure

- Umean analytical: 7.57575757576e-05 m/s.
- Center velocity: 0.000113636363636 m/s; analytical 0.000113636363636; relative error 2.26121205094e-13.
- Cross-section mean: 7.57575757576e-05 m/s; relative error 1.69948690537e-14.
- Center/mean ratio: 1.50000000000; relative error to 1.5: 2.09017988103e-13.
- Mid-channel dp/dx: -0.170454545455 Pa/m; analytical -0.170454545455; relative error 3.69874501396e-12.
- Global inlet-outlet pressure drop: 0.00937500000000 Pa; analytical 0.00937500000000; relative error 7.95659834315e-15.
- Upper wall shear: -0.000340909090909 Pa; magnitude error 5.66098094327e-14.
- Lower wall shear: 0.000340909090909 Pa; magnitude error 4.21393244373e-14.
- Analytical wall-shear magnitude: 0.000340909090909 Pa.
- Inlet mass flow: 1.50000000000e-05 kg/s.
- Outlet mass flow: 1.50000000000e-05 kg/s.
- Mass-balance relative error: 7.90564084104e-16.

## Frozen pressure-outlet end-effect reference

The frozen pressure outlet is retained as a diagnostic row, not silently accepted: global pressure-drop error 0.00379718420366, outlet-boundary mass discrepancy 0.000648143839375, while the middle-channel dp/dx error is 7.96251953261e-14. This distinguishes global truncation/outlet behavior from the fully developed middle solution. No global speed maximum is used; center velocity is evaluated only at x=Lcell/2, y=Hcell/2.

## Flow-sweep conservation errors

- Q/Qbaseline=0.250000000000: 1.12937726301e-16
- Q/Qbaseline=0.500000000000: 3.38813178902e-16
- Q/Qbaseline=1.00000000000: 6.77626357803e-16
- Q/Qbaseline=2.00000000000: 5.64688631503e-16
- Q/Qbaseline=4.00000000000: 3.38813178902e-16

## Medium-to-fine changes

- Center velocity: 1.80801554284e-13
- Cross-section mean: 2.91596174290e-14
- Center/mean ratio: 1.51582450295e-13
- Mid pressure gradient: 4.10761794947e-12
- Global pressure drop: 8.88178419700e-15
- Upper/lower wall shear: 1.68557297749e-14 / 9.54097911787e-16
- Inlet/outlet mass flow: 2.82344315751e-15 / 3.72694496792e-15
- All target changes <= 0.5%: PASS.

## MPH and log checks

- Independent MPH reload and retained center-velocity evaluation: PASS.
- Frozen baseline SHA-256 before/after: PASS; 12 paths unchanged.
- Fatal matches: 0.
- Warning matches: 0.

- None.

## Commands

- `& 'F:\COMSOL64\Multiphysics\bin\win64\comsolcompile.exe' 'F:\LiNRR_COMSOL\worktrees\LiNRR_PreGeometry\src\java\LiNRR_M01_Flow.java'`
- isolated frozen-source batch: `& 'F:\COMSOL64\Multiphysics\bin\win64\comsolbatch.exe' -inputfile 'F:\LiNRR_COMSOL\worktrees\LiNRR_PreGeometry\src\java\LiNRR_M01_Flow.class' -batchlog 'F:\LiNRR_COMSOL\worktrees\LiNRR_PreGeometry\runs\20260715_190412_M01_2\M01_2_frozen_source.log'`
- `& 'F:\COMSOL64\Multiphysics\bin\win64\comsolcompile.exe' 'F:\LiNRR_COMSOL\worktrees\LiNRR_PreGeometry\src\java\LiNRR_M01_2_Metrics.java'`
- `& 'F:\COMSOL64\Multiphysics\bin\win64\comsolcompile.exe' -classpathadd 'F:\LiNRR_COMSOL\worktrees\LiNRR_PreGeometry\src\java' 'F:\LiNRR_COMSOL\worktrees\LiNRR_PreGeometry\src\java\LiNRR_M01_2_FlowVerification.java'`
- `& 'F:\COMSOL64\Multiphysics\bin\win64\comsolcompile.exe' 'F:\LiNRR_COMSOL\worktrees\LiNRR_PreGeometry\tests\java\LiNRR_M01_2_LoadCheck.java'`
- `& 'F:\COMSOL64\Multiphysics\bin\win64\comsolbatch.exe' -inputfile 'F:\LiNRR_COMSOL\worktrees\LiNRR_PreGeometry\src\java\LiNRR_M01_2_FlowVerification.class' -batchlog 'F:\LiNRR_COMSOL\worktrees\LiNRR_PreGeometry\runs\20260715_190412_M01_2\M01_2_build.log'`
- `& 'F:\COMSOL64\Multiphysics\bin\win64\comsolbatch.exe' -inputfile 'F:\LiNRR_COMSOL\worktrees\LiNRR_PreGeometry\tests\java\LiNRR_M01_2_LoadCheck.class' -batchlog 'F:\LiNRR_COMSOL\worktrees\LiNRR_PreGeometry\runs\20260715_190412_M01_2\M01_2_reload.log'`

## Remaining uncertainty

Absolute pressure drop and shear scale directly with the provisional viscosity; Umean scales with provisional geometry and flow. The closure model is an ideal 2D parallel-plate benchmark and omits manifolds, 3D sidewalls, GDE permeability, gas-liquid effects, and experimental comparison. The fully developed outlet is an analytical-closure boundary, whereas the frozen pressure-outlet row quantifies computational truncation behavior.

RUN_STATE = SYNTHETIC_SMOKE_TEST

CALIBRATION_MODE = PROVISIONAL

M03B_READY = FALSE
