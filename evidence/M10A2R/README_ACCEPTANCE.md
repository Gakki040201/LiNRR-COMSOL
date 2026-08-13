# M10A2R GitHub acceptance interface

This directory is the primary GitHub-readable inspection interface for the COMSOL MPH.

Review in this order:

1. `model_tree.json` and `component_inventory.csv` for REAL/PHYSICAL versus REDUCED model separation.
2. `geometry_feature_provenance.csv` for every geometry feature and the real-CAD origin of the visible flared port profile.
3. `selection_inventory.csv`, `physics_inventory.csv`, `study_inventory.csv`, `dataset_inventory.csv`, and `result_inventory.csv` for stable tags and reloadable model structure.
4. `parameter_inventory.csv` for units and provenance language.
5. `numerical_invariance.csv` for the unchanged M10A2 mathematical baseline.
6. PNGs for physical assembly, PFA stubs, real fluid domains, physical SSC, and reduced Darcy/wetting contexts.

Key interpretation rules:

- `Physical Cell 3D` excludes 20 cm 1D Pipe Flow routes and all reduced rectangles.
- PFA gas stubs are hollow OD 3 mm / ID 2 mm and are visualization-only.
- No PEEK internal geometry is modeled.
- The visible flared port profile belongs to the original current-collector STEP B-rep; it is not a synthetic PEEK cone.
- Darcy and wetting are reduced through-plane models, not the 60 mm × 60 mm physical SSC geometry.
- No General Extrusion mapping is claimed: a stable coordinate mapping was not established, and constant replication would misrepresent the reduced solution as 3D-resolved wetting. The reduced plots remain independent beside the true-scale SSC context view.
- No M10A3 species physics is present or authorized by this evidence package.
