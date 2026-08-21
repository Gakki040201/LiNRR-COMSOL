# M10A4 Acceptance Evidence

This directory is the evidence package for the editable COMSOL 6.4 model at
`models/generated/LiNRR_M10A4_ionic_current_li_plating.mph`.

- Final MPH SHA256: `FADEEA4D8ADF9E472B4B855B05E39C22EF418214C0F62FB0C1977ACA36DA667B`
- Final MPH size: `807839751` bytes
- Pre-audit checkpoint SHA256: `E51EFEBA7AD3DF67FE0759D4A279A6C7B82758508E58D519EE7658888E321887`
- Accepted A4B checkpoint SHA256: `C91A7B417259A552EAC027B37ABDBC114E5C6CC06E1435FFF640EB7FF2FCE714`
- Accepted A4C checkpoint SHA256: `69520C78330FB5157C74C9C96949759956A6F347445CDAD40E1986FDA69EB0FC`
- Accepted A4D checkpoint SHA256: `C833E9D657560D294886136F4872F9DF8C1988E05765E3D9F7EBA0DE866BC04B`
- Accepted A4E checkpoint SHA256: `84419695F470B4B42BE8CBAE83ACDE0A2FD7A396F363C6DF4BF02843622CDE4E`

The inventories and `model_tree.json` were exported read-only from the final MPH.
The PNGs were rendered by COMSOL from stored result dependencies. The authoritative
CSV tables are copied from `results/tables/`; historical failed-attempt tables stay
in the run/results tree and are not competing final evidence.

The immutable 3.3 GB pre-audit checkpoint preserves the full accepted archive.
The published MPH is a separately generated LFS-safe compact artifact retaining
full `sol15`, stationary `sol19`/`sol20`, and final-state `sol21`; its complete
required-result reload passed both before and after official-path promotion.

Acceptance summary:

- A4A current closure: `1.80512328405505e-15`
- A4B Li/BF4 residuals: `2.03040317664811e-9` / `2.03040321979805e-9`
- Raw ion range: `957.945310917445` to `5622.11679840875 mol/m^3`
- A4C reaction-plane area: `0.003844 m^2`
- A4D maximum Faraday-ledger residual: `1.43543987022242e-16`
- A4E threshold set: `0.40, 0.50, 0.60`; category closure is unity
- Mesh maximum key difference: `0.0621400622644018` (`PASS_WITH_LIMITATION`)
- Independent reload: `PASS`, no solve, zero fatal/warning hits
- Prohibited physics: none in the final model

The coarse mesh's localized warnings are reported in
`M10A4_mesh_convergence.csv`; the final accepted medium model is warning-free.
M10A4 is not experimental validation, a Li-NRR kinetic model, an FE prediction,
or a real retained-Li thickness prediction.
