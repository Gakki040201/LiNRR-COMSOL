# M10A2R manual reconciliation contract

## Scope

M10A2R is a geometry-truth, visualization-truth, and archival derivative of the accepted M10A2 model. It loads `LiNRR_M10A2_ssc_pipe_wetting.mph` read-only, preserves all existing equations and solved fields, and saves `LiNRR_M10A2R_manual_reconciled.mph` as a new editable COMSOL 6.4 model. It adds no M10A3 species physics.

## Manual truth

The primary Manual is archived byte-for-byte with SHA-256 `EE9AEB5309B6D5BCA5649458DA2EE7B531D43FA50C7E6A6A1909EFFA81871EF7`. Gas tubing is PFA ID 2.0 mm / OD 3.0 mm. Liquid tubing is PFA ID 1.5 mm / OD 3.0 mm. The two approximately 20 cm gas outlet tubes are external hydraulic segments, not physical routes through the cell. Gas and liquid connections use bottom inlet/top outlet. N2 is on the negative/cathode/SSC side; H2 is on the positive/PtAuSSC side.

The Manual names PEEK reverse-cone fittings but gives no cone angle, cone length, thread pitch, internal bore geometry, or compression deformation. M10A2R therefore creates no PEEK fitting or conical internal fluid domain. The real-CAD flared port profile is retained as `REAL_CAD` and is not relabeled as PEEK.

## Physical and reduced representations

`comp_cell_physical` is the physical assembly context. Its current collectors and chamber are strict imports of the unchanged STEP inputs. Cathode 316L 500-mesh SSC and anode PtAu/316L SSC are separate 60 mm × 60 mm, 30 um domains; 30 um is `LITERATURE_SAME_PLATFORM`, not `LAB_MEASURED`. Two gasket display frames use a schematic display thickness because compressed thickness remains unknown. Four short gas stubs are hollow PFA OD 3 mm / ID 2 mm. `L_gas_stub_visual=15 mm` is `SCHEMATIC_VISUAL_ONLY`, `VISUALIZATION ONLY`, and has `NO HYDRAULIC ROLE`.

The existing `comp_pipe_*` components remain 1D external hydraulic networks. They are not physical routing. `comp_ssc_darcy` and `comp_ssc_wetting` remain reduced through-plane rectangles. `comp_n2_transfer` remains reduced and phenomenological. No constant replicated field is described as 3D-resolved wetting; mapping is omitted unless a stable COMSOL mapping can be verified.

## Numerical invariance

Accepted M10A2 scalar metrics are frozen in `config/M10A2R_numerical_baseline.csv`. M10A2R reads the unchanged saved values from the input MPH. Every requested metric must have relative difference no greater than `1e-10`. Any real fluid-domain change would invalidate this route and require a separately explained solve; none is made here.

## Acceptance

Acceptance requires Java compilation, a timestamped COMSOL batch run, new MPH existence, full log preservation and fatal-pattern scan, physical/reduced/result independent reload markers, inventory and model-tree exports, all nine PNG exports, original-input hash invariance, and structural checks. Passing is numerical/structural acceptance only, not experimental validation.
