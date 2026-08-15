# M10A3 real-geometry neutral-species transport contract

## Scope

M10A3 is an editable COMSOL 6.4 derivative of the immutable M10A2R model. It adds real-CAD N2 and H2 channel transport, real electrolyte-domain neutral-species transport, a prescribed numerical NH3 source and downstream closure, and a numerical real-cell tracer RTD. It does not add electrochemistry, Li plating, Li-NRR/HER/HOR kinetics, current distribution, Faradaic efficiency, or yield prediction.

The immutable input is `models/generated/LiNRR_M10A2R_manual_reconciled.mph`, SHA-256 `B105EBEAF85582E63389427DFAD7320187FA4A472043E207B5363E258E94CEA7`. The current-collector and chamber STEP files are also hash-gated before and after every build stage. Generated files never overwrite M10A2R.

## Geometry truth and inactive cavities

The physical cell retains all real CAD. Domain membership is audited before species physics is created. Empty CAD structures that are not used by the experiment are classified `INERT_REAL_CAD_FEATURE` and remain present in `comp_cell_physical` while being excluded from N2, H2, liquid, and species selections. M10A3 is prohibited from continuing if any such cavity belongs to or connects with a selected main fluid component.

N2 is the negative/cathode/SSC side; H2 is the positive/anode/PtAuSSC side. The two channel domains are derived from the same real STEP and mirrored construction. Volume, wetted surface, hydraulic-diameter metric, port areas, topology, and connected-component count must agree within numerical geometry tolerance. Equal volumetric flow, not molecular weight, controls the bulk mean velocity in the matched geometry.

## Transport architecture

`LiNRR_M10A3_RealSpeciesTransport` uses one parameterized gas-species builder for N2 and H2. It copies the solved real flow components, reconstructs stable named selections, and applies the same advection-diffusion operator, mesh method, conservation ledger, and result pattern. Species identity, properties, interface, and source/sink parameters are the intended differences.

`comp_species_n2_real` explicitly represents `N2_g` in the real gas channel. `N2_dissolved` is a separate dependent variable in `comp_species_liq_real`; the two variables are never conflated. The liquid-side interfacial transfer is a homogenized SSC resistance with explicit porosity, tortuosity, saturation factor, and Henry-type equilibrium parameters. Those empirical inputs remain `PROVISIONAL_SENSITIVITY` or `CALIBRATION_REQUIRED`. Wetting information is reduced-model support and is not described as pore-scale 3D wetting.

The real-liquid N2 and NH3 equations use conservative weak-form advection-diffusion on the copied exact electrolyte CAD and formal same-component `withsol` coupling to the solved liquid-flow dataset. Concentrations are represented as `c_transport_zero_feed*exp(z)`, with the explicitly reported `1e-4 mol/m^3` trace feed classified `NUMERICAL_VERIFICATION_ONLY`. This preserves positivity in the solved variables; it is not postprocessing clipping. A fixed conservative mesh-Peclet diffusion coefficient (`beta_species_numdiff=0.5`) is likewise `NUMERICAL_VERIFICATION_ONLY`, is recorded in the parameter inventory, and was not fitted to a desired concentration field. The prescribed interfacial source is smoothly started over `tau_species_nom/100`, while its final amplitude is unchanged.

`comp_species_h2_real` mirrors the shared operator to the PtAuSSC side and computes gas/porous-interface availability only. Electrolyte-specific H2 solubility is absent, so bulk-liquid H2 dissolution is intentionally not predicted and remains `CALIBRATION_REQUIRED`.

The project record does not uniquely establish a proton-donor identity. The dependent variable therefore remains generic `proton_donor`, classified `CALIBRATION_REQUIRED`. It is transported without reaction consumption.

NH3 uses a cathode-boundary source classified `NUMERICAL_VERIFICATION_ONLY`. It verifies transport from the cell into the electrolyte and outlet. The external liquid line is an explicitly reduced 1D, volume-preserving PFA network connected by strict molar-flow transfer; it is not presented as continuous 3D CAD. No FE, NH3 yield, or Li-NRR rate is inferred.

## RTD and numerical gates

The tracer solve is a genuine time-dependent advection-diffusion calculation in the selected real electrolyte CAD domain with a finite inlet pulse. `Q_liq_sweep` remains `PROVISIONAL_SENSITIVITY`, so the result is a numerical RTD rather than an experimentally calibrated RTD. The exported response includes outlet concentration/flow, normalized `E(t)`, cumulative `F(t)`, mean, variance, standard deviation, t10/t50/t90, and comparison with `V/Q`.

All stationary species and tracer fields retain their raw values. No `max(c,0)`, `abs(c)`, or hard clipping is permitted. The positivity audit reports raw minima and treats only values below the documented numerical-zero tolerance of `-1e-8 mol/m^3` as significant negative concentration.

Every species has an explicit conservation ledger. Relative residuals must not exceed `1e-6`. Coarse/medium comparisons cover N2 flux and reaction-plane mean, H2 interface availability, NH3 outlet flux, and RTD mean time. Differences up to 5% pass, 5--10% are limitations, and values above 10% block acceptance.

## Acceptance and archival

Acceptance requires Java compilation, timestamped single-process COMSOL batch runs, seven restart checkpoints, final MPH creation, fatal-log scanning, required-plot execution after independent reload, evidence inventories and representative PNG export, immutable input hash rechecks, and Git LFS archival. Passing is numerical and structural acceptance only; it is not experimental validation.
