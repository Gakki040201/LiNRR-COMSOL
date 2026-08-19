# M03A.3 prescribed-current stoichiometric coupling verification

- Baseline commit: e9695b08441cac3f8f82847ab96c4ab5dfeda4c8
- COMSOL version: COMSOL Multiphysics 6.4.0.293; installation audited at F:\COMSOL64\Multiphysics.
- Java compile: PASS (comsolcompile plus isolated ECJ class output).
- MPH build and independent reload: PASS; SHA-256 $MphHash.
- Model state: synthetic numerical smoke test; no experimental calibration.

## Geometry and 2D/3D area

The frozen 2D electrode boundary length is Lcell; Wcell is the out-of-plane thickness. The 3D equivalent current-carrying area is Aeq_M033=Lcell*Wcell. A 2D line length is not treated as square-metre area. Named selections were retained and audited without durable raw entity numbers.

## Current sign and physics tree

Primary Current Distribution uses an upper prescribed-current boundary and lower zero-potential boundary. Raw signed outward-normal current is integrated on every boundary. The cathode signed current is positive before it is assigned to j_cathodic_positive; no bs() is used. Current conservation, fine analytical current error, and fine resistance error are recorded in the formal CSVs. Independent reload inspected actual physics and feature types; no prohibited physics-tree entry exists.

## Flow, species, and nitrogen conservation

FE=0 frozen-model invariance passed for M01.2 flow metrics and M02.2 conservative N2/NH3 transport metrics. Flow relative differences are at most 1e-6 and transport relative differences at most 1e-4. The coupled fine grid passes current, N2, NH3 and nitrogen-atom conservation. Single-species checks are independent; nitrogen closure is not used to conceal either species error.

## Ohmic analytical comparison and mesh convergence

Coarse, medium and fine mapped meshes were retained. The rectangular uniform-conductivity result is compared with I=kappa*Aeq*DeltaPhi/Hcell and R=Hcell/(kappa*Aeq). Exact linear finite-element results are classified EXACT_POLYNOMIAL_REPRESENTATION; no convergence order is fabricated. The coupled medium-to-fine changes in current, N2 consumption, NH3 production and outlet species rates are all below 0.5%.

## Faraday current-to-flux closure

Three independent routes were used: current-boundary integration, species-boundary total-flux integration, and inlet/outlet species balances. N2 demand uses FE_prescribed*I_cath/(6F) and NH3 generation uses FE_prescribed*I_cath/(3F). Fine current-to-N2, current-to-NH3 and 2:1 stoichiometric relative errors pass 1e-6. FE_prescribed is prescribed synthetic input and is not predicted.

## Linearity and one-way decoupling

Conductivity and potential scans use multipliers 0.1, 0.3, 1, 3 and 10; each current fit has R2 at least 0.999999. FE scans retain 0, 0.1, 0.5 and 1.0 and demonstrate linear N2/NH3 mapping. FE=0 has zero cathode species reaction flux while current remains nonzero and transport returns to the uncoupled baseline. Flow changes do not change constant-conductivity ohmic current beyond 1e-6 relative; applied-potential changes do not change flow beyond 1e-6 relative.

## 25-point current-flow map

All 25 configured current-density/flow pairs are retained: PASS=3, WARNING=0, failed or outside applicability=22. Every warning and failure remains in M03A_3_current_flow_map.csv with solver status, scientific status and reason. Theta>1 is classified outside applicability because prescribed demand exceeds inlet N2 capacity; it is not a physical limiting-current or performance claim.

| j (A/m2) | Umean (m/s) | Theta | min cN2 | N2 err | NH3 err | N err | status | reason |
|--:|--:|--:|--:|--:|--:|--:|:--|:--|
| 0.100000000000 | 1.00000000000e-05 | 0.0475332788588 | 4.19185544705 | 0.00406381200607 | 0.0194527140235 | 0.00311890511772 | FAILED_CONSERVATION | one_or_more_current_species_or_nitrogen_balance_exceeds_threshold |
| 0.100000000000 | 3.00000000000e-05 | 0.0166233480541 | -0.972754998122 | 0.0403287242622 | 0.376120588360 | 0.0336967815484 | FAILED_CONSERVATION | one_or_more_current_species_or_nitrogen_balance_exceeds_threshold |
| 0.100000000000 | 0.000300000000000 | 0.00158342997819 | 4.91351163663 | 2.40377393439e-07 | 0.000152211476741 | 6.65005919984e-10 | FAILED_CONSERVATION | one_or_more_current_species_or_nitrogen_balance_exceeds_threshold |
| 0.100000000000 | 0.00100000000000 | 0.000474486354976 | -23.3499564932 | 0.00563296060897 | 0.742155877501 | 0.00477243712192 | FAILED_CONSERVATION | one_or_more_current_species_or_nitrogen_balance_exceeds_threshold |
| 1.00000000000 | 1.00000000000e-05 | 0.475123983729 | 1.35311393998 | 0.000196293373757 | 0.000322351835972 | 4.31351265384e-05 | FAILED_CONSERVATION | one_or_more_current_species_or_nitrogen_balance_exceeds_threshold |
| 1.00000000000 | 0.000300000000000 | 0.0158342539218 | 4.13595819378 | 5.51162651599e-06 | 0.000250854228257 | 1.53890361377e-06 | FAILED_CONSERVATION | one_or_more_current_species_or_nitrogen_balance_exceeds_threshold |
| 1.00000000000 | 0.00100000000000 | 0.00475028924040 | 4.43784953526 | 7.87775201983e-07 | 0.000166849790314 | 4.90523322443e-09 | FAILED_CONSERVATION | one_or_more_current_species_or_nitrogen_balance_exceeds_threshold |
| 10.0000000000 | 1.00000000000e-05 | 4.75251483660 | -31.4672322843 | 9.82766409643e-05 | 9.82729670274e-05 | 3.64808570117e-09 | OUTSIDE_MODEL_APPLICABILITY | prescribed demand exceeds inlet N2 capacity |
| 10.0000000000 | 3.00000000000e-05 | 1.58443810987 | -15.4636067561 | 0.000399051005211 | 0.000384733849789 | 1.43160633705e-05 | OUTSIDE_MODEL_APPLICABILITY | prescribed demand exceeds inlet N2 capacity |
| 10.0000000000 | 0.000100000000000 | 0.475143068256 | -7.88218728036 | 0.000239924116945 | 0.000503020446551 | 9.17363050907e-07 | FAILED_CONSERVATION | one_or_more_current_species_or_nitrogen_balance_exceeds_threshold |
| 10.0000000000 | 0.000300000000000 | 0.158341063545 | -3.68445287033 | 1.31636158254e-05 | 6.30807723880e-05 | 3.17530218052e-06 | FAILED_NEGATIVE_CONCENTRATION | unclipped_negative_N2_concentration |
| 10.0000000000 | 0.00100000000000 | 0.0475182814527 | -0.0145985242477 | 0.000447528001152 | 0.000559180840833 | 0.000420946009822 | FAILED_CONSERVATION | one_or_more_current_species_or_nitrogen_balance_exceeds_threshold |
| 100.000000000 | 1.00000000000e-05 | 48.1621444377 | -359.733543337 | 0.000266755364670 | 0.000266832890583 | 8.32292131265e-08 | OUTSIDE_MODEL_APPLICABILITY | prescribed demand exceeds inlet N2 capacity |
| 100.000000000 | 3.00000000000e-05 | 15.8353090014 | -199.637913659 | 9.13258494716e-06 | 9.10998569789e-06 | 2.24790589010e-08 | OUTSIDE_MODEL_APPLICABILITY | prescribed demand exceeds inlet N2 capacity |
| 100.000000000 | 0.000100000000000 | 4.75972018304 | -123.801833902 | 0.000367562712481 | 0.000368292315019 | 7.47830857304e-07 | OUTSIDE_MODEL_APPLICABILITY | prescribed demand exceeds inlet N2 capacity |
| 100.000000000 | 0.000300000000000 | 1.58340097614 | -81.9015782416 | 1.07582723280e-05 | 5.25280176119e-06 | 5.50548358907e-06 | OUTSIDE_MODEL_APPLICABILITY | prescribed demand exceeds inlet N2 capacity |
| 100.000000000 | 0.00100000000000 | 0.475803369582 | -519.647414832 | 0.00385192679213 | 0.00158406404063 | 0.00309822378397 | FAILED_CONSERVATION | one_or_more_current_species_or_nitrogen_balance_exceeds_threshold |
| 1000.00000000 | 1.00000000000e-05 | 1.23211679888 | -31553.5171624 | 0.809524751171 | 1.00003489191 | 0.283661169967 | OUTSIDE_MODEL_APPLICABILITY | prescribed demand exceeds inlet N2 capacity |
| 1000.00000000 | 3.00000000000e-05 | 133.886320420 | -685569.401406 | 0.164084620316 | 0.0337007147034 | 0.134717853232 | OUTSIDE_MODEL_APPLICABILITY | prescribed demand exceeds inlet N2 capacity |
| 1000.00000000 | 0.000100000000000 | 52.4863413992 | -2184.76517454 | 0.0155975569336 | 0.00251864288201 | 0.0130351100457 | OUTSIDE_MODEL_APPLICABILITY | prescribed demand exceeds inlet N2 capacity |
| 1000.00000000 | 0.000300000000000 | 15.8630566611 | -864.010016865 | 0.000100195509403 | 0.000112062710470 | 1.18683121689e-05 | OUTSIDE_MODEL_APPLICABILITY | prescribed demand exceeds inlet N2 capacity |
| 1000.00000000 | 0.00100000000000 | 4.58627233958 | -109487.220488 | 0.0397048725928 | 0.00688277014024 | 0.0330783891197 | OUTSIDE_MODEL_APPLICABILITY | prescribed demand exceeds inlet N2 capacity |

## Applicability, evidence gaps, and M03B readiness

M03A.3 does not contain electrode kinetics.
The current-to-species coupling is prescribed and one-way.
FE_prescribed is an input, not a prediction.
The model is not experimentally calibrated.
No failed or negative-concentration case was clipped or deleted.
M03A.3 does not authorize M03B.

Experimental conductivity, dissolved-N2 data, effective diffusivities, validated geometry/area, current distribution and independent ammonia/isotope measurements remain missing. Therefore no quantitative experimental prediction or optimization is authorized. Successful finalization/reload warning matches: 0; fatal matches: 0. The completed A-F batch encountered 1 recovered fatal event during the post-benchmark fine-state restoration because LU memory was insufficient; the failure log is preserved and a fresh COMSOL process recovered the same fine low-conversion state before final save. Encoding-sensitive English-pattern matches in that Chinese batch log: 0. No failed sweep row was removed.

RUN_STATE = SYNTHETIC_SMOKE_TEST
CALIBRATION_MODE = PROVISIONAL
COUPLING_MODE = PRESCRIBED_CURRENT_ONE_WAY
M03B_READY = FALSE