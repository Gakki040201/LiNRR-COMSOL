# M03A.2 Formal Synthetic EIS Contract Data Dictionary

M03A.2 verifies synthetic EIS and calibration-input handling only. It is not
experimental calibration and cannot authorize M03B.

## Manifest and provenance

`config/M03A_2_eis_manifest.csv` uses the only legal `data_origin` values
`SYNTHETIC`, `EXPERIMENTAL`, `LITERATURE`, and `UNKNOWN`. Every generated
fixture is `SYNTHETIC`. C023 is an intentionally incomplete `EXPERIMENTAL`
manifest row: its file and critical provenance are absent, no substitute data
are generated, and its case outcome is `EXPERIMENTAL_INPUT_INCOMPLETE`.
`RUN_STATE` describes the stage and is not a case classification.

## Units and import

Frequency tokens are case-sensitive and distinct: `Hz`, `kHz`, `mHz`, and
`MHz`. The last two mean millihertz and megahertz respectively and never share
a normalized token. Impedance tokens are ASCII `ohm`, `mohm`, and `kohm`;
`mohm` means **milliohm**, not megaohm. Unclear Unicode unit tokens are blocked
rather than guessed.

Fixture columns are `frequency`, `z_real`, and `z_imag`. `z_imag` is signed:
negative is capacitive and positive is inductive. InvariantCulture parsing is
used. Physical input line numbers begin at 2 and remain traceable after stable
descending-frequency normalization. Duplicate, unsorted, inductive, and
disagreeing-replicate data are retained. Blank, string, NaN, and infinite
numeric input blocks the whole case while preserving raw-row audit records.

## HFR coverage and methods

The high-frequency candidate region is explicitly the highest frequency
decade. Coverage jointly evaluates candidate count, frequency span, proximity
to `Zimag = 0`, real-axis crossing or fit support, inductive artifacts,
duplicate frequencies, normalized fit residual, and complete-semicircle model
dependence. A high maximum frequency and many points alone are insufficient.

`selected_original_rows`, selected frequency bounds, intercept model, residual,
coverage, artifact, duplicate, and fit-quality statuses are written for every
estimator. Full-arc circle-fit rows are called fit rows, not high-frequency
candidate points.

- `FIRST_POINT_DIAGNOSTIC` is always `LAB_SCREENING_HEURISTIC`, including when
  its input is blocked. It is never accepted as HFR.
- `HIGH_FREQUENCY_INTERCEPT` uses a resistive level or a declared full-arc
  circle left intercept. Complete-semicircle dependence is explicit.
- `USER_DEFINED_EQUIVALENT_CIRCUIT` reads the circuit, software, fit file,
  `Rs`, `Rs` standard error, fit-quality name/value, source, status, and notes.
  The synthetic interface supports only `Rs + (Rct || Cdl)` and declares
  `SUPPORTED_SYNTHETIC_RANDLES_ONLY`. Missing circuit, quality, source, or
  finite positive `Rs` blocks the result. No first-point callback is used.

The `RS_RCT_CPE` regression is `METHOD_DEPENDENT_PROVISIONAL`. Arbitrary CPE
nonlinear fitting is not implemented; the result depends on the estimator and
does not demonstrate experimental CPE parameter identifiability.

## Resistance and conductivity

The resistance ledger keeps total HFR separate from fixture, contact,
membrane, other-series, and electrolyte resistance. Each non-electrolyte term
requires a finite value, uncertainty, and source; or source-backed
`NOT_APPLICABLE`; or a source-backed experimentally measured zero. Missing
terms never default to zero. Nonpositive electrolyte resistance cannot enter
conductivity inversion.

Conductivity uses `kappa = Hcell/(EIS_area*R_electrolyte)`. Thickness, EIS area,
their definitions and uncertainties, analytical propagation, fixed-seed Monte
Carlo counts, and failure reasons are explicit. Monte Carlo samples are either
accepted unchanged or rejected unchanged; no clipping or repair occurs. A
rejected fraction greater than 0.01 is `FAILED_INPUT_DISTRIBUTION`. The
assumption is `INDEPENDENCE_ASSUMPTION_PROVISIONAL`.

## Area and replicate outputs

The area audit is long-form, one definition per row, covering geometric
electrode, actual wetted, catalytic-layer projected, GDE exposed, EIS, and
current-density reporting areas. They are never assumed equal. Mapping between
EIS and reporting areas requires a mapping reference, formula, source, and
mapping uncertainty; otherwise the result is `AREA_BASIS_MISMATCH`.

Individual replicate estimates remain in the HFR table. The consensus table
has one row per sample and method and reports count, valid count, mean, median,
sample standard deviation, coefficient of variation, extrema, and relative
range. Outlying replicates remain in all statistics.

## Terminal state

```text
RUN_STATE = SYNTHETIC_SMOKE_TEST
CALIBRATION_MODE = PROVISIONAL
M03B_READY = FALSE
```

M03A.2 passing means synthetic EIS and calibration-input verification only.
It is not experimental calibration. It does not authorize M03B.
