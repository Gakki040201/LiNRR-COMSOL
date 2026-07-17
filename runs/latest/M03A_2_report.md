# M03A.2 Formal Synthetic EIS Contract Verification

- Run: `20260717_130553`
- Result: **PASS - synthetic verification only**
- Manifest cases: `33`; data origins: SYNTHETIC=32, EXPERIMENTAL=1.
- Synthetic fixture count: `32`; `EXPERIMENTAL_INPUT_INCOMPLETE` count: `1`.
- HFR method counts: FIRST_POINT_DIAGNOSTIC=33, HIGH_FREQUENCY_INTERCEPT=31, USER_DEFINED_EQUIVALENT_CIRCUIT=2.
- Selected-row/range audit: `26` estimator rows have explicit original-row lists and frequency ranges; full-arc fit rows are not mislabeled as high-frequency candidates.
- Equivalent-circuit contract: supported synthetic Randles `1`; incomplete contracts blocked `1`; declared `Rs` is read from the fit contract, never substituted by the first point.
- Resistance source completeness: one complete provenance row; missing, negative, and zero de-embedded regressions remain blocked.
- Area long-form audit: `18` rows, six independent definitions per sample/case; mapping requires reference, formula, source, and uncertainty.
- Monte Carlo: accepted `20000`, rejected `0`; failed-distribution regression rejected `6039` of `20000` without clipping or repair.
- CPE case: `METHOD_DEPENDENT_PROVISIONAL`. Arbitrary CPE nonlinear fitting is not implemented; the estimate depends on method choice and does not demonstrate experimental CPE parameter identifiability.

## Current experimental input gaps

- C023 has no experimental EIS file, source reference, temperature, electrolyte composition, water content, instrument, measurement date, or EIS area evidence.
- No experimental fixture/contact/membrane/other-series resistance values with uncertainties and provenance are supplied.
- No experimental area mapping, spacing measurement, direct conductivity measurement, or arbitrary-CPE fit-quality evidence is supplied.

M03A.2 passing means synthetic EIS and calibration-input verification only.
It is not experimental calibration.
It does not authorize M03B.

RUN_STATE = SYNTHETIC_SMOKE_TEST
CALIBRATION_MODE = PROVISIONAL
M03B_READY = FALSE