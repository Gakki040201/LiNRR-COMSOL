# M03A.1 Experimental Calibration Data Dictionary

This file defines the input contract for `config/M03A_calibration_inputs.csv`. Blank values are allowed and are reported by the readiness gate. A blank, assumed, derived-from-provisional, or `PROVISIONAL` value is never promoted to experimental evidence.

## CSV columns

| column | definition |
|:--|:--|
| `field_name` | Stable field identifier. One row per field. |
| `value` | Numeric or categorical value. Leave blank when unavailable. |
| `unit` | Explicit SI-compatible unit or `category` for text. Resistance is `ohm`, conductivity is `S/m`, current is `A`, voltage is `V`, area is `m^2`, length is `m`/`mm`, temperature is `K`. |
| `uncertainty` | Standard uncertainty (1 sigma) in `uncertainty_unit`. For paired `*_uncertainty` rows, put the standard uncertainty in `value`. |
| `uncertainty_unit` | Unit of the uncertainty. |
| `status` | `AVAILABLE`, `MISSING`, `AMBIGUOUS`, `INCONSISTENT`, or `PROVISIONAL`. Only `AVAILABLE` can satisfy a critical experimental gate. |
| `source_type` | `MEASURED`, `LITERATURE`, `FITTED`, `DERIVED`, or `ASSUMED`. Experimental calibration normally requires `MEASURED`; every nonblank value needs an explicit source. |
| `source_reference` | File, notebook, instrument export, DOI, report section, or other traceable reference. |
| `measurement_date` | ISO `YYYY-MM-DD` date, when applicable. |
| `temperature_K` | Temperature of the referenced measurement, in K. |
| `notes` | Conditions, definitions, exclusions, preprocessing, or provenance details. |

## Required quantities and definitions

- `Lcell`, `Hcell`, `Wcell`: model dimensions. `Hcell` must explicitly mean actual electrode spacing, electrolyte-layer thickness, separator thickness, or a documented model-equivalent distance.
- `electrode_active_area`: area used to convert total current to applied current density. It must be reconciled with all area-audit fields below.
- `temperature`: operating/calibration temperature.
- `kappa_direct`: directly measured electrolyte conductivity at the stated composition and temperature.
- `EIS_HFR_total`: total measured high-frequency resistance. It is not automatically electrolyte resistance.
- `fixture_resistance`, `contact_resistance`, `membrane_resistance`, `other_series_resistance`: non-electrolyte series contributions used for de-embedding.
- `Icell`, `measured_cell_voltage`, `WE_potential`, `CE_potential`: simultaneous or clearly matched electrical observations, including polarity/reference conventions.
- `electrolyte_composition`, `water_content`: complete electrolyte identity and preparation/measurement basis.
- `measurement_method`: conductivity and EIS instrument, cell, frequency range, amplitude, fitting/intercept method, and preprocessing.
- `EIS_intercept_definition`: equivalent-circuit or real-axis intercept definition used to obtain HFR.
- `replicate_count`: number of independent preparations or measurements; technical repeats must be identified separately.

Paired fields such as `EIS_HFR_uncertainty` are accepted for laboratory templates that store uncertainty as a separate observation. If both the target row's `uncertainty` column and a paired row are populated, they must agree in common units or the input is `INCONSISTENT`.

## Area audit

The following are deliberately separate and must not be silently converted:

- geometric electrode area, normally `Lcell*Wcell`;
- `actual_wetted_area`;
- `catalytic_layer_projected_area`;
- `GDE_exposed_area`;
- `EIS_area`;
- `current_density_reporting_area`.

`area_basis` must state which one `electrode_active_area` represents. If available area values or definitions disagree without a documented mapping, the calibration status is `AREA_BASIS_MISMATCH` and `M03B_READY=FALSE`.

## Calibration modes and run states

- `DIRECT_CONDUCTIVITY`: uses traceable measured `kappa_direct`.
- `DEEMBEDDED_HFR`: uses `R_electrolyte_from_HFR = EIS_HFR_total - R_non_electrolyte`, then `kappa_from_HFR = Hcell/(electrode_active_area*R_electrolyte_from_HFR)`.
- `CROSS_CHECK`: compares both independent conductivity estimates. A relative difference above 10% is `CALIBRATION_CONFLICT`; neither is automatically selected as truth.
- `PROVISIONAL`: uses the frozen M03A numerical values only for a reproducible synthetic smoke test.

Run states are `SYNTHETIC_SMOKE_TEST`, `EXPERIMENTAL_INPUT_INCOMPLETE`, and `EXPERIMENTALLY_CALIBRATED`. The last state requires every critical readiness item to be `AVAILABLE`, traceable sources, consistent area/spacing definitions, a valid conductivity path, and no conflict.

## Uncertainty convention

Input uncertainties are interpreted as standard uncertainties unless notes state otherwise. M03A.1 reports first-order propagation and fixed-seed Monte Carlo propagation. Positive physical quantities use rejection sampling; samples with nonpositive thickness, area, conductivity, or de-embedded electrolyte resistance are invalid. More than 1% invalid samples yields `FAILED_INPUT_DISTRIBUTION`.

The synthetic smoke test assigns explicit assumed uncertainties only inside the analysis script. Those assumptions are labeled `PROVISIONAL` and are not written back as experimental input.
