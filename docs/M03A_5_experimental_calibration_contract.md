# M03A.5 experimental calibration acquisition contract

## Purpose and scope

M03A.5 defines the acquisition, provenance, normalization, uncertainty, and
readiness contract for real experimental calibration evidence. It does not
contain experimental observations in its initial state. It does not run COMSOL,
create an experimental calibrated MPH, transfer a parameter, or authorize
M03B.

All submitted quantities must retain their origin. Missing information remains
`MISSING`; it is never imputed, copied from synthetic work, or interpreted as
zero. Synthetic files under `tests/fixtures/M03A_5/` are regression fixtures
only and can never satisfy an experimental gate.

## Evidence classes

The following evidence classes are distinct and must not be substituted for one
another:

1. **Raw instrument file**: the immutable file emitted by an instrument or its
   acquisition software. The manifest records repository-relative path, byte
   size, SHA-256, instrument, software, operator, time, and source reference.
2. **Normalized experimental table**: a UTF-8, RFC 4180 CSV derived without
   row deletion from the raw file. It contains `frequency`, `z_real`, and
   `z_imag`, with declared units. Duplicate frequencies, inductive points, and
   insufficient high-frequency coverage remain visible. Its size and SHA-256
   are recorded separately from the raw file.
3. **Manually declared metadata**: sample, cell, replicate, acquisition,
   electrolyte, water, gas, pressure, flow, stabilization, area-basis, and
   notebook fields supplied by the responsible experimenter. These declarations
   are not inferred from filenames.
4. **Derived HFR**: a finite positive high-frequency resistance with method,
   uncertainty, status, source reference, and retained diagnostics. A first
   frequency point is not automatically promoted to HFR.
5. **De-embedded electrolyte resistance**: `R_electrolyte = HFR_total -
   R_fixture - R_contact - R_membrane - R_other_series`. Every subtracted term
   must be independently present, unique, finite, traceable, and nonnegative.
   A missing term never defaults to zero. The result must be strictly positive.
6. **HFR-derived conductivity**: `kappa_HFR = electrode_spacing /
   (EIS_area * R_electrolyte)`. Spacing and EIS area must be measured and
   positive; uncertainty propagates from every parent.
7. **Direct conductivity**: an independent conductivity measurement with its
   own uncertainty, temperature, method, cell constant, calibration standard,
   instrument, raw evidence, and source reference.
8. **Model-transfer parameter**: a manually selected, provenance-bound value
   declared only after the intake, uncertainty, and consistency gates pass.
   The validator never chooses automatically between direct and HFR-derived
   conductivity.
9. **Readiness state**: a machine-readable gate result. `M03B_CANDIDATE` is an
   eligibility finding, not authorization. `M03B_READY` requires a later human
   review and remains `FALSE` in this stage.

## Canonical files

- `config/M03A_5_experimental_manifest.csv`: EIS acquisition, raw and
  normalized-file provenance, manually declared metadata, derived-HFR fields,
  and explicit transfer-selection declaration.
- `config/M03A_5_resistance_ledger.csv`: one row each for fixture, contact,
  membrane, and other-series resistance.
- `config/M03A_5_area_measurements.csv`: spacing, out-of-plane depth, geometric
  electrode area, EIS area, and current-density reporting area as separate
  quantities.
- `config/M03A_5_direct_conductivity.csv`: independent direct-conductivity
  evidence.
- `config/M03A_5_acceptance_thresholds.csv`: the sole source of numerical
  intake thresholds.

Paths are repository-relative. Hashes are lowercase or uppercase 64-character
SHA-256 hexadecimal strings. File size is the exact byte count parsed directly
as an invariant-culture `Int64`: decimal, exponent, NaN, Infinity, negative,
and out-of-range tokens are forbidden. Raw and normalized paths must resolve to
different files. No segment from repository root to an evidence file may be a
symlink, junction, or other reparse point. Formal rows use
`data_origin=EXPERIMENTAL`. Initial blank rows use `status=MISSING` and leave
numeric cells empty.

## HFR and normalized-EIS eligibility

An experimental replicate can enter HFR consensus only with `hfr_status` equal
to `ACCEPTED` or `ACCEPTED_WITH_RETAINED_DIAGNOSTIC`. Synthetic regression uses
the separate `ACCEPTED_SYNTHETIC` and
`ACCEPTED_SYNTHETIC_WITH_RETAINED_DIAGNOSTIC` statuses. All other statuses,
including `REJECTED`, `WARNING`, `UNREVIEWED`, `INSUFFICIENT_HF`, `MISSING`, and
unknown strings, are ineligible.

Allowed reviewed methods are `HIGH_FREQUENCY_INTERCEPT_REVIEWED` and
`EQUIVALENT_CIRCUIT_FIT_REVIEWED`. `FIRST_POINT`,
`FIRST_FREQUENCY_POINT`, unreviewed intercepts, and unknown methods are
ineligible. Status `MEASURED` alone never qualifies HFR.

Before eligibility, both evidence roles must pass independent path, exact-size,
and hash validation. The normalized table must preserve every `frequency`,
`z_real`, and `z_imag` row and close its actual frequency minimum, maximum, and
average points-per-decade against the manifest under centralized tolerances.
Duplicate-frequency and inductive diagnostics are retained. Insufficient
high-frequency information blocks only the corresponding replicate and is
never replaced by its first frequency point.

## Replicate consensus and uncertainty

Replicates in one sample consensus must agree in cell, instrument and software,
electrolyte composition and batch, water-content unit/method, gas atmosphere,
DC/OCP condition, and EIS area basis. Configured tolerances apply to
temperature, salt concentration, water content, pressure, flow, stabilization
time, perturbation amplitude, EIS area, and non-OCP DC bias. Non-OCP bias is
required; OCP bias may remain blank. Replicate IDs are unique within a sample,
and no outlier is deleted.

The configured HFR relative-range gate is
`(max(HFR)-min(HFR))/abs(mean(HFR))`. If it passes, the uncertainty of the mean
is:

```text
u_within_mean^2  = sum(u_i^2) / n^2
u_between_mean^2 = s_between^2 / n
u_HFR_mean       = sqrt(u_within_mean^2 + u_between_mean^2)
```

Both components are reported. Exceeding the relative-range limit blocks
de-embedding and conductivity calculation.

## Status and gate policy

`MISSING` is a truthful waiting state and passes framework/schema audit while
blocking experimental completeness. A row may be declared `MEASURED` only when
all required values, metadata, paths, sizes, hashes, and source references are
present and valid. A mislabeled or incomplete `MEASURED` row is blocking.

Replicates are never removed as outliers by the intake validator. Numeric values
are never clipped. Nonphysical Monte Carlo draws are counted against the fixed
requested denominator and are never repaired or resampled. Direct-versus-HFR
conflict is reported without selecting a preferred value. Each measured EIS
sample must map to exactly four series components, exactly five geometry/area
definitions, and exactly one direct-conductivity record of the same sample.
Orphans, cross-sample assembly, missing/duplicate direct records, and unresolved
direct/HFR temperature mismatch are blocking. Transfer selection also requires
a manual authorization reference; the validator never runs transfer.

The driver resolves the repository from its own location. It accepts normal
clones, detached HEAD, later branches, and merged descendants when the frozen
baseline commit exists, is an ancestor of `HEAD`, and every baseline path passes
the frozen audit. Branch and optional origin state are records, not hard gates.
Timestamped output is opt-in through `-CreateTimestampedRun`; canonical reports
are always written identically to `runs/latest/M03A_5_report.md` and
`results/reports/M03A_5_report.md`.

The current empty package must terminate with:

```text
RUN_STATE = EXPERIMENTAL_INPUT_WAIT
CALIBRATION_MODE = PROVISIONAL
EXPERIMENTAL_INPUT_COMPLETE = FALSE
PARAMETER_TRANSFER_MODE = NOT_RUN
M03B_CANDIDATE = FALSE
M03B_READY = FALSE
```
