# Codex execution contract — Paper V1 visualization repair

Paste this entire file into Codex on the Windows COMSOL workstation and execute it as one controlled task.

---

You are performing **Paper V1 Visualization Repair / Model Comprehension Pass** for the LiNRR-COMSOL repository.

This is a result-only operation. It must not reopen scientific modeling.

## 0. Repository and branch

Repository root:

`F:\LiNRR_COMSOL\LiNRR_COMSOL_Codex_Starter`

Remote repository:

`Gakki040201/LiNRR-COMSOL`

Remote branch already exists:

`paper/v1-visualization-repair`

Authoritative frozen source tag:

`paper-v1-model-freeze-v1.1`

Authoritative source model:

`models/generated/LiNRR_M10A4_ionic_current_li_plating.mph`

Required source SHA256:

`FADEEA4D8ADF9E472B4B855B05E39C22EF418214C0F62FB0C1977ACA36DA667B`

Required source bytes:

`807839751`

Read first:

- `paper/v1/visualization/PLOT_INTEGRITY_AUDIT.md`
- `paper/v1/visualization/PLOT_ROLE_MATRIX.csv`
- `evidence/M10A4/result_inventory.csv`
- `evidence/M10A4/dataset_inventory.csv`
- `evidence/M10A4/selection_inventory.csv`
- `evidence/M10A4/model_tree.json`
- `src/java/LiNRR_M10A1_1_ResultRepair.java`
- `src/java/LiNRR_M10A3_RealSpeciesTransport.java`
- `src/java/LiNRR_M10A4_FinalReload.java`

## 1. Absolute prohibitions

DO NOT:

- run any Study
- run any solver sequence
- rebuild mesh
- change physics
- change boundary conditions
- change materials
- change parameter values
- change accepted numerical result tables
- change CAD
- overwrite the frozen M10A4 MPH
- force move any tag
- modify Paper V1 frozen scientific figures
- relabel generic donor as ethanol
- relabel Li-equivalent as predicted retained metallic Li
- relabel co-limitation as NH3 rate or FE
- relabel electrolyte potential as full-cell voltage
- infer SEI/Li3N/HER/HOR mechanism

No call to `study(...).run()`, `study(...).runNoGen()`, `sol(...).runAll()`, or mesh `.run()` is permitted in the visualization repair Java code.

## 2. Worktree

Fetch first:

```powershell
git -C F:\LiNRR_COMSOL\LiNRR_COMSOL_Codex_Starter fetch origin --prune --tags
```

Create or reuse:

`F:\LiNRR_COMSOL\worktrees\LiNRR_PAPER_V1_VISUALIZATION`

on branch:

`paper/v1-visualization-repair`

If the worktree already exists, verify it is on the exact branch and clean before proceeding.

Do not create another branch.

## 3. Preflight source identity

Before COMSOL is launched:

- verify branch
- verify source tag exists
- verify branch descends from `paper-v1-model-freeze-v1.1`
- verify source MPH SHA256 and bytes
- record Git HEAD
- record source MPH last-write time
- verify source MPH is not writable by the task except read access

Create run directory:

`runs\PaperV1_Visualization\<timestamp>_visualization_repair`

Create evidence directory:

`evidence\PaperV1_Visualization`

Do not store generated class files in tracked source directories after completion.

## 4. Reuse repository COMSOL execution style

COMSOL binaries currently used by this repository are under:

`F:\COMSOL64\Multiphysics\bin\win64`

Reuse:

- `comsolcompile.exe`
- `comsolbatch.exe`
- `scripts/windows/lib/Invoke-ComsolCaptured.ps1`

Use an isolated task-local COMSOL preferences directory if external file export requires it, following the existing M10A2R/M10A3 pattern. Do not modify global COMSOL preferences.

## 5. Create read-only runtime plot audit

Create:

`src/java/LiNRR_M10A4_PlotIntegrityAudit.java`

and an accompanying runtime-input class generated under the run directory.

The audit program must:

1. load the frozen M10A4 MPH;
2. never save it;
3. enumerate every existing `model.result().tags()` PlotGroup;
4. for each PlotGroup record:
   - tag
   - type
   - label
   - configured dataset tag if available
   - each child plot feature tag/type
   - expression(s) if available
   - unit(s) if available
   - whether child selection exists
   - plot run status
   - `hasWarning()`
   - warning text if API exposes it safely
   - exception class/message if run fails;
5. call `model.result(tag).run()` only — this is result rendering, not a Study solve;
6. continue auditing after a single legacy plot failure rather than aborting immediately;
7. write:

`evidence/PaperV1_Visualization/PLOT_INTEGRITY_INPUT.csv`

The audit is descriptive. Legacy failures are expected and must not trigger a solve.

Required summary:

- total PlotGroups
- runtime PASS count
- warning count
- exception count
- counts by `MAIN_TEACHING`, `MAIN_SUPPORT`, `SI_SUPPORT`, `INTERMEDIATE_SUPPORT` from `PLOT_ROLE_MATRIX.csv`

The static inventory currently has 68 result PlotGroups. If runtime count differs, report the exact delta and do not silently normalize it.

## 6. Explicit canonical-field pre-repair checks

Before creating any new visualization nodes, prove the underlying accepted fields are numerically readable without solving.

Use temporary DerivedValue numerical features and remove each after evaluation.

At minimum evaluate finite min/max/mean for:

### Real liquid flow

Dataset: `dset_liq_solution`

Component/selection:

`comp_electrolyte_flow/sel_dom_electrolyte_fluid`

Expression:

`sqrt(u^2+v^2+w^2)`

Pressure checks on inlet/outlet using `p`.

### Real N2 flow

Dataset: `dset_n2_solution`

Component/selection:

`comp_n2_flow/sel_dom_n2_channel`

Expression:

`sqrt(u2^2+v2^2+w2^2)`

Pressure checks using `p2`.

### N2 gas species

Dataset: `dset_species_n2_gas`

Selection:

`comp_species_n2_real/m10a3_sel_dom_n2_channel`

Expression:

`cN2g`

### Dissolved N2

Dataset: `dset_species_liq_n2`

Selection:

`comp_species_liq_real/m10a3_sel_dom_electrolyte_fluid`

Expression:

`cN2d`

For time-dependent solution datasets select the final stored solution level explicitly. Reuse the final-reload pattern that sets `looplevelinput` to `last` for numerical evaluations.

### Generic donor

Dataset: `dset_species_liq_n2`

Expression:

`cDonor`

### Reaction plane

Selection:

`comp_species_liq_real/m10a3_sel_bnd_electrolyte_gde_top`

Require dimension 2 and non-zero entity count.

### Frozen M10A4 fields

Use the final reload logic for:

- `cd.phil`
- `cLi_a4b`
- `cBF4_a4b`
- `j_ion_a4b_mag`
- accepted cathode current expression
- Li-equivalent expressions for 9/45/54/99/297 C
- N2-current overlap
- donor-current overlap
- four-field co-limitation
- Joule heating density

If canonical field evaluation fails, STOP. Do not repair by solving.

Output:

`evidence/PaperV1_Visualization/CANONICAL_FIELD_READABILITY.csv`

## 7. Create a separate visualization model

Create:

`src/java/LiNRR_M10A4_VisualizationRepair.java`

Input:

frozen authoritative MPH

Output:

`models/visualization/LiNRR_M10A4_VISUAL_REVIEW.mph`

Create the output directory if needed.

Never overwrite the frozen source MPH.

The source model must remain byte-identical before and after.

The visualization model may change only:

- Result PlotGroups
- Result datasets created strictly for display
- views/camera/display settings
- result exports
- labels/comments identifying presentation authority

It must not mutate scientific solution state.

## 8. Do not try to repair history in place

Keep all existing historical PlotGroups for provenance.

Add a new clean presentation layer using new tags beginning with `viz_` and labels beginning with `V00` through `V90`.

Do not delete old plots.

Do not rely on a legacy PlotGroup merely because its tag exists.

## 9. Required clean visualization groups

### V00 REACTOR

Required:

- `viz00_reactor_full`
- `viz00_reactor_exploded`
- `viz00_reaction_plane`

For full/exploded physical geometry, reuse/copy the accepted physical visualization semantics rather than rebuilding scientific geometry.

`viz00_reaction_plane` must clearly show the authoritative cathode reaction plane using:

`m10a3_sel_bnd_electrolyte_gde_top`

The label must contain:

`AUTHORITATIVE REACTION PLANE | REAL INTERFACE`

If a single PlotGroup cannot safely overlay physical-cell mesh geometry and the solution-boundary dataset across components, keep them as separate adjacent PlotGroups. Do not create fake geometry to obtain an overlay.

### V01 FLOW

Create from canonical solved datasets, not legacy result bindings:

- `viz01_liq_velocity`
- `viz01_liq_pressure`
- `viz01_liq_streamlines`
- `viz01_n2_velocity`
- `viz01_n2_pressure`
- `viz01_n2_streamlines`

Liquid velocity expression:

`sqrt(u^2+v^2+w^2)`

N2 velocity expression:

`sqrt(u2^2+v2^2+w2^2)`

Streamlines must seed from the authoritative inlet selection of the same component/dataset.

Use Slice/Surface/Streamline combinations that show the internal flow path clearly. Do not expose a meaningless exterior-only surface if it hides the interior field.

### V02 SPECIES

Create:

- `viz02_n2_gas`
- `viz02_n2_dissolved`
- `viz02_n2_reaction_plane`
- `viz02_donor`

Use canonical M10A3 datasets and the final stored time for static presentation.

The donor label must include:

`GENERIC DONOR | CALIBRATION REQUIRED`

Do not create a main-view NH3 production map.

Existing NH3 numerical-source/tracer/downstream plots belong in V90/SI.

### V03 IONICS

Create presentation copies from accepted final datasets/expressions:

- `viz03_electrolyte_potential`
- `viz03_li_concentration`
- `viz03_bf4_concentration`
- `viz03_ionic_current`

Labels must preserve:

- `NOT FULL-CELL VOLTAGE`
- `PROVISIONAL SENSITIVITY` where appropriate
- `SPECIES-FLUX IDENTITY` for ionic current

### V04 CURRENT

Create:

- `viz04_cathode_current`
- `viz04_current_nonuniformity`
- `viz04_current_crowding`

All must use the authoritative real reaction plane.

Where an accepted existing PlotGroup already contains the correct final expression, recover the expression programmatically from that result feature rather than retyping a scientific formula unnecessarily.

### V05 LI-EQUIVALENT

Create:

- `viz05_h9`
- `viz05_h45`
- `viz05_h54`
- `viz05_h99`
- `viz05_h297`

Recover each accepted expression from the frozen source PlotGroup where possible.

All labels must say:

`LI-EQUIVALENT | NUMERICAL UPPER BOUND f=1`

Do not call these real Li thickness.

### V06 CO-LIMITATION

Create:

- `viz06_n2_current`
- `viz06_donor_current`
- `viz06_colim`
- `viz06_robustness`

Labels must retain `DIAGNOSTIC ONLY`, generic-donor calibration boundary, and the accepted threshold-robustness interpretation.

### V07 ELECTRICAL

Create or preserve clean views for:

- electrolyte ohmic-drop diagnostic
- Joule-source diagnostic

Keep these supporting rather than mechanistic.

### V90 SI SUPPORT

Do not automatically rebuild every old support plot into the main clean layer.

Runtime-audit and classify:

- H2 flow/species scaffold
- liquid/N2 wall shear
- external pipe pressure
- SSC Darcy pressure/velocity
- SSC Sl/Sg
- liquid penetration/flood front
- reduced phenomenological N2 transfer
- N2 transport resistance/flux
- NH3 numerical-verification field
- tracer/RTD/downstream reduced model
- intermediate A4A/A4B diagnostics

If a support plot depends on an empty selection, mark `BLOCKED_EMPTY_SELECTION`. Do not guess a replacement.

## 10. Visualization quality rules

For all new main views:

- use consistent camera orientation for reaction-plane maps;
- use clear units;
- show color legend;
- use the same spatial orientation when comparing N2, Li+, donor, current and co-limitation;
- use the same reaction-plane framing for all surface maps;
- do not use manually edited scientific numbers in plot titles;
- make full-cell 3D plots visually distinct from reaction-plane maps;
- keep true-scale physical geometry separate from display-only exploded geometry;
- mark display-only deformation/explosion as `DISPLAY SCALE ONLY`.

Do not spend time on journal color styling yet. This pass is for scientific readability and reproducibility.

## 11. Runtime validate every new presentation plot

Create helper logic that, after building each new plot:

1. calls `model.result(tag).run()`;
2. requires `hasWarning()==false`;
3. performs a finite numerical check on the canonical dataset/expression/selection when applicable;
4. records PASS/FAIL.

Node creation alone is never PASS.

Write:

`evidence/PaperV1_Visualization/PLOT_INTEGRITY_OUTPUT.csv`

Columns should include at minimum:

`plot_tag,label,section,role,dataset,component,selection,expression,dimension,selection_count,plot_run_ok,warning,finite_min,finite_max,status,notes`

For pure mesh/display-only physical views where a scalar min/max is not meaningful, use `NOT_APPLICABLE` and require successful plot run + non-empty geometry/selection evidence.

## 12. Independent reload

After saving `LiNRR_M10A4_VISUAL_REVIEW.mph`, close/remove the model and load the output fresh in a new COMSOL model handle.

Re-run every `viz_` PlotGroup.

Require:

- every MAIN_TEACHING/MAIN_SUPPORT `viz_` plot runs
- no warning
- finite field checks repeat
- reaction-plane selection remains non-empty

Write:

`evidence/PaperV1_Visualization/VISUALIZATION_RELOAD_AUDIT.csv`

Print:

`PAPER_V1_VISUALIZATION_INDEPENDENT_RELOAD=PASS`

## 13. Scientific field invariance

Before repair and after independent reload, compare a field signature including at least:

- liquid mean/max velocity
- liquid inlet/outlet pressure
- N2 mean/max velocity
- N2 inlet/outlet pressure
- N2 gas min/max
- dissolved N2 min/max
- donor min/max
- electrolyte potential min/max
- Li+ min/max
- BF4- min/max
- ionic current min/max
- cathode current min/max
- Li-equivalent 297 C min/max
- co-limitation min/max
- Joule source min/max

Use relative tolerance only for serialization/readback numerical roundoff, target `<=1e-12` where the COMSOL API returns deterministic doubles. Exact source artifact SHA is separately required unchanged.

Write:

`evidence/PaperV1_Visualization/VISUALIZATION_FIELD_INVARIANCE.csv`

No changed scientific field is allowed.

## 14. Export comprehension images

Create:

`evidence/PaperV1_Visualization/images/`

Export at minimum:

- reactor full
- reactor exploded
- reaction plane
- liquid velocity
- liquid streamlines
- N2 velocity
- N2 streamlines
- N2 gas concentration
- dissolved N2
- generic donor
- electrolyte potential
- Li+
- cathode current
- 9 C Li-equivalent
- 297 C Li-equivalent
- N2-current overlap
- donor-current overlap
- four-field co-limitation

Do not manually retouch these images outside the script in this phase.

## 15. Build SI visualization index

Create:

`paper/v1/supplement/SI_VISUALIZATION_INDEX.csv`

Columns:

`si_section,source_stage,plot_tag_or_evidence,scientific_role,authority,main_or_si,status,notes`

Required proposed sections:

- S1 CAD/geometry audit
- S2 M01 flow verification
- S3 M02 transport verification
- S4 M03 current/Faraday verification
- S5 real-cell flow
- S6 reduced SSC support models
- S7 real neutral-species transport and RTD
- S8 ionic transport
- S9 current distribution
- S10 Li-equivalent Faraday ledger
- S11 spatial co-limitation robustness
- S12 mesh/numerical robustness
- S13 parameter provenance/calibration gaps
- S14 reproducibility/model tree

Do not place failed development logs into SI merely because they exist. Keep those as repository provenance.

## 16. Build COMSOL learning guide from this exact model

Create:

`paper/v1/visualization/COMSOL_READING_GUIDE.md`

It must teach using this repository, not a generic toy example.

At minimum explain:

`Geometry → Selection → Material → Physics → Mesh → Study → Solver → Solution → Dataset → Plot`

Then explain:

`Plot empty != Solve failed`

and the debug sequence:

1. dataset
2. component
3. solution level/time
4. expression
5. entity dimension
6. selection count
7. Derived Values finite check
8. PlotGroup run/warning

Map the clean V00–V07 groups to the Paper V1 scientific story.

## 17. PowerShell driver

Create:

`scripts/windows/Run_PaperV1_VisualizationRepair.ps1`

Reuse `Invoke-ComsolCaptured.ps1`.

The driver must:

- verify branch
- verify input hash/bytes
- generate runtime classes under run dir
- compile audit Java
- compile repair Java
- execute input runtime audit
- execute visualization repair
- execute independent reload audit
- scan logs for fatal errors
- verify frozen input SHA/bytes/mtime unchanged
- verify output MPH exists and is nonzero
- verify all required CSV/image outputs exist
- print machine-readable final markers

Do not use the COMSOL `-outputfile` mechanism to accidentally overwrite the frozen input. The Java code must explicitly load input and save only the distinct visualization output.

## 18. Static no-solve audit

Before COMSOL execution, scan the new Java files and fail if they contain executable calls matching:

- `.study(` followed by `.run`
- `.sol(` followed by `.run`
- `.runAll()` on solver objects
- `.mesh(` followed by `.run`

Allow `model.result(...).run()` because rendering is required.

Also inspect runtime logs for solver/study execution markers. If a study/solver unexpectedly executes, BLOCK and discard the visualization output.

## 19. Git policy

Do not commit the generated visualization MPH unless the repository's existing LFS policy explicitly supports it and its size is intentional. The source code, scripts, audits, small CSVs and exported evidence images are the primary Git deliverables.

Do not modify:

`models/generated/LiNRR_M10A4_ionic_current_li_plating.mph`

Commit visualization repair source/evidence only after PASS.

Suggested commit:

`Add reproducible Paper V1 COMSOL visualization layer`

Push:

`paper/v1-visualization-repair`

No merge to main in this run.

## 20. Required final markers

Print at least:

```text
SOURCE_MPH_SHA256_BEFORE=FADEEA4D8ADF9E472B4B855B05E39C22EF418214C0F62FB0C1977ACA36DA667B
SOURCE_MPH_SHA256_AFTER=FADEEA4D8ADF9E472B4B855B05E39C22EF418214C0F62FB0C1977ACA36DA667B
SOURCE_MPH_UNCHANGED=TRUE
STUDY_RUNS_TRIGGERED=0
SOLVER_RUNS_TRIGGERED=0
MESH_RUNS_TRIGGERED=0
INPUT_PLOTGROUP_COUNT=
INPUT_PLOT_RUNTIME_PASS_COUNT=
INPUT_PLOT_WARNING_COUNT=
INPUT_PLOT_EXCEPTION_COUNT=
CANONICAL_FIELD_READABILITY=PASS
REACTION_PLANE_SELECTION=PASS
VIZ_MAIN_PLOT_COUNT=
VIZ_MAIN_PLOT_RUNTIME_PASS=PASS
VIZ_MAIN_PLOT_WARNINGS=0
VISUALIZATION_FIELD_INVARIANCE=PASS
PAPER_V1_VISUALIZATION_INDEPENDENT_RELOAD=PASS
SI_VISUALIZATION_INDEX=PASS
COMSOL_READING_GUIDE=PASS
PAPER_V1_VISUALIZATION_REPAIR=PASS
```

## 21. Hard stop

After visualization repair passes and the branch is pushed, STOP.

Do not start manuscript prose in the same run.

Do not add new physics.

Do not rerun COMSOL studies.

Do not repair a blocked reduced-model plot by inventing a selection.

The next human review is visual inspection of the exported V00–V07 images and the clean `LiNRR_M10A4_VISUAL_REVIEW.mph`.

Start now with Git/hash/worktree preflight and the read-only 68-PlotGroup runtime audit.
