# M03A.5 measurement protocol

## General handling

Create one stable `sample_id` for a physical electrolyte/cell condition and a
unique `replicate_id` for every independent EIS acquisition. Preserve the raw
instrument file unchanged. Export a normalized EIS CSV without deleting,
averaging, clipping, or repairing rows. Compute file byte sizes and SHA-256
hashes only after files are final. Use SI values in the canonical numeric
columns and decimal points, not locale commas.

Every source reference must resolve to a laboratory notebook page, electronic
record, or controlled acquisition record. Never enter an assumed zero for a
missing resistance.

## A. EIS acquisition

For every replicate record all of the following:

- sample ID, cell ID, replicate ID, operator, and measurement date/time with
  timezone;
- instrument manufacturer/model/serial where available and acquisition software
  plus version;
- raw instrument file path, exact byte size, and SHA-256;
- normalized table path, exact byte size, and SHA-256;
- frequency unit, impedance unit, measured minimum and maximum frequency,
  points per decade, and all retained frequency/real/imaginary impedance rows;
- perturbation amplitude and unit;
- DC bias value and reference, or an explicit OCP condition;
- temperature and where it was measured;
- complete electrolyte composition, electrolyte batch, salt concentration,
  water content, water-content unit, and water-content method;
- gas atmosphere, pressure, flow rate, and stabilization time;
- EIS area basis and measured EIS area;
- source notebook/page/reference;
- HFR method, HFR value, HFR uncertainty, HFR status, and HFR source reference
  after the derivation is reviewed.

Use a reviewed HFR method (`HIGH_FREQUENCY_INTERCEPT_REVIEWED` or
`EQUIVALENT_CIRCUIT_FIT_REVIEWED`) and one of the two accepted experimental HFR
statuses (`ACCEPTED` or `ACCEPTED_WITH_RETAINED_DIAGNOSTIC`). Never use the
first acquired frequency point as HFR merely because it is first.

Use at least three accepted replicates unless the centralized threshold is
formally revised. Replicates for one consensus must have compatible cell ID,
instrument and software, temperature, electrolyte composition and batch, salt
concentration, water content/unit/method, gas, pressure, flow, stabilization,
perturbation amplitude, DC/OCP condition and non-OCP bias, EIS area basis, and
EIS area value. Keep duplicate frequencies, inductive high-frequency
observations, and insufficient-high-frequency flags in the normalized evidence
and audit; do not silently discard them. The normalized table's actual minimum,
maximum, and average sampling density must close against the manifest.

## B. Series-resistance measurements

Create exactly one ledger row for each of `fixture`, `contact`, `membrane`, and
`other_series`. For every item record:

- value and standard uncertainty;
- explicit unit (`ohm`);
- method;
- replicate count;
- temperature;
- source reference;
- raw evidence path, exact byte size, and SHA-256;
- status.

If a physical contribution is believed not to apply, retain the component and
provide a measured or independently justified value with evidence; absence is
not zero. A missing, duplicate, negative, or untraceable component blocks
de-embedding.

## C. Geometry and area measurements

Measure and record these quantities independently:

- electrode spacing (`m`);
- out-of-plane depth (`m`);
- geometric electrode area (`m^2`);
- EIS area (`m^2`);
- current-density reporting area (`m^2`).

For each, provide value, uncertainty, method, definition, raw-evidence path and
hash, and source reference. Do not assume that geometric electrode area, EIS
area, and current-density reporting area are equal. If equality is physically
established, document the measurement or mapping evidence explicitly.

## D. Direct conductivity

For each independent direct measurement record:

- measured conductivity and uncertainty in `S/m`;
- measurement temperature;
- method;
- positive cell constant and its unit (`1/m` or `m^-1`);
- calibration standard;
- instrument;
- raw-evidence path, exact byte size, and SHA-256;
- source reference.

Compare direct conductivity to HFR-derived conductivity only after temperature
and sample compatibility are established using the centralized tolerance. Do
not calculate or accept a relative difference across incompatible temperatures.
A relative difference above the
central threshold is `CALIBRATION_CONFLICT`. Agreement does not authorize an
automatic choice: the transfer source and its authorization reference must be
declared manually.

## Submission checklist

1. Place controlled experimental evidence at stable repository-relative paths.
2. Complete every canonical CSV using `data_origin=EXPERIMENTAL`.
3. Change a row from `MISSING` to `MEASURED` only when its evidence is complete.
4. Run `scripts/windows/18_verify_M03A_5_experimental_intake.ps1`. Add
   `-CreateTimestampedRun` only when a retained timestamped audit copy is
   specifically needed.
5. Review every row in the completeness, missing-input, provenance, readiness,
   and regression tables.
6. Submit the package for strict review. Do not run parameter transfer or COMSOL
   until separately authorized.
