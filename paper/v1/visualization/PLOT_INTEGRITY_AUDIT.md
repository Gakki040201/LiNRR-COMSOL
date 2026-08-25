# Paper V1 Plot Integrity Audit

## Scope

This audit is a static repository-level audit of the frozen Paper V1 model visualization layer. It does **not** run COMSOL and therefore distinguishes repository evidence from runtime GUI evidence.

Authoritative scientific model:

- `models/generated/LiNRR_M10A4_ionic_current_li_plating.mph`
- SHA256 `FADEEA4D8ADF9E472B4B855B05E39C22EF418214C0F62FB0C1977ACA36DA667B`
- frozen scientific base: `paper-v1-model-freeze-v1.1`

No scientific model, parameter, accepted numerical result, CAD, physics, mesh, study, or solver is to be changed by the visualization repair.

## Static evidence inspected

- `evidence/M10A4/result_inventory.csv`
- `evidence/M10A4/dataset_inventory.csv`
- `evidence/M10A4/selection_inventory.csv`
- `evidence/M10A4/model_tree.json`
- `src/java/LiNRR_M10A1_RealCAD_Flow.java`
- `src/java/LiNRR_M10A1_1_ResultRepair.java`
- `src/java/LiNRR_M10A3_RealSpeciesTransport.java`
- `src/java/LiNRR_M10A4_FinalAssembly.java`
- `src/java/LiNRR_M10A4_FinalReload.java`
- `scripts/windows/29_build_M10A1_1_result_repair.ps1`
- `scripts/windows/30R_build_M10A2R_manual_reconciliation.ps1`
- `scripts/windows/34_build_M10A3_real_species_transport.ps1`

## Main finding

The scientific freeze and the GUI integrity are different acceptance layers.

The final M10A4 model contains 68 PlotGroups, but `LiNRR_M10A4_FinalReload.java` only hard-runs a restricted Paper-V1-critical subset. Therefore a final-model PASS does not imply that every historical M10A1/M10A2/M10A3 PlotGroup remains warning-free or correctly rebound in the final integrated MPH.

This is consistent with the current user-observed final-MPH behavior: legacy flow/species/support nodes can show `Plot empty` while the accepted M10A4 electrochemical plots still render.

## Finding P1 — legacy flow plots are not covered by the final M10A4 reload contract

The historical M10A1.1 result-only repair explicitly ran and required warning-free reload for:

- `pg_liq_velocity`
- `pg_liq_pressure`
- `pg_liq_streamlines`
- `pg_liq_wall_shear`
- `pg_n2_velocity`
- `pg_n2_pressure`
- `pg_n2_streamlines`
- `pg_n2_wall_shear`

It also checked solved-field invariance.

However M10A3 is built from `LiNRR_M10A2R_manual_reconciled.mph`, and the M10A2R acceptance contract requires physical/reduced-view reload but does not re-run the eight M10A1.1 flow PlotGroups as a final integration gate. The final M10A4 reload also does not include them.

Conclusion: the flow solution/datasets can remain valid while the final-MPH legacy PlotGroup bindings are no longer guaranteed by acceptance.

Repair policy: do **not** recompute flow. Rebuild a new presentation-layer flow view from canonical datasets and selections, then independently reload and run the new views.

Canonical flow resources already present:

- liquid dataset: `dset_liq_solution`
- liquid domain: `comp_electrolyte_flow/sel_dom_electrolyte_fluid` (count 1)
- liquid inlet/outlet: count 1 each
- N2 dataset: `dset_n2_solution`
- N2 domain: `comp_n2_flow/sel_dom_n2_channel` (count 1)
- N2 inlet/outlet/interface: count 1 each

## Finding P1 — M10A3 species fields exist, but their GUI plots are not final-reload-gated

`LiNRR_M10A3_RealSpeciesTransport.java` creates canonical real-cell species plots and datasets including:

- gas N2: `dset_species_n2_gas`, expression `cN2g`
- dissolved N2: `dset_species_liq_n2`, expression `cN2d`
- reaction-plane N2 flux/uniformity on `m10a3_sel_bnd_electrolyte_gde_top`
- generic proton donor: `dset_species_liq_n2`, expression `cDonor`
- numerical-verification-only NH3: `dset_species_liq_nh3`, expression `cNH3`

The final model inventory still contains these datasets. The final selection inventory also shows non-empty copied M10A3 selections for the gas domains, liquid domain, inlet/outlet and reaction plane.

But the final M10A4 reload contract does not run the M10A3 species PlotGroups. Therefore their current GUI runtime integrity is not established by the Paper V1 freeze.

Repair policy: rebuild presentation species views from canonical datasets; keep NH3 numerical-source visualization in SI/support only.

## Finding P1 — reduced M10A2 support plots must be separated from the main physical story

The final model contains reduced-model result groups for external pipe pressure, SSC Darcy flow, wetting/saturation, flooding and phenomenological N2 transfer. These are not the same spatial authority as the real 3D cell.

The selection inventory contains two explicitly empty legacy selections in `comp_n2_transfer`:

- `sel_n2_ssc`, dimension `-1`, count `0`
- `sel_n2_liq`, dimension `-1`, count `0`

This is a concrete reason not to treat all reduced-model PlotGroups as main-text-ready views without runtime reconstruction.

Repair policy: reduced models are `SI_SUPPORT` by default. They must be runtime-audited individually. Any plot that depends on an empty selection is `BLOCKED` until a source-authoritative replacement selection is demonstrated; do not invent geometry or silently substitute another selection.

## Finding P1 — reaction plane is numerically authoritative but visually under-emphasized

The final model has a non-empty authoritative cathode reaction-plane selection:

`comp_species_liq_real/m10a3_sel_bnd_electrolyte_gde_top`

with dimension 2 and count 1.

M10A4 current, Li-equivalent and spatial co-limitation diagnostics are evaluated on this real interface. The visualization layer should therefore include a dedicated, clearly labelled reaction-plane view before the current/Li-equivalent/co-limitation views.

## Finding P2 — result labels are not a runtime integrity test

`LiNRR_M10A4_FinalAssembly.java` relabels the major M10A4 result nodes into sections 00/03/04/05/06/07. This improves semantics but does not prove that every PlotGroup can run.

A repaired presentation model must require both:

1. `model.result(plotTag).run()` succeeds without warning; and
2. a finite numerical min/max/mean check succeeds on the same canonical dataset/expression/selection where applicable.

Node existence alone is not PASS.

## Finding P2 — the Results tree mixes scientific authority levels

The final tree mixes:

- real physical geometry
- historical M10A1 real flow
- M10A2 reduced engineering/support models
- M10A3 real species transport
- intermediate A4A/A4B support plots
- accepted A4C/A4D/A4E Paper V1 diagnostics

A learning/presentation model should not delete the historical nodes. Instead it should add a clean presentation layer and classify old nodes as main, SI/support, or archive/intermediate.

## Accepted final-M10A4 runtime-validated subset

The final reload explicitly runs these PlotGroups:

- `pg00_physical_cell`
- `pg_a4a_electrolyte_potential`
- `pg_a4b_li_conc`
- `pg_a4b_bf4_conc`
- `pg_a4b_ionic_current_mag`
- `pg_a4c_cathode_current`
- `pg_a4c_anode_current`
- `pg_a4d_h9`
- `pg_a4d_h45`
- `pg_a4d_h54`
- `pg_a4d_h99`
- `pg_a4d_h297`
- `pg_a4e_n2_current`
- `pg_a4e_donor_current`
- `pg_a4e_colim`
- `pg_a4loss_joule`

The same reload separately checks finite values for electrolyte potential, Li+, BF4-, ionic current, cathode/anode current, all five Li-equivalent charge states, N2-current overlap, donor-current overlap, co-limitation and Joule-heating density.

This subset remains the strongest runtime evidence in the frozen final MPH.

## Presentation-layer target

Create a separate output model, never overwrite the frozen MPH:

`LiNRR_M10A4_VISUAL_REVIEW.mph`

Recommended clean result story:

- `V00 | REACTOR | Physical cell`
- `V00 | REACTOR | Exploded cell`
- `V00 | REACTOR | Authoritative reaction plane`
- `V01 | FLOW | Electrolyte velocity + streamlines`
- `V01 | FLOW | Electrolyte pressure`
- `V01 | FLOW | N2 velocity + streamlines`
- `V01 | FLOW | N2 pressure`
- `V02 | SPECIES | N2 gas`
- `V02 | SPECIES | Dissolved N2`
- `V02 | SPECIES | N2 at reaction plane`
- `V02 | SPECIES | Generic proton donor`
- `V03 | IONICS | Electrolyte potential`
- `V03 | IONICS | Li+`
- `V03 | IONICS | BF4-`
- `V03 | IONICS | Ionic current density`
- `V04 | CURRENT | Cathode current density`
- `V04 | CURRENT | Current nonuniformity`
- `V04 | CURRENT | Current crowding`
- `V05 | LI-EQUIVALENT | 9 C`
- `V05 | LI-EQUIVALENT | 45 C`
- `V05 | LI-EQUIVALENT | 54 C`
- `V05 | LI-EQUIVALENT | 99 C`
- `V05 | LI-EQUIVALENT | 297 C`
- `V06 | CO-LIMITATION | N2-current overlap`
- `V06 | CO-LIMITATION | donor-current overlap`
- `V06 | CO-LIMITATION | four-field diagnostic`
- `V06 | CO-LIMITATION | threshold robustness`
- `V07 | ELECTRICAL | ohmic-drop diagnostic`
- `V07 | ELECTRICAL | Joule source diagnostic`
- `V90 | SI SUPPORT | ...`

## Main/SI/archive policy

### Main/teaching priority

Physical reactor, authoritative reaction plane, liquid/N2 flow, N2 transport, generic donor, Li+/BF4-/ionic current, cathode-current distribution, charge-indexed Li-equivalent fields, spatial co-limitation.

### SI/support

H2 flow/species scaffold, wall shear, N2 transfer flux/resistance, external 1D pipe models, SSC Darcy/wetting/saturation/flooding, RTD, numerical NH3 transport scaffold, detailed electrical-loss plots, mesh/verification plots.

### Archive/intermediate

Redundant A4A/A4B plots that are superseded by accepted A4C/A4D/A4E presentation results, failed development attempts, temporary checkpoints and diagnostic-only builder outputs not needed in SI.

## Runtime acceptance required on Windows/COMSOL

The repository-level audit cannot certify runtime GUI integrity. The Windows repair must produce:

- `PLOT_INTEGRITY_INPUT.csv`: all 68 existing result groups, run/warning/exception status
- `PLOT_INTEGRITY_OUTPUT.csv`: all new `Vxx` plots
- `VIZ_RESULT_INVENTORY.csv`
- `SI_VISUALIZATION_INDEX.csv`
- independent reload PASS
- frozen input SHA unchanged before/after
- no Study/Solver run
- no accepted field value change

Every `MAIN` presentation plot must satisfy:

`plot.run PASS + no warning + finite canonical-field check + non-empty source selection`.

## Scientific boundary

Visualization repair must not convert a support model into a stronger scientific claim.

In particular:

- generic donor stays generic donor
- Li-equivalent stays a numerical Faradaic upper-bound/sensitivity representation
- co-limitation stays diagnostic
- NH3 prescribed numerical source stays numerical-verification-only
- reduced SSC transport stays reduced/phenomenological
- electrolyte potential is not full-cell voltage
- Joule source has no thermal feedback

## Audit verdict

`REMOTE_STATIC_PLOT_INTEGRITY_AUDIT=PASS_WITH_RUNTIME_REPAIR_REQUIRED`

The frozen science is not reopened. The next task is a result-only visualization repair and runtime integrity audit on Windows/COMSOL.