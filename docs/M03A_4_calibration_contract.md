# M03A.4 calibration intake and synthetic parameter-transfer contract

M03A.4 provides an experimental-input framework but does not fabricate experimental data. The formal experimental manifest row is intentionally incomplete and has no generated substitute file. Synthetic regression inputs are isolated under `tests/fixtures/M03A_4/` and carry `data_origin = SYNTHETIC`.

## Intake and de-embedding

EIS import preserves original row numbers, retains duplicates and inductive artifacts, and uses stable descending-frequency normalization. Blank, string, NaN, infinite, or nonpositive-frequency input blocks the entire case. HFR estimation retains selected rows, frequency coverage, fit method, replicate membership, and equivalent-circuit provenance.

The resistance ledger requires `EIS_HFR_total`, `fixture`, `contact`, `membrane`, and `other_series`. Synthetic fixed definitions are `SYNTHETIC_DEFINED`, and the accepted synthetic HFR row is `SYNTHETIC_CONSENSUS_ACCEPTED`; they are never mislabeled as measured. Experimental entries may be `MEASURED`, `NOT_APPLICABLE_WITH_EVIDENCE`, `MISSING`, or `INVALID`. Each required sample/component pair must occur exactly once. Missing, duplicate, or invalid terms never default to zero. The de-embedded resistance is

`R_electrolyte = R_HFR_total - R_fixture - R_contact - R_membrane - R_other_series`.

Nonpositive de-embedded resistance blocks conductivity inversion. Conductivity is `kappa_HFR = Hcell/(EIS_area*R_electrolyte)`. First-order uncertainty includes spacing, EIS area, HFR, and all series components. Fixed-seed Monte Carlo uses at least 20,000 samples; nonpositive spacing, area, resistance, or conductivity samples are rejected unchanged. Rejection above 1% is `FAILED_INPUT_DISTRIBUTION`.

All downstream analytical, Monte Carlo, transfer, and reload work consumes only `results/tables/M03A_4_resolved_inputs.csv`. PowerShell creates this canonical artifact with `Import-Csv`, RFC 4180 escaping, explicit unit/origin/evidence fields, source rows, references, and SHA-256 provenance. Java reads no raw experimental or configuration CSV.

## Area and conductivity policy

Electrode spacing, out-of-plane thickness, geometric electrode area, actual wetted area, catalytic-layer projected area, GDE exposed area, EIS area, and current-density reporting area are independent long-form definitions. Synthetic rows use `data_origin = SYNTHETIC` and `status = SYNTHETIC_DEFINED`; experimental template rows use `data_origin = EXPERIMENTAL`, `status = MISSING`, and blank values. No equality is assumed. Mapping requires an exact whitelisted formula, reference, and uncertainty. Direct and HFR-derived conductivity remain independent; a relative difference above 0.10 is `CALIBRATION_CONFLICT`, and no value is automatically selected as truth.

## Synthetic transfer dry-run

The M03A.3 MPH is loaded read-only and saved to a new derivative M03A.4 MPH. Seven explicit cases cover identity, each single-variable perturbation, and a formal combined nonidentity case. Every case sets `kappa_dry_run`, `Hcell_dry_run`, `depth_dry_run`, `A_EIS_dry_run`, and `A_j_report_dry_run`, rebuilds geometry, audits named selections, rebuilds mesh, clears old solution data, activates `spf`, `tds`, and `cd`, and executes a fresh stationary solve. EIS area enters only conductivity inversion. Reporting area enters only `j_reported = I_total/A_j_report_dry_run`. The dry-run retains the M03A.3 physics tree. No electrode kinetics, Secondary/Tertiary Current Distribution, or ElectrodeReaction is introduced.

Algebraic `m033_n2_imposed_cath` and `m033_nh3_imposed_cath` values are classified only as `ALGEBRAIC_STOICHIOMETRIC_MAPPING`. Formal transport acceptance uses current-run actual TDS cathode flux, N2/NH3 inlet and outlet fluxes, and independent species and nitrogen balances. Acceptance requires resistance and current errors no greater than `1e-3`, current conservation no greater than `1e-6`, current-to-TDS N2/NH3 and 2:1 ratio errors no greater than `1e-6`, and N2, NH3, and nitrogen balances no greater than `1e-4`. A passing model is classified only as `SYNTHETIC_PARAMETER_TRANSFER_PASS`.

Experimental calibration is incomplete. M03A.4 does not authorize M03B.

RUN_STATE = SYNTHETIC_SMOKE_TEST

CALIBRATION_MODE = PROVISIONAL

PARAMETER_TRANSFER_MODE = SYNTHETIC_DRY_RUN

EXPERIMENTAL_INPUT_COMPLETE = FALSE

M03B_READY = FALSE
