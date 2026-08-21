# M10A4 Ionic Current and Li-Equivalent Deposition Contract

## Scope and ancestry

M10A4 continues the authoritative PRE-A4 tree at commit `1e89f18cf28bb39abf767e1d03a76bb9be55a470`. The accepted M10A3 artifact and both STEP sources retain their frozen SHA256 values. Real liquid flow is reused from `sol20`; no uniform-velocity replacement or complete M10A3 rerun is used. Durable physics and postprocessing refer to named real-CAD selections.

This model is numerical verification and diagnostic infrastructure. It is **not** a Li-NRR kinetic model, does not predict Faradaic efficiency, does not predict real retained metallic-Li thickness, and does not model SEI, Li3N, HER, HOR, Butler-Volmer reactions, full-cell voltage, Heat Transfer, or thermal feedback.

## A4A — primary current distribution

The real electrolyte domain uses Primary Current Distribution with `kappa_M10A4_nominal=0.3 S/m`. Conductivity is `PROVISIONAL_SENSITIVITY`, not experimentally calibrated and not fitted to the Manual `<=2.5 ohm` plausibility statement. The accepted currents are approximately `+0.03844 A` at the electrolyte-outward cathode and `-0.03844 A` at the anode, with relative closure `1.8051e-15`. The resulting `11.0169921792010 ohm` electrolyte resistance is a `DERIVED_DIAGNOSTIC`, not full-cell resistance or voltage.

## A4B — reduced electroneutral binary-ion transport

A4B solves one positive common salt field, `c_salt=c_salt_bulk_a4b*exp(zSalt_a4b)`, and reconstructs

`N_Li=N_salt+t_plus*i_A4A/F`

`N_BF4=N_salt-(1-t_plus)*i_A4A/F`.

Thus `cLi=cBF4` and `F*(N_Li-N_BF4)=i_A4A` hold identically. Boundary ledgers use conservative imposed fluxes. `D_salt_a4b_eff=1e-8 m^2/s` and `t_plus_a4b=0.5` remain `PROVISIONAL_SENSITIVITY / CALIBRATION_REQUIRED`. There is no clipping or hidden concentration replacement. Accepted Li and BF4 residuals are about `2.0304e-9`, charge closure is `1.8051e-15`, electroneutrality is exact, and the raw common concentration is `957.945` to `5622.117 mol/m^3`.

## A4C — current distribution

The authoritative reaction plane is named selection `m10a3_sel_bnd_electrolyte_gde_top`, one real-CAD boundary of area `0.003844 m^2` (`3844 mm^2`). It is not the nominal `60 mm x 60 mm` SSC cut. The electrolyte-outward current and conventional cathodic current retain an explicit sign transform. Nonuniformity uses an explicitly derived surface-current magnitude after the sign audit, never `abs()` as a repair.

Mean, extrema, standard deviation, CV, and P10/P50/P90 use COMSOL surface quadrature. Weighted quantiles are obtained by bisection of area-integrated indicator CDFs; weights are positive and sum to the audited surface area. Regional comparisons are `SPATIAL_ASSOCIATION / DIAGNOSTIC_ONLY`, not causal claims.

## A4D — Li-equivalent Faraday scaffold

The deposition sign chain is explicit: conventional cathodic-negative current gives positive `-j_Li_equiv/F` and positive equivalent thickness rate. Charge checkpoints are `9, 45, 54, 99, 297 C`; current partitions are `0.25, 0.50, 0.75, 1.00`. The `f=1` case is a `NUMERICAL_UPPER_BOUND`; lower fractions are `CURRENT_PARTITION_SENSITIVITY`. Ledgers close total electron charge against `Q` and Li-equivalent charge against `f_Li_current*Q`. These fields are Li-equivalent only and are not retained-metallic-Li predictions.

## A4E — frozen-field spatial diagnostics

The accepted M10A3 dissolved-N2 and generic-donor fields, A4B Li+ field, and A4C current magnitude are collocated on the same component, geometry, dataset ancestry, and reaction-plane selection with full coverage and no extrapolation. The donor remains generic; it is not retroactively renamed ethanol. Area-weighted percentile thresholds `q={0.40,0.50,0.60}` form an exhaustive category partition with explicit `MIXED_UNCLASSIFIED`. Outputs are `SPATIAL_CO_LIMITATION_DIAGNOSTIC`, not rate, FE, selectivity, reaction probability, or mechanistic proof.

## Electrical loss, mesh, and reload

`q_ohmic=i dot E` and integrated Joule source are `DERIVED_ELECTRICAL_SOURCE / NO_THERMAL_FEEDBACK`. No temperature equation is solved.

The immutable warning-free medium model is the final model. A separate 85,016-element coarse diagnostic retains all 89 boundary refinements and has `MESH_MAX_KEY_DIFFERENCE=0.0621400622644018`, limited by `colim_MIXED_UNCLASSIFIED`; this is `PASS_WITH_LIMITATION` under the frozen 5–10% gate. Its 630 repeated inverted high-order-element warnings at one coordinate plus one low-quality warning are preserved as a limitation and are not hidden. The final medium model does not inherit those warnings.

A fresh COMSOL process independently reloaded the final MPH, ran every required stored result, evaluated finite final-state fields, and invoked no study. The reload and its fatal/warning scan pass. To satisfy the remote 2 GiB LFS object limit, the published artifact retains full `sol15`, stationary `sol19`/`sol20`, and the final state of `sol21`; other solver caches are cleared only in a separately created compact artifact. The immutable 3.3 GB pre-audit checkpoint remains untouched. The compact official path was independently reloaded again before acceptance. Parameter provenance remains explicit; none of the provisional transport, conductivity, current-partition, or unresolved electrochemical inputs is promoted to experimental validation.

## Authoritative artifacts

- Final model: `models/generated/LiNRR_M10A4_ionic_current_li_plating.mph`
- Final SHA256: `FADEEA4D8ADF9E472B4B855B05E39C22EF418214C0F62FB0C1977ACA36DA667B`
- Immutable pre-audit checkpoint: `runs/M10A4/20260820_121258/checkpoint_A4_final_pre_audit.mph`
- Acceptance evidence: `evidence/M10A4/`
