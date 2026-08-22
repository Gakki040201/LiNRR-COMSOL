# Paper V1 model freeze v1.1 corrective provenance report

## Outcome

This is the Paper V1 Model Freeze v1.1 corrective provenance release, based on scientific source commit `a9314f89ee4a79492b88948dff912dc885feb7d6` and source tag `m10a4-realcell-electrochemistry-v1`. The historical v1.0 tag `paper-v1-model-freeze` is retained unchanged. The accepted compact M10A4 model remains byte-identical at `FADEEA4D8ADF9E472B4B855B05E39C22EF418214C0F62FB0C1977ACA36DA667B` (807839751 bytes).

No scientific model parameter changed. No COMSOL model changed. No solve occurred. No numerical result changed. Corrections are limited to actual Git artifact provenance, panel-level figure dependencies, programmatic recovery of scientific values, independent figure QA, byte-identical deterministic rebuild verification, and long-term source-base ancestry semantics.

## Verification architecture

M01 flow, M02 conservative transport, and M03 current/Faraday regression evidence establish operator-level numerical gates. M10 transfers those operators to real CAD ancestry; M10A3 supplies accepted real-cell flow and neutral-species fields; M10A4 supplies reduced electroneutral ionic transport, current distribution, Li-equivalent Faraday diagnostics, spatial co-limitation, electrical-loss diagnostics, mesh comparison, and independent reload.

## Frozen numerical anchors

- Current conservation: `1.80512328405505E-15`
- Li conservation: `2.03040317664811E-09`
- BF4 conservation: `2.03040321979805E-09`
- Electroneutrality maximum absolute residual: `0`
- Cathode current-magnitude CV: `0.309354151871061`
- Faraday ledger maximum relative residual: `1.43543987022242E-16`
- Mesh maximum key difference: `0.0621400622644018` (PASS_WITH_LIMITATION; `colim_MIXED_UNCLASSIFIED`)
- Independent reload: `PASS`

## Claims and parameter authority

`CLAIM_EVIDENCE_MATRIX.csv` separates numerical verification, real geometry facts, real-cell model predictions, diagnostics, provisional sensitivities, experimental claims, and unsupported mechanistic claims. `PARAMETER_PROVENANCE.csv` preserves calibration boundaries: kappa is provisional, D_salt and t_plus require calibration, applied current is a sensitivity, and the donor field remains generic.

## Figure and SI package

Seven 2400x1500 PNG figures are generated from accepted CSVs and renderings. Panel-level dependencies carry byte hashes, build provenance records builder/source/output hashes, and `Audit_PaperV1_Figures.ps1` independently rebuilds into a temporary directory and requires byte-identical PNGs before reproducible status is granted.

## Limitations and experimental gaps

The model does not establish reaction mechanisms, FE, NH3 kinetics, full-cell voltage, or retained metallic-Li thickness. Quantitative real-cell validation requires run-specific conductivity/EIS, ionic transport, spatial concentration/current, product, and retained-Li evidence. Mesh comparison passes with a declared limitation; the medium model remains authoritative.

## Acceptance

Final acceptance is determined only by `scripts/windows/PaperV1_ModelFreezeAudit.ps1` after commit A and final manifest synchronization. The manifest separates the frozen source base from each artifact's actual introducing/latest commit and excludes its own recursive row.
