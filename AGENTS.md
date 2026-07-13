# AGENTS.md — Li-NRR COMSOL Automation Contract

## Mission

Build a reproducible COMSOL 6.4 modeling system for continuous-flow lithium-mediated ammonia electrosynthesis. Every generated model must remain editable in the COMSOL Model Builder GUI.

## Primary automation route

1. Use COMSOL Java API as the default automation interface.
2. Use PowerShell for compile, batch-run, logging, and file orchestration.
3. Use LiveLink for MATLAB only when a valid license is confirmed and MATLAB adds clear value for inverse fitting, optimization, or data analysis.
4. Do not automate the COMSOL GUI through brittle mouse-coordinate clicking.

## Non-negotiable modeling rules

- Never overwrite files under `models/master/`.
- Write generated MPH files to `models/generated/`.
- Write every run to a timestamped directory under `runs/`.
- Keep units explicit in all COMSOL parameters.
- Do not use raw boundary/domain numbers in durable code. Create and use named selections.
- Keep stable tags for components, geometry, physics, studies, solvers, datasets, plots, and exports.
- Build one physical layer at a time. Do not add multiple unvalidated couplings in one change.
- Do not report a model as validated unless numerical predictions have been compared against independent experimental observations.
- Distinguish measured, literature-derived, fitted, and assumed parameters.
- Never hide solver warnings. Preserve full logs.
- Do not infer microscopic Li-NRR mechanism from a continuum fit alone.
- Any empirical FE or kinetic law must be labeled phenomenological and documented with its calibration range.
- Enforce mass, charge, dimensional, and sign-convention checks.

## Required model stages

Each stage must pass before the next begins:

1. Geometry builds.
2. Mesh builds.
3. Single physics solves.
4. Conservation checks pass.
5. Mesh independence is assessed.
6. Coupled model solves with continuation.
7. Outputs are exported.
8. Experimental comparison is documented.

## Code organization

- `src/java/`: complete model builders and model updaters.
- `src/methods/`: short API methods recorded from the GUI.
- `config/`: parameters and run configurations.
- `changes/`: user-recorded GUI changes awaiting integration.
- `tests/`: automated numerical and structural checks.
- `docs/DECISIONS.md`: modeling decisions and rejected alternatives.

Prefer short modular methods:

- `defineParameters`
- `buildGeometry`
- `createSelections`
- `assignMaterials`
- `addFlowPhysics`
- `addSpeciesTransport`
- `addElectrochemistry`
- `buildMesh`
- `createStudies`
- `createResults`
- `runChecks`
- `saveModel`

## Completion criteria for every Codex task

Before declaring completion:

1. Compile changed Java code with `comsolcompile`.
2. Run the relevant model with `comsolbatch`.
3. Confirm a new MPH or exported result exists.
4. Search the batch log for `error`, `failed`, `undefined`, `singular`, and `out of memory`.
5. Report warnings separately from fatal errors.
6. Record changed files, model assumptions, commands run, and remaining uncertainty.
7. Do not modify experimental raw data.

## Human edit synchronization

When a user changes the model in COMSOL GUI:

1. The user records exactly one logical change with Developer > Record Method.
2. Save the recorded method text in `changes/`.
3. Integrate that method into modular Java source.
4. Rebuild from a clean baseline.
5. Compare the regenerated model with the manually edited model.
6. Update `docs/DECISIONS.md`.

## Scientific priorities

The model must support falsifiable questions:

- Where is the reactor N2-transport-limited?
- Where is it Li+-transport-limited?
- Where does proton supply favor NH3 versus H2?
- How do flow rate, electrolyte thickness, pressure difference, and GDE permeability change current uniformity?
- What produces voltage growth and stability loss?
- Which parameters are identifiable from the available experiments?
- Which design changes increase NH3 production without merely moving losses outside the modeled boundary?

## Safety and research integrity

- Never fabricate missing material properties.
- Mark provisional values as provisional.
- Do not optimize against an unvalidated objective function.
- Preserve provenance for literature and experimental parameters.
- Treat ammonia quantification and isotope controls as independent experimental evidence, not simulation outputs.
