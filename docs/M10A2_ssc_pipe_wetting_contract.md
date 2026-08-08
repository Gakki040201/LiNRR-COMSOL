# M10A2 SSC, pipe, wetting, and gas-liquid transport contract

## Scope and lineage

M10A2 is the real-geometry application layer derived from the passed M10A1.1
result-repaired model. It does not modify the M10A1 solved MPH or either raw STEP
file. All new COMSOL components, physics, studies, datasets, plots, and selections
use stable tags and remain editable in Model Builder.

This stage contains no lithium plating, Li-NRR kinetics, SEI chemistry, or full
electrochemistry. A continuum wetting or transport result is not evidence of a
microscopic mechanism or experimental validation.

## Evidence classes

Hardware dimensions and gas operating procedures come from the local read-only
`Li-NRR实验流程-修订中(1).docx`, SHA-256
`EE9AEB5309B6D5BCA5649458DA2EE7B531D43FA50C7E6A6A1909EFFA81871EF7`.
The manual has 403 text paragraphs, no DOCX table objects, and 71 embedded images.
No OCR was used. The section `不同转速下电解液流量测试` is present, but an exact
RPM-to-flow calibration is not available as reliable structured text or a table.
Therefore `Q_liquid_lab` is blank and `CALIBRATION_REQUIRED`; 0.5, 1, 2, and
5 cm3/min are numerical sensitivity cases only.

The 30 um SSC thickness is `LITERATURE_SAME_PLATFORM`, sourced from the same
25 cm2 DTU platform family recorded in
`docs/M10A0_public_cell_geometry_contract.md`. It is not a lab measurement.
Permeability, porosity, contact angle, capillary entry pressure, tortuosity,
electrolyte Henry data, electrolyte diffusivity, fluid properties, initial
saturation, and the far-field sink remain provisional or calibration-required.

## Geometry and topology

- The cathode is an explicit 316L SSC thin domain and the anode is an explicit
  PtAu/316L SSC thin domain. SSC is not represented only as a reaction boundary.
- The Manual cut is 60 mm by 60 mm (3600 mm2). The real-CAD N2/SSC flow-field
  footprint is derived as 2474.04981995 mm2; the open-channel interface is
  1278.22088213 mm2. No 36 cm2 electrochemical active area is assumed.
- A zero-thickness 75 mm outer / 55 mm inner gasket mask closes the hydraulic
  footprint without inventing gasket compression thickness.
- PFA tube IDs control hydraulics. ODs are visualization-only. PEEK is limited to
  documented fittings/connectors. Undocumented route bends are schematic only.
- Pipe Flow is licensed and is used for documented external PFA segments. Unknown
  gas-inlet and latex lengths are explicitly excluded from the pressure total.

## Physics sequence and limitations

M10A2a solves real-CAD N2, H2, and electrolyte flow plus 1D external Pipe Flow.
Gas baselines are 50 mL/min; startup is retained as an optional 20 mL/min for
20 min case. The saved liquid case is 1 cm3/min and is not an experimental value.
M10A1 used a 10 mL/min numerical N2 test, so the M10A1/M10A2 pressure-drop table
records both flows and does not attribute the full pressure difference to the
new SSC and pipe representations.

M10A2b uses a homogenized Darcy SSC layer and a provisional permeability sweep
from 1e-13 to 1e-9 m2 under an imposed 15 mbar comparison pressure. The resulting
high velocities at the upper sweep values signal loss of Darcy-model applicability;
they are sensitivity bounds, not predictions. Permeability is never inferred from
mesh count or nominal pore size.

M10A2c uses the licensed Phase Transport in Porous Media interface coupled to
Darcy flow. Brooks-Corey capillarity, `k_r = Sl^2`, and a 0--5 ms transient are
explicit provisional constitutive choices. The 0, 5, 10, 15, 20, and 30 mbar
sweep is not a measured pressure history. No breakthrough threshold is reached in
the audited time window using the provisional `Sl >= 0.5` gas-side diagnostic,
which is not proof that breakthrough cannot occur later.

M10A2d solves dissolved N2 separately from gas N2 in a homogenized SSC/electrolyte
slab. The Henry boundary, effective diffusivity, and well-mixed far-field sink are
phenomenological sensitivity assumptions. There is no consumption reaction.

## Acceptance and reproducibility

The formal builder must compile with `comsolcompile`, run with `comsolbatch`, save
`models/generated/LiNRR_M10A2_ssc_pipe_wetting.mph`, reopen it independently, run
all 18 required GUI result nodes, pass mass/N-atom conservation checks, and scan
all logs for fatal patterns and warnings. The build script verifies before and
after hashes for the immutable M10A1 MPH and both STEP inputs.

Raw experimental evidence remains outside Git. Passing M10A2 establishes a
numerical baseline only and does not pass the M03A.5/M03A.6 experimental gate.
