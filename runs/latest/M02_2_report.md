# M02.2 Conservative Transport Verification

- Run: `20260716_154328`
- Recovery snapshot: `F:\LiNRR_COMSOL\recovery\M02_2_crash_recovery_20260716_152444`.
- Crash state: **CODE_ONLY**; no partial M02.2 COMSOL result or Pe-Da checkpoint existed or was reused.
- Runtime recovery: pre-A launches exposed COMSOL's getenv and limited-file-access policies. All blocked logs are preserved; the repository-standard run-input bridge and an isolated run-local `-prefsdir` with file access enabled were used. Runtime/property/network/process/security permissions remain disabled, and the completed isolated M02.1 rebuild was reused without rerunning it.
- Status: **PASS - synthetic numerical transport verification only; not experimental validation**.
- Calibration mode: **PROVISIONAL**. The first-order wall law is phenomenological and has no experimental calibration range.
- Physics scope: conservative N2/NH3 transport only; no electrode kinetics and no concentration clipping.
- Frozen M02.1: rebuilt in isolated timestamped staging; 42 frozen paths retained identical SHA-256 before/after.

## Benchmark A - uniform transport

- min/max/mean N2: 5.00000000000 / 5.00000000000 / 5.00000000000 mol/m^3.
- min/max/mean NH3: 0.00000000000 / 0.00000000000 / 0.00000000000 mol/m^3.
- N2/NH3 balance errors: 2.40634360015e-16 / 0.00000000000.
- status: MACHINE_PRECISION_CLOSURE.

## Benchmark B - exactly linear diffusion

- Three-grid relative L2 errors: 1.57413528654e-16, 1.73543828742e-16, 2.32981047397e-16.
- Fine flux balance error: 1.17130067207e-13; min concentration: 1.00000000000 mol/m^3.
- Fine status: EXACT_POLYNOMIAL_REPRESENTATION.

## Benchmark C - manufactured solution convergence

| mesh | elements | relative L2 error | Linf error | source-flux error | observed order | minimum concentration |
|:--|:--|--:|--:|--:|--:|--:|
| coarse | 40x20 | 7.35850778881e-05 | 0.00379018873825 | 0.000456942234329 | NaN | 5.00000000000 |
| medium | 80x40 | 2.69037931401e-06 | 0.000167730927719 | 0.000488264604437 | 4.77353173943 | 5.00000000000 |
| fine | 160x80 | 4.22218328425e-07 | 5.38100620098e-05 | 0.000127849195991 | 2.67174847902 | 5.00000000000 |

Medium-to-fine observed order is 2.67174847902. The three relative L2 errors above are the required three-grid error record.
The fine reconstructed Dirichlet-boundary physical-flux closure is 0.000127849195991, within its explicit `2e-4` truncation tolerance; this diagnostic is separate from the conservative wall-reaction balance.

## Benchmark D - low-Da wall reaction

| mesh | elements | N2 species error | NH3 species error | nitrogen-atom error | min N2 | min NH3 | max key change |
|:--|:--|--:|--:|--:|--:|--:|--:|
| coarse | 80x40 | 1.44145208574e-05 | 0.000187947994273 | 1.46733097392e-05 | 4.86274613306 | 3.68031007157e-06 | NaN |
| medium | 160x80 | 1.67526527379e-06 | 0.000104243905154 | 1.81856753713e-06 | 4.95537805132 | 3.63467855556e-06 | 0.00773644409311 |
| fine | 320x160 | 7.29605021655e-08 | 3.13445148560e-05 | 1.16024753039e-07 | 4.98005926028 | 3.63032823280e-06 | 0.000670262993641 |

- Fine stoichiometric ratio / relative error: 1.00000000000 / 0.00000000000.
- Fine-grid status: PASS. Coarse and medium rows are retained as pre-asymptotic mesh evidence even where they exceed the final-grid conservation tolerance.
- Maximum finite single-species errors over low-Da and Pe-Da: N2=0.00017804592629; NH3=0.000187947994273.
- Maximum finite nitrogen-atom balance error over low-Da and Pe-Da: 0.000178045869716.

## Benchmark E - complete 25-point Pe-Da map

Counts: PASS=12, WARNING=5, FAILED=8. OUTSIDE_MODEL_APPLICABILITY is counted as FAILED for this summary. No point was deleted.

| Pe_H | Da_H | N2 error | NH3 error | nitrogen error | min N2 | min NH3 | conversion | classification | reason |
|--:|--:|--:|--:|--:|--:|--:|--:|:--|:--|
| 0.100000000000 | 0.000100000000000 | 4.58715158456e-07 | 3.05350833013e-05 | 8.73953365549e-07 | 4.93284765491 | 0.0733239081872 | 0.0135978511154 | PASS | finite_conservative_and_nonnegative |
| 0.100000000000 | 0.00100000000000 | 7.25083319390e-07 | 2.72674693382e-05 | 2.63822045047e-06 | 4.38401361589 | 0.669358958085 | 0.123342280185 | PASS | finite_conservative_and_nonnegative |
| 0.100000000000 | 0.0100000000000 | 1.16173488244e-06 | 2.13510974381e-05 | 1.23206685759e-05 | 1.83765706629 | 3.62881586769 | 0.631449549783 | PASS | finite_conservative_and_nonnegative |
| 0.100000000000 | 0.100000000000 | 2.28827134477e-07 | 2.62979623514e-05 | 2.57436349799e-05 | 0.0599746565681 | 7.18328245124 | 0.987596928579 | PASS | finite_conservative_and_nonnegative |
| 0.100000000000 | 1.00000000000 | 6.60763116214e-07 | 3.20619372819e-05 | 3.14020816360e-05 | 1.10099232513e-05 | 8.78455390706 | 0.999996993948 | PASS | finite_conservative_and_nonnegative |
| 1.00000000000 | 0.000100000000000 | 1.38361371000e-07 | 2.31857074586e-06 | 1.41547892565e-07 | 4.98729233263 | 0.000877954644240 | 0.00137447066782 | PASS | finite_conservative_and_nonnegative |
| 1.00000000000 | 0.00100000000000 | 1.61371577722e-07 | 3.02645227746e-06 | 2.02661071789e-07 | 4.92612908652 | 0.00875953354625 | 0.0136426668635 | PASS | finite_conservative_and_nonnegative |
| 1.00000000000 | 0.0100000000000 | 4.50810398070e-07 | 0.000172687585597 | 2.23784446869e-05 | 4.35909684736 | 0.0857274097317 | 0.126979088058 | FAILED_CONSERVATION | one_or_more_species_or_nitrogen_balance_exceeds_1e-4 |
| 1.00000000000 | 0.100000000000 | 8.02227584792e-07 | 3.22155406973e-05 | 2.19529282422e-05 | 1.42367168196 | 0.710594135390 | 0.706319010612 | PASS | finite_conservative_and_nonnegative |
| 1.00000000000 | 1.00000000000 | 2.28477221373e-07 | 8.97002280878e-05 | 8.98341232688e-05 | 0.00388076941101 | 2.82135935244 | 0.998945804844 | PASS | finite_conservative_and_nonnegative |
| 10.0000000000 | 0.000100000000000 | 1.78518857471e-05 | 9.69839894558e-05 | 1.78652305834e-05 | 4.96132685928 | 3.63556236425e-07 | 0.000119733113270 | PASS | finite_conservative_and_nonnegative |
| 10.0000000000 | 0.00100000000000 | 1.67526527439e-06 | 0.000104268058460 | 1.81860074433e-06 | 4.95537805132 | 3.63468030580e-06 | 0.00137286384417 | FAILED_CONSERVATION | one_or_more_species_or_nitrogen_balance_exceeds_1e-4 |
| 10.0000000000 | 0.0100000000000 | 1.96964734153e-06 | 0.000104197271263 | 3.38848996372e-06 | 4.89561094944 | 3.62616721913e-05 | 0.0136134999385 | FAILED_CONSERVATION | one_or_more_species_or_nitrogen_balance_exceeds_1e-4 |
| 10.0000000000 | 0.100000000000 | 1.26521583276e-07 | 0.000106438229871 | 1.30980674112e-05 | 4.24147001420 | 0.000354078464830 | 0.124233509585 | FAILED_CONSERVATION | one_or_more_species_or_nitrogen_balance_exceeds_1e-4 |
| 10.0000000000 | 1.00000000000 | 3.21049527702e-07 | 0.000113364907637 | 7.12092155774e-05 | 1.35944166260 | 0.00287153780697 | 0.630902400853 | FAILED_CONSERVATION | one_or_more_species_or_nitrogen_balance_exceeds_1e-4 |
| 100.000000000 | 0.000100000000000 | 2.38009904136e-05 | 1.03736621417e-06 | 2.38010047032e-05 | 4.93386225258 | -4.89167087308e-10 | -1.00260827187e-05 | PASS | finite_conservative_and_nonnegative |
| 100.000000000 | 0.00100000000000 | 6.34658069369e-05 | 7.32746247657e-07 | 6.34659078348e-05 | 4.92601638821 | -9.85175170413e-09 | 7.42323855672e-05 | PASS | finite_conservative_and_nonnegative |
| 100.000000000 | 0.0100000000000 | 6.62210470583e-06 | 6.75055657017e-07 | 6.62303128829e-06 | 4.90364995593 | -9.98098644251e-08 | 0.00136597869882 | WARNING_NUMERICAL_OSCILLATION | conservative_small_unclipped_undershoot |
| 100.000000000 | 0.100000000000 | 8.94198488410e-06 | 9.74683884376e-05 | 7.64541328169e-06 | 4.69634160721 | -9.25002403129e-07 | 0.0132935412427 | WARNING_NUMERICAL_OSCILLATION | conservative_small_unclipped_undershoot |
| 100.000000000 | 1.00000000000 | 9.03571647182e-07 | 1.22892846839e-05 | 2.14752641107e-06 | 3.24455264705 | -8.41950223574e-06 | 0.101220458385 | WARNING_NUMERICAL_OSCILLATION | conservative_small_unclipped_undershoot |
| 1000.00000000 | 0.000100000000000 | 0.000178045926290 | 4.09073557926e-05 | 0.000178045869716 | 2.68482873306 | -1.93199284856e-08 | -0.000176662951374 | FAILED_CONSERVATION | one_or_more_species_or_nitrogen_balance_exceeds_1e-4 |
| 1000.00000000 | 0.00100000000000 | 0.000122439788040 | 2.66497410156e-05 | 0.000122439419575 | 2.75360950630 | -4.68097299399e-07 | -0.000108613560290 | FAILED_CONSERVATION | one_or_more_species_or_nitrogen_balance_exceeds_1e-4 |
| 1000.00000000 | 0.0100000000000 | 4.15591299523e-05 | 3.60281197258e-05 | 4.15641035555e-05 | 2.86127624270 | -1.97624500390e-06 | 0.000179606943179 | WARNING_NUMERICAL_OSCILLATION | conservative_small_unclipped_undershoot |
| 1000.00000000 | 0.100000000000 | 6.37109556369e-05 | 4.15138271142e-05 | 6.37674651531e-05 | 2.81699928920 | -1.71171345916e-05 | 0.00142493250800 | WARNING_NUMERICAL_OSCILLATION | conservative_small_unclipped_undershoot |
| 1000.00000000 | 1.00000000000 | 9.95129427638e-05 | 4.32438983210e-05 | 0.000100029071128 | 2.32261529800 | -0.000170784728982 | 0.0120348005703 | FAILED_CONSERVATION | one_or_more_species_or_nitrogen_balance_exceeds_1e-4 |

## Minimum concentrations

- A uniform overall minimum: 0.00000000000 mol/m^3.
- B diffusion coarse/medium/fine: 1.00000000000, 1.00000000000, 1.00000000000 mol/m^3.
- C MMS coarse/medium/fine: 5.00000000000, 5.00000000000, 5.00000000000 mol/m^3.
- D low-Da N2 minima: 4.86274613306, 4.95537805132, 4.98005926028 mol/m^3.
- D low-Da NH3 minima: 3.68031007157e-06, 3.63467855556e-06, 3.63032823280e-06 mol/m^3.
- E Pe-Da global finite min N2 / min NH3: 1.10099232513E-05 / -0.000170784728982 mol/m^3; every point is retained in the table.

## Integrity, reload, logs, and commands

- Independent reload of operator and wall MPH, named selections, physics, studies, retained solutions, and numerical nodes: PASS.
- CSV data rows: uniform=1, diffusion=3, MMS=3, low-Da=3, mesh-audit=9, Pe-Da=25; headers/field counts checked.
- Five PNGs: nonempty and PNG signatures checked.
- Frozen baseline SHA-256: PASS; manifests are `F:\LiNRR_COMSOL\worktrees\LiNRR_M02_2\runs\20260716_154328_M02_2\M02_2_frozen_hashes_before.csv` and `F:\LiNRR_COMSOL\worktrees\LiNRR_M02_2\runs\20260716_154328_M02_2\M02_2_frozen_hashes_after.csv`.
- Fatal batch-log matches: 0.
- Warning batch-log matches: 0; full logs are preserved under `F:\LiNRR_COMSOL\worktrees\LiNRR_M02_2\runs\20260716_154328_M02_2`.

- None.

Commands executed: isolated staging `comsolcompile` plus COMSOL-bundled ECJ for frozen M02.1; isolated `comsolbatch` rebuild; `comsolcompile` for metrics, verification, and reload classes; checkpointed M02.2 recovery batches that resumed after completed A, B, C, D, or E stages without rerunning a completed benchmark; independent reload `comsolbatch`.

## Changed/generated scope and remaining uncertainty

Changed source scope is D0012, the two M02.2 Java implementation files, the M02.2 reload test, and the Windows verification script. Generated scope is the separate diffusion and MMS operator MPH files, the wall-audit MPH, the required consolidated MPH, six CSVs, five PNGs, timestamped evidence, latest report, and latest build log. Benchmark A retains its structured stdout, CSV, and PNG evidence from the completed recovery stage. Experimental raw data were not modified.

All geometry, velocity, diffusivity, feed concentration, and phenomenological wall-rate inputs remain provisional. The model is 2D and steady, does not identify a microscopic Li-NRR mechanism, does not establish experimental transport parameters, and does not authorize optimization or M03B. Failed or outside-applicability Pe-Da points are numerical applicability evidence, not physical limiting-current claims.

RUN_STATE = SYNTHETIC_SMOKE_TEST

CALIBRATION_MODE = PROVISIONAL

M03B_READY = FALSE
