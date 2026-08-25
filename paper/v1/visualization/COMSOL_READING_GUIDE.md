# Paper V1 COMSOL visualization reading guide

## Scientific identity and scope

`models/generated/LiNRR_M10A4_ionic_current_li_plating.mph` is the authoritative compact Paper V1 scientific artifact (SHA256 `FADEEA4D8ADF9E472B4B855B05E39C22EF418214C0F62FB0C1977ACA36DA667B`, 807839751 bytes). `models/visualization/LiNRR_M10A4_VISUAL_REVIEW.mph` is a result-only presentation layer created from it. The visualization pass did not run a study, solver, or mesh and did not change parameters, physics, boundary conditions, CAD, or accepted tables.

The compacting operation intentionally retained full `sol15`, stationary `sol19`, stationary `sol20`, and the final state of `sol21`, while clearing non-required historical caches. Consequently, tag existence alone does not establish dataset readability. The historical `dset_liq_solution` (`sol3`) is preserved but empty after compaction; accepted real liquid flow is routed through `dset_a4b_flow_repair` (`sol20`) on `comp_species_liq_real`. The authoritative reaction plane is `comp_species_liq_real/m10a3_sel_bnd_electrolyte_gde_top` and is a REAL INTERFACE.

## Presentation tree

- `V00 | REACTOR`: physical cell, exploded SSC/GDE location, and authoritative reaction plane. The exploded geometry is labelled DISPLAY SCALE ONLY.
- `V01 | FLOW`: retained liquid velocity, pressure, and inlet-seeded streamlines. N2 flow is an external accepted-source export described below.
- `V02 | SPECIES`: N2 gas concentration, dissolved N2, reaction-plane N2, and GENERIC DONOR.
- `V03 | IONICS`: electrolyte potential, Li+, BF4-, and ionic-current magnitude.
- `V04 | CURRENT`: accepted cathode-current distribution and spatial diagnostics.
- `V05 | LI-EQUIVALENT`: 9/45/54/99/297 C numerical Li-equivalent upper bounds (`f=1`). These are not retained metallic Li predictions.
- `V06 | CO-LIMITATION`: N2-current, generic-donor-current, four-field, and threshold-robustness diagnostics. These are not NH3 rate or Faradaic efficiency.
- `V07 | ELECTRICAL`: electrolyte ohmic-drop and Joule-source diagnostics. Electrolyte potential is not full-cell voltage; Joule source has no thermal feedback.
- `V90 | SI SUPPORT`: the audited legacy/support tree remains available in the source model and is indexed separately.

All new reaction-plane maps use the same dataset/component/selection boundary and inherit a common accepted reaction-plane view where the API exposes it.

## N2-flow source boundary

The compact 807 MB M10A4 artifact does not retain a scientifically usable N2 velocity/pressure cache: `dset_n2_solution` points to cleared `sol4`, while zero-valued inactive-component states attached to retained solution tags are not accepted as N2 flow. No solve was used to reconstruct it.

The N2 velocity, pressure, and streamline PNGs are exported independently from the immutable accepted pre-compaction M10A4 checkpoint:

- source: `runs/M10A4/20260820_121258/checkpoint_A4_final_pre_audit.mph` in the M10A4 integration worktree;
- SHA256: `E51EFEBA7AD3DF67FE0759D4A279A6C7B82758508E58D519EE7658888E321887`;
- classification: `ACCEPTED_PRECOMPACTION_VISUALIZATION_SOURCE`;
- provenance: referenced by `docs/M10A4_ionic_current_li_plating_contract.md` and used as the input to `LiNRR_M10A4_FinalCompact.java`.

These three fields are not embedded into `LiNRR_M10A4_VISUAL_REVIEW.mph`, because COMSOL cannot safely make a cross-model Solution dataset without changing stored solution state. The compact M10A4 remains the scientific identity authority. See `ACCEPTED_FALLBACK_SOURCE_ROUTING.csv` and `PLOT_INTEGRITY_FALLBACK.csv` for exact field ranges and provenance.

## Audit trail

Read the audit files in this order:

1. `PLOT_INTEGRITY_INPUT.csv`: runtime result-only audit of all 68 original PlotGroups.
2. `RETAINED_SOLUTION_ROUTING.csv`: actual loaded-MPH dataset/solution properties and non-empty Derived Value tests.
3. `ACCEPTED_FALLBACK_SOURCE_ROUTING.csv`: accepted external N2-flow provenance and numerical consistency.
4. `CANONICAL_FIELD_READABILITY.csv`: corrected effective routing for every Paper V1 field; the earlier failed legacy-liquid observation is retained in notes.
5. `PLOT_INTEGRITY_OUTPUT.csv`: 28 compact-MPH presentation plots plus three explicitly external N2-flow plots.
6. `VISUALIZATION_RELOAD_AUDIT.csv`: fresh-load rerun of all visualization PlotGroups embedded in `VISUAL_REVIEW.mph`.
7. `VISUALIZATION_FIELD_INVARIANCE.csv`: before/after min/max/mean agreement for compact-source scientific fields.

The labels GENERIC DONOR, LI-EQUIVALENT, CO-LIMITATION DIAGNOSTIC, and ELECTROLYTE POTENTIAL are deliberate scientific limitations and must not be strengthened in downstream captions.
