# M10A3 GitHub acceptance interface

This directory is the GitHub-readable inspection interface for
`models/generated/LiNRR_M10A3_real_species_transport.mph`. Review the evidence in
this order:

1. `inactive_cavity_audit.csv` and `M10A3_gas_path_symmetry.csv` establish the
   real fluid-domain membership and mirrored N2/H2 geometry before species physics.
2. `model_tree.json` and the component, geometry, selection, physics, study,
   dataset, and result inventories establish the independently reloadable COMSOL
   structure and stable tags.
3. `parameter_inventory.csv` and `M10A3_calibration_required.csv` establish units,
   provenance, and intentionally unresolved experimental inputs.
4. The flow, transport, conservation, and mesh-convergence tables contain the
   numerical acceptance gates.
5. Representative PNGs show the physical cell and real spatial solution context.

## Interpretation classes

- **REAL / PHYSICAL** means a field is solved on a selected domain or boundary of
  the imported cell CAD (or is displayed in the unchanged physical assembly).
  `comp_species_n2_real`, `comp_species_h2_real`, and
  `comp_species_liq_real` are in this class.
- **REDUCED** means the geometry is an explicit lower-dimensional engineering
  control volume. SSC Darcy/wetting support, legacy phenomenological N2 transfer,
  external Pipe Flow, and the equivalent downstream NH3 PFA line are not continuous
  3D cell geometry.
- **PROVISIONAL** means the governing solve is real but at an uncalibrated
  sensitivity value. In particular, `Q_liq_sweep` is not a laboratory-calibrated
  liquid flow, so the reported RTD is a numerical sensitivity and not an
  experimental RTD.
- **NUMERICAL_VERIFICATION_ONLY** means an artificial input exists only to close
  and test a transport calculation. The prescribed cathodic NH3 source and tracer
  pulse do not predict Li-NRR rate, NH3 yield, or Faradaic efficiency. The fixed
  trace-positive liquid reference and conservative mesh-Peclet diffusion are also
  numerical discretization inputs, not experimental properties.

## Scientific boundaries

N2 gas and dissolved N2 are separate dependent variables. H2 bulk-liquid
dissolution is intentionally absent because electrolyte-specific solubility data
are unavailable. The donor remains generic `proton_donor` because the available
Manual and project schema do not uniquely identify it. Homogenized SSC properties,
Henry partitioning, and liquid flow remain provisional or calibration-required and
are never relabeled as measured.

All reported concentration minima are raw COMSOL values. No clipping, `abs`, or
`max(c,0)` is used. Real-liquid N2 and NH3 use the solved exponential definition
`c=1e-4 mol/m^3*exp(z)`; the trace reference is retained in the conservation ledger
and is not hidden after solution. The audit's explicit significant-negativity
tolerance is `1e-8 mol/m^3`. Species conservation requires relative residual no
greater than `1e-6`. Coarse/medium key differences above 10% block acceptance.

No Secondary/Tertiary Current Distribution, Nernst-Planck ionic model, Li plating,
Butler-Volmer, SEI, Li3N, Li-NRR, HER, HOR, or Faradaic-efficiency physics is present.
Acceptance is numerical and structural only, not experimental validation.

## Final closure result (2026-08-15)

All five M10A3 transport stages pass their unchanged numerical gates. The accepted
medium tracer solution is preserved in
`runs/M10A3/20260815_181029_transient_closure/checkpoint_07_rtd_medium_accepted.mph`
and is included in `models/generated/LiNRR_M10A3_real_species_transport.mph`.

The failed tau/300 audit was diagnosed before recomputation. Piecewise Simpson
integration changed the saved outlet mass by only `5.22e-9` relative, and the
tau/300-to-tau/600 outlet-mass change was only `8.94012494475816e-9`; outlet
temporal quadrature was therefore not the source of the `3.406701906534549e-6`
failure. The original injected-mass ledger used nominal `Q_liq_sweep`, whereas the
Flux Danckwerts boundary uses the solved boundary-normal velocity. On the accepted
medium mesh the actual integrated inlet flow is `1.66667104670018e-8 m^3/s`.
Numerical integration of the formal inlet flux gives `5.00001314010052e-7 mol`,
and the analytical sin-squared pulse using that actual flow gives
`5.00001314010053e-7 mol`; their relative difference is
`1.48230376217094e-15`.

The tau/600 post-pulse output has 3,703 points at
`dt=3.45724531430096 s`. Its raw tracer minimum is
`9.67239532760858e-29 mol/m^3`, instantaneous relative residual is
`1.09961115379377e-8`, and cumulative relative residual is
`7.87929544513639e-7`. All satisfy the unchanged gates. The accepted mean RTD is
`2869.92588647136 s`, versus nominal `2074.34718848247 s`; t10, t50, and t90 are
`732.825475219163 s`, `2209.34970261211 s`, and `6000.82769586463 s`.
The coarse-to-medium mean difference is `0.00828680513910211` (0.829%), below the
10% hard gate.

The final MPH was independently reloaded. All required physical-cell, gas,
dissolved-N2, interface-availability, proton-donor, NH3, tracer, and downstream
plots ran without warnings. Accepted RTD E(t) and F(t) are stored as native COMSOL
Results tables (`tbl_rtd_e`, `tbl_rtd_f`); each reloaded with 3,703 finite rows and
no undefined values.

For GitHub LFS packaging, the final MPH retains the accepted `sol18` medium-mesh
solution sequence with all 121 dense-pulse field states and 151 representative
post-pulse/final field states (272 stored field snapshots total). The complete
3,703-point accepted outlet history, E(t), and F(t) remain in the native Results
tables and CSV ledger. Only redundant legacy solver caches, including the
intermediate liquid-flow cache after its dependent species fields were solved,
were cleared; the independently reloaded required plots and tables all pass. The
unpruned accepted solution remains preserved in the timestamped checkpoint above.
