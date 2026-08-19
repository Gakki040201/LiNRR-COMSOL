# M03A.5 experimental calibration intake report

Canonical framework audit; no timestamped run was requested by default.

No real experimental files are present. This is a framework/schema audit, not an experimental calibration PASS. COMSOL was not started, no MPH was created, no parameter was transferred, and the M03A.4 active release was not modified.

## Enforced readiness

- RUN_STATE = EXPERIMENTAL_INPUT_WAIT - intake state only; no COMSOL run
- CALIBRATION_MODE = PROVISIONAL - not an experimental calibration
- EXPERIMENTAL_INPUT_COMPLETE = FALSE - samples=0; missing=True; blocked=0
- ANALYTICAL_UNCERTAINTY_READY = FALSE - all parent uncertainties and between-replicate dispersion required
- MONTE_CARLO_UNCERTAINTY_READY = FALSE - fixed configured sample count; no repair or resampling
- PARAMETER_TRANSFER_MODE = NOT_RUN - manual selection and authorization required; validator never transfers
- M03B_CANDIDATE = FALSE - candidate is not authorization
- M03B_READY = FALSE - M03A.5 never authorizes M03B

## Data the user must collect or enter next

### config/M03A_5_experimental_manifest.csv

- `EXP_TEMPLATE_001/` / `cell_id`: Collect or declare cell_id
- `EXP_TEMPLATE_001/` / `replicate_id`: Collect or declare replicate_id
- `EXP_TEMPLATE_001/` / `operator`: Collect or declare operator
- `EXP_TEMPLATE_001/` / `measurement_datetime`: Collect or declare measurement_datetime
- `EXP_TEMPLATE_001/` / `instrument`: Collect or declare instrument
- `EXP_TEMPLATE_001/` / `instrument_software`: Collect or declare instrument_software
- `EXP_TEMPLATE_001/` / `raw_file_path`: Collect or declare raw_file_path
- `EXP_TEMPLATE_001/` / `raw_file_size_bytes`: Collect or declare raw_file_size_bytes
- `EXP_TEMPLATE_001/` / `raw_file_sha256`: Collect or declare raw_file_sha256
- `EXP_TEMPLATE_001/` / `normalized_table_path`: Collect or declare normalized_table_path
- `EXP_TEMPLATE_001/` / `normalized_table_size_bytes`: Collect or declare normalized_table_size_bytes
- `EXP_TEMPLATE_001/` / `normalized_table_sha256`: Collect or declare normalized_table_sha256
- `EXP_TEMPLATE_001/` / `frequency_unit`: Collect or declare frequency_unit
- `EXP_TEMPLATE_001/` / `impedance_unit`: Collect or declare impedance_unit
- `EXP_TEMPLATE_001/` / `frequency_min_Hz`: Collect or declare frequency_min_Hz
- `EXP_TEMPLATE_001/` / `frequency_max_Hz`: Collect or declare frequency_max_Hz
- `EXP_TEMPLATE_001/` / `points_per_decade`: Collect or declare points_per_decade
- `EXP_TEMPLATE_001/` / `perturbation_amplitude_V`: Collect or declare perturbation_amplitude_V
- `EXP_TEMPLATE_001/` / `dc_condition`: Collect or declare dc_condition
- `EXP_TEMPLATE_001/` / `temperature_K`: Collect or declare temperature_K
- `EXP_TEMPLATE_001/` / `electrolyte_composition`: Collect or declare electrolyte_composition
- `EXP_TEMPLATE_001/` / `electrolyte_batch`: Collect or declare electrolyte_batch
- `EXP_TEMPLATE_001/` / `salt_concentration_mol_L`: Collect or declare salt_concentration_mol_L
- `EXP_TEMPLATE_001/` / `water_content`: Collect or declare water_content
- `EXP_TEMPLATE_001/` / `water_content_unit`: Collect or declare water_content_unit
- `EXP_TEMPLATE_001/` / `water_content_method`: Collect or declare water_content_method
- `EXP_TEMPLATE_001/` / `gas_atmosphere`: Collect or declare gas_atmosphere
- `EXP_TEMPLATE_001/` / `pressure_Pa`: Collect or declare pressure_Pa
- `EXP_TEMPLATE_001/` / `flow_rate_m3_s`: Collect or declare flow_rate_m3_s
- `EXP_TEMPLATE_001/` / `stabilization_time_s`: Collect or declare stabilization_time_s
- `EXP_TEMPLATE_001/` / `eis_area_basis`: Collect or declare eis_area_basis
- `EXP_TEMPLATE_001/` / `eis_area_m2`: Collect or declare eis_area_m2
- `EXP_TEMPLATE_001/` / `source_notebook_reference`: Collect or declare source_notebook_reference
- `EXP_TEMPLATE_001/` / `hfr_method`: Collect or declare hfr_method
- `EXP_TEMPLATE_001/` / `hfr_ohm`: Collect or declare hfr_ohm
- `EXP_TEMPLATE_001/` / `hfr_uncertainty_ohm`: Collect or declare hfr_uncertainty_ohm
- `EXP_TEMPLATE_001/` / `hfr_status`: Collect or declare hfr_status
- `EXP_TEMPLATE_001/` / `hfr_source_reference`: Collect or declare hfr_source_reference

### config/M03A_5_resistance_ledger.csv

- `EXP_TEMPLATE_001/fixture` / `value_ohm`: Collect or declare value_ohm
- `EXP_TEMPLATE_001/fixture` / `uncertainty_ohm`: Collect or declare uncertainty_ohm
- `EXP_TEMPLATE_001/fixture` / `unit`: Collect or declare unit
- `EXP_TEMPLATE_001/fixture` / `method`: Collect or declare method
- `EXP_TEMPLATE_001/fixture` / `replicate_count`: Collect or declare replicate_count
- `EXP_TEMPLATE_001/fixture` / `temperature_K`: Collect or declare temperature_K
- `EXP_TEMPLATE_001/fixture` / `source_reference`: Collect or declare source_reference
- `EXP_TEMPLATE_001/fixture` / `raw_evidence_path`: Collect or declare raw_evidence_path
- `EXP_TEMPLATE_001/fixture` / `raw_evidence_size_bytes`: Collect or declare raw_evidence_size_bytes
- `EXP_TEMPLATE_001/fixture` / `raw_evidence_sha256`: Collect or declare raw_evidence_sha256
- `EXP_TEMPLATE_001/contact` / `value_ohm`: Collect or declare value_ohm
- `EXP_TEMPLATE_001/contact` / `uncertainty_ohm`: Collect or declare uncertainty_ohm
- `EXP_TEMPLATE_001/contact` / `unit`: Collect or declare unit
- `EXP_TEMPLATE_001/contact` / `method`: Collect or declare method
- `EXP_TEMPLATE_001/contact` / `replicate_count`: Collect or declare replicate_count
- `EXP_TEMPLATE_001/contact` / `temperature_K`: Collect or declare temperature_K
- `EXP_TEMPLATE_001/contact` / `source_reference`: Collect or declare source_reference
- `EXP_TEMPLATE_001/contact` / `raw_evidence_path`: Collect or declare raw_evidence_path
- `EXP_TEMPLATE_001/contact` / `raw_evidence_size_bytes`: Collect or declare raw_evidence_size_bytes
- `EXP_TEMPLATE_001/contact` / `raw_evidence_sha256`: Collect or declare raw_evidence_sha256
- `EXP_TEMPLATE_001/membrane` / `value_ohm`: Collect or declare value_ohm
- `EXP_TEMPLATE_001/membrane` / `uncertainty_ohm`: Collect or declare uncertainty_ohm
- `EXP_TEMPLATE_001/membrane` / `unit`: Collect or declare unit
- `EXP_TEMPLATE_001/membrane` / `method`: Collect or declare method
- `EXP_TEMPLATE_001/membrane` / `replicate_count`: Collect or declare replicate_count
- `EXP_TEMPLATE_001/membrane` / `temperature_K`: Collect or declare temperature_K
- `EXP_TEMPLATE_001/membrane` / `source_reference`: Collect or declare source_reference
- `EXP_TEMPLATE_001/membrane` / `raw_evidence_path`: Collect or declare raw_evidence_path
- `EXP_TEMPLATE_001/membrane` / `raw_evidence_size_bytes`: Collect or declare raw_evidence_size_bytes
- `EXP_TEMPLATE_001/membrane` / `raw_evidence_sha256`: Collect or declare raw_evidence_sha256
- `EXP_TEMPLATE_001/other_series` / `value_ohm`: Collect or declare value_ohm
- `EXP_TEMPLATE_001/other_series` / `uncertainty_ohm`: Collect or declare uncertainty_ohm
- `EXP_TEMPLATE_001/other_series` / `unit`: Collect or declare unit
- `EXP_TEMPLATE_001/other_series` / `method`: Collect or declare method
- `EXP_TEMPLATE_001/other_series` / `replicate_count`: Collect or declare replicate_count
- `EXP_TEMPLATE_001/other_series` / `temperature_K`: Collect or declare temperature_K
- `EXP_TEMPLATE_001/other_series` / `source_reference`: Collect or declare source_reference
- `EXP_TEMPLATE_001/other_series` / `raw_evidence_path`: Collect or declare raw_evidence_path
- `EXP_TEMPLATE_001/other_series` / `raw_evidence_size_bytes`: Collect or declare raw_evidence_size_bytes
- `EXP_TEMPLATE_001/other_series` / `raw_evidence_sha256`: Collect or declare raw_evidence_sha256

### config/M03A_5_area_measurements.csv

- `EXP_TEMPLATE_001/electrode_spacing` / `value_SI`: Collect or declare value_SI
- `EXP_TEMPLATE_001/electrode_spacing` / `uncertainty_SI`: Collect or declare uncertainty_SI
- `EXP_TEMPLATE_001/electrode_spacing` / `unit`: Collect or declare unit
- `EXP_TEMPLATE_001/electrode_spacing` / `measurement_method`: Collect or declare measurement_method
- `EXP_TEMPLATE_001/electrode_spacing` / `source_reference`: Collect or declare source_reference
- `EXP_TEMPLATE_001/electrode_spacing` / `raw_evidence_path`: Collect or declare raw_evidence_path
- `EXP_TEMPLATE_001/electrode_spacing` / `raw_evidence_size_bytes`: Collect or declare raw_evidence_size_bytes
- `EXP_TEMPLATE_001/electrode_spacing` / `raw_evidence_sha256`: Collect or declare raw_evidence_sha256
- `EXP_TEMPLATE_001/electrode_spacing` / `definition`: Collect or declare definition
- `EXP_TEMPLATE_001/out_of_plane_depth` / `value_SI`: Collect or declare value_SI
- `EXP_TEMPLATE_001/out_of_plane_depth` / `uncertainty_SI`: Collect or declare uncertainty_SI
- `EXP_TEMPLATE_001/out_of_plane_depth` / `unit`: Collect or declare unit
- `EXP_TEMPLATE_001/out_of_plane_depth` / `measurement_method`: Collect or declare measurement_method
- `EXP_TEMPLATE_001/out_of_plane_depth` / `source_reference`: Collect or declare source_reference
- `EXP_TEMPLATE_001/out_of_plane_depth` / `raw_evidence_path`: Collect or declare raw_evidence_path
- `EXP_TEMPLATE_001/out_of_plane_depth` / `raw_evidence_size_bytes`: Collect or declare raw_evidence_size_bytes
- `EXP_TEMPLATE_001/out_of_plane_depth` / `raw_evidence_sha256`: Collect or declare raw_evidence_sha256
- `EXP_TEMPLATE_001/out_of_plane_depth` / `definition`: Collect or declare definition
- `EXP_TEMPLATE_001/geometric_electrode_area` / `value_SI`: Collect or declare value_SI
- `EXP_TEMPLATE_001/geometric_electrode_area` / `uncertainty_SI`: Collect or declare uncertainty_SI
- `EXP_TEMPLATE_001/geometric_electrode_area` / `unit`: Collect or declare unit
- `EXP_TEMPLATE_001/geometric_electrode_area` / `measurement_method`: Collect or declare measurement_method
- `EXP_TEMPLATE_001/geometric_electrode_area` / `source_reference`: Collect or declare source_reference
- `EXP_TEMPLATE_001/geometric_electrode_area` / `raw_evidence_path`: Collect or declare raw_evidence_path
- `EXP_TEMPLATE_001/geometric_electrode_area` / `raw_evidence_size_bytes`: Collect or declare raw_evidence_size_bytes
- `EXP_TEMPLATE_001/geometric_electrode_area` / `raw_evidence_sha256`: Collect or declare raw_evidence_sha256
- `EXP_TEMPLATE_001/geometric_electrode_area` / `definition`: Collect or declare definition
- `EXP_TEMPLATE_001/EIS_area` / `value_SI`: Collect or declare value_SI
- `EXP_TEMPLATE_001/EIS_area` / `uncertainty_SI`: Collect or declare uncertainty_SI
- `EXP_TEMPLATE_001/EIS_area` / `unit`: Collect or declare unit
- `EXP_TEMPLATE_001/EIS_area` / `measurement_method`: Collect or declare measurement_method
- `EXP_TEMPLATE_001/EIS_area` / `source_reference`: Collect or declare source_reference
- `EXP_TEMPLATE_001/EIS_area` / `raw_evidence_path`: Collect or declare raw_evidence_path
- `EXP_TEMPLATE_001/EIS_area` / `raw_evidence_size_bytes`: Collect or declare raw_evidence_size_bytes
- `EXP_TEMPLATE_001/EIS_area` / `raw_evidence_sha256`: Collect or declare raw_evidence_sha256
- `EXP_TEMPLATE_001/EIS_area` / `definition`: Collect or declare definition
- `EXP_TEMPLATE_001/current_density_reporting_area` / `value_SI`: Collect or declare value_SI
- `EXP_TEMPLATE_001/current_density_reporting_area` / `uncertainty_SI`: Collect or declare uncertainty_SI
- `EXP_TEMPLATE_001/current_density_reporting_area` / `unit`: Collect or declare unit
- `EXP_TEMPLATE_001/current_density_reporting_area` / `measurement_method`: Collect or declare measurement_method
- `EXP_TEMPLATE_001/current_density_reporting_area` / `source_reference`: Collect or declare source_reference
- `EXP_TEMPLATE_001/current_density_reporting_area` / `raw_evidence_path`: Collect or declare raw_evidence_path
- `EXP_TEMPLATE_001/current_density_reporting_area` / `raw_evidence_size_bytes`: Collect or declare raw_evidence_size_bytes
- `EXP_TEMPLATE_001/current_density_reporting_area` / `raw_evidence_sha256`: Collect or declare raw_evidence_sha256
- `EXP_TEMPLATE_001/current_density_reporting_area` / `definition`: Collect or declare definition

### config/M03A_5_direct_conductivity.csv

- `EXP_TEMPLATE_001/direct_conductivity` / `conductivity_S_m`: Collect or declare conductivity_S_m
- `EXP_TEMPLATE_001/direct_conductivity` / `uncertainty_S_m`: Collect or declare uncertainty_S_m
- `EXP_TEMPLATE_001/direct_conductivity` / `unit`: Collect or declare unit
- `EXP_TEMPLATE_001/direct_conductivity` / `temperature_K`: Collect or declare temperature_K
- `EXP_TEMPLATE_001/direct_conductivity` / `method`: Collect or declare method
- `EXP_TEMPLATE_001/direct_conductivity` / `cell_constant`: Collect or declare cell_constant
- `EXP_TEMPLATE_001/direct_conductivity` / `cell_constant_unit`: Collect or declare cell_constant_unit
- `EXP_TEMPLATE_001/direct_conductivity` / `calibration_standard`: Collect or declare calibration_standard
- `EXP_TEMPLATE_001/direct_conductivity` / `instrument`: Collect or declare instrument
- `EXP_TEMPLATE_001/direct_conductivity` / `raw_evidence_path`: Collect or declare raw_evidence_path
- `EXP_TEMPLATE_001/direct_conductivity` / `raw_evidence_size_bytes`: Collect or declare raw_evidence_size_bytes
- `EXP_TEMPLATE_001/direct_conductivity` / `raw_evidence_sha256`: Collect or declare raw_evidence_sha256
- `EXP_TEMPLATE_001/direct_conductivity` / `source_reference`: Collect or declare source_reference

## Scientific gates

- HFR consensus accepts only reviewed allowlisted statuses and methods after raw provenance, normalized provenance, normalized-row audit, and frequency metadata closure pass for that replicate.
- Replicate compatibility covers cell, instrument/software, temperature, electrolyte/batch/concentration/water metadata, gas/pressure/flow/stabilization, perturbation, DC/OCP condition and bias, and EIS area basis/value.
- HFR mean uncertainty uses `u_within_mean^2 = sum(u_i^2)/n^2`, `u_between_mean^2 = s_between^2/n`, and `u_HFR_mean = sqrt(u_within_mean^2 + u_between_mean^2)`.
- Direct and HFR-derived conductivity are compared only after sample and configured temperature compatibility; conflicts are reported without automatic selection.
- Monte Carlo uses the configured requested denominator and fixed seed, rejects nonphysical draws, and performs no clipping, repair, or resampling.

## Framework and provenance audit

- 136 required user inputs were reconstructed from the canonical schemas.
- Raw and normalized evidence are independent roles with repository-relative path, reparse-point, exact Int64 byte-size, and SHA-256 checks.
- Normalized frequency, z_real, and z_imag rows are retained; duplicate, inductive, and insufficient-HF diagnostics are not deleted.
- 69/69 synthetic structural and negative regressions passed; synthetic data cannot open experimental or M03B gates.
- Portable repository preflight, 259-path frozen baseline audit, and byte-prefix D0016 append-only audit passed.

## Remaining uncertainty

Every experimental value, evidence hash, replicate consensus, de-embedded resistance, conductivity comparison, uncertainty result, and transfer authorization remains unknown until controlled experimental evidence is supplied.
