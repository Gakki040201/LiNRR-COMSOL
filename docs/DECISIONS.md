# Modeling Decision Log

## D0001 — Automation interface

Decision: COMSOL Java API is the primary automation interface.

Reason:
- Full model control;
- no MATLAB dependency for the core path;
- output remains an editable MPH;
- suitable for Codex, Git, and batch execution.

Alternative:
- LiveLink for MATLAB, retained as an optional analysis layer after license confirmation.

## D0002 — Model construction strategy

Decision: Incremental M00–M10 construction.

Reason:
- isolates numerical failures;
- supports conservation and mesh checks;
- prevents an uninterpretable all-at-once multiphysics model.

## D0003 — Model truth hierarchy

1. Raw experimental data;
2. calibrated parameters with uncertainty;
3. literature parameters with exact conditions;
4. provisional assumptions;
5. qualitative hypotheses.

No lower level may be presented as a higher level.

## D0004 — M01 numerical smoke-test flow model

Decision: M01 is a stationary, single-phase, two-dimensional Laminar Flow model only.

Implementation:
- coordinate-based named selections are used for the electrolyte domain, inlet, outlet, and both walls;
- the inlet uses COMSOL's fully developed laminar condition with average velocity `uin`;
- the mapped mesh has 100 elements along `Lcell` and 200 elements through `Hcell`;
- acceptance requires a boundary-integrated mass-balance relative error no greater than `1e-4`.

Parameter provenance:
- `rho_el = 900[kg/m^3]` and `mu_el = 3[mPa*s]` are **PROVISIONAL — numerical smoke test only**;
- `Qliq = 1 mL/min` is stored as the exactly equivalent `1[cm^3/min]` because COMSOL 6.4 rejected the literal `mL` token as an unknown unit during a load/evaluation check.

Limitations:
- passing M01 is numerical smoke-test acceptance, not experimental validation or mesh independence;
- no species transport, electrochemistry, porous media, two-phase flow, SEI, HOR, or heat transfer is included.

## D0005 — M01.1 independent numerical audit

Decision: Keep the accepted `LiNRR_M01_Flow.java` and M01 MPH unchanged; perform the
mesh, cross-section, maximum-location, and mass-flow postprocessing audit in the
independent `LiNRR_M01_Audit.java` derivative.

Findings:
- `uin=Qliq/(Hcell*Wcell)` is the volumetric cross-sectional mean velocity used by
  the fully developed Laminar Inflow condition, not the inlet maximum velocity;
- coarse/medium/fine mapped meshes change both streamwise and height resolution
  (40x20, 80x80, and 160x200);
- the fine-grid middle-channel ratio is `u_center/u_mean=1.50003749`, consistent
  with the 2D parallel-plate reference value 1.5;
- the global speed maximum occurs in the outlet region, not in the true channel
  middle; the outlet vicinity retains a larger ratio because of the outlet end effect;
- default and explicitly eighth-order mass-flow integrations agree to numerical
  precision. The need for high height resolution in the accepted M01 balance is
  therefore a discrete flow/outlet-boundary error, not a low postprocessing
  integration order.

Limitation: this is numerical mesh/conservation evidence, not experimental validation.

## D0006 — M02 provisional N2/NH3 transport smoke test

Decision: M02 adds only Transport of Diluted Species to the M01 Laminar Flow
geometry and velocity field. The cathode uses a phenomenological first-order wall
law `rN2=kN2*cN2` with the enforced stoichiometry `rNH3=2*rN2`.

Flux convention:
- COMSOL General Inward Flux is positive into the liquid;
- N2 consumption uses `J0_N2=-rN2`;
- NH3 generation uses `J0_NH3=+rNH3`.

Parameter status:
- `DN2=2e-9[m^2/s]`, `DNH3=2e-9[m^2/s]`, `cN2_in=5[mol/m^3]`, and
  `kN2=1e-5[m/s]` are **PROVISIONAL — numerical smoke test only**;
- these values are not assigned to or claimed representative of a real
  Diglyme/LiBF4 electrolyte;
- the wall law is phenomenological and has no experimental calibration range.

Numerical policy:
- concentrations are never clipped;
- parameter combinations below `-1e-5*cN2_in` are retained and marked FAILED;
- the two independent nitrogen-atom balances must each be no greater than `1e-4`;
- M02 passing means numerical smoke-test acceptance only, not experimental validation.

## D0007 — M02.1 conservative transport and positivity audit

Decision: M03 remains blocked. The M02.1 accepted base uses COMSOL TDS conservative
convection (`AdvancedSettings/ConvectiveTerm=cons`), a confirmed
`Inflow/BoundaryConditionType=FluxDanckwerts` inlet, `Outflow`, streamline plus
crosswind consistent stabilization, second-order concentration shape functions,
and a 350 x 200 mapped mesh.

Reason:
- M02 already used COMSOL's generated `tds.ntflux_cN2` and `tds.ntflux_cNH3`
  variables, but its nonconservative convection form produced individual-species
  errors of about 0.38% and 1.58%; nitrogen-element cancellation hid this defect;
- conservative convection without stabilization closed species balances but had
  large high-Peclet oscillations;
- coarse stabilized cases suppressed oscillations but did not pass the physical
  total-flux tolerance;
- the selected second-order 350 x 200 case is the first tested combination that
  passes both base species balances and the specified FAILED-level concentration
  threshold.

Boundary interpretation:
- the physical total flux is `N_i = u*c_i - D_i*grad(c_i)`;
- the reported COMSOL normal total flux is positive outward, while inlet molar
  rates are reported positive into the reactor;
- a zero NH3 feed concentration does not imply zero inlet diffusion, so NH3
  reverse diffusion is retained and quantified;
- Danckwerts inflow is preferred for the continuous-flow liquid reactor because
  it prescribes incoming feed flux without using a fixed boundary concentration
  to conceal back diffusion.

Numerical acceptance:
- base N2 and NH3 individual-species errors are below `1e-4`;
- both nitrogen-element errors are below `1e-4`;
- base negative concentration is WARNING-level, not FAILED-level;
- all 25 scan rows are retained and classified; failed rows are numerical/model
  applicability findings, not proof of a physical mass-transfer limit.

Limitations: all transport properties and the phenomenological wall law remain
provisional and uncalibrated. M02.1 is numerical acceptance, not experimental
validation, microscopic-mechanism evidence, or authorization to start M03.

## D0008 — Decoupled M03A primary-current numerical baseline

Decision: keep the coupled M03 milestone blocked and add an independent M03A model
that solves only primary current distribution in a uniform electrolyte.

API evidence and implementation:
- the installed COMSOL 6.4 Electrochemistry Module example reported physics type
  `PrimaryCurrentDistribution`;
- an actual construction probe confirmed the direct, no-kinetics boundary feature
  types `ElectrolyteCurrent` and `ElectrolytePotential`;
- M03A applies `j_app=Icell/Aelec` at the full upper anode, fixes electrolyte
  potential to 0 V at the lower cathode, and leaves both side boundaries insulated;
- no `ElectrodeReaction` feature or empirical kinetic law is present.

Sign convention:
- reported boundary current is the signed outward-normal integral of electrolyte
  current density;
- positive specified `Icell` enters through the anode, so the anode integral is
  negative and the cathode integral is positive;
- signs are checked before magnitudes are compared.

Numerical policy:
- the independent 40x20, 80x40, and 160x80 mapped-mesh audit selects 40x20 as
  the lowest-cost passing mesh for this exactly linear full-electrode problem;
- all 25 conductivity-current combinations are retained and checked against
  `R=Hcell/(kappa_el*Aelec)`;
- this coarse-mesh choice must not be carried into nonuniform or coupled models
  without a new audit.

Parameter status and limitation:
- `kappa_el=0.5 S/m` and `Icell=100 mA` are **PROVISIONAL — numerical smoke test
  only** and are not assigned to a real LiBF4/Diglyme/EtOH electrolyte;
- M03A is a pure-ohmic numerical benchmark, not a Li-NRR mechanism model,
  reaction model, calibration, or experimental validation;
- Secondary Current Distribution remains gated on traceable conductivity,
  geometry/effective area, EIS/cell/WE/CE voltage data, and effective cathode and
  HOR kinetic evidence.

## D0009 — M03A.1 calibration gate and resistance de-embedding

Decision: preserve M03A as a frozen numerical baseline and implement M03A.1 as an
independent primary-current calibration, resistance-ledger, and uncertainty
framework. M03A.1 does not add reaction kinetics or Secondary Current
Distribution.

Calibration policy:
- total EIS HFR is never identified with electrolyte resistance without evidence;
- electrolyte resistance is de-embedded by subtracting traceable fixture,
  contact, membrane, and other series terms;
- direct conductivity and HFR-derived conductivity remain independent estimates;
- a difference greater than 10% is a `CALIBRATION_CONFLICT` and does not trigger
  automatic selection;
- area and electrode-spacing definitions are explicit readiness items, and an
  area-basis mismatch blocks calibration;
- incomplete data produce a reproducible `SYNTHETIC_SMOKE_TEST` or
  `EXPERIMENTAL_INPUT_INCOMPLETE` state, never `EXPERIMENTALLY_CALIBRATED`.

Uncertainty policy: report both first-order analytical propagation and a
fixed-seed Monte Carlo calculation with rejection of nonphysical samples. The
provisional M03A values and any assumed smoke-test uncertainty are not
experimental evidence and cannot support paper-level quantitative conclusions.

## D0010 — M00.2 parameterized-geometry selection audit

Decision: preserve the frozen M00/M01/M01.1 files and implement M00.2 as an
independent no-physics Java model. Coordinate-based Box selections are rebuilt
and measured after scaling `Lcell`, `Hcell`, and `Wcell` independently by 0.5
and 2.0, in addition to the baseline case.

Acceptance policy:
- the electrolyte domain and four boundary selections must each map to exactly
  one entity in every case;
- selection measures must match the parameter-derived area or length and remain
  positive;
- geometry, mesh, MPH save, and independent-process MPH reload must all pass;
- any missing or multiple selection mapping is `FAILED_SELECTION_MAPPING`.

Limitation: the audit verifies parameterization, named-selection mapping, and
serialization only. All dimensions remain provisional, and the result is
numerical verification rather than experimental validation.

## D0011 — M01.2 parallel-plate analytical closure

Decision: rebuild the frozen M01 model in an isolated timestamped staging root,
load that MPH as an independent derivative, and leave every frozen M00/M01/M01.1
source and output unchanged. M01.2 uses the same stationary Laminar Flow physics
and named selections, raises only the fluid discretization order to 2, and uses
the required mapped 40x20, 80x40, and 160x80 element families.

Outlet interpretation:
- the frozen zero-pressure outlet is solved first as a diagnostic truncation
  reference; its global pressure drop and direct outlet mass discrepancy are
  retained rather than hidden;
- analytical acceptance uses COMSOL's fully developed outlet with average exit
  pressure 0 Pa, which closes the ideal two-dimensional parallel-plate problem;
- mid-channel center velocity and pressure gradient are evaluated at `x/L=0.5`;
  no global maximum velocity is substituted for the center value;
- the global inlet-outlet pressure drop is reported separately from the local
  middle-channel pressure gradient.

Analytical reference:
- `Umean=Q/(Hcell*Wcell)`;
- `u(y)=6*Umean*(y/Hcell)*(1-y/Hcell)` and `umax/Umean=1.5`;
- `dp/dx=-12*mu*Umean/Hcell^2`;
- `delta_p=12*mu*Lcell*Umean/Hcell^2`;
- `abs(tau_wall)=6*mu*Umean/Hcell`.

Parameter status and limitation: geometry, density, viscosity, and flow remain
**PROVISIONAL**. Passing M01.2 is a synthetic numerical closure and mesh check,
not experimental validation, calibration, or authorization to enter M03B.

## D0012 — M02.2 conservative transport verification

Decision: keep M03A.2 and M03B blocked and verify the conservative transport
operator independently before adding any further physical layer. M02.2 contains
no electrode kinetics and does not modify the frozen M00–M02.1 implementations.

Benchmark architecture:
- A verifies that conservative convection with a divergence-free prescribed
  velocity, Danckwerts inflow, Outflow, and no-flux walls preserves uniform N2
  and zero NH3 without creating or losing either species;
- B isolates diffusion, concentration-boundary signs, and outward-normal flux
  signs against an exactly linear one-dimensional solution;
- C uses a strictly positive manufactured solution to verify convection,
  diffusion, volumetric-source sign, units, global source-flux closure, and
  observed mesh convergence;
- D derives from an isolated timestamped rebuild of frozen M02.1 and verifies
  the cathode N2/NH3 signs, 2:1 stoichiometry, individual-species balances,
  nitrogen-atom balance, and three-grid stability at low Damköhler number;
- E retains all 25 Pe–Da cases, including failures, to map the numerical range
  of applicability without treating solver failure as a physical transport limit.

Equation and sign convention: the stationary conservative equation is
`div(N_i)=R_i`, with physical total flux
`N_i=u*c_i-D_i*grad(c_i)`. For
`c_exact=c_ref[1+a*sin(pi*x/Lcell)*sin(pi*y/Hcell)]` and constant
`u=(U_mms,0)`, the manufactured source is
`R_exact=-D_mms*laplacian(c_exact)+U_mms*dc_exact/dx`. COMSOL General
Inward Flux is positive into the liquid, while `tds.ntflux_*` and reported
boundary-normal total flux are positive outward. Reported inlet flow is made
positive into the reactor; outlet flow is positive out; cathode N2 consumption
and NH3 generation are positive magnitudes. A zero NH3 feed concentration does
not imply zero inlet diffusion, so reverse diffusion remains in every balance.

Boundary interpretation: Danckwerts inflow prescribes incoming feed flux without
forcing the total inlet diffusion to zero. Outflow supplies the downstream
transport boundary. Individual N2 and NH3 balances are acceptance checks in
their own right; nitrogen-element closure cannot be used to cancel or conceal
opposite single-species errors.

Numerical acceptance: wall-model individual-species and nitrogen-element
balances use a `1e-4` relative tolerance. Benchmark C reports a separate
Dirichlet-boundary flux reconstructed from the finite-element concentration
gradient; because that value is not the conservative boundary reaction flux,
its fine-grid truncation tolerance is `2e-4`. Benchmark D requires the fine grid
to meet conservation and the medium-to-fine change in conversion, NH3 outlet,
and N2 consumption to remain below `0.5%`; coarse and medium rows are retained
even when they do not yet meet the final-grid tolerance.

Dimensionless and classification policy: `Pe_H=Umean*Hcell/DN2` and
`Da_H=kN2*Hcell/DN2`. Conservation failure has priority over concentration
classification. With `cN2_in` as the scale, values below `-1e-5*cN2_in` are
`FAILED_NEGATIVE_CONCENTRATION`; values from `-1e-5*cN2_in` up to but excluding
`-1e-8*cN2_in` are `WARNING_NUMERICAL_OSCILLATION`; conservative values at or
above `-1e-8*cN2_in` are `PASS`. Nonfinite results or solver failure are
`OUTSIDE_MODEL_APPLICABILITY`. Every row and its original failure reason is
retained. Concentrations are never clipped by `max`, conditional zeroing, or
postprocessing replacement.

Parameter status and limitation: all geometry, transport properties, feed
concentrations, velocities, and the phenomenological first-order wall law remain
**PROVISIONAL — numerical verification only**. Passing M02.2 is not experimental
validation, calibration, a microscopic-mechanism inference, or permission to
start M03B. M03B remains blocked.

## D0013 — M03A.2 synthetic EIS and calibration-input verification

Decision: preserve every M00–M03A.1 and M02.2 file as a frozen baseline and add
an independent Windows PowerShell 5.1 verification layer for synthetic EIS/HFR
and calibration inputs. M03A.2 creates no COMSOL physics and does not modify an
MPH file.

EIS policy:
- imported numeric fields use InvariantCulture and explicit frequency and
  impedance units;
- rows are normalized to descending frequency while retaining their original
  CSV line number and stable order for equal frequencies;
- duplicate frequencies, inductive high-frequency points, and replicate
  disagreement are retained and reported rather than deleted;
- blank, illegal, NaN, or infinite numeric values block that case;
- the first high-frequency point is reported only as
  `FIRST_POINT_DIAGNOSTIC` with method class `LAB_SCREENING_HEURISTIC`; it is
  never promoted automatically to HFR;
- accepted HFR values come from `HIGH_FREQUENCY_INTERCEPT` or a complete
  `USER_DEFINED_EQUIVALENT_CIRCUIT` fit-result contract. The latter reads the
  declared circuit, software, fit file, finite positive `Rs`, standard error,
  fit quality, source, status, and notes; it never substitutes the first point.
- high-frequency coverage jointly checks candidate count and span, proximity to
  the real axis, crossing or fit support, inductive and duplicate artifacts,
  residual quality, and complete-semicircle model dependence.

Calibration-input policy: total HFR remains distinct from electrolyte
resistance. Every fixture, contact, membrane, and other series term must be
present before de-embedding; a nonpositive de-embedded value blocks the path and
is never clipped. Direct and HFR-derived conductivity remain independent, with
differences above 10% classified `CALIBRATION_CONFLICT`. Area-basis mismatch and
more than 1% nonphysical Monte Carlo inputs are blocking findings.

State and limitation: generated fixtures are fixed-definition `SYNTHETIC`
data. C023 is an incomplete `EXPERIMENTAL` manifest regression and has no
fabricated file. The CPE case is method-dependent and does not demonstrate
experimental CPE identifiability. Passing M03A.2 means verification of import,
estimation, bookkeeping, analytical and fixed-seed Monte Carlo uncertainty,
and readiness gates only. It is not experimental calibration, does not select
a conductivity truth, and does not authorize M03B. The enforced terminal state is `RUN_STATE =
SYNTHETIC_SMOKE_TEST`, `CALIBRATION_MODE = PROVISIONAL`, and `M03B_READY =
FALSE`.

## D0014 — M03A.3 prescribed-current stoichiometric coupling verification

Decision: preserve every M00–M03A.2 file as a frozen baseline, except for this
append-only decision, and add an independent M03A.3 numerical verification MPH.
The model uses the verified COMSOL 6.4 Primary Current Distribution interface
and a prescribed-current, one-way Faradaic stoichiometric mapping from positive
cathodic-current magnitude to N2 consumption and NH3 generation.

Scope and interpretation:
- the current distribution is pure ohmic Primary Current Distribution;
- the current-to-species coupling is prescribed and one-way;
- the model contains no reaction kinetics or electrode-reaction feature;
- `FE_prescribed` is a prescribed synthetic input, not a predicted FE;
- synthetic conductivity and current inputs are not experimental properties or
  predicted operating current;
- no experimental calibration is performed;
- passing M03A.3 does not authorize M03B.

Integrity policy: raw signed current and the sign-audited positive cathodic
magnitude are both retained, and no `abs()` is used to conceal direction.
Negative concentrations are not clipped. Every failed, warning, or
outside-applicability sweep point is retained with its original classification
and reason. `Theta > 1` is only the prescribed-current supply-capacity boundary,
not a physical reaction-performance limit.

## D0015 — M03A.4 calibration intake framework and synthetic parameter-transfer dry-run

Decision: add an independent experimental-input contract and a synthetic-only
parameter-transfer dry-run while preserving every M00–M03A.3 file. No
experimental data are fabricated: the formal experimental template remains
incomplete, no substitute EIS file is generated, and the experimental
completeness gate remains false.

The dry-run parameters are explicitly `SYNTHETIC`. The M03A.3 MPH is loaded
read-only and its physics tree is audited before and after transfer. A new
derived M03A.4 MPH is saved without overwriting the M03A.3 source. Direct and
HFR-derived conductivity remain independent, missing series terms never default
to zero, area definitions are not assumed equal, and nonphysical Monte Carlo
samples are rejected without clipping or repair.

Scope limitation: the derivative retains Primary Current Distribution and the
prescribed one-way Faraday mapping. It contains no electrode kinetics,
Butler–Volmer law, Secondary or Tertiary Current Distribution, or
ElectrodeReaction. A passing synthetic dry-run is not experimental calibration
and does not authorize M03B.

The formal transfer evidence is a combined nonidentity, current-run full solve:
geometry and named selections are rebuilt and audited, the mesh is rebuilt, old
solution data are cleared, and `spf`, `tds`, and `cd` are activated together.
Formal Faraday closure uses actual TDS cathode and inlet/outlet fluxes;
`m033_n2_imposed_cath` and `m033_nh3_imposed_cath` remain separately labeled
`ALGEBRAIC_STOICHIOMETRIC_MAPPING`. All downstream consumers use the hashed
canonical resolved-input artifact, and publication occurs only after independent
reload and threshold acceptance.

Derived conductivity provenance is a deterministic seven-parent contract:
accepted replicate-consensus HFR, fixture, contact, membrane, other-series
resistance, electrode spacing, and EIS area. Values, uncertainties, units,
origins, acceptance states, and full source hashes are canonically serialized;
their SHA-256 digest is the conductivity source identity. Analytical
uncertainty is resolved before the conductivity row is emitted, and both the
analytical and fixed-seed Monte Carlo artifacts are bound to the final resolved
artifact hash.

Formal publication is a two-phase release. A timestamped immutable bundle and
full file manifest pass independent COMSOL reload and integration audits before
one same-volume atomic active-release pointer is changed. Compatibility paths
are secondary copies with retained backups and rollback; they are not release
identity. The MPH stores the complete per-parameter value, unit, origin, source
SHA-256, resolved-artifact SHA-256, case, quantity, and target metadata, all of
which are compared after reload.
